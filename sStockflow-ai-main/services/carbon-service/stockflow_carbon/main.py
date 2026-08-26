import os
from math import ceil
from typing import Literal

from fastapi import FastAPI, Header, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from geopy.distance import geodesic
from pydantic import BaseModel, Field


def configured_origins() -> list[str]:
    raw = os.getenv(
        "ALLOWED_ORIGINS",
        "http://localhost:4200,http://127.0.0.1:4200,https://stockflow-ai-oveyj.pages.dev",
    )
    return [origin.strip() for origin in raw.split(",") if origin.strip()]


app = FastAPI(
    title="StockFlow Route and Carbon Service",
    description="Explainable route, vehicle-capacity and carbon calculations for StockFlow AI.",
    version="1.0.0",
)
app.add_middleware(
    CORSMiddleware,
    allow_origins=configured_origins(),
    allow_credentials=True,
    allow_methods=["GET", "POST"],
    allow_headers=["Content-Type", "Authorization", "X-Tenant-ID"],
)


# Prototype factors are returned with every calculation for auditability.
# Production factors must be replaced with verified fleet- or fuel-specific values.
VEHICLE_FACTORS_KG_PER_KM = {
    "electric": 0.05,
    "cng": 0.20,
    "diesel": 0.27,
    "petrol": 0.25,
}
FUEL_FACTORS_KG_PER_LITRE = {
    "diesel": 2.68,
    "petrol": 2.31,
    "cng": 2.00,
    "electric": 0.0,
}


def require_tenant(tenant_id: str) -> str:
    normalized = tenant_id.strip()
    if not normalized:
        raise HTTPException(status_code=400, detail="X-Tenant-ID is required")
    return normalized


class RouteCandidate(BaseModel):
    id: str
    lane: str
    stops: list[str] = Field(min_length=2)
    vehicle: str
    loadKg: float = Field(ge=0)
    capacityKg: float = Field(gt=0)
    baselineKm: float = Field(gt=0)
    priority: str = "Medium"
    status: str = "Draft"


class OptimiseRoutesRequest(BaseModel):
    objective: Literal[
        "Balanced cost and carbon",
        "Lowest transport cost",
        "Lowest carbon impact",
        "Fastest service recovery",
    ] = "Balanced cost and carbon"
    vehicleType: str = "All eligible vehicles"
    routes: list[RouteCandidate] = Field(min_length=1, max_length=100)


class CoordinateRouteRequest(BaseModel):
    originLat: float = Field(ge=-90, le=90)
    originLon: float = Field(ge=-180, le=180)
    destinationLat: float = Field(ge=-90, le=90)
    destinationLon: float = Field(ge=-180, le=180)
    mileageKmPerLitre: float = Field(gt=0)
    fuelType: Literal["Diesel", "Petrol", "Electric", "CNG"]


class CarbonRequest(BaseModel):
    distanceKm: float = Field(ge=0)
    vehicleType: Literal["diesel", "petrol", "electric", "cng"]
    loadKg: float = Field(default=0, ge=0)
    capacityKg: float = Field(default=1, gt=0)
    trips: int = Field(default=1, ge=1)
    baselineDistanceKm: float | None = Field(default=None, ge=0)


class TransferRecommendationRequest(BaseModel):
    sourceAvailable: float = Field(ge=0)
    sourceSafetyStock: float = Field(ge=0)
    destinationShortage: float = Field(ge=0)
    vehicleCapacity: float = Field(gt=0)
    distanceKm: float = Field(gt=0)
    vehicleType: Literal["diesel", "petrol", "electric", "cng"] = "diesel"
    unitValue: float = Field(default=0, ge=0)
    transportCost: float = Field(default=0, ge=0)


def vehicle_family(vehicle: str) -> str:
    normalized = vehicle.lower()
    return next((name for name in VEHICLE_FACTORS_KG_PER_KM if name in normalized), "diesel")


def load_adjustment(load_kg: float, capacity_kg: float) -> float:
    ratio = min(max(load_kg / capacity_kg, 0.0), 1.0)
    return 0.5 + 0.5 * ratio


def carbon_result(
    distance_km: float,
    vehicle_type: str,
    load_kg: float,
    capacity_kg: float,
    trips: int = 1,
    baseline_distance_km: float | None = None,
) -> dict:
    factor = VEHICLE_FACTORS_KG_PER_KM[vehicle_type]
    adjustment = load_adjustment(load_kg, capacity_kg)
    emissions = distance_km * factor * adjustment * trips
    baseline = (baseline_distance_km if baseline_distance_km is not None else distance_km) * factor * adjustment * trips
    avoided = max(baseline - emissions, 0.0)
    return {
        "distanceKm": round(distance_km, 2),
        "vehicleType": vehicle_type,
        "emissionFactorKgPerKm": factor,
        "loadFactor": round(adjustment, 4),
        "trips": trips,
        "emissionsKgCo2e": round(emissions, 2),
        "baselineEmissionsKgCo2e": round(baseline, 2),
        "emissionsAvoidedKgCo2e": round(avoided, 2),
        "method": "distance × vehicle factor × load adjustment × trips",
        "classification": "prototype estimate",
    }


def optimisation_ratio(objective: str, priority: str) -> float:
    base = {
        "Balanced cost and carbon": 0.88,
        "Lowest transport cost": 0.90,
        "Lowest carbon impact": 0.84,
        "Fastest service recovery": 0.94,
    }[objective]
    if priority.lower() == "critical" and objective == "Fastest service recovery":
        return 0.97
    return base


