"""Business-ready, read-only StockFlow question engine used by MCP.

The engine owns domain resolution and calculations. The Copilot host must not
reimplement inventory, risk, forecast, transfer, route, or carbon logic.
"""
from __future__ import annotations

from collections import defaultdict
from datetime import datetime
from math import ceil
import re

from .common import CARBON_API, CORE_API, get_json, post_json


def normalise(value: object) -> str:
    text = str(value or "").lower()
    text = re.sub(r"(?<=\d)(?=[a-z])|(?<=[a-z])(?=\d)", " ", text)
    return re.sub(r"[^a-z0-9 ]+", " ", text).strip()


def result(answer: str, intent: str, tools: list[str], as_of: str | None = None, warnings: list[str] | None = None, data: object | None = None) -> dict:
    return {"answer": answer, "intent": intent, "toolsUsed": tools, "source": "StockFlow live APIs via MCP", "asOf": as_of, "warnings": warnings or [], "data": data}


def resolve(question: str, rows: list[dict], fields: tuple[str, ...]) -> dict | None:
    q = normalise(question)
    q_tokens = set(q.split())
    ignored = {"warehouse", "regional", "central", "distribution", "hub", "item", "tablet", "capsule", "sachet"}
    candidates: list[tuple[int, dict]] = []
    for row in rows:
        scores = []
        for field in fields:
            value = normalise(row.get(field))
            if not value:
                continue
            if value in q:
                scores.append(1000 + len(value))
                continue
            tokens = {token for token in value.split() if token not in ignored}
            overlap = tokens & q_tokens
            distinctive = {token for token in overlap if len(token) >= 4 and not token.isdigit()}
            if distinctive and (len(overlap) / max(len(tokens), 1) >= 0.5 or max(map(len, distinctive)) >= 6):
                scores.append(100 * len(overlap) + sum(len(token) for token in overlap))
        score = max(scores, default=0)
        if score:
            candidates.append((score, row))
    return max(candidates, key=lambda item: item[0])[1] if candidates else None


def money(value: float) -> str:
    return f"INR {value:,.2f}"


def quantity(row: dict) -> float:
    return float(row.get("usableQuantity", row.get("availableQuantity", 0)) or 0)


def inventory_value(row: dict) -> float:
    return quantity(row) * float(row.get("unitCost", 0) or 0)


