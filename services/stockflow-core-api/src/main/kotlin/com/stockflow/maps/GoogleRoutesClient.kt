package com.stockflow.maps

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

@Component
class GoogleRoutesClient(
    @Value("\${stockflow.google-maps.api-key:}") private val apiKey: String,
    @Value("\${stockflow.google-maps.routes-api-url:https://routes.googleapis.com/directions/v2:computeRoutes}") private val routesApiUrl: String,
    @Value("\${stockflow.google-maps.connect-timeout-seconds:5}") connectTimeoutSeconds: Long,
    @Value("\${stockflow.google-maps.read-timeout-seconds:15}") readTimeoutSeconds: Long
) {
    private val requestFactory = JdkClientHttpRequestFactory(
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds.coerceIn(1, 60)))
            .build()
    ).apply { setReadTimeout(Duration.ofSeconds(readTimeoutSeconds.coerceIn(1, 120))) }

    private val client = RestClient.builder()
        .requestFactory(requestFactory)
        .build()

    fun computeRoute(command: GoogleRouteCommand): GoogleRouteView {
        if (apiKey.isBlank()) throw GoogleRoutesException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "GOOGLE_ROUTES_NOT_CONFIGURED",
            "Google Routes API is not configured on the StockFlow server"
        )

        val body = mapOf(
            "origin" to waypoint(command.originLatitude, command.originLongitude),
            "destination" to waypoint(command.destinationLatitude, command.destinationLongitude),
            "travelMode" to "DRIVE",
            "routingPreference" to "TRAFFIC_AWARE",
            "computeAlternativeRoutes" to false,
            "languageCode" to "en-IN",
            "units" to "METRIC"
        )

        val response = request {
            client.post()
                .uri(routesApiUrl)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .header(HttpHeaders.ACCEPT, "application/json")
                .header("X-Goog-Api-Key", apiKey)
                .header("X-Goog-FieldMask", "routes.duration,routes.distanceMeters,routes.polyline.encodedPolyline")
                .body(body)
                .retrieve()
                .body(JsonNode::class.java)
        }

        val routes = response.path("routes")
        if (!routes.isArray || routes.isEmpty) throw GoogleRoutesException(
            HttpStatus.BAD_GATEWAY,
            "GOOGLE_ROUTES_EMPTY_RESPONSE",
            "Google Routes API did not return a driving route for these points"
        )
        val route = routes[0]
        val encodedPolyline = route.path("polyline").path("encodedPolyline").asText()
        val points = try {
            GooglePolylineDecoder.decode(encodedPolyline)
        } catch (_: IllegalArgumentException) {
            emptyList()
        }
        if (points.size < 2) throw GoogleRoutesException(
            HttpStatus.BAD_GATEWAY,
            "GOOGLE_ROUTES_INVALID_POLYLINE",
            "Google Routes API returned an invalid route polyline"
        )

        return GoogleRouteView(
            distanceMeters = route.path("distanceMeters").asLong(),
            durationSeconds = parseDurationSeconds(route.path("duration").asText()),
            points = points
        )
    }

    private fun waypoint(latitude: Double, longitude: Double) = mapOf(
        "location" to mapOf("latLng" to mapOf("latitude" to latitude, "longitude" to longitude))
    )

    private fun parseDurationSeconds(duration: String): Long =
        duration.removeSuffix("s").toDoubleOrNull()?.toLong()?.coerceAtLeast(0) ?: 0

    private fun request(call: () -> JsonNode?): JsonNode = try {
        call() ?: throw GoogleRoutesException(
            HttpStatus.BAD_GATEWAY,
            "GOOGLE_ROUTES_EMPTY_RESPONSE",
            "Google Routes API returned an empty response"
        )
    } catch (error: RestClientResponseException) {
        val upstreamStatus = error.statusCode.value()
        val message = when (upstreamStatus) {
            400 -> "Google Routes API rejected the route coordinates or request"
            401, 403 -> "Google Routes API rejected the configured backend key or its restrictions"
            429 -> "Google Routes API rate limit or quota was reached"
            else -> "Google Routes API could not compute the route"
        }
        throw GoogleRoutesException(HttpStatus.BAD_GATEWAY, "GOOGLE_ROUTES_UPSTREAM_ERROR", "$message (HTTP $upstreamStatus)")
    } catch (_: ResourceAccessException) {
        throw GoogleRoutesException(
            HttpStatus.BAD_GATEWAY,
            "GOOGLE_ROUTES_UNREACHABLE",
            "Google Routes API could not be reached"
        )
    }
}
