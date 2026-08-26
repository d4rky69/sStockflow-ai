package com.stockflow.maps

data class GoogleRouteCommand(
    val originLatitude: Double,
    val originLongitude: Double,
    val destinationLatitude: Double,
    val destinationLongitude: Double
)

data class GoogleRoutePoint(
    val latitude: Double,
    val longitude: Double
)

data class GoogleRouteView(
    val distanceMeters: Long,
    val durationSeconds: Long,
    val points: List<GoogleRoutePoint>,
    val source: String = "GOOGLE_ROUTES_API"
)
