from mcp.server.fastmcp import FastMCP
from .common import CORE_API, get_json
from .domain_engine import answer_question

mcp = FastMCP("StockFlow Data MCP", host="127.0.0.1", port=8201, stateless_http=True, json_response=True)

@mcp.tool()
def answer_stockflow_question(question: str, tenant_id: str, access_token: str = "", selected_warehouse_id: str = "", selected_sku_id: str = "") -> dict:
    """Resolve and answer a StockFlow business question using live tenant APIs.

    Owns product/location resolution, aggregations, risk and forecast evidence,
    replenishment policy, route/carbon assumptions, and read-only safeguards.
    """
    return answer_question(question, tenant_id, access_token, selected_warehouse_id, selected_sku_id)

@mcp.tool()
def get_inventory_summary() -> dict:
    """Returns the authorised dashboard inventory summary. Read-only."""
    return get_json(f"{CORE_API}/api/v1/dashboard/overview")

@mcp.tool()
def search_locations() -> list | dict:
    """Returns every authorised warehouse/location in the tenant."""
    return get_json(f"{CORE_API}/api/v1/warehouses")

@mcp.tool()
def search_products() -> list | dict:
    """Returns every authorised medicine/product SKU in the tenant."""
    return get_json(f"{CORE_API}/api/v1/skus")

@mcp.tool()
def get_current_inventory(warehouse_id: str = "", sku_id: str = "") -> list | dict:
    """Returns current batch-level inventory, optionally filtered by location or product."""
    params = {key: value for key, value in {"warehouseId": warehouse_id, "skuId": sku_id}.items() if value}
    return get_json(f"{CORE_API}/api/v1/inventory/batches", params)

@mcp.tool()
def find_near_expiry_inventory(days: int = 60, limit: int = 100) -> list | dict:
    """Returns read-only inventory expiry risks within the requested number of days."""
    return get_json(f"{CORE_API}/api/v1/risks/expiry", {"days": days, "limit": limit})

@mcp.tool()
def find_stockout_risks(limit: int = 100) -> list | dict:
    """Returns read-only stockout risks for the authorised tenant."""
    return get_json(f"{CORE_API}/api/v1/risks/stockout", {"limit": limit})

@mcp.tool()
def get_inventory_risks(limit: int = 100, risk_type: str = "", severity: str = "") -> list | dict:
    """Returns read-only inventory risk records."""
    params = {key: value for key, value in {"limit": limit, "type": risk_type, "severity": severity}.items() if value}
    return get_json(f"{CORE_API}/api/v1/risks/inventory", params)

@mcp.tool()
def get_data_freshness() -> dict:
    """Returns the latest authorised dashboard snapshot timestamp."""
    overview = get_json(f"{CORE_API}/api/v1/dashboard/overview")
    return {"sourceSystem": "StockFlow Core API", "asOf": overview.get("asOf") if isinstance(overview, dict) else None, "tenantId": "server-scoped"}

@mcp.tool()
def get_demand_summary(window_days: int = 30) -> dict:
    """Returns tenant-scoped historical demand totals for the requested window."""
    return get_json(f"{CORE_API}/api/v1/analytics/demand/summary", {"windowDays": window_days})

@mcp.tool()
def get_demand_by_sku(window_days: int = 30, limit: int = 100) -> list | dict:
    """Returns actual demand by SKU for ranking and planning."""
    return get_json(f"{CORE_API}/api/v1/analytics/demand/skus", {"windowDays": window_days, "limit": limit})

@mcp.tool()
def get_latest_forecasts(warehouse_id: str = "", sku_id: str = "", limit: int = 100) -> list | dict:
    """Returns latest persisted forecasts with horizon, confidence and model evidence."""
    params = {key: value for key, value in {"warehouseId": warehouse_id, "skuId": sku_id, "limit": limit}.items() if value != ""}
    return get_json(f"{CORE_API}/api/v1/forecasts/latest", params)

@mcp.tool()
def get_forecast_summary() -> dict:
    """Returns the latest forecast run summary and confidence distribution."""
    return get_json(f"{CORE_API}/api/v1/forecasts/summary")

@mcp.tool()
def get_forecast_accuracy() -> dict:
    """Returns latest forecast accuracy metrics."""
    return get_json(f"{CORE_API}/api/v1/forecasts/accuracy-summary")

@mcp.tool()
def get_forecast_diagnostics(warehouse_id: str = "", sku_id: str = "", limit: int = 100) -> list | dict:
    """Returns model eligibility, evidence and diagnostic reasons."""
    params = {key: value for key, value in {"warehouseId": warehouse_id, "skuId": sku_id, "limit": limit}.items() if value != ""}
    return get_json(f"{CORE_API}/api/v1/forecasts/diagnostics", params)

@mcp.tool()
def get_stockout_projections(limit: int = 100) -> list | dict:
    """Returns products with forecasted stockout dates."""
    return get_json(f"{CORE_API}/api/v1/forecasts/stockout-projections", {"limit": limit})

@mcp.resource("stockflow://dashboard/overview")
def dashboard_overview() -> str:
    """Dashboard overview resource for the current authorised tenant."""
    return str(get_inventory_summary())

if __name__ == "__main__":
    mcp.run(transport="streamable-http")