def _inventory_answer(q: str, warehouses: list[dict], skus: list[dict], batches: list[dict], overview: dict) -> dict | None:
    as_of = str(overview.get("asOf") or max((row.get("snapshotDate", "") for row in batches), default=""))
    warehouse = resolve(q, warehouses, ("warehouseName", "city", "state", "warehouseId"))
    sku = resolve(q, skus, ("skuName", "skuId", "productId"))

    if any(term in q for term in ("overall inventory value", "total inventory value", "inventory value overall")):
        kpi = next((item for item in overview.get("kpis", []) if item.get("key") == "inventoryValue"), {})
        return result(f"The overall inventory value is {kpi.get('value', money(sum(inventory_value(row) for row in batches)))}.", "inventory.total_value", ["get_inventory_summary"], as_of)

    if ("warehouse" in q and "inventory value" in q and any(term in q for term in ("each", "every", "all", "wise"))):
        names = {row["warehouseId"]: row["warehouseName"] for row in warehouses}
        totals: dict[str, float] = defaultdict(float)
        for row in batches:
            totals[row["warehouseId"]] += inventory_value(row)
        lines = [f"- {names.get(key, key)}: {money(value)}" for key, value in sorted(totals.items(), key=lambda item: item[1], reverse=True)]
        return result("Usable inventory value by warehouse:\n" + "\n".join(lines), "inventory.value_by_warehouse", ["get_current_inventory", "search_locations"], as_of, data=totals)

    if "highest inventory value" in q:
        names = {row["warehouseId"]: row["warehouseName"] for row in warehouses}
        totals: dict[str, float] = defaultdict(float)
        for row in batches:
            totals[row["warehouseId"]] += inventory_value(row)
        key, value = max(totals.items(), key=lambda item: item[1])
        return result(f"{names.get(key, key)} has the highest usable-inventory value at {money(value)}.", "inventory.highest_value", ["get_current_inventory", "search_locations"], as_of)

    if "how many products" in q:
        count = len({row["skuId"] for row in batches if quantity(row) > 0})
        return result(f"{count} distinct products currently have usable inventory across all warehouses.", "inventory.product_count", ["get_current_inventory"], as_of)

    if "last updated" in q or "how fresh" in q:
        latest_movement = max((row.get("lastMovementAt", "") for row in batches), default="")
        return result(f"The inventory snapshot is dated {as_of}; the latest recorded stock movement is {latest_movement or 'unavailable'}.", "inventory.freshness", ["get_data_freshness", "get_current_inventory"], as_of)

    filtered = batches
    if warehouse:
        filtered = [row for row in filtered if row.get("warehouseId") == warehouse.get("warehouseId")]
    if sku:
        filtered = [row for row in filtered if row.get("skuId") == sku.get("skuId")]
    names = {row["warehouseId"]: row["warehouseName"] for row in warehouses}
    sku_names = {row["skuId"]: row["skuName"] for row in skus}

    if sku and ("which warehouse" in q or "which warehouses" in q or "where" in q):
        totals: dict[str, float] = defaultdict(float)
        for row in filtered:
            totals[row["warehouseId"]] += quantity(row)
        lines = [f"- {names.get(key, key)}: {value:,.0f} usable units" for key, value in totals.items() if value > 0]
        return result(f"{sku.get('skuName')} is available at:\n" + ("\n".join(lines) or "No warehouse with usable stock."), "inventory.product_locations", ["search_products", "get_current_inventory", "search_locations"], as_of)

    if sku and any(term in q for term in ("current stock", "available", "availability", "inventory")):
        total = sum(quantity(row) for row in filtered)
        by_warehouse: dict[str, float] = defaultdict(float)
        for row in filtered:
            by_warehouse[row["warehouseId"]] += quantity(row)
        lines = [f"- {names.get(key, key)}: {value:,.0f} usable units" for key, value in by_warehouse.items()]
        return result(f"Current usable stock of {sku.get('skuName')}: {total:,.0f} units.\n" + "\n".join(lines), "inventory.product_stock", ["search_products", "get_current_inventory"], as_of)

    if warehouse and ("batch" in q or "stock" in q or "inventory" in q):
        lines = [f"- {sku_names.get(row['skuId'], row['skuId'])}, batch {row['batchNumber']}: {quantity(row):,.0f} usable units, expires {row['expiryDate']}" for row in filtered[:30]]
        return result(f"Batches at {warehouse.get('warehouseName')} ({len(filtered)} total; showing {min(len(filtered), 30)}):\n" + ("\n".join(lines) or "No batches found."), "inventory.warehouse_batches", ["search_locations", "get_current_inventory"], as_of)
    return None


def _forecast_answer(q: str, warehouse: dict | None, sku: dict | None, tenant_id: str, access_token: str) -> dict:
    params = {"limit": 100}
    if warehouse:
        params["warehouseId"] = warehouse["warehouseId"]
    if sku:
        params["skuId"] = sku["skuId"]
    forecasts = get_json(f"{CORE_API}/api/v1/forecasts/latest", params, tenant_id, access_token)
    if not forecasts:
        target = " / ".join(filter(None, [sku.get("skuName") if sku else None, warehouse.get("warehouseName") if warehouse else None])) or "that selection"
        return result(f"No persisted forecast exists for {target}. I will not invent a prediction.", "forecast.no_data", ["get_latest_forecasts"], warnings=["Create a forecast run for this product and warehouse first."])
    if "highest" in q:
        row = max(forecasts, key=lambda item: float(item.get("totalForecastQuantity", 0)))
    else:
        row = forecasts[0]
    answer = (f"Forecast for {row['skuName']} at {row['warehouseName']}: {float(row['totalForecastQuantity']):,.2f} units over the persisted {row['horizonDays']}-day horizon "
              f"({float(row['averageDailyForecast']):,.2f}/day), using {row['selectedModel']} with {row['confidence']} confidence. "
              f"As of {row['asOfDate']}; WAPE {row['wape']}%; training samples {row['trainingSampleCount']}. "
              f"Reasons: {', '.join(row.get('diagnosticReasons', [])) or 'none supplied'}.")
    requested = re.search(r"(\d+)\s*days?", q)
    warnings = []
    if requested and int(requested.group(1)) != int(row["horizonDays"]):
        warnings.append(f"The question requested {requested.group(1)} days, but the latest persisted run contains {row['horizonDays']} days; no extrapolation was made.")
    return result(answer, "forecast.position", ["get_latest_forecasts"], str(row["asOfDate"]), warnings, row)


