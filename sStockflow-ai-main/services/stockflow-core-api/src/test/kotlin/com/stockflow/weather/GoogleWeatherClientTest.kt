package com.stockflow.weather

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress

class GoogleWeatherClientTest {
    private var server: HttpServer? = null

    @AfterEach
    fun stopServer() {
        server?.stop(0)
    }

    @Test
    fun `hazard alerts retain flood and landslide zones and discard unrelated weather`() {
        server = HttpServer.create(InetSocketAddress(0), 0).apply {
            createContext("/v1/publicAlerts:lookup") { exchange ->
                val response = """
                    {
                      "regionCode": "IN",
                      "weatherAlerts": [
                        {
                          "alertId": "flood-1",
                          "alertTitle": {"text": "River flood warning"},
                          "eventType": "RIVER_FLOODING",
                          "areaName": "Test river corridor",
                          "polygon": "{\"type\":\"Polygon\",\"coordinates\":[[[91.0,26.0],[92.0,26.0],[92.0,25.0],[91.0,26.0]]]}",
                          "severity": "SEVERE",
                          "certainty": "LIKELY",
                          "urgency": "IMMEDIATE",
                          "startTime": "2020-01-01T00:00:00Z",
                          "expirationTime": "2099-01-01T00:00:00Z",
                          "dataSource": {"name": "Test Authority", "authorityUri": "https://authority.example"}
                        },
                        {
                          "alertId": "landslide-1",
                          "alertTitle": {"text": "Landslide watch"},
                          "eventType": "LANDSLIDE",
                          "areaName": "Test hill district",
                          "severity": "MODERATE",
                          "certainty": "POSSIBLE",
                          "urgency": "FUTURE",
                          "startTime": "2099-01-01T00:00:00Z",
                          "dataSource": {"name": "Test Authority", "authorityUri": "https://authority.example"}
                        },
                        {
                          "alertId": "wind-1",
                          "alertTitle": {"text": "Wind advisory"},
                          "eventType": "WIND",
                          "areaName": "Test district",
                          "dataSource": {"name": "Test Authority"}
                        }
                      ]
                    }
                """.trimIndent().toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, response.size.toLong())
                exchange.responseBody.use { it.write(response) }
            }
            start()
        }

        val client = GoogleWeatherClient("test-key", "http://localhost:${server!!.address.port}", 2, 2)
        val result = client.hazardAlerts(listOf(HazardAlertLocation(25.5, 91.5)))

        assertEquals(2, result.count)
        assertEquals(setOf("FLOOD", "LANDSLIDE"), result.alerts.map { it.hazardType }.toSet())
        assertEquals("ACTIVE", result.alerts.first { it.id == "flood-1" }.phase)
        assertEquals("FORECAST", result.alerts.first { it.id == "landslide-1" }.phase)
        assertTrue(result.alerts.first { it.id == "flood-1" }.polygonGeoJson!!.contains("Polygon"))
        assertEquals(setOf("IN"), result.regionCodes)
    }
}
