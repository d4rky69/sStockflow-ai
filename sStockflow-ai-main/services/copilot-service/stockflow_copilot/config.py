import os
from pathlib import Path
from dotenv import load_dotenv

load_dotenv(Path(__file__).resolve().parents[1] / ".env")


def env(name: str, default: str = "") -> str:
    return os.getenv(name, default)


GEMINI_API_KEY = env("GEMINI_API_KEY")
GEMINI_MODEL = env("GEMINI_MODEL", "gemini-2.5-flash")
# Authentication must fail closed unless local development explicitly opts out.
AUTH_DISABLED_FOR_LOCAL = env("AUTH_DISABLED_FOR_LOCAL", "false").lower() == "true"
DEV_TENANT_ID = env("DEV_TENANT_ID", "TEN-ACME-PHARMA")
DEFAULT_AUTHENTICATED_TENANT_ID = env("DEFAULT_AUTHENTICATED_TENANT_ID")
ENABLE_ACTIONS = env("STOCKFLOW_ENABLE_ACTIONS", "false").lower() == "true"
SUPABASE_URL = env("SUPABASE_URL")
SUPABASE_JWT_ISSUER = env("SUPABASE_JWT_ISSUER") or f"{SUPABASE_URL}/auth/v1"
SUPABASE_JWT_AUDIENCE = env("SUPABASE_JWT_AUDIENCE", "authenticated")
ALLOWED_ORIGINS = [
    origin.strip()
    for origin in env(
        "ALLOWED_ORIGINS",
        "http://localhost:4200,http://127.0.0.1:4200",
    ).split(",")
    if origin.strip()
]

MCP_SERVERS = {
    "stockflow_data": env("STOCKFLOW_DATA_MCP_URL", "http://127.0.0.1:8201/mcp"),
    "stockflow_intelligence": env("STOCKFLOW_INTELLIGENCE_MCP_URL", "http://127.0.0.1:8202/mcp"),
}
if ENABLE_ACTIONS:
    MCP_SERVERS["stockflow_action"] = env("STOCKFLOW_ACTION_MCP_URL", "http://127.0.0.1:8203/mcp")