def _risk_answer(q: str, risks: list[dict], expiry: list[dict]) -> dict | None:
    if "expir" in q or "waste" in q:
        rows = expiry
        if not rows:
            return result("No near-expiry batches were returned for the current 60-day window.", "risk.expiry", ["find_near_expiry_inventory"])
        if "highest" in q and "loss" in q:
            row = max(rows, key=lambda item: float(item.get("inventoryValue", 0)))
            return result(f"Highest potential expiry exposure is {row['skuName']} batch {row.get('batchNumber')} at {row['warehouseName']}: {money(float(row.get('inventoryValue', 0)))}, expiring {row.get('expiryDate')}.", "risk.expiry_highest", ["find_near_expiry_inventory"], str(row.get("asOfDate")), data=row)
        total_units = sum(float(row.get("usableQuantity", 0) or 0) for row in rows)
        lines = [f"- {row['skuName']} batch {row.get('batchNumber')} at {row['warehouseName']}: {row.get('usableQuantity')} units, expires {row.get('expiryDate')}" for row in rows[:20]]
        warning = "Redistribution potential is an upper bound; destination demand and source safety stock must be checked before claiming avoided waste."
        return result(f"Near-expiry pool: {total_units:,.0f} usable units across {len(rows)} risk record(s).\n" + "\n".join(lines), "risk.expiry", ["find_near_expiry_inventory"], str(rows[0].get("asOfDate")), [warning], rows)

    if "excess" in q or "slow" in q:
        rows = [row for row in risks if "EXCESS" in row.get("riskType", "") or "SLOW" in row.get("riskType", "")]
    else:
        rows = [row for row in risks if row.get("riskType") == "STOCKOUT_RISK"]
        if "seven" in q or "7 day" in q:
            rows = [row for row in rows if float(row.get("daysOfCover", 999999)) <= 7]
    if not rows:
        return result("No matching inventory risks were returned by the current rules.", "risk.none", ["get_inventory_risks"])
    lines = [f"- {row['skuName']} at {row['warehouseName']}: {row['reason']}. Action: {row['recommendedAction']}" for row in rows[:20]]
    return result("Matching inventory risks:\n" + "\n".join(lines), "risk.inventory", ["get_inventory_risks"], str(rows[0].get("asOfDate")), data=rows)


def _replenishment_answer(q: str, risks: list[dict], skus: list[dict], selected_sku: dict | None = None) -> dict:
    stockout = [row for row in risks if row.get("riskType") == "STOCKOUT_RISK"]
    if selected_sku:
        stockout = [row for row in stockout if row.get("skuId") == selected_sku.get("skuId")]
    sku_map = {row["skuId"]: row for row in skus}
    lines = []
    for row in stockout[:15]:
        policy_target = float(row.get("averageDailyDemand30", 0)) * 14
        raw = max(policy_target - float(row.get("usableQuantity", 0)), 0)
        multiple = int(sku_map.get(row["skuId"], {}).get("reorderMultiple", 1) or 1)
        suggested = ceil(raw / multiple) * multiple if raw else 0
        lines.append(f"- {row['skuName']} at {row['warehouseName']}: {suggested:,.0f} units; {row['reason']}")
    return result("Read-only replenishment candidates using a 14-day cover target and SKU reorder multiples:\n" + ("\n".join(lines) or "No candidates."), "replenishment.recommend", ["find_stockout_risks", "search_products"], str(stockout[0].get("asOfDate")) if stockout else None, ["Planning recommendation only; supplier lead time and open orders are not yet available."], stockout)


