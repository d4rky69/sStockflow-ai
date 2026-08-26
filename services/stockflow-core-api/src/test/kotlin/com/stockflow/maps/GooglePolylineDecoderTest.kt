package com.stockflow.maps

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GooglePolylineDecoderTest {
    @Test
    fun `decodes the standard Google encoded polyline`() {
        val points = GooglePolylineDecoder.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@")

        assertThat(points).hasSize(3)
        assertThat(points[0].latitude).isEqualTo(38.5)
        assertThat(points[0].longitude).isEqualTo(-120.2)
        assertThat(points[1].latitude).isEqualTo(40.7)
        assertThat(points[1].longitude).isEqualTo(-120.95)
        assertThat(points[2].latitude).isEqualTo(43.252)
        assertThat(points[2].longitude).isEqualTo(-126.453)
    }

    @Test
    fun `returns no points for an empty polyline`() {
        assertThat(GooglePolylineDecoder.decode("")).isEmpty()
    }
}
