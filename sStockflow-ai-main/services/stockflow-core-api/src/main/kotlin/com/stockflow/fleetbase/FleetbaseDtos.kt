package com.stockflow.fleetbase

data class FleetbaseConfigurationView(
    val enabled: Boolean,
    val configured: Boolean,
    val apiUrl: String,
    val mode: String,
    val writeOperationsEnabled: Boolean = false,
    val tenantMapping: FleetbaseTenantMappingView? = null
)

data class FleetbaseTenantMappingView(
    val tenantId: String,
    val mapped: Boolean,
    val expectedOrganizationId: String?,
    val organizationVerificationEnabled: Boolean
)

data class FleetbaseOrganizationView(
    val id: String,
    val name: String?,
    val timezone: String?,
    val country: String?,
    val currency: String?,
    val matchesExpectedOrganization: Boolean
)

data class FleetbaseVehicleView(
    val id: String,
    val name: String?,
    val internalId: String?,
    val plateNumber: String?,
    val type: String?,
    val status: String?,
    val online: Boolean?,
    val payloadCapacity: Double?,
    val make: String?,
    val model: String?,
    val year: String?,
    val latitude: Double?,
    val longitude: Double?,
    val heading: Double?,
    val speed: Double?,
    val altitude: Double?,
    val positionUpdatedAt: String?
)

data class FleetbaseVehicleListView(
    val vehicles: List<FleetbaseVehicleView>,
    val count: Int,
    val source: String = "FLEETBASE_API"
)

data class FleetbaseOrderCreateCommand(
    val internalId: String,
    val pickup: Map<String, Any>,
    val dropoff: Map<String, Any>,
    val vehicleId: String?,
    val notes: String,
    val meta: Map<String, Any>
)

data class FleetbaseCreatedOrder(
    val id: String,
    val internalId: String?,
    val status: String?,
    val dispatched: Boolean,
    val trackingNumber: String?,
    val trackingUrl: String?
)

data class FleetbaseDispatchedOrder(
    val id: String,
    val status: String?,
    val dispatched: Boolean,
    val trackingNumber: String?
)

data class FleetbaseRemoteOrder(
    val id: String,
    val status: String?,
    val dispatched: Boolean,
    val started: Boolean,
    val trackingNumber: String?
)

data class FleetbaseTrackingSnapshot(
    val latitude: Double?,
    val longitude: Double?,
    val progressPercentage: Double?,
    val totalDistanceMeters: Long?,
    val completedDistanceMeters: Long?,
    val currentDestinationEtaSeconds: Long?,
    val completionEtaSeconds: Long?,
    val estimatedCompletionTime: String?,
    val currentDestination: String?,
    val etaByDestination: Map<String, Long>
)
