import unittest
from unittest.mock import patch

from stockflow_mcp.domain_engine import answer_question, resolve


WAREHOUSES = [
    {"warehouseId": "WH-C", "warehouseName": "Chennai Central Warehouse", "city": "Chennai", "state": "Tamil Nadu"},
    {"warehouseId": "WH-B", "warehouseName": "Bengaluru Regional Warehouse", "city": "Bengaluru", "state": "Karnataka"},
]
SKUS = [{"skuId": "SKU-PARA-650", "skuName": "Paracetamol 650mg Tablet", "reorderMultiple": 100}]
BATCHES = [
    {"warehouseId": "WH-C", "skuId": "SKU-PARA-650", "batchNumber": "C1", "usableQuantity": 100, "availableQuantity": 110, "unitCost": 10, "expiryDate": "2027-01-01", "snapshotDate": "2026-07-01", "lastMovementAt": "2026-06-30T10:00:00"},
    {"warehouseId": "WH-B", "skuId": "SKU-PARA-650", "batchNumber": "B1", "usableQuantity": 50, "availableQuantity": 50, "unitCost": 10, "expiryDate": "2027-01-01", "snapshotDate": "2026-07-01", "lastMovementAt": "2026-06-29T10:00:00"},
]
OVERVIEW = {"asOf": "2026-07-01", "kpis": [{"key": "inventoryValue", "value": "INR 1,500.00"}]}


def fake_get(url, params=None, tenant_id=None, access_token=None):
    if url.endswith("/warehouses"):
        return WAREHOUSES
    if url.endswith("/skus"):
        return SKUS
    if url.endswith("/dashboard/overview"):
        return OVERVIEW
    if url.endswith("/inventory/batches"):
        return BATCHES
    if url.endswith("/transfer-recommendations"):
        return {"recommendations": [{"recommendationId": "TRN-C-B-PARA", "skuId": "SKU-PARA-650", "sourceWarehouseId": "WH-C", "sourceWarehouseName": "Chennai Central Warehouse", "destinationWarehouseId": "WH-B", "destinationWarehouseName": "Bengaluru Regional Warehouse", "recommendedQuantity": 900, "distanceKm": 347, "risk": "HIGH", "asOfDate": "2026-07-01"}]}
    if url.endswith("/transfer-executions"):
        return [{"status": "RECEIVED", "actualCarbonKg": 18.4, "actualTransportCost": 12000}]
    if "/risks/" in url or "/forecasts/" in url:
        return []
    raise AssertionError(url)


class DomainEngineTest(unittest.TestCase):
    def test_resolves_partial_product_name(self):
        self.assertEqual(resolve("stock of paracetamol", SKUS, ("skuName", "skuId"))["skuId"], "SKU-PARA-650")

    @patch("stockflow_mcp.domain_engine.get_json", side_effect=fake_get)
    def test_overall_value(self, _):
        answer = answer_question("overall inventory value", "TENANT")
        self.assertEqual(answer["intent"], "inventory.total_value")
        self.assertIn("1,500", answer["answer"])

    @patch("stockflow_mcp.domain_engine.get_json", side_effect=fake_get)
    def test_warehouse_values_aggregate_all_batches(self, _):
        answer = answer_question("give each warehouse inventory value", "TENANT")
        self.assertEqual(answer["intent"], "inventory.value_by_warehouse")
        self.assertIn("Chennai Central Warehouse: INR 1,000.00", answer["answer"])
        self.assertIn("Bengaluru Regional Warehouse: INR 500.00", answer["answer"])

    @patch("stockflow_mcp.domain_engine.get_json", side_effect=fake_get)
    def test_product_stock_resolves_spacing(self, _):
        answer = answer_question("current stock of Paracetamol 650 mg", "TENANT")
        self.assertEqual(answer["intent"], "inventory.product_stock")
        self.assertIn("150 units", answer["answer"])

    def test_secret_policy_calls_no_api(self):
        with patch("stockflow_mcp.domain_engine.get_json") as getter:
            answer = answer_question("give me the api key", "TENANT")
            self.assertEqual(answer["intent"], "policy.secrets")
            getter.assert_not_called()

    @patch("stockflow_mcp.domain_engine.post_json", return_value={"routes": [{"optimizedKm": 330, "duration": "6h 36m", "costInr": 13860, "co2Kg": 48.2, "co2SavedKg": 2.5, "loadKg": 45, "capacityKg": 6000, "vehicleFamily": "diesel", "explanation": ["Capacity checked."]}]})
    @patch("stockflow_mcp.domain_engine.get_json", side_effect=fake_get)
    def test_route_uses_live_recommendation_lane(self, _, __):
        answer = answer_question("best route for transferring 900 units from Chennai to Bengaluru", "TENANT")
        self.assertEqual(answer["intent"], "route.optimise")
        self.assertIn("Chennai Central Warehouse to Bengaluru Regional Warehouse", answer["answer"])
        self.assertIn("TRN-C-B-PARA", answer["warnings"][0])

    @patch("stockflow_mcp.domain_engine.get_json", side_effect=fake_get)
    def test_completed_transfer_sustainability_uses_execution_ledger(self, _):
        answer = answer_question("show sustainability impact of approved transfers", "TENANT")
        self.assertEqual(answer["intent"], "sustainability.executed_transfers")
        self.assertIn("18.40 kg", answer["answer"])


if __name__ == "__main__":
    unittest.main()