def _transfer_answer(q: str, warehouses: list[dict], skus: list[dict], batches: list[dict], risks: list[dict], selected_sku: dict | None, destination: dict | None) -> dict:
    if not selected_sku:
        return result("Please specify the product or SKU for the transfer analysis. No quantity was inferred.", "transfer.needs_product", ["recommend_sustainable_transfer"], warnings=["Product is required."])
    if not destination:
        return result("Please specify the destination warehouse or city for the transfer analysis. No quantity was inferred.", "transfer.needs_destination", ["recommend_sustainable_transfer"], warnings=["Destination is required."])
    sku_id = selected_sku["skuId"]
    safety = float(selected_sku.get("minimumSafetyStock", 0) or 0)
    stock: dict[str, float] = defaultdict(float)
    for row in batches:
        if row.get("skuId") == sku_id:
            stock[row["warehouseId"]] += quantity(row)
    destination_id = destination["warehouseId"]
    destination_risk = next((row for row in risks if row.get("skuId") == sku_id and row.get("warehouseId") == destination_id), {})
    daily = float(destination_risk.get("averageDailyDemand30", 0) or 0)
    target = max(safety, daily * 14)
    shortage = max(target - stock.get(destination_id, 0), 0)
    candidates = []
    for warehouse in warehouses:
        wid = warehouse["warehouseId"]
        if wid == destination_id:
            continue
        surplus = max(stock.get(wid, 0) - safety, 0)
        candidates.append((min(surplus, shortage), surplus, warehouse))
    transferable, surplus, source = max(candidates, key=lambda item: item[0], default=(0, 0, {}))
    if transferable <= 0:
        return result(f"No safe transfer is currently recommended for {selected_sku['skuName']} to {destination['warehouseName']}. Destination usable stock is {stock.get(destination_id, 0):,.0f}, the calculated 14-day target is {target:,.0f}, and no source can cover a positive shortage while remaining above {safety:,.0f} safety stock.", "transfer.no_action", ["get_current_inventory", "find_stockout_risks"], warnings=["No action was executed."])
    remaining = stock[source["warehouseId"]] - transferable
    unit_cost = float(selected_sku.get("unitCost", 0) or 0)
    answer = (f"Recommended read-only transfer: {transferable:,.0f} units of {selected_sku['skuName']} from {source['warehouseName']} to {destination['warehouseName']}. "
              f"Source usable stock is {stock[source['warehouseId']]:,.0f}; it would retain {remaining:,.0f}, above the {safety:,.0f} safety stock. "
              f"Destination usable stock is {stock.get(destination_id, 0):,.0f} against a {target:,.0f} 14-day target. Inventory value protected is approximately {money(transferable * unit_cost)}. Human approval is required.")
    warnings = ["Transport cost, actual route distance, open orders and product shipment weight are not available, so purchase-versus-transfer net benefit is incomplete."]
    return result(answer, "transfer.recommend", ["get_current_inventory", "find_stockout_risks", "recommend_sustainable_transfer"], str(destination_risk.get("asOfDate") or ""), warnings, {"source": source, "destination": destination, "quantity": transferable, "sourceRemaining": remaining, "safetyStock": safety})


