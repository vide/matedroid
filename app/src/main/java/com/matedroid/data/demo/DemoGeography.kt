package com.matedroid.data.demo

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** A point on a demo route: real coordinates, with the elevation that belongs to them. */
internal data class DemoPoint(val lat: Double, val lon: Double, val elevationM: Int)

/**
 * A route the demo car drives, as a coarse polyline through real places.
 *
 * Distance is measured from [waypoints] rather than stored alongside them, so the number
 * on the drive card can never disagree with the line drawn on the map.
 */
internal data class DemoRoute(
    val name: String,
    val startAddress: String,
    val endAddress: String,
    val waypoints: List<DemoPoint>,
    /** Cruising speed, used to derive both duration and the per-position speed trace. */
    val cruiseKmh: Int,
    /** Fair-weather consumption; the generator adds a seasonal penalty on top. */
    val baseConsumptionWhKm: Double
) {
    val distanceKm: Double by lazy {
        waypoints.zipWithNext().sumOf { (a, b) -> haversineKm(a, b) }
    }

    /**
     * Average speed is below cruising: every route has junctions, towns and a stop at each
     * end. The factor is tuned against the speed profile the generator builds, so that the
     * profile needs almost no rescaling to cover the distance in this time — rescaling is
     * what used to push motorway peaks past 140 km/h.
     */
    val durationMin: Int
        get() = ((distanceKm / (cruiseKmh * AVERAGE_SPEED_FACTOR)) * 60).toInt().coerceAtLeast(1)

    fun reversed(name: String): DemoRoute = copy(
        name = name,
        startAddress = endAddress,
        endAddress = startAddress,
        waypoints = waypoints.reversed()
    )

    /**
     * A stretch of this route, both ends inclusive. Splitting a drive at a charger is how a
     * Supercharger stop mid-journey stays coherent: two drives with a charge between them,
     * which is also how TeslaMate records it.
     */
    fun segment(name: String, fromIndex: Int, toIndex: Int, startAddress: String, endAddress: String) =
        copy(
            name = name,
            startAddress = startAddress,
            endAddress = endAddress,
            waypoints = waypoints.subList(fromIndex, toIndex + 1)
        )

    private companion object {
        const val AVERAGE_SPEED_FACTOR = 0.72
    }
}

internal fun haversineKm(a: DemoPoint, b: DemoPoint): Double {
    val earthRadiusKm = 6371.0
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLon = Math.toRadians(b.lon - a.lon)
    val h = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * earthRadiusKm * asin(min(1.0, sqrt(h)))
}

/**
 * The demo car lives in Girona and drives the Costa Brava, with the Barcelona commute,
 * the odd run over the border to Perpignan, and a couple of trips up to Andorra.
 *
 * Three countries is the point of the France and Andorra routes: Visited Countries has
 * nothing to show from a dataset that never leaves one.
 */
internal object DemoRoutes {

    // Home, and the anchor for the home charger and the parked-car status.
    val HOME = DemoPoint(41.9794, 2.8214, 70)
    const val HOME_ADDRESS = "Carrer de la Rutlla, Girona"

    private val GIRONA_TO_BARCELONA = DemoRoute(
        name = "Girona → Barcelona",
        startAddress = HOME_ADDRESS,
        endAddress = "Avinguda Diagonal, Barcelona",
        waypoints = listOf(
            HOME,
            DemoPoint(41.8875, 2.7842, 110),  // Riudellots de la Selva
            DemoPoint(41.7717, 2.7350, 85),   // Maçanet de la Selva
            DemoPoint(41.7486, 2.6367, 60),   // Hostalric
            DemoPoint(41.6897, 2.4919, 140),  // Sant Celoni
            DemoPoint(41.6083, 2.2875, 145),  // Granollers
            DemoPoint(41.5397, 2.2130, 65),   // Mollet del Vallès
            DemoPoint(41.4416, 2.1867, 40),   // Nus de la Trinitat
            DemoPoint(41.3936, 2.1400, 25)    // Avinguda Diagonal
        ),
        cruiseKmh = 118,
        baseConsumptionWhKm = 158.0
    )

    private val GIRONA_TO_PALS = DemoRoute(
        name = "Girona → Pals",
        startAddress = HOME_ADDRESS,
        endAddress = "Avinguda del Mediterrani, Pals",
        waypoints = listOf(
            HOME,
            DemoPoint(42.0206, 2.8892, 45),   // Celrà
            DemoPoint(42.0347, 2.9403, 30),   // Bordils
            DemoPoint(42.0617, 3.0433, 20),   // Verges
            DemoPoint(42.0432, 3.1274, 15),   // Torroella de Montgrí
            DemoPoint(41.9683, 3.1467, 25)    // Pals
        ),
        cruiseKmh = 85,
        baseConsumptionWhKm = 146.0
    )

    private val COSTA_BRAVA_LOOP = DemoRoute(
        name = "Costa Brava loop",
        startAddress = "Avinguda del Mediterrani, Pals",
        endAddress = "Avinguda del Mediterrani, Pals",
        waypoints = listOf(
            DemoPoint(41.9683, 3.1467, 25),   // Pals
            DemoPoint(41.9542, 3.2078, 200),  // Begur
            DemoPoint(41.9175, 3.1631, 85),   // Palafrugell
            DemoPoint(41.9683, 3.1467, 25)    // Pals
        ),
        cruiseKmh = 62,
        baseConsumptionWhKm = 171.0
    )

