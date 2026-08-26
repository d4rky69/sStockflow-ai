package com.stockflow.maps

object GooglePolylineDecoder {
    fun decode(encoded: String): List<GoogleRoutePoint> {
        if (encoded.isBlank()) return emptyList()
        val points = mutableListOf<GoogleRoutePoint>()
        var index = 0
        var latitude = 0
        var longitude = 0

        while (index < encoded.length) {
            val latitudeValue = decodeValue(encoded, index)
            index = latitudeValue.nextIndex
            latitude += latitudeValue.delta

            if (index >= encoded.length) throw IllegalArgumentException("Encoded polyline ended before longitude")
            val longitudeValue = decodeValue(encoded, index)
            index = longitudeValue.nextIndex
            longitude += longitudeValue.delta

            points += GoogleRoutePoint(latitude / 100000.0, longitude / 100000.0)
        }
        return points
    }

    private fun decodeValue(encoded: String, startIndex: Int): DecodedValue {
        var index = startIndex
        var result = 0
        var shift = 0
        var value: Int
        do {
            if (index >= encoded.length) throw IllegalArgumentException("Malformed encoded polyline")
            value = encoded[index++].code - 63
            result = result or ((value and 0x1f) shl shift)
            shift += 5
        } while (value >= 0x20)
        val delta = if ((result and 1) != 0) (result shr 1).inv() else result shr 1
        return DecodedValue(delta, index)
    }

    private data class DecodedValue(val delta: Int, val nextIndex: Int)
}
