from datetime import date, timedelta
from math import sin, pi
from fastapi import FastAPI
from pydantic import BaseModel, Field

app = FastAPI(title="StockFlow Forecasting Service", version="0.1.0")

class ForecastRequest(BaseModel):
    tenant_id: str
    warehouse_id: str
    sku_id: str
    horizon_days: int = Field(default=30, ge=1, le=180)

@app.get("/health")
def health() -> dict:
    return {"status": "ok", "service": "forecasting-service"}

@app.post("/api/v1/forecast")
def forecast(request: ForecastRequest) -> dict:
    start = date.today() + timedelta(days=1)
    points = []
    for index in range(request.horizon_days):
        baseline = 42.0
        weekly = 1 + 0.12 * sin(2 * pi * index / 7)
        predicted = round(baseline * weekly, 2)
        points.append({
            "date": (start + timedelta(days=index)).isoformat(),
            "predictedDemand": predicted,
            "lowerBound": round(predicted * 0.85, 2),
            "upperBound": round(predicted * 1.15, 2),
        })
    return {
        "forecastRunId": "SPRINT1-DEMO",
        "modelVersion": "seasonal-baseline-0.1",
        "tenantId": request.tenant_id,
        "warehouseId": request.warehouse_id,
        "skuId": request.sku_id,
        "confidence": 0.78,
        "points": points,
        "note": "Sprint 1 baseline; real model evaluation is implemented in Sprint 3."
    }