    private val GIRONA_TO_PERPIGNAN = DemoRoute(
        name = "Girona → Perpignan",
        startAddress = HOME_ADDRESS,
        endAddress = "Boulevard Wilson, Perpignan",
        waypoints = listOf(
            HOME,
            DemoPoint(42.1108, 2.9330, 50),   // Báscara
            DemoPoint(42.2662, 2.9622, 40),   // Figueres
            DemoPoint(42.4194, 2.8760, 110),  // La Jonquera — the border
            DemoPoint(42.5261, 2.8342, 90),   // Le Boulou
            DemoPoint(42.6987, 2.8955, 40)    // Perpignan
        ),
        cruiseKmh = 120,
        baseConsumptionWhKm = 168.0
    )

    private val GIRONA_TO_ANDORRA = DemoRoute(
        name = "Girona → Andorra la Vella",
        startAddress = HOME_ADDRESS,
        endAddress = "Avinguda Meritxell, Andorra la Vella",
        waypoints = listOf(
            HOME,
            DemoPoint(41.9301, 2.2545, 494),  // Vic
            DemoPoint(42.1017, 1.8461, 715),  // Berga
            DemoPoint(42.2661, 1.7625, 1100), // Túnel del Cadí
            DemoPoint(42.3697, 1.7767, 1050), // Bellver de Cerdanya
            DemoPoint(42.3582, 1.4590, 691),  // La Seu d'Urgell
            DemoPoint(42.5063, 1.5218, 1023)  // Andorra la Vella
        ),
        cruiseKmh = 92,
        baseConsumptionWhKm = 189.0
    )

    private val GIRONA_ERRAND = DemoRoute(
        name = "Girona errand",
        startAddress = HOME_ADDRESS,
        endAddress = "Carrer de Santa Clara, Girona",
        waypoints = listOf(
            HOME,
            DemoPoint(41.9861, 2.8250, 75),   // Parc de la Devesa
            DemoPoint(41.9930, 2.8300, 80),   // Pont Major
            DemoPoint(41.9836, 2.8235, 72)    // Carrer de Santa Clara
        ),
        cruiseKmh = 34,
        baseConsumptionWhKm = 194.0
    )

    val COMMUTE_OUT = GIRONA_TO_BARCELONA
    val COMMUTE_BACK = GIRONA_TO_BARCELONA.reversed("Barcelona → Girona")
    val BEACH_OUT = GIRONA_TO_PALS
    val BEACH_BACK = GIRONA_TO_PALS.reversed("Pals → Girona")
    val COASTAL = COSTA_BRAVA_LOOP
    val FRANCE_OUT = GIRONA_TO_PERPIGNAN
    val FRANCE_BACK = GIRONA_TO_PERPIGNAN.reversed("Perpignan → Girona")
    val ANDORRA_OUT = GIRONA_TO_ANDORRA
    val ANDORRA_BACK = GIRONA_TO_ANDORRA.reversed("Andorra la Vella → Girona")
    val ERRAND = GIRONA_ERRAND
    val ERRAND_BACK = GIRONA_ERRAND.reversed("Girona → home")

    // The Barcelona commute, split at the Maçanet Supercharger (waypoint 6 of the return
    // leg) for the days the car stops to charge on the way home.
    val COMMUTE_BACK_TO_MACANET = COMMUTE_BACK.segment(
        name = "Barcelona → Maçanet de la Selva",
        fromIndex = 0,
        toIndex = 6,
        startAddress = "Avinguda Diagonal, Barcelona",
        endAddress = "Supercharger Maçanet de la Selva"
    )

    val MACANET_TO_HOME = COMMUTE_BACK.segment(
        name = "Maçanet de la Selva → Girona",
        fromIndex = 6,
        toIndex = 8,
        startAddress = "Supercharger Maçanet de la Selva",
        endAddress = HOME_ADDRESS
    )
}

/** Where the demo car charges. Superchargers are DC; home and the hotel are AC. */
internal data class DemoChargerSite(
    val address: String,
    val point: DemoPoint,
    val isDc: Boolean,
    val peakPowerKw: Int,
    val pricePerKwh: Double,
    val phases: Int?,
    val voltage: Int,
    val maxCurrent: Int
)

internal object DemoChargers {
    val HOME = DemoChargerSite(
        address = "Home",
        point = DemoRoutes.HOME,
        isDc = false,
        peakPowerKw = 11,
        pricePerKwh = 0.1450,
        phases = 2,
        voltage = 233,
        maxCurrent = 16
    )

    val MACANET = DemoChargerSite(
        address = "Supercharger Maçanet de la Selva",
        point = DemoPoint(41.7845, 2.7333, 90),
        isDc = true,
        peakPowerKw = 197,
        pricePerKwh = 0.3900,
        phases = 0,
        voltage = 396,
        maxCurrent = 497
    )

    val FIGUERES = DemoChargerSite(
        address = "Supercharger Figueres",
        point = DemoPoint(42.2620, 2.9750, 42),
        isDc = true,
        peakPowerKw = 172,
        pricePerKwh = 0.4100,
        phases = 0,
        voltage = 391,
        maxCurrent = 440
    )

    val PERPIGNAN = DemoChargerSite(
        address = "Supercharger Perpignan Sud",
        point = DemoPoint(42.6633, 2.8794, 45),
        isDc = true,
        peakPowerKw = 148,
        pricePerKwh = 0.4700,
        phases = 0,
        voltage = 388,
        maxCurrent = 381
    )

    val ANDORRA_HOTEL = DemoChargerSite(
        address = "Hotel, Avinguda Meritxell, Andorra la Vella",
        point = DemoPoint(42.5063, 1.5218, 1023),
        isDc = false,
        peakPowerKw = 22,
        pricePerKwh = 0.0,
        phases = 2,
        voltage = 231,
        maxCurrent = 32
    )
}
