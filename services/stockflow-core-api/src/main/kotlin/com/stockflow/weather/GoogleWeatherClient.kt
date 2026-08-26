package com.stockflow.weather

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import tools.jackson.databind.JsonNode
import java.net.http.HttpClient
import java.time.Duration
import java.time.Instant
import kotlin.math.ceil
import kotlin.math.roundToInt

@Component
class GoogleWeatherClient(
    @Value("\${stockflow.google-weather.api-key:}") private val apiKey: String,
    @Value("\${stockflow.google-weather.api-url:https://weather.googleapis.com}") apiUrl: String,
    @Value("\${stockflow.google-weather.connect-timeout-seconds:5}") connectTimeoutSeconds: Long,
    @Value("\${stockflow.google-weather.read-timeout-seconds:15}") readTimeoutSeconds: Long
) {
    private val requestFactory = JdkClientHttpRequestFactory(
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(connectTimeoutSeconds.coerceIn(1, 60))).build()
    ).apply { setReadTimeout(Duration.ofSeconds(readTimeoutSeconds.coerceIn(1, 120))) }
    private val client = RestClient.builder().baseUrl(apiUrl.trimEnd('/')).requestFactory(requestFactory).build()

    private val supportedHazards = setOf(
        "COASTAL_FLOOD", "FLASH_FLOOD", "FLOOD", "MUDDY_FLOOD", "RIVER_FLOODING", "STORM_SURGE", "LANDSLIDE"
    )

    fun routeForecast(latitude: Double, longitude: Double, etaMinutes: Int, locationLabel: String): RouteWeatherForecastView {
        if (apiKey.isBlank()) throw GoogleWeatherException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "GOOGLE_WEATHER_NOT_CONFIGURED",
            "Google Weather API is not configured on the StockFlow server"
        )
        val selectedHour = (etaMinutes.coerceAtLeast(0) / 60.0).roundToInt().coerceIn(0, 23)
        val hours = ceil(etaMinutes.coerceAtLeast(0) / 60.0).toInt().plus(2).coerceIn(1, 24)
        val response = request {
            client.get()
                .uri { builder -> builder
                    .path("/v1/forecast/hours:lookup")
                    .queryParam("location.latitude", latitude)
                    .queryParam("location.longitude", longitude)
                    .queryParam("unitsSystem", "METRIC")
                    .queryParam("hours", hours)
                    .queryParam("pageSize", hours)
                    .queryParam("languageCode", "en-IN")
                    .build() }
                .header(HttpHeaders.ACCEPT, "application/json")
                .header("X-Goog-Api-Key", apiKey)
                .retrieve()
                .body(JsonNode::class.java)
        }
        val forecasts = response.path("forecastHours")
        if (!forecasts.isArray || forecasts.isEmpty) throw GoogleWeatherException(
            HttpStatus.BAD_GATEWAY,
            "GOOGLE_WEATHER_EMPTY_RESPONSE",
            "Google Weather API did not return an hourly forecast for this route"
        )
        return toView(forecasts[selectedHour.coerceAtMost(forecasts.size() - 1)], locationLabel)
    }

    fun hazardAlerts(locations: List<HazardAlertLocation>): HazardAlertsView {
        requireConfigured()
        val regionCodes = linkedSetOf<String>()
        val alerts = linkedMapOf<String, HazardAlertView>()
        locations.forEach { location ->
            val response = request {
                client.get()
                    .uri { builder -> builder
                        .path("/v1/publicAlerts:lookup")
                        .queryParam("location.latitude", location.latitude)
                        .queryParam("location.longitude", location.longitude)
                        .queryParam("languageCode", "en")
                        .build() }
                    .header(HttpHeaders.ACCEPT, "application/json")
                    .header("X-Goog-Api-Key", apiKey)
                    .retrieve()
                    .body(JsonNode::class.java)
            }
            response.path("regionCode").asText().takeIf { it.isNotBlank() }?.let(regionCodes::add)
            val weatherAlerts = response.path("weatherAlerts")
            if (weatherAlerts.isArray) weatherAlerts.forEach { alert ->
                val eventType = alert.path("eventType").asText()
                if (eventType in supportedHazards) {
                    val view = toHazardAlert(alert, eventType, location)
                    alerts.putIfAbsent(view.id, view)
                }
            }
        }
        return HazardAlertsView(
            alerts = alerts.values.toList(),
            count = alerts.size,
            monitoredLocations = locations.size,
            regionCodes = regionCodes
        )
    }

    private fun requireConfigured() {
        if (apiKey.isBlank()) throw GoogleWeatherException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "GOOGLE_WEATHER_NOT_CONFIGURED",
            "Google Weather API is not configured on the StockFlow server"
        )
    }

    private fun toHazardAlert(alert: JsonNode, eventType: String, location: HazardAlertLocation): HazardAlertView {
        val startTime = alert.path("startTime").asText().takeIf { it.isNotBlank() }
        val phase = startTime?.let { runCatching { Instant.parse(it) }.getOrNull() }
            ?.takeIf { it.isAfter(Instant.now()) }
            ?.let { "FORECAST" } ?: "ACTIVE"
        val dataSource = alert.path("dataSource")
        val instruction = alert.path("instruction").takeIf { it.isArray }
            ?.joinToString(" ") { it.asText() }
            ?.takeIf { it.isNotBlank() }
        val id = alert.path("alertId").asText().ifBlank {
            "$eventType:${alert.path("areaName").asText()}:$startTime"
        }
        return HazardAlertView(
            id = id,
            title = alert.path("alertTitle").path("text").asText().ifBlank { eventType.replace('_', ' ') },
            eventType = eventType,
            hazardType = if (eventType == "LANDSLIDE") "LANDSLIDE" else "FLOOD",
            areaName = alert.path("areaName").asText().ifBlank { "Affected area" },
            polygonGeoJson = alert.path("polygon").asText().takeIf { it.isNotBlank() },
            severity = alert.path("severity").asText().ifBlank { "UNKNOWN" },
            certainty = alert.path("certainty").asText().ifBlank { "UNKNOWN" },
            urgency = alert.path("urgency").asText().ifBlank { "UNKNOWN" },
            description = alert.path("description").asText().takeIf { it.isNotBlank() }?.take(2000),
            instruction = instruction?.take(1000),
            startTime = startTime,
            expirationTime = alert.path("expirationTime").asText().takeIf { it.isNotBlank() },
            phase = phase,
            matchedLatitude = location.latitude,
            matchedLongitude = location.longitude,
            dataSourceName = dataSource.path("name").asText().ifBlank { dataSource.path("publisher").asText().ifBlank { "Official alert authority" } },
            dataSourceUri = dataSource.path("authorityUri").asText().takeIf { it.startsWith("https://") }
        )
    }

    private fun toView(hour: JsonNode, locationLabel: String): RouteWeatherForecastView {
        val condition = hour.path("weatherCondition").path("description").path("text").asText().ifBlank { "Conditions unavailable" }
        val conditionType = hour.path("weatherCondition").path("type").asText()
        val precipitationProbability = hour.path("precipitation").path("probability").path("percent").asInt()
        val precipitation = hour.path("precipitation").path("qpf").path("quantity").asDouble()
        val thunderstorm = hour.path("thunderstormProbability").asInt()
        val windSpeed = hour.path("wind").path("speed").path("value").asDouble()
        val windGust = hour.path("wind").path("gust").path("value").asDouble()
        val visibility = hour.path("visibility").path("distance").asDouble()
        val riskScore = calculateRisk(conditionType, precipitationProbability, precipitation, thunderstorm, windSpeed, windGust, visibility)
        val riskLevel = when {
            riskScore >= 60 -> "HIGH"
            riskScore >= 30 -> "MODERATE"
            else -> "LOW"
        }
        val advice = when (riskLevel) {
            "HIGH" -> "High disruption risk near arrival. Review alternate routes and delay dispatch if field conditions deteriorate."
            "MODERATE" -> "Weather may affect travel time. Monitor the corridor and keep the alternate route ready."
            else -> "No major weather disruption is forecast at arrival. Continue monitoring route conditions."
        }
        val iconBase = hour.path("weatherCondition").path("iconBaseUri").asText()
        return RouteWeatherForecastView(
            locationLabel = locationLabel,
            forecastTime = hour.path("interval").path("startTime").asText().takeIf { it.isNotBlank() },
            condition = condition,
            temperatureCelsius = hour.path("temperature").path("degrees").asDouble(),
            feelsLikeCelsius = hour.path("feelsLikeTemperature").path("degrees").asDouble(),
            precipitationProbabilityPercent = precipitationProbability,
            precipitationMillimeters = precipitation,
            thunderstormProbabilityPercent = thunderstorm,
            windSpeedKph = windSpeed,
            windGustKph = windGust,
            visibilityKm = visibility,
            humidityPercent = hour.path("relativeHumidity").asInt(),
            riskScore = riskScore,
            riskLevel = riskLevel,
            operationalAdvice = advice,
            iconUrl = iconBase.takeIf { it.isNotBlank() }?.plus(".svg")
        )
    }

    private fun calculateRisk(condition: String, rainChance: Int, rainMm: Double, thunderChance: Int, wind: Double, gust: Double, visibility: Double): Int {
        var score = 0
        if (rainChance >= 70) score += 20 else if (rainChance >= 40) score += 10
        if (rainMm >= 7.5) score += 25 else if (rainMm >= 2.5) score += 12
        if (thunderChance >= 50) score += 25 else if (thunderChance >= 25) score += 12
        if (wind >= 40 || gust >= 55) score += 20 else if (wind >= 25 || gust >= 35) score += 10
        if (visibility in 0.01..3.0) score += 20 else if (visibility in 3.01..6.0) score += 10
        if (condition.contains("HEAVY") || condition.contains("THUNDER") || condition.contains("WIND_AND_RAIN")) score += 15
        return score.coerceAtMost(100)
    }

    private fun request(call: () -> JsonNode?): JsonNode = try {
        call() ?: throw GoogleWeatherException(HttpStatus.BAD_GATEWAY, "GOOGLE_WEATHER_EMPTY_RESPONSE", "Google Weather API returned an empty response")
    } catch (error: RestClientResponseException) {
        val status = error.statusCode.value()
        val message = when (status) {
            401, 403 -> "Google Weather API rejected the backend key or its API restrictions"
            429 -> "Google Weather API quota or rate limit was reached"
            else -> "Google Weather API could not provide the forecast"
        }
        throw GoogleWeatherException(HttpStatus.BAD_GATEWAY, "GOOGLE_WEATHER_UPSTREAM_ERROR", "$message (HTTP $status)")
    } catch (_: ResourceAccessException) {
        throw GoogleWeatherException(HttpStatus.BAD_GATEWAY, "GOOGLE_WEATHER_UNREACHABLE", "Google Weather API could not be reached")
    }
}
