"""Authenticated transport host for the StockFlow domain MCP."""
from contextlib import asynccontextmanager
from datetime import datetime, timezone
import json
import logging
from uuid import uuid4

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware

from .auth import get_auth_context
from .config import ALLOWED_ORIGINS, AUTH_DISABLED_FOR_LOCAL, ENABLE_ACTIONS, MCP_SERVERS
from .mcp_client import MCPHub
from .models import ChatRequest, ChatResponse, Evidence

hub = MCPHub(MCP_SERVERS)
logger = logging.getLogger("stockflow-copilot")


def _mcp_text(call_result) -> str:
    return "\n".join(getattr(item, "text", str(item)) for item in getattr(call_result, "content", []))


def _mcp_json(call_result) -> dict:
    structured = getattr(call_result, "structuredContent", None)
    if structured is None:
        structured = getattr(call_result, "structured_content", None)
    if isinstance(structured, dict):
        value = structured.get("result", structured)
        if isinstance(value, dict):
            return value
    text = _mcp_text(call_result).strip()
    try:
        value = json.loads(text)
        if isinstance(value, dict):
            return value.get("result", value) if isinstance(value.get("result", value), dict) else value
    except json.JSONDecodeError:
        pass
    return {}


@asynccontextmanager
async def lifespan(_: FastAPI):
    await hub.connect()
    yield
    await hub.close()


app = FastAPI(title="StockFlow Copilot Host", version="0.3.0", lifespan=lifespan)
app.add_middleware(
    CORSMiddleware,
    allow_origins=ALLOWED_ORIGINS,
    allow_credentials=True,
    allow_methods=["GET", "POST"],
    allow_headers=["Authorization", "Content-Type", "X-Tenant-ID"],
)


@app.get("/health")
async def health() -> dict:
    return {
        "status": "UP",
        "architecture": "domain-mcp",
        "dataSource": "live-api-only",
        "primaryTool": "answer_stockflow_question",
        "mcpTools": hub.tool_names,
        "actionsEnabled": ENABLE_ACTIONS,
    }


@app.post("/api/v1/copilot/chat", response_model=ChatResponse)
async def chat(payload: ChatRequest, request: Request) -> ChatResponse:
    context = get_auth_context(request)
    correlation_id = str(uuid4())
    try:
        call_result = await hub.call_tool("answer_stockflow_question", {
            "question": payload.message,
            "tenant_id": context.tenant_id,
            "access_token": context.access_token or "",
            "selected_warehouse_id": payload.selectedWarehouseId or "",
            "selected_sku_id": payload.selectedSkuId or "",
        })
        domain_result = _mcp_json(call_result)
        if not domain_result:
            raise ValueError("The StockFlow domain MCP returned no structured result")
        answer = str(domain_result.get("answer") or "No readable answer was returned.")
        as_of = str(domain_result.get("asOf") or datetime.now(timezone.utc).isoformat())
        tools = [str(item) for item in domain_result.get("toolsUsed", ["answer_stockflow_question"])]
        warnings = [str(item) for item in domain_result.get("warnings", [])]
        warnings.append("Read-only answer; no inventory action was executed.")
        answer_type = "NO_DATA" if domain_result.get("intent") in ("forecast.no_data", "action.requires_workflow", "clarification.required") else "GROUNDED_EXPLANATION"
        return ChatResponse(
            answer=answer,
            answerType=answer_type,
            confidence="GROUNDED",
            toolsUsed=tools,
            evidence=[Evidence(
                source=str(domain_result.get("source") or "StockFlow Domain MCP"),
                asOf=as_of,
                freshness="CURRENT",
                correlationId=correlation_id,
            )],
            warnings=warnings,
        )
    except Exception as exc:
        logger.exception("Copilot request failed; tenant=%s correlation_id=%s", context.tenant_id, correlation_id)
        details = f" {type(exc).__name__}: {exc}" if AUTH_DISABLED_FOR_LOCAL else ""
        return ChatResponse(
            answer=f"The StockFlow domain MCP could not complete this request.{details} No value was inferred.",
            answerType="ERROR",
            toolsUsed=["answer_stockflow_question"],
            warnings=[f"Correlation ID: {correlation_id}"],
        )
