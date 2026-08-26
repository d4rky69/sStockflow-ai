package com.stockflow.weather

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/integrations/google-weather")
class GoogleWeatherController(private val client: GoogleWeatherClient) {
    @GetMapping("/route-forecast")
    fun routeForecast(
        @RequestHeader("X-Tenant-ID") tenantId: String,
        @RequestParam latitude: Double,
        @RequestParam longitude: Double,
        @RequestParam(defaultValue = "0") etaMinutes: Int,
        @RequestParam(defaultValue = "Route destination") locationLabel: String
    ): RouteWeatherForecastView {
        if (tenantId.isBlank()) throw GoogleWeatherException(HttpStatus.BAD_REQUEST, "TENANT_REQUIRED", "A tenant ID is required")
        if (!latitude.isFinite() || latitude !in -90.0..90.0 || !longitude.isFinite() || longitude !in -180.0..180.0) {
            throw GoogleWeatherException(HttpStatus.BAD_REQUEST, "INVALID_WEATHER_LOCATION", "Weather forecast coordinates are invalid")
        }
        if (etaMinutes !in 0..1380) throw GoogleWeatherException(HttpStatus.BAD_REQUEST, "INVALID_WEATHER_ETA", "Weather ETA must be between 0 and 1380 minutes")
        return client.routeForecast(latitude, longitude, etaMinutes, locationLabel.take(80))
    }

    @GetMapping("/hazard-alerts")
    fun hazardAlerts(
        @RequestHeader("X-Tenant-ID") tenantId: String,
        @RequestParam latitude: List<Double>,
        @RequestParam longitude: List<Double>
    ): HazardAlertsView {
        if (tenantId.isBlank()) throw GoogleWeatherException(HttpStatus.BAD_REQUEST, "TENANT_REQUIRED", "A tenant ID is required")
        if (latitude.size != longitude.size || latitude.isEmpty() || latitude.size > 8) {
            throw GoogleWeatherException(HttpStatus.BAD_REQUEST, "INVALID_ALERT_LOCATIONS", "Provide between 1 and 8 matching latitude and longitude values")
        }
        val locations = latitude.zip(longitude).map { (lat, lng) ->
            if (!lat.isFinite() || lat !in -90.0..90.0 || !lng.isFinite() || lng !in -180.0..180.0) {
                throw GoogleWeatherException(HttpStatus.BAD_REQUEST, "INVALID_ALERT_LOCATION", "Hazard alert coordinates are invalid")
            }
            HazardAlertLocation(lat, lng)
        }
        return client.hazardAlerts(locations)
    }
}
