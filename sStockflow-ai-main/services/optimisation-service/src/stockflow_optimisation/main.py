from fastapi import FastAPI
from pydantic import BaseModel, Field

app = FastAPI(title="StockFlow Optimisation Service", version="0.1.0")

class TransferRequest(BaseModel):
    tenant_id: str
    sku_id: str
    source_warehouse_id: str
    destination_warehouse_id: str
    source_available: float = Field(ge=0)
    source_safety_stock: float = Field(ge=0)
    destination_shortage: float = Field(ge=0)
    transport_cost: float = Field(ge=0)
    unit_value: float = Field(ge=0)

@app.get("/health")
def health() -> dict:
    return {"status": "ok", "service": "optimisation-service"}

@app.post("/api/v1/recommend-transfer")
def recommend_transfer(request: TransferRequest) -> dict:
    transferable = max(request.source_available - request.source_safety_stock, 0)
    quantity = min(transferable, request.destination_shortage)
    protected_value = quantity * request.unit_value
    net_benefit = protected_value - request.transport_cost
    return {
        "action": "TRANSFER" if quantity > 0 and net_benefit > 0 else "NO_ACTION",
        "quantity": round(quantity, 2),
        "sourceWarehouseId": request.source_warehouse_id,
        "destinationWarehouseId": request.destination_warehouse_id,
        "estimatedProtectedValue": round(protected_value, 2),
        "transportCost": round(request.transport_cost, 2),
        "netExpectedBenefit": round(net_benefit, 2),
        "constraintsChecked": ["SOURCE_SAFETY_STOCK", "DESTINATION_SHORTAGE"],
        "note": "Sprint 1 deterministic scaffold; OR-Tools constraints are implemented in Sprint 3."
    }
