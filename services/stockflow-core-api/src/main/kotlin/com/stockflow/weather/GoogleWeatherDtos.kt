package com.stockflow.weather

data class RouteWeatherForecastView(
    val locationLabel: String,
    val forecastTime: String?,
    val condition: String,
    val iconUrl: String?,
    val temperatureCelsius: Double,
    val feelsLikeCelsius: Double,
    val precipitationProbabilityPercent: Int,
    val precipitationMillimeters: Double,
    val thunderstormProbabilityPercent: Int,
    val windSpeedKph: Double,
    val windGustKph: Double,
    val visibilityKm: Double,
    val humidityPercent: Int,
    val riskScore: Int,
    val riskLevel: String,
    val operationalAdvice: String,
    val source: String = "GOOGLE_WEATHER_API",
    val attribution: String = "Source: Includes weather data from Google"
)

data class HazardAlertLocation(
    val latitude: Double,
    val longitude: Double
)

data class HazardAlertView(
    val id: String,
    val title: String,
    val eventType: String,
    val hazardType: String,
    val areaName: String,
    val polygonGeoJson: String?,
    val severity: String,
    val certainty: String,
    val urgency: String,
    val description: String?,
    val instruction: String?,
    val startTime: String?,
    val expirationTime: String?,
    val phase: String,
    val matchedLatitude: Double,
    val matchedLongitude: Double,
    val dataSourceName: String,
    val dataSourceUri: String?
)

data class HazardAlertsView(
    val alerts: List<HazardAlertView>,
    val count: Int,
    val monitoredLocations: Int,
    val regionCodes: Set<String>,
    val source: String = "GOOGLE_WEATHER_PUBLIC_ALERTS",
    val disclaimer: String = "Official authority alerts only. An empty result does not prove that a location is hazard-free."
)
