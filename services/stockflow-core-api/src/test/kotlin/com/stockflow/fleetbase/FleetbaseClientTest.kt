package com.stockflow.fleetbase

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicReference

class FleetbaseClientTest {
    private var server: HttpServer? = null
    private fun testKey(suffix: String) = listOf("flb", "test", suffix).joinToString("_")

    @AfterEach
    fun stopServer() {
        server?.stop(0)
    }

    @Test
    fun `configuration never exposes credential and keeps writes disabled`() {
        val key = testKey("example")
        val client = FleetbaseClient(false, "https://api.fleetbase.io/v1/", key, false, 2, 2)

        val status = client.configuration()

        assertFalse(status.enabled)
        assertTrue(status.configured)
        assertEquals("https://api.fleetbase.io/v1", status.apiUrl)
        assertEquals("TEST", status.mode)
        assertFalse(status.writeOperationsEnabled)
        assertFalse(status.toString().contains(key))
    }

    @Test
    fun `vehicle listing uses bearer auth and normalizes data envelope`() {
        val authorization = AtomicReference<String>()
        val query = AtomicReference<String>()
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/v1/vehicles") { exchange ->
                authorization.set(exchange.requestHeaders.getFirst("Authorization"))
                query.set(exchange.requestURI.query)
                val body = """{"data":[{"id":"vehicle_01","name":"StockFlow Truck","internal_id":"SF-01","plate_number":"TN-01-1001","type":"truck","status":"operational","online":true,"payload_capacity":6000,"make":"Tata","model":"Ultra T.7","year":"2025","location":{"type":"Point","coordinates":[91.7362,26.1445]},"heading":82,"speed":34,"altitude":55,"updated_at":"2026-08-25T09:25:09Z"}]}"""
                val bytes = body.toByteArray(StandardCharsets.UTF_8)
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            start()
        }
        val key = testKey("example")
        val client = FleetbaseClient(true, "http://127.0.0.1:${server!!.address.port}/v1", key, false, 2, 2)
        val service = FleetbaseIntegrationService(client, FleetbaseTenantBinding("TEN-ACME-PHARMA", ""))

        val result = service.listVehicles("TEN-ACME-PHARMA", 25)

