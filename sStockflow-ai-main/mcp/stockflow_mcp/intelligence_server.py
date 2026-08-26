from mcp.server.fastmcp import FastMCP
from .common import CARBON_API, FORECAST_API, OPTIMISATION_API, TENANT_ID, get_json, post_json

mcp = FastMCP("StockFlow Intelligence MCP", host="127.0.0.1", port=8202, stateless_http=True, json_response=True)

@mcp.tool()
def forecast_demand(warehouse_id: str, sku_id: str, horizon_days: int = 30) -> dict:
    """Calculates demand forecast. Computational only; no side effects."""
    return post_json(f"{FORECAST_API}/api/v1/forecast", {
        "tenant_id": TENANT_ID,
        "warehouse_id": warehouse_id,
        "sku_id": sku_id,
        "horizon_days": horizon_days,
    })

@mcp.tool()
def recommend_stock_transfer(
    sku_id: str,
    source_warehouse_id: str,
    destination_warehouse_id: str,
    source_available: float,
    source_safety_stock: float,
    destination_shortage: float,
    transport_cost: float,
    unit_value: float,
) -> dict:
    """Calculates a transfer candidate. It does not create or execute a transfer."""
    return post_json(f"{OPTIMISATION_API}/api/v1/recommend-transfer", {"tenant_id": TENANT_ID, "sku_id": sku_id, "source_warehouse_id": source_warehouse_id, "destination_warehouse_id": destination_warehouse_id, "source_available": source_available, "source_safety_stock": source_safety_stock, "destination_shortage": destination_shortage, "transport_cost": transport_cost, "unit_value": unit_value})

@mcp.tool()
def get_emission_factors() -> dict:
    """Returns configured, auditable vehicle and fuel emission factors."""
    return get_json(f"{CARBON_API}/api/v1/carbon/emission-factors")

@mcp.tool()
def calculate_carbon(distance_km: float, vehicle_type: str, load_kg: float, capacity_kg: float, trips: int = 1, baseline_distance_km: float | None = None) -> dict:
    """Calculates transparent prototype CO2e using distance, vehicle, load and capacity."""
    payload = {"distanceKm": distance_km, "vehicleType": vehicle_type, "loadKg": load_kg, "capacityKg": capacity_kg, "trips": trips}
    if baseline_distance_km is not None:
        payload["baselineDistanceKm"] = baseline_distance_km
    return post_json(f"{CARBON_API}/api/v1/carbon/calculate", payload)

@mcp.tool()
def optimise_transfer_route(origin: str, destination: str, load_kg: float, capacity_kg: float, baseline_km: float, vehicle: str = "diesel", objective: str = "Balanced cost and carbon", priority: str = "High") -> dict:
    """Ranks a transfer route for capacity, cost and carbon; never dispatches a vehicle."""
    return post_json(f"{CARBON_API}/api/v1/routes/optimise", {
        "objective": objective,
        "vehicleType": vehicle,
        "routes": [{"id": "copilot-candidate", "lane": f"{origin} to {destination}", "stops": [origin, destination], "vehicle": vehicle, "loadKg": load_kg, "capacityKg": capacity_kg, "baselineKm": baseline_km, "priority": priority, "status": "Draft"}],
    })

@mcp.tool()
def recommend_sustainable_transfer(source_available: float, source_safety_stock: float, destination_shortage: float, vehicle_capacity: float, distance_km: float, vehicle_type: str, unit_value: float, transport_cost: float) -> dict:
    """Calculates a read-only transfer recommendation with safety, capacity, financial and carbon constraints."""
    return post_json(f"{CARBON_API}/api/v1/transfers/recommend", {"sourceAvailable": source_available, "sourceSafetyStock": source_safety_stock, "destinationShortage": destination_shortage, "vehicleCapacity": vehicle_capacity, "distanceKm": distance_km, "vehicleType": vehicle_type, "unitValue": unit_value, "transportCost": transport_cost})

if __name__ == "__main__":
    mcp.run(transport="streamable-http")
