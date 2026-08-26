import unittest

from stockflow_carbon.main import (
    CarbonRequest,
    OptimiseRoutesRequest,
    calculate_carbon,
    optimise_routes,
)


class CarbonServiceTest(unittest.TestCase):
    def test_optimized_route_reduces_distance_and_emissions(self) -> None:
        request = OptimiseRoutesRequest.model_validate({
            "objective": "Lowest carbon impact",
            "vehicleType": "All eligible vehicles",
            "routes": [{
                "id": "RTE-TEST",
                "lane": "Chennai to Bengaluru",
                "stops": ["Chennai", "Bengaluru"],
                "vehicle": "12T electric truck",
                "loadKg": 8000,
                "capacityKg": 12000,
                "baselineKm": 350,
                "priority": "High",
                "status": "Draft",
            }],
        })

        response = optimise_routes(request, "TEN-ACME-PHARMA")
        route = response["routes"][0]

        self.assertLess(route["optimizedKm"], 350)
        self.assertGreater(route["co2SavedKg"], 0)
        self.assertEqual(route["status"], "Optimized")

    def test_carbon_response_exposes_assumptions(self) -> None:
        response = calculate_carbon(
            CarbonRequest(
                distanceKm=240,
                baselineDistanceKm=300,
                vehicleType="diesel",
                loadKg=8000,
                capacityKg=12000,
                trips=1,
            ),
            "TEN-ACME-PHARMA",
        )

        self.assertEqual(response["emissionFactorKgPerKm"], 0.27)
        self.assertEqual(response["classification"], "prototype estimate")
        self.assertGreater(response["emissionsAvoidedKgCo2e"], 0)


if __name__ == "__main__":
    unittest.main()