def _route_answer(q: str, tenant_id: str, access_token: str, warehouses: list[dict], skus: list[dict]) -> dict:
    quantity_match = re.search(r"(\d[\d,]*)\s*(?:units|kg)", q)
    requested_quantity = float(quantity_match.group(1).replace(",", "")) if quantity_match else None
    summary = get_json(f"{CORE_API}/api/v1/replenishment/transfer-recommendations", {"targetCoverDays": 30}, tenant_id, access_token)
    candidates = summary.get("recommendations", []) if isinstance(summary, dict) else []
    requested_warehouses = [row for row in warehouses if normalise(row.get("warehouseName")) in q or normalise(row.get("city")) in q]
    requested_sku = resolve(q, skus, ("skuName", "skuId", "productId"))
    if requested_warehouses:
        ids = {row["warehouseId"] for row in requested_warehouses}
        candidates = [row for row in candidates if row.get("sourceWarehouseId") in ids or row.get("destinationWarehouseId") in ids]
    if requested_sku:
        candidates = [row for row in candidates if row.get("skuId") == requested_sku.get("skuId")]
    if not candidates:
        return result("No live transfer recommendation matches that route or product. I will not invent a lane, distance or shipment quantity.", "route.no_data", ["get_transfer_recommendations"], warnings=["Generate a database-backed transfer recommendation first."])
    recommendation = candidates[0]
    units = requested_quantity or float(recommendation["recommendedQuantity"])
    load = units * 0.05
    compare = any(term in q for term in ("compare", "fastest", "cheapest", "lowest carbon", "road vehicles"))
    vehicles = ["diesel", "cng", "electric", "petrol"] if compare else ["diesel"]
    origin = recommendation["sourceWarehouseName"]
    destination = recommendation["destinationWarehouseName"]
    baseline = float(recommendation["distanceKm"])
    routes = [{"id": f"copilot-{vehicle}", "lane": f"{origin} to {destination}", "stops": [origin, destination], "vehicle": vehicle, "loadKg": load, "capacityKg": 6000.0, "baselineKm": baseline, "priority": recommendation["risk"].title(), "status": "Draft"} for vehicle in vehicles]
    payload = {"objective": "Balanced cost and carbon", "vehicleType": "All eligible vehicles" if compare else "diesel", "routes": routes}
    response = post_json(f"{CARBON_API}/api/v1/routes/optimise", payload, tenant_id, access_token)
    route = response["routes"][0]
    answer = (f"Read-only {origin} to {destination} route candidate for {units:,.0f} units: {route['optimizedKm']} km, {route['duration']}, cost {money(float(route['costInr']))}, "
              f"emissions {route['co2Kg']} kg CO2e, saving {route['co2SavedKg']} kg CO2e. Load {route['loadKg']} kg of {route['capacityKg']} kg. "
              f"Human approval is required. Explanations: {' '.join(route['explanation'])}")
    if compare:
        answer = "Vehicle/route comparison (sorted by carbon then cost):\n" + "\n".join(f"- {item['vehicleFamily']}: {item['optimizedKm']} km, {item['duration']}, {money(float(item['costInr']))}, {item['co2Kg']} kg CO2e" for item in response["routes"])
    warnings = [f"Distance and quantity come from live recommendation {recommendation['recommendationId']}; vehicle factors remain prototype estimates without live traffic or road time windows."]
    if "units" in q:
        warnings.append("A disclosed 0.05 kg representative unit weight was used; replace it with SKU shipment weight for dispatch.")
    return result(answer, "route.optimise", ["get_transfer_recommendations", "optimise_transfer_route"], str(recommendation.get("asOfDate") or ""), warnings, {"recommendation": recommendation, "routeComparison": response})


