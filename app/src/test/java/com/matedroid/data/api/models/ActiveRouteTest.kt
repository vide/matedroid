package com.matedroid.data.api.models

import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Locks the JSON field names of TeslaMate's `active_route` block and the rule that
 * decides whether the dashboard treats it as a live route.
 */
class ActiveRouteTest {

    private val adapter = Moshi.Builder().build().adapter(CarStatusResponse::class.java)

    /** A trimmed copy of a real /status payload from a car that is navigating. */
    private fun statusJson(activeRoute: String) = """
        {"data":{"status":{
            "state":"driving",
            "driving_details":{
                "shift_state":"D","power":43,"speed":114,"heading":68,"elevation":87,
                "active_route":$activeRoute
            }
        }}}
    """.trimIndent()

    @Test
    fun `an active route is parsed field by field`() {
        val json = statusJson(
            """
            {"destination":"Casa","energy_at_arrival":32,
             "distance_to_arrival":50.138019575424,"minutes_to_arrival":35.61006,
             "traffic_minutes_delay":0,
             "location":{"latitude":41.970389,"longitude":3.150913}}
            """.trimIndent()
        )

        val route = adapter.fromJson(json)?.data?.status?.drivingDetails?.activeRoute
        assertNotNull(route)
        assertEquals("Casa", route!!.destination)
        assertEquals(32, route.energyAtArrival)
        assertEquals(50.138019575424, route.distanceToArrival!!, 1e-9)
        assertEquals(35.61006, route.minutesToArrival!!, 1e-9)
        assertEquals(0.0, route.trafficMinutesDelay!!, 1e-9)
        val destinationPin = route.location!!
        assertEquals(41.970389, destinationPin.latitude!!, 1e-9)
        assertEquals(3.150913, destinationPin.longitude!!, 1e-9)
    }

    @Test
    fun `the dashboard sees a route while there is distance and time left`() {
        val json = statusJson(
            """{"destination":"Casa","distance_to_arrival":50.1,"minutes_to_arrival":35.6}"""
        )

        assertEquals("Casa", adapter.fromJson(json)?.data?.status?.activeRoute?.destination)
    }

    @Test
    fun `a route left over after arrival is not shown`() {
        // TeslaMate keeps the destination name in later position rows once the drive ends.
        val json = statusJson(
            """{"destination":"Casa","distance_to_arrival":0,"minutes_to_arrival":0}"""
        )

        assertNull(adapter.fromJson(json)?.data?.status?.activeRoute)
    }

    @Test
    fun `a nameless route is not shown`() {
        val json = statusJson(
            """{"destination":"","distance_to_arrival":50.1,"minutes_to_arrival":35.6}"""
        )

        assertNull(adapter.fromJson(json)?.data?.status?.activeRoute)
    }

    @Test
    fun `a status without any route block is not a route`() {
        val json = """{"data":{"status":{"state":"asleep","driving_details":{"power":0}}}}"""

        assertNull(adapter.fromJson(json)?.data?.status?.activeRoute)
    }

    @Test
    fun `minutes and traffic delay are rounded to whole minutes`() {
        val route = ActiveRoute(
            destination = "Casa",
            distanceToArrival = 50.1,
            minutesToArrival = 35.61006,
            trafficMinutesDelay = 7.4
        )

        assertEquals(36, route.minutesToArrivalRounded)
        assertEquals(7, route.trafficDelayMinutes)
    }

    @Test
    fun `no traffic delay means no delay to show`() {
        val route = ActiveRoute(
            destination = "Casa",
            distanceToArrival = 50.1,
            minutesToArrival = 35.6,
            trafficMinutesDelay = 0.2
        )

        assertNull(route.trafficDelayMinutes)
    }
}
