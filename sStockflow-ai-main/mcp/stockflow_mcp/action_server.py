"""Human-gated StockFlow proposal MCP tools.

These tools create and transition proposals. They never post inventory,
dispatch a vehicle, receive goods, or execute a financial transaction.
"""
from mcp.server.fastmcp import FastMCP

from .common import CORE_API, get_json, post_json

mcp = FastMCP("StockFlow Action MCP", host="127.0.0.1", port=8203, stateless_http=True, json_response=True)


@mcp.tool()
def create_transfer_proposal(tenant_id: str, access_token: str, idempotency_key: str, sku_id: str, quantity: float, source_warehouse_id: str, destination_warehouse_id: str, reason: str, unit_cost: float | None = None, transport_cost: float | None = None, recommendation_evidence: str | None = None) -> dict:
    """Creates a DRAFT transfer proposal. Does not submit, approve or execute it."""
    return post_json(f"{CORE_API}/api/v1/actions/transfers", {"skuId": sku_id, "quantity": quantity, "sourceWarehouseId": source_warehouse_id, "destinationWarehouseId": destination_warehouse_id, "reason": reason, "unitCost": unit_cost, "transportCost": transport_cost, "recommendationEvidence": recommendation_evidence}, tenant_id, access_token, {"Idempotency-Key": idempotency_key})


@mcp.tool()
def create_purchase_proposal(tenant_id: str, access_token: str, idempotency_key: str, sku_id: str, quantity: float, destination_warehouse_id: str, reason: str, supplier_reference: str | None = None, unit_cost: float | None = None, recommendation_evidence: str | None = None) -> dict:
    """Creates a DRAFT purchase proposal. Does not place an order."""
    return post_json(f"{CORE_API}/api/v1/actions/purchases", {"skuId": sku_id, "quantity": quantity, "destinationWarehouseId": destination_warehouse_id, "reason": reason, "supplierReference": supplier_reference, "unitCost": unit_cost, "recommendationEvidence": recommendation_evidence}, tenant_id, access_token, {"Idempotency-Key": idempotency_key})


@mcp.tool()
def list_proposals(tenant_id: str, access_token: str, status: str = "", proposal_type: str = "") -> list | dict:
    """Lists tenant proposals and their current approval status."""
    params = {key: value for key, value in {"status": status, "type": proposal_type}.items() if value}
    return get_json(f"{CORE_API}/api/v1/actions/proposals", params, tenant_id, access_token)


@mcp.tool()
def submit_proposal(tenant_id: str, access_token: str, proposal_id: str, comment: str = "") -> dict:
    """Submits the caller's draft proposal for independent human approval."""
    return post_json(f"{CORE_API}/api/v1/actions/proposals/{proposal_id}/submit", {"comment": comment}, tenant_id, access_token)


@mcp.tool()
def approve_proposal(tenant_id: str, access_token: str, proposal_id: str, comment: str = "") -> dict:
    """Approves a pending proposal only when RBAC permits and caller is not proposer. Does not execute it."""
    return post_json(f"{CORE_API}/api/v1/actions/proposals/{proposal_id}/approve", {"comment": comment}, tenant_id, access_token)


@mcp.tool()
def reject_proposal(tenant_id: str, access_token: str, proposal_id: str, comment: str) -> dict:
    """Rejects a pending proposal with a required human reason."""
    return post_json(f"{CORE_API}/api/v1/actions/proposals/{proposal_id}/reject", {"comment": comment}, tenant_id, access_token)


if __name__ == "__main__":
    mcp.run(transport="streamable-http")