def answer_question(question: str, tenant_id: str, access_token: str = "", selected_warehouse_id: str = "", selected_sku_id: str = "") -> dict:
    q = normalise(question)
    if any(term in q for term in ("api key", "password", "access token", "secret key", "private key", "connection string", "credential")):
        return result("I cannot reveal credentials or secrets. Configure them only in the server secret manager.", "policy.secrets", ["security_policy_guard"], warnings=["No API was called."])
    if any(term in q for term in ("approve yourself", "approve it yourself", "bypass approval", "without approval", "execute immediately", "transfer immediately")):
        return result("I cannot approve or execute my own recommendation. An authorised human must review it.", "policy.approval", ["human_approval_guard"], warnings=["No action was executed."])
    if any(term in q for term in ("create a transfer proposal", "submit this proposal", "approval status")):
        return result("StockFlow now has a human-gated Action API for transfer and purchase proposals. This chat remains read-only until the explicit proposal form supplies the SKU, quantity, warehouses, reason and idempotency key; use the proposal workflow to create, submit or review an action. A proposer cannot approve their own proposal.", "action.requires_workflow", ["action_capability_guard"], warnings=["No action was executed from chat."])

    warehouses = get_json(f"{CORE_API}/api/v1/warehouses", tenant_id=tenant_id, access_token=access_token)
    skus = get_json(f"{CORE_API}/api/v1/skus", tenant_id=tenant_id, access_token=access_token)
    overview = get_json(f"{CORE_API}/api/v1/dashboard/overview", tenant_id=tenant_id, access_token=access_token)
    warehouse = resolve(q, warehouses, ("warehouseName", "city", "state", "warehouseId"))
    sku = resolve(q, skus, ("skuName", "skuId", "productId"))
    if not warehouse and selected_warehouse_id:
        warehouse = next((row for row in warehouses if row.get("warehouseId") == selected_warehouse_id), None)
    if not sku and selected_sku_id:
        sku = next((row for row in skus if row.get("skuId") == selected_sku_id), None)

    if "approved transfer" in q and ("sustainability" in q or "impact" in q):
        executions = get_json(f"{CORE_API}/api/v1/actions/transfer-executions", tenant_id=tenant_id, access_token=access_token)
        completed = [row for row in executions if row.get("status") == "RECEIVED"]
        carbon = sum(float(row.get("actualCarbonKg") or 0) for row in completed)
        cost = sum(float(row.get("actualTransportCost") or 0) for row in completed)
        return result(f"Completed approved transfers: {len(completed)}. Recorded actual transport cost: {money(cost)}; recorded actual emissions: {carbon:,.2f} kg CO2e.", "sustainability.executed_transfers", ["list_transfer_executions"], warnings=["Only completed executions with recorded actual values are included."], data=completed)
    if any(term in q for term in ("route", "vehicle", "carbon", "co2", "emission", "combined deliver", "fastest", "cheapest")):
        return _route_answer(q, tenant_id, access_token, warehouses, skus)
    if "forecast" in q or "predicted demand" in q or "prediction" in q:
        return _forecast_answer(q, warehouse, sku, tenant_id, access_token)

    risks = None
    expiry = None
    is_transfer = any(term in q for term in ("transfer stock", "transfer instead", "source warehouse", "after the transfer", "recommend this transfer", "transfer recommendation"))
    if any(term in q for term in ("stockout", "stock out", "risk", "excess", "slow", "expir", "waste", "reorder", "purchase", "replenish", "why did you recommend", "evidence", "alternative recommendation", "incorrect")) or is_transfer:
        risks = get_json(f"{CORE_API}/api/v1/risks/inventory", {"limit": 250}, tenant_id, access_token)
        expiry = get_json(f"{CORE_API}/api/v1/risks/expiry", {"days": 60, "limit": 250}, tenant_id, access_token)
    if is_transfer:
        batches = get_json(f"{CORE_API}/api/v1/inventory/batches", tenant_id=tenant_id, access_token=access_token)
        return _transfer_answer(q, warehouses, skus, batches, risks or [], sku, warehouse)
    if any(term in q for term in ("reorder", "purchase", "replenish", "how many units")):
        return _replenishment_answer(q, risks or [], skus, sku)
    if risks is not None:
        return _risk_answer(q, risks, expiry or []) or result("No matching risk logic was found.", "risk.no_match", ["get_inventory_risks"])

    batches = get_json(f"{CORE_API}/api/v1/inventory/batches", tenant_id=tenant_id, access_token=access_token)
    inventory = _inventory_answer(q, warehouses, skus, batches, overview)
    if inventory:
        return inventory
    if "warehouse" in q or "location" in q:
        lines = [f"- {row['warehouseName']} ({row['city']}, {row['state']})" for row in warehouses]
        return result("Authorised warehouses:\n" + "\n".join(lines), "locations.list", ["search_locations"], str(overview.get("asOf")), data=warehouses)
    if "product" in q or "medicine" in q or "drug" in q:
        lines = [f"- {row['skuName']} ({row['skuId']})" for row in skus[:50]]
        return result("Authorised products:\n" + "\n".join(lines), "products.list", ["search_products"], str(overview.get("asOf")), data=skus)
    return result("I could not map that question to a StockFlow domain operation. Ask about inventory, risks, forecasts, replenishment, routes, carbon, or approvals, and include a product/location when relevant.", "clarification.required", ["stockflow_domain_router"], str(overview.get("asOf")), ["No value was inferred."])
