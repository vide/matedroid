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

        // Iterative Douglas-Peucker: mark points to keep in a bitset, using an explicit
        // stack of index ranges. Same result as the classic recursion, but with no
        // per-level list allocation and a bounded heap stack (no deep call recursion).
        val keep = BooleanArray(points.size)
        keep[0] = true
        keep[points.size - 1] = true

        val stack = ArrayDeque<Int>()
        stack.addLast(0)
        stack.addLast(points.size - 1)
        while (stack.isNotEmpty()) {
            val end = stack.removeLast()
            val start = stack.removeLast()

            val ax = lat(points[start]); val ay = lon(points[start])
            val bx = lat(points[end]); val by = lon(points[end])

            var maxDist = 0.0
            var maxIndex = -1
            for (i in start + 1 until end) {
                val d = perpendicularDistance(lat(points[i]), lon(points[i]), ax, ay, bx, by)
                if (d > maxDist) {
                    maxDist = d
                    maxIndex = i
                }
            }

            if (maxIndex != -1 && maxDist > epsilon) {
                keep[maxIndex] = true
                stack.addLast(start); stack.addLast(maxIndex)
                stack.addLast(maxIndex); stack.addLast(end)
            }
        }

        return points.filterIndexed { i, _ -> keep[i] }
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
