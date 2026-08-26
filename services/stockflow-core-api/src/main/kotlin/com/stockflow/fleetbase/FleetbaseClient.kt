package com.stockflow.fleetbase

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
class FleetbaseClient(
    @Value("\${stockflow.fleetbase.enabled:false}") private val enabled: Boolean,
    @Value("\${stockflow.fleetbase.api-url:https://api.fleetbase.io/v1}") apiUrl: String,
    @Value("\${stockflow.fleetbase.api-key:}") private val apiKey: String,
    @Value("\${stockflow.fleetbase.write-operations-enabled:false}") private val writeOperationsEnabled: Boolean,
    @Value("\${stockflow.fleetbase.connect-timeout-seconds:5}") connectTimeoutSeconds: Long,
    @Value("\${stockflow.fleetbase.read-timeout-seconds:15}") readTimeoutSeconds: Long
) {
    private val normalizedApiUrl = apiUrl.trim().trimEnd('/')
    private val requestFactory = JdkClientHttpRequestFactory(
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds.coerceIn(1, 60)))
            .build()
    ).apply {
        setReadTimeout(Duration.ofSeconds(readTimeoutSeconds.coerceIn(1, 120)))
    }
    private val client = RestClient.builder()
        .baseUrl(normalizedApiUrl)
        .requestFactory(requestFactory)
        .build()

    fun configuration(): FleetbaseConfigurationView = FleetbaseConfigurationView(
        enabled = enabled,
        configured = apiKey.isNotBlank(),
        apiUrl = normalizedApiUrl,
        mode = when {
            apiKey.startsWith("flb_test_") -> "TEST"
            apiKey.startsWith("flb_live_") -> "LIVE"
            apiKey.isBlank() -> "UNCONFIGURED"
            else -> "RESTRICTED_OR_CUSTOM"
        },
        writeOperationsEnabled = writeOperationsEnabled
    )

    fun listVehicles(limit: Int): JsonNode {
        requireAvailable()
        return request("list vehicles") {
            client.get()
                .uri { builder -> builder.path("/vehicles").queryParam("limit", limit).queryParam("offset", 0).build() }
                .headers(::authenticationHeaders)
                .retrieve()
                .body(JsonNode::class.java)
        }
    }

    fun currentOrganization(): JsonNode {
        requireAvailable()
        return request("resolve the current organization") {
            client.get()
                .uri("/organizations/current")
                .headers(::authenticationHeaders)
                .retrieve()
                .body(JsonNode::class.java)
        }
    }

    fun createOrder(command: FleetbaseOrderCreateCommand): FleetbaseCreatedOrder {
        requireWritesAvailable()
        val body = linkedMapOf<String, Any>(
            "internal_id" to command.internalId,
            "pickup" to command.pickup,
            "dropoff" to command.dropoff,
            "dispatch" to false,
            "type" to "transport",
            "pod_required" to false,
            "notes" to command.notes,
            "meta" to command.meta
        )
        command.vehicleId?.takeIf { it.isNotBlank() }?.let { body["vehicle"] = it }
        val response = request("create an undispatched order") {
            client.post()
                .uri("/orders")
                .headers(::authenticationHeaders)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .body(body)
                .retrieve()
                .body(JsonNode::class.java)
        }
        val orderId = response.path("id").asText().trim()
        if (orderId.isBlank()) throw FleetbaseIntegrationException(
            HttpStatus.BAD_GATEWAY,
            "FLEETBASE_INVALID_ORDER_RESPONSE",
            "Fleetbase created an order but did not return an order ID"
        )
        val dispatched = response.path("dispatched").asBoolean(false)
        if (dispatched) throw FleetbaseIntegrationException(
            HttpStatus.BAD_GATEWAY,
            "FLEETBASE_UNEXPECTED_DISPATCH",
            "Fleetbase returned a dispatched order even though StockFlow explicitly disabled dispatch"
        )
        val tracking = response.path("tracking_number")
        return FleetbaseCreatedOrder(
            id = orderId,
            internalId = response.path("internal_id").asText().takeIf { it.isNotBlank() },
            status = response.path("status").asText().takeIf { it.isNotBlank() },
            dispatched = false,
            trackingNumber = tracking.path("tracking_number").asText().takeIf { it.isNotBlank() },
            trackingUrl = tracking.path("url").asText().takeIf { it.isNotBlank() }
        )
    }

    fun dispatchOrder(orderId: String): FleetbaseDispatchedOrder {
        requireWritesAvailable()
        if (orderId.isBlank()) throw FleetbaseIntegrationException(
            HttpStatus.BAD_REQUEST,
            "FLEETBASE_ORDER_ID_REQUIRED",
            "A Fleetbase order ID is required before dispatch"
        )
        val response = request("dispatch an order") {
            client.patch()
                .uri("/orders/{orderId}/dispatch", orderId)
                .headers(::authenticationHeaders)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .retrieve()
                .body(JsonNode::class.java)
        }
        val returnedId = response.path("id").asText().trim()
        if (returnedId != orderId) throw FleetbaseIntegrationException(
            HttpStatus.BAD_GATEWAY,
            "FLEETBASE_ORDER_IDENTITY_MISMATCH",
            "Fleetbase returned a different order identity after dispatch"
        )
        val dispatched = response.path("dispatched").asBoolean(false)
        if (!dispatched) throw FleetbaseIntegrationException(
            HttpStatus.BAD_GATEWAY,
            "FLEETBASE_DISPATCH_NOT_CONFIRMED",
            "Fleetbase did not confirm that the order was dispatched"
        )
        val tracking = response.path("tracking_number")
        return FleetbaseDispatchedOrder(
            id = returnedId,
            status = response.path("status").asText().takeIf { it.isNotBlank() },
            dispatched = true,
            trackingNumber = tracking.path("tracking_number").asText().takeIf { it.isNotBlank() }
        )
    }

    fun getOrder(orderId: String): FleetbaseRemoteOrder {
        requireAvailable()
        val response = request("retrieve an order") {
            client.get()
                .uri("/orders/{orderId}", orderId)
                .headers(::authenticationHeaders)
                .retrieve()
                .body(JsonNode::class.java)
        }
        val node = if (response.path("data").isObject) response.path("data") else response
        val returnedId = node.path("id").asText().trim()
        if (returnedId != orderId) throw FleetbaseIntegrationException(
            HttpStatus.BAD_GATEWAY,
            "FLEETBASE_ORDER_IDENTITY_MISMATCH",
            "Fleetbase returned a different order identity during reconciliation"
        )
        val tracking = node.path("tracking_number")
        return FleetbaseRemoteOrder(
            id = returnedId,
            status = node.path("status").asText().takeIf { it.isNotBlank() },
            dispatched = node.path("dispatched").asBoolean(false),
            started = node.path("started").asBoolean(false),
            trackingNumber = tracking.path("tracking_number").asText().takeIf { it.isNotBlank() }
        )
    }

    fun tracking(orderId: String): FleetbaseTrackingSnapshot {
        requireAvailable()
        val tracker = request("retrieve order tracking") {
            client.get()
                .uri("/orders/{orderId}/tracker", orderId)
                .headers(::authenticationHeaders)
                .retrieve()
                .body(JsonNode::class.java)
        }
        val eta = request("retrieve order ETA") {
            client.get()
                .uri("/orders/{orderId}/eta", orderId)
                .headers(::authenticationHeaders)
                .retrieve()
                .body(JsonNode::class.java)
        }
        val location = tracker.path("driver_current_location")
        val destination = tracker.path("current_destination")
        val etaValues = eta.properties().associate { entry -> entry.key to entry.value.asLong() }
        return FleetbaseTrackingSnapshot(
            latitude = location.path("latitude").takeUnless { it.isMissingNode || it.isNull }?.asDouble(),
            longitude = location.path("longitude").takeUnless { it.isMissingNode || it.isNull }?.asDouble(),
            progressPercentage = tracker.path("progress_percentage").takeUnless { it.isMissingNode || it.isNull }?.asDouble(),
            totalDistanceMeters = tracker.path("total_distance").takeUnless { it.isMissingNode || it.isNull }?.asLong(),
            completedDistanceMeters = tracker.path("completed_distance").takeUnless { it.isMissingNode || it.isNull }?.asLong(),
            currentDestinationEtaSeconds = tracker.path("current_destination_eta").takeUnless { it.isMissingNode || it.isNull }?.asLong(),
            completionEtaSeconds = tracker.path("completion_eta").takeUnless { it.isMissingNode || it.isNull }?.asLong(),
            estimatedCompletionTime = tracker.path("estimated_completion_time").asText().takeIf { it.isNotBlank() },
            currentDestination = destination.path("name").asText().takeIf { it.isNotBlank() },
            etaByDestination = etaValues
        )
    }

    private fun authenticationHeaders(headers: HttpHeaders) {
        headers.setBearerAuth(apiKey)
        headers.set(HttpHeaders.ACCEPT, "application/json")
    }

    private fun requireAvailable() {
        if (!enabled) throw FleetbaseIntegrationException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "FLEETBASE_DISABLED",
            "Fleetbase integration is disabled on the StockFlow server"
        )
        if (apiKey.isBlank()) throw FleetbaseIntegrationException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "FLEETBASE_NOT_CONFIGURED",
            "Fleetbase integration is enabled but no server-side API key is configured"
        )
    }

    private fun requireWritesAvailable() {
        requireAvailable()
        if (!writeOperationsEnabled) throw FleetbaseIntegrationException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "FLEETBASE_WRITES_DISABLED",
            "Fleetbase write operations are disabled on the StockFlow server"
        )
    }

    private fun request(operation: String, call: () -> JsonNode?): JsonNode = try {
        call() ?: throw FleetbaseIntegrationException(
            HttpStatus.BAD_GATEWAY,
            "FLEETBASE_EMPTY_RESPONSE",
            "Fleetbase returned an empty response while StockFlow attempted to $operation"
        )
    } catch (error: RestClientResponseException) {
        val upstreamStatus = error.statusCode.value()
        val message = when (upstreamStatus) {
            401, 403 -> "Fleetbase rejected the configured server credential"
            429 -> "Fleetbase rate-limited the StockFlow request"
            else -> "Fleetbase could not $operation"
        }
        throw FleetbaseIntegrationException(HttpStatus.BAD_GATEWAY, "FLEETBASE_UPSTREAM_ERROR", "$message (HTTP $upstreamStatus)")
    } catch (error: ResourceAccessException) {
        throw FleetbaseIntegrationException(HttpStatus.BAD_GATEWAY, "FLEETBASE_UNREACHABLE", "Fleetbase could not be reached while StockFlow attempted to $operation")
    }
}