@app.get("/health")
def health() -> dict:
    return {"status": "UP", "service": "stockflow-carbon-route", "version": app.version}


@app.get("/api/v1/carbon/emission-factors")
def emission_factors(tenant_id: str = Header(alias="X-Tenant-ID")) -> dict:
    return {
        "tenantId": require_tenant(tenant_id),
        "vehicleFactorsKgCo2ePerKm": VEHICLE_FACTORS_KG_PER_KM,
        "fuelFactorsKgCo2ePerLitre": FUEL_FACTORS_KG_PER_LITRE,
        "status": "prototype-configurable",
    }


@app.post("/api/v1/carbon/calculate")
def calculate_carbon(request: CarbonRequest, tenant_id: str = Header(alias="X-Tenant-ID")) -> dict:
    return {"tenantId": require_tenant(tenant_id), **carbon_result(
        request.distanceKm,
        request.vehicleType,
        request.loadKg,
        request.capacityKg,
        request.trips,
        request.baselineDistanceKm,
    )}


@app.post("/api/v1/routes/distance-estimate")
def coordinate_route(request: CoordinateRouteRequest, tenant_id: str = Header(alias="X-Tenant-ID")) -> dict:
    distance = geodesic(
        (request.originLat, request.originLon),
        (request.destinationLat, request.destinationLon),
    ).km
    fuel_type = request.fuelType.lower()
    fuel_used = 0.0 if fuel_type == "electric" else distance / request.mileageKmPerLitre
    emissions = fuel_used * FUEL_FACTORS_KG_PER_LITRE[fuel_type]
    return {
        "tenantId": require_tenant(tenant_id),
        "distanceKm": round(distance, 2),
        "fuelUsedLitres": round(fuel_used, 2),
        "emissionsKgCo2e": round(emissions, 2),
        "travelTimeHours": round(distance / 50, 2),
        "method": "geodesic prototype estimate; replace with a road-network matrix for dispatch",
    }


@app.post("/api/v1/routes/optimise")
def optimise_routes(request: OptimiseRoutesRequest, tenant_id: str = Header(alias="X-Tenant-ID")) -> dict:
    results = []
    for route in request.routes:
        ratio = optimisation_ratio(request.objective, route.priority)
        optimised_km = max(round(route.baselineKm * ratio, 1), 1.0)
        selected_vehicle = request.vehicleType if request.vehicleType != "All eligible vehicles" else route.vehicle
        family = vehicle_family(selected_vehicle)
        carbon = carbon_result(
            optimised_km,
            family,
            route.loadKg,
            route.capacityKg,
            baseline_distance_km=route.baselineKm,
        )
        speed = 58 if request.objective == "Fastest service recovery" else 50
        duration_hours = optimised_km / speed
        cost_rate = {"electric": 28, "cng": 34, "diesel": 42, "petrol": 45}[family]
        results.append({
            **route.model_dump(),
            "optimizedKm": optimised_km,
            "duration": f"{int(duration_hours)}h {round((duration_hours % 1) * 60):02d}m",
            "costInr": round(optimised_km * cost_rate),
            "co2Kg": carbon["emissionsKgCo2e"],
            "co2SavedKg": carbon["emissionsAvoidedKgCo2e"],
            "vehicleFamily": family,
            "status": "Optimized" if route.status == "Draft" else route.status,
            "explanation": [
                f"Ranked for {request.objective.lower()}.",
                f"Load utilisation is {round(route.loadKg / route.capacityKg * 100, 1)}%.",
                f"Estimated using {family} factor {carbon['emissionFactorKgPerKm']} kg CO2e/km.",
                "Human approval is required before dispatch.",
            ],
        })
    results.sort(key=lambda item: (item["co2Kg"], item["costInr"]))
    return {
        "tenantId": require_tenant(tenant_id),
        "objective": request.objective,
        "routes": results,
        "solver": "explainable deterministic prototype",
        "limitations": ["No live traffic", "No road-network time windows", "OR-Tools VRP is the next solver phase"],
    }


@app.post("/api/v1/transfers/recommend")
def recommend_transfer(request: TransferRecommendationRequest, tenant_id: str = Header(alias="X-Tenant-ID")) -> dict:
    transferable = max(request.sourceAvailable - request.sourceSafetyStock, 0)
    quantity = min(transferable, request.destinationShortage, request.vehicleCapacity)
    trips = ceil(quantity / request.vehicleCapacity) if quantity > 0 else 0
    carbon = carbon_result(
        request.distanceKm,
        request.vehicleType,
        quantity,
        request.vehicleCapacity,
        max(trips, 1),
    )
    protected_value = quantity * request.unitValue
    net_benefit = protected_value - request.transportCost
    action = "TRANSFER" if quantity > 0 and net_benefit >= 0 else "NO_ACTION"
    return {
        "tenantId": require_tenant(tenant_id),
        "action": action,
        "quantity": round(quantity, 2),
        "trips": trips,
        "estimatedProtectedValue": round(protected_value, 2),
        "transportCost": round(request.transportCost, 2),
        "netExpectedBenefit": round(net_benefit, 2),
        "carbon": carbon,
        "constraintsChecked": ["SOURCE_SAFETY_STOCK", "DESTINATION_SHORTAGE", "VEHICLE_CAPACITY"],
        "approvalRequired": True,
    }
