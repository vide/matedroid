package com.matedroid.domain

/**
 * Douglas-Peucker polyline simplification.
 * Reduces GPS point count while preserving route shape.
 */
object RouteSimplifier {

    /**
     * Simplify a list of [points] using the Douglas-Peucker algorithm.
     * [epsilon] is the maximum perpendicular distance (in degrees) a point
     * can deviate before it must be kept. A value of ~0.0001 (~11m) works
     * well for trip overview maps.
     */
    fun <T> simplify(
        points: List<T>,
        epsilon: Double = 0.0001,
        lat: (T) -> Double,
        lon: (T) -> Double
    ): List<T> {
        if (points.size <= 2) return points

        // Find the point with the maximum distance from the line start→end
        var maxDist = 0.0
        var maxIndex = 0
        val start = points.first()
        val end = points.last()

        for (i in 1 until points.size - 1) {
            val d = perpendicularDistance(
                lat(points[i]), lon(points[i]),
                lat(start), lon(start),
                lat(end), lon(end)
            )
            if (d > maxDist) {
                maxDist = d
                maxIndex = i
            }
        }

        return if (maxDist > epsilon) {
            val left = simplify(points.subList(0, maxIndex + 1), epsilon, lat, lon)
            val right = simplify(points.subList(maxIndex, points.size), epsilon, lat, lon)
            left.dropLast(1) + right
        } else {
            listOf(start, end)
        }
    }

    private fun perpendicularDistance(
        px: Double, py: Double,
        ax: Double, ay: Double,
        bx: Double, by: Double
    ): Double {
        val dx = bx - ax
        val dy = by - ay
        if (dx == 0.0 && dy == 0.0) {
            // Start and end are the same point
            val ex = px - ax
            val ey = py - ay
            return kotlin.math.sqrt(ex * ex + ey * ey)
        }
        val num = kotlin.math.abs(dy * px - dx * py + bx * ay - by * ax)
        val den = kotlin.math.sqrt(dx * dx + dy * dy)
        return num / den
    }
}