        assertEquals("Bearer $key", authorization.get())
        assertTrue(query.get().contains("limit=25"))
        assertEquals(1, result.count)
        assertEquals("vehicle_01", result.vehicles.single().id)
        assertEquals("TN-01-1001", result.vehicles.single().plateNumber)
        assertEquals(6000.0, result.vehicles.single().payloadCapacity)
        assertEquals("Tata", result.vehicles.single().make)
        assertEquals("Ultra T.7", result.vehicles.single().model)
        assertEquals("2025", result.vehicles.single().year)
        assertEquals(26.1445, result.vehicles.single().latitude)
        assertEquals(91.7362, result.vehicles.single().longitude)
        assertEquals(82.0, result.vehicles.single().heading)
        assertEquals(34.0, result.vehicles.single().speed)
    }

    @Test
    fun `organization verification resolves credential scope without exposing the key`() {
        val authorization = AtomicReference<String>()
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/v1/organizations/current") { exchange ->
                authorization.set(exchange.requestHeaders.getFirst("Authorization"))
                val body = """{"id":"org_stockflow","name":"StockFlow Logistics","timezone":"Asia/Kolkata","country":"IN","currency":"INR"}"""
                val bytes = body.toByteArray(StandardCharsets.UTF_8)
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            start()
        }
        val key = testKey("organization")
        val client = FleetbaseClient(true, "http://127.0.0.1:${server!!.address.port}/v1", key, false, 2, 2)
        val service = FleetbaseIntegrationService(client, FleetbaseTenantBinding("TEN-ACME-PHARMA", "org_stockflow"))

        val organization = service.organization("TEN-ACME-PHARMA")

        assertEquals("Bearer $key", authorization.get())
        assertEquals("org_stockflow", organization.id)
        assertEquals("StockFlow Logistics", organization.name)
        assertTrue(organization.matchesExpectedOrganization)
        assertFalse(organization.toString().contains(key))
    }

    @Test
    fun `upstream errors do not expose credential or response body`() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/v1/vehicles") { exchange ->
                val body = "sensitive upstream diagnostic"
                val bytes = body.toByteArray(StandardCharsets.UTF_8)
                exchange.sendResponseHeaders(500, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            start()
        }
        val key = testKey("do_not_expose")
        val client = FleetbaseClient(true, "http://127.0.0.1:${server!!.address.port}/v1", key, false, 2, 2)

        val error = assertThrows(FleetbaseIntegrationException::class.java) { client.listVehicles(10) }

        assertEquals("FLEETBASE_UPSTREAM_ERROR", error.code)
        assertFalse(error.message.contains(key))
        assertFalse(error.message.contains("sensitive upstream diagnostic"))
    }

    @Test
    fun `order creation is explicitly undispatched and retains StockFlow identity`() {
        val authorization = AtomicReference<String>()
        val requestBody = AtomicReference<String>()
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/v1/orders") { exchange ->
                authorization.set(exchange.requestHeaders.getFirst("Authorization"))
                requestBody.set(exchange.requestBody.bufferedReader().use { it.readText() })
                val body = """{"id":"order_stockflow_01","internal_id":"SF-TRF-ABC123","status":"created","dispatched":false,"tracking_number":{"tracking_number":"FB-1001","url":"https://example.test/FB-1001"}}"""
                val bytes = body.toByteArray(StandardCharsets.UTF_8)
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(201, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            start()
        }
        val key = testKey("write")
        val client = FleetbaseClient(true, "http://127.0.0.1:${server!!.address.port}/v1", key, true, 2, 2)

        val created = client.createOrder(FleetbaseOrderCreateCommand(
            internalId = "SF-TRF-ABC123",
            pickup = mapOf("name" to "Chennai Warehouse", "address" to "Chennai, India"),
            dropoff = mapOf("name" to "Bengaluru Warehouse", "address" to "Bengaluru, India"),
            vehicleId = "vehicle_01",
            notes = "StockFlow transfer",
            meta = mapOf("stockflow_execution_id" to "execution-01")
        ))

        assertEquals("Bearer $key", authorization.get())
        assertTrue(requestBody.get().contains("\"dispatch\":false"))
        assertTrue(requestBody.get().contains("\"internal_id\":\"SF-TRF-ABC123\""))
        assertTrue(requestBody.get().contains("\"vehicle\":\"vehicle_01\""))
        assertEquals("order_stockflow_01", created.id)
        assertEquals("FB-1001", created.trackingNumber)
        assertFalse(created.dispatched)
    }

    @Test
    fun `order dispatch uses the explicit Fleetbase dispatch transition`() {
        val authorization = AtomicReference<String>()
        val method = AtomicReference<String>()
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/v1/orders/order_stockflow_01/dispatch") { exchange ->
                authorization.set(exchange.requestHeaders.getFirst("Authorization"))
                method.set(exchange.requestMethod)
                val body = """{"id":"order_stockflow_01","status":"dispatched","dispatched":true,"tracking_number":{"tracking_number":"FB-1001"}}"""
                val bytes = body.toByteArray(StandardCharsets.UTF_8)
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            start()
        }
        val key = testKey("dispatch")
        val client = FleetbaseClient(true, "http://127.0.0.1:${server!!.address.port}/v1", key, true, 2, 2)

        val dispatched = client.dispatchOrder("order_stockflow_01")

        assertEquals("PATCH", method.get())
        assertEquals("Bearer $key", authorization.get())
        assertTrue(dispatched.dispatched)
        assertEquals("dispatched", dispatched.status)
        assertEquals("FB-1001", dispatched.trackingNumber)
    }

    @Test
    fun `order tracker and ETA are normalized for StockFlow`() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/v1/orders/order_track_01") { exchange ->
                val body = """{"id":"order_track_01","status":"started","dispatched":true,"started":true,"tracking_number":{"tracking_number":"FB-TRACK-01"}}"""
                val bytes = body.toByteArray(StandardCharsets.UTF_8)
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            createContext("/v1/orders/order_track_01/tracker") { exchange ->
                val body = """{"driver_current_location":{"latitude":12.9716,"longitude":77.5946},"progress_percentage":42.5,"total_distance":10000,"completed_distance":4250,"current_destination_eta":600,"completion_eta":1200,"estimated_completion_time":"2026-08-25T15:00:00Z","current_destination":{"name":"Bengaluru Hub"}}"""
                val bytes = body.toByteArray(StandardCharsets.UTF_8)
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            createContext("/v1/orders/order_track_01/eta") { exchange ->
                val body = """{"place_01":600,"place_02":1200}"""
                val bytes = body.toByteArray(StandardCharsets.UTF_8)
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            start()
        }
        val client = FleetbaseClient(true, "http://127.0.0.1:${server!!.address.port}/v1", testKey("tracking"), false, 2, 2)

        val order = client.getOrder("order_track_01")
        val tracking = client.tracking("order_track_01")

        assertEquals("started", order.status)
        assertTrue(order.dispatched)
        assertEquals("FB-TRACK-01", order.trackingNumber)
        assertEquals(42.5, tracking.progressPercentage)
        assertEquals(12.9716, tracking.latitude)
        assertEquals("Bengaluru Hub", tracking.currentDestination)
        assertEquals(1200L, tracking.etaByDestination["place_02"])
    }
}
