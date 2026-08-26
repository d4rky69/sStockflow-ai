package com.stockflow.maps

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service

@Service
class GoogleRoutesService(private val client: GoogleRoutesClient) {
    fun compute(tenantId: String, command: GoogleRouteCommand): GoogleRouteView {
        if (tenantId.isBlank()) throw GoogleRoutesException(HttpStatus.BAD_REQUEST, "TENANT_REQUIRED", "A tenant ID is required")
        validateCoordinate("origin latitude", command.originLatitude, -90.0, 90.0)
        validateCoordinate("origin longitude", command.originLongitude, -180.0, 180.0)
        validateCoordinate("destination latitude", command.destinationLatitude, -90.0, 90.0)
        validateCoordinate("destination longitude", command.destinationLongitude, -180.0, 180.0)
        if (command.originLatitude == command.destinationLatitude && command.originLongitude == command.destinationLongitude) {
            throw GoogleRoutesException(HttpStatus.BAD_REQUEST, "ROUTE_POINTS_IDENTICAL", "Route origin and destination must be different")
        }
        return client.computeRoute(command)
    }

    private fun validateCoordinate(name: String, value: Double, minimum: Double, maximum: Double) {
        if (!value.isFinite() || value !in minimum..maximum) throw GoogleRoutesException(
            HttpStatus.BAD_REQUEST,
            "INVALID_ROUTE_COORDINATE",
            "The $name is invalid"
        )
    }
}
