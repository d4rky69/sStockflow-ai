package com.stockflow.fleetbase

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import tools.jackson.databind.JsonNode

@Service
class FleetbaseIntegrationService(
    private val client: FleetbaseClient,
    private val tenantBinding: FleetbaseTenantBinding
) {
    fun configuration(tenantId: String): FleetbaseConfigurationView = client.configuration().copy(
        tenantMapping = tenantBinding.view(tenantId)
    )

    fun organization(tenantId: String): FleetbaseOrganizationView {
        tenantBinding.requireMapped(tenantId)
        return organization(client.currentOrganization())
    }

    fun listVehicles(tenantId: String, limit: Int): FleetbaseVehicleListView {
        tenantBinding.requireMapped(tenantId)
        if (limit !in 1..100) throw FleetbaseIntegrationException(
            HttpStatus.BAD_REQUEST,
            "INVALID_FLEETBASE_LIMIT",
            "Fleetbase vehicle limit must be between 1 and 100"
        )
        if (tenantBinding.verificationEnabled()) organization(client.currentOrganization(), failOnMismatch = true)
        val vehicles = vehicleNodes(client.listVehicles(limit)).mapNotNull(::vehicle)
        return FleetbaseVehicleListView(vehicles, vehicles.size)
    }

    private fun organization(response: JsonNode, failOnMismatch: Boolean = false): FleetbaseOrganizationView {
        val node = when {
            response.path("data").isObject -> response.path("data")
            else -> response
        }
        val id = text(node, "id") ?: throw FleetbaseIntegrationException(
            HttpStatus.BAD_GATEWAY,
            "FLEETBASE_ORGANIZATION_INVALID",
            "Fleetbase did not return an organization identifier for the configured credential"
        )
        val matches = tenantBinding.matchesOrganization(id)
        if (failOnMismatch && !matches) throw FleetbaseIntegrationException(
            HttpStatus.BAD_GATEWAY,
            "FLEETBASE_ORGANIZATION_MISMATCH",
            "The configured Fleetbase credential does not belong to the organization mapped to this StockFlow tenant"
        )
        return FleetbaseOrganizationView(
            id = id,
            name = text(node, "name"),
            timezone = text(node, "timezone"),
            country = text(node, "country"),
            currency = text(node, "currency"),
            matchesExpectedOrganization = matches
        )
    }

    internal fun vehicleNodes(response: JsonNode): List<JsonNode> = when {
        response.isArray -> response.toList()
        response.path("data").isArray -> response.path("data").toList()
        response.path("vehicles").isArray -> response.path("vehicles").toList()
        response.path("id").isString -> listOf(response)
        else -> emptyList()
    }

    private fun vehicle(node: JsonNode): FleetbaseVehicleView? {
        val id = text(node, "id") ?: return null
        val location = node.path("location")
        val coordinates = location.path("coordinates")
        val longitude = number(location, "longitude")
            ?: coordinates.takeIf { it.isArray && it.size() >= 2 }?.get(0)?.takeUnless { it.isNull }?.asDouble()
        val latitude = number(location, "latitude")
            ?: coordinates.takeIf { it.isArray && it.size() >= 2 }?.get(1)?.takeUnless { it.isNull }?.asDouble()
        return FleetbaseVehicleView(
            id = id,
            name = text(node, "name"),
            internalId = text(node, "internal_id"),
            plateNumber = text(node, "plate_number"),
            type = text(node, "type"),
            status = text(node, "status"),
            online = node.path("online").takeUnless { it.isMissingNode || it.isNull }?.asBoolean(),
            payloadCapacity = node.path("payload_capacity").takeUnless { it.isMissingNode || it.isNull }?.asDouble(),
            make = text(node, "make"),
            model = text(node, "model"),
            year = text(node, "year"),
            latitude = latitude,
            longitude = longitude,
            heading = number(node, "heading"),
            speed = number(node, "speed"),
            altitude = number(node, "altitude"),
            positionUpdatedAt = text(node, "position_updated_at") ?: text(node, "updated_at")
        )
    }

    private fun number(node: JsonNode, field: String): Double? = node.path(field)
        .takeUnless { it.isMissingNode || it.isNull || !it.isNumber }
        ?.asDouble()

    private fun text(node: JsonNode, field: String): String? = node.path(field)
        .takeUnless { it.isMissingNode || it.isNull }
        ?.stringValue()
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
}
