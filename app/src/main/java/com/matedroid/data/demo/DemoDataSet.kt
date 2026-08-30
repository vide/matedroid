package com.matedroid.data.demo

import com.matedroid.data.api.models.BatteryDetails
import com.matedroid.data.api.models.BatteryHealth
import com.matedroid.data.api.models.CarData
import com.matedroid.data.api.models.CarDetails
import com.matedroid.data.api.models.CarExterior
import com.matedroid.data.api.models.CarGeodata
import com.matedroid.data.api.models.CarSettings
import com.matedroid.data.api.models.CarStatus
import com.matedroid.data.api.models.CarStatusDetails
import com.matedroid.data.api.models.CarVersions
import com.matedroid.data.api.models.ChargeBatteryDetails
import com.matedroid.data.api.models.ChargeBatteryInfo
import com.matedroid.data.api.models.ChargeData
import com.matedroid.data.api.models.ChargeDetail
import com.matedroid.data.api.models.ChargePoint
import com.matedroid.data.api.models.ChargeRange
import com.matedroid.data.api.models.ChargerDetails
import com.matedroid.data.api.models.ChargingDetails
import com.matedroid.data.api.models.ClimateDetails
import com.matedroid.data.api.models.DriveBatteryDetails
import com.matedroid.data.api.models.DriveBatteryInfo
import com.matedroid.data.api.models.DriveClimateInfo
import com.matedroid.data.api.models.DriveData
import com.matedroid.data.api.models.DriveDetail
import com.matedroid.data.api.models.DriveOdometerDetails
import com.matedroid.data.api.models.DrivePosition
import com.matedroid.data.api.models.DriveRange
import com.matedroid.data.api.models.DrivingDetails
import com.matedroid.data.api.models.TeslamateStats
import com.matedroid.data.api.models.TpmsDetails
import com.matedroid.data.api.models.Units
import com.matedroid.data.api.models.UpdateData
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * The sample dataset behind [DemoMode], generated rather than shipped as a JSON asset.
 *
 * Generating it buys the one property a canned file cannot have: the history always ends
 * *today*. A bundled dataset would be visibly stale the first time someone opened the demo a
 * year after the release that contained it, and every "last 30 days" filter in the app would
 * come back empty. Everything here is anchored to [anchor], so the newest drive is always
 * yesterday's and the year filters always have two years to choose between.
 *
 * The history is a simulation rather than a pile of independent random rows: one running
 * state-of-charge and one running odometer are carried through the whole year, drives spend
 * energy and charges put it back. That is what keeps the derived screens honest — battery
 * levels line up end-to-end between consecutive drives, the odometer only ever increases,
 * and charge costs match the energy actually delivered.
 *
 * Deterministic given [anchor]: a fixed seed drives every random choice, so the same day
 * produces the same history and drive ids stay stable while the process lives.
 *
 * Everything is metric. TeslamateAPI converts to the user's unit system server-side and the
 * app never converts (see `UnitFormatter`), so the demo does what a metric TeslaMate would:
 * it reports km / bar / °C and says so in [units].
 */
@Suppress("LargeClass")
internal class DemoDataSet(private val anchor: Instant, private val zone: ZoneId) {

    companion object {
        private const val SEED = 20_260_601L

        /** How much history to simulate. A year spans two calendar years, so year filters have a choice. */
        private const val HISTORY_DAYS = 365L

        /** Model Y Juniper Long Range AWD, as new and as it is now. */
        private const val CAPACITY_NEW_KWH = 78.4
        private const val CAPACITY_NOW_KWH = 74.6
        private const val RANGE_NEW_KM = 533.0
        private const val RANGE_NOW_KM = 507.1

        private const val ODOMETER_START_KM = 46_500.0

        /** SoC the car charges up to at home, and the level that prompts plugging in. */
        private const val CHARGE_LIMIT_SOC = 80
        private const val PLUG_IN_BELOW_SOC = 38

        /**
         * The live session runs on a wall-clock cycle so the demo shows both of the states
         * that matter: a charge in progress (the app's live charging view and notification)
         * and a car parked at its charge limit. Without the cycle one of the two would never
         * be reachable, and which one you got would depend on when the release was built.
         */
        private const val CYCLE_SECONDS = 4 * 60 * 60L

        /** 45% → 80% of 74.6 kWh at 11 kW works out at this much of the cycle. */
        private const val CHARGE_PHASE_SECONDS = 8_540L
        private const val LIVE_START_SOC = 45

        private val TIMESTAMP: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

        /** Sampling interval for the position and charge-point traces behind the detail charts. */
        private const val DRIVE_SAMPLE_SECONDS = 20
        private const val CHARGE_SAMPLE_SECONDS = 120

        /** How long the car takes to reach cruising speed, and to brake back out of it. */
        private const val PULL_AWAY_SECONDS = 22.0

        /** How much of a drive is spent getting out of, and back into, a town. */
        private const val URBAN_TAIL_FRACTION = 0.12
        private const val TOWN_SPEED_FRACTION = 0.34

        /** How much of the tail is the join itself, rather than town speed. */
        private const val URBAN_TAIL_JOIN = 0.035

        /** Kerb weight of a Model Y with someone in it, for the hill and acceleration terms. */
        private const val KERB_WEIGHT_KG = 2050.0
        private const val GRAVITY = 9.81

        // Rolling resistance and aero drag, sized so a Model Y at 120 km/h draws the ~18 kW
        // that its ~158 Wh/km motorway consumption implies.
        private const val ROLLING_RESISTANCE_N = 201.0
        private const val AERO_DRAG_N_S2_M2 = 0.331

        /** Climate, screen and the rest of the car, drawn whether or not it is moving. */
        private const val AUX_LOAD_KW = 0.7

        /**
         * How long a traffic slowdown lasts. Held in seconds rather than as a fraction of the
         * drive so that a long motorway run and a short errand are slowed by the same kind of
         * event, and the profile's average speed stays comparable between them.
         */
        private const val SLOWDOWN_SECONDS = 45.0
    }

    val units = Units(unitOfLength = "km", unitOfPressure = "bar", unitOfTemperature = "C")

    private val random = Random(SEED)

    // ---------------------------------------------------------------- history

    private data class DriveRecord(
        val id: Int,
        val route: DemoRoute,
        val start: Instant,
        val durationMin: Int,
        val startSoc: Int,
        val endSoc: Int,
        val odometerStart: Double,
        val energyKwh: Double,
        val outsideTemp: Double,
        val insideTemp: Double
    ) {
        val end: Instant get() = start.plus(durationMin.toLong(), ChronoUnit.MINUTES)
        val odometerEnd: Double get() = odometerStart + route.distanceKm
    }

    private data class ChargeRecord(
        val id: Int,
        val site: DemoChargerSite,
        val start: Instant,
        val durationMin: Int,
        val startSoc: Int,
        val endSoc: Int,
        val odometer: Double,
        val outsideTemp: Double
    ) {
        val end: Instant get() = start.plus(durationMin.toLong(), ChronoUnit.MINUTES)
    }

    private val driveRecords = mutableListOf<DriveRecord>()
    private val chargeRecords = mutableListOf<ChargeRecord>()

    /** The live session's id sits above every historical one so it never collides. */
    private val liveChargeId: Int

    init {
        simulateHistory()
        liveChargeId = (chargeRecords.maxOfOrNull { it.id } ?: 0) + 1
    }

    // ---------------------------------------------------------------- the car

    val car: CarData = CarData(
        carId = DemoMode.CAR_ID,
        name = "Elektra",
        carDetails = CarDetails(
            model = "Y",
            trimBadging = "74",
            vin = "XP7YGCEK9SB000042",
            // kWh/km, matching what TeslamateAPI reports for this field.
            efficiency = 0.148
        ),
        carExterior = CarExterior(
            exteriorColor = "UltraRed",
            spoilerType = "None",
            wheelType = "Crossflow19"
        ),
        carSettings = CarSettings(freeSupercharging = false),
        teslamateStats = TeslamateStats(
            totalCharges = chargeRecords.size,
            totalDrives = driveRecords.size
        )
    )

    val batteryHealth = BatteryHealth(
        maxRange = RANGE_NEW_KM,
        currentRange = RANGE_NOW_KM,
        maxCapacity = CAPACITY_NEW_KWH,
        currentCapacity = CAPACITY_NOW_KWH,
        // kWh/100 km, as TeslamateAPI reports it: capacity over range.
        ratedEfficiency = (CAPACITY_NOW_KWH / RANGE_NOW_KM * 100).round(1),
        batteryHealthPercentage = (CAPACITY_NOW_KWH / CAPACITY_NEW_KWH * 100).round(2)
    )

    /**
     * Software history, with versions derived from their own install date so they age with
     * the dataset instead of naming a year that has since passed.
     */
    val updates: List<UpdateData> = buildUpdates()

    // ---------------------------------------------------------------- public API

    val drives: List<DriveData> = driveRecords
        .sortedByDescending { it.start }
        .map { it.toSummary() }

    /**
     * Completed charges, newest first. The live session joins the list only once it has
     * finished — an in-progress charge belongs to `/charges/current`, and listing it too
     * would double-count its energy in every total on the charges screen.
     */
    fun charges(now: Instant): List<ChargeData> {
        val history = chargeRecords.sortedByDescending { it.start }.map { it.toSummary() }
        val live = liveSession(now)
        return if (live.charging) history else listOf(live.toSummary()) + history
    }

    fun driveDetail(driveId: Int): DriveDetail? =
        driveRecords.firstOrNull { it.id == driveId }?.toDetail()

    fun chargeDetail(chargeId: Int, now: Instant): ChargeDetail? {
        if (chargeId == liveChargeId) return liveSession(now).toDetail()
        return chargeRecords.firstOrNull { it.id == chargeId }?.toDetail()
    }

    /** Null when nothing is plugged in — the caller turns that into the API's "no active charge". */
    fun currentCharge(now: Instant): ChargeDetail? =
        liveSession(now).takeIf { it.charging }?.toDetail()

    fun status(now: Instant): CarStatus {
        val live = liveSession(now)
        val soc = live.soc
        val rangeKm = socToRangeKm(soc)
        val outside = outsideTempAt(now)
        val lastDrive = driveRecords.maxByOrNull { it.start }

        return CarStatus(
            displayName = car.name,
            state = if (live.charging) "charging" else "asleep",
            stateSince = (if (live.charging) live.start else live.end).format(),
            odometer = ((lastDrive?.odometerEnd) ?: ODOMETER_START_KM).round(2),
            carStatus = CarStatusDetails(
                healthy = true,
                locked = true,
                sentryMode = true,
                windowsOpen = false,
                doorsOpen = false,
                trunkOpen = false,
                frunkOpen = false,
                isUserPresent = false,
                centerDisplayState = "0"
            ),
            carGeodata = CarGeodata(
                geofence = "Home",
                latitude = DemoRoutes.HOME.lat,
                longitude = DemoRoutes.HOME.lon
            ),
            carVersions = CarVersions(
                version = updates.firstOrNull()?.version,
                updateAvailable = false,
                updateVersion = ""
            ),
            drivingDetails = DrivingDetails(
                shiftState = "",
                power = if (live.charging) -live.powerKw else 0,
                speed = 0,
                heading = 214,
                elevation = DemoRoutes.HOME.elevationM
            ),
            climateDetails = ClimateDetails(
                isClimateOn = false,
                insideTemp = (outside + 2.4).round(1),
                outsideTemp = outside.round(1),
                isPreconditioning = false
            ),
            batteryDetails = BatteryDetails(
                batteryLevel = soc,
                usableBatteryLevel = soc,
                estBatteryRange = (rangeKm * 0.88).round(2),
                ratedBatteryRange = rangeKm.round(2),
                idealBatteryRange = rangeKm.round(2)
            ),
            chargingDetails = if (live.charging) {
                ChargingDetails(
                    pluggedIn = true,
                    chargingState = "charging",
                    chargeEnergyAdded = live.energyAddedKwh.round(2),
                    chargeLimitSoc = CHARGE_LIMIT_SOC,
                    chargePortDoorOpen = true,
                    chargerActualCurrent = DemoChargers.HOME.maxCurrent,
                    chargerPhases = DemoChargers.HOME.phases,
                    chargerPower = live.powerKw,
                    chargerVoltage = DemoChargers.HOME.voltage,
                    chargeCurrentRequest = DemoChargers.HOME.maxCurrent,
                    chargeCurrentRequestMax = DemoChargers.HOME.maxCurrent,
                    timeToFullCharge = live.hoursToFull.round(2)
                )
            } else {
                ChargingDetails(
                    pluggedIn = false,
                    chargingState = "Disconnected",
                    chargeEnergyAdded = 0.0,
                    chargeLimitSoc = CHARGE_LIMIT_SOC,
                    chargePortDoorOpen = false,
                    timeToFullCharge = 0.0
                )
            },
            tpmsDetails = TpmsDetails(
                pressureFl = 2.925,
                pressureFr = 2.900,
                pressureRl = 2.975,
                pressureRr = 2.950,
                warningFl = false,
                warningFr = false,
                warningRl = false,
                warningRr = false
            )
        )
    }

    // ---------------------------------------------------------------- live session

    private data class LiveSession(
        val id: Int,
        val charging: Boolean,
        val start: Instant,
        val end: Instant,
        val soc: Int,
        val energyAddedKwh: Double,
        val powerKw: Int,
        val hoursToFull: Double,
        val outsideTemp: Double,
        val odometer: Double
    )

    private fun liveSession(now: Instant): LiveSession {
        val phase = Math.floorMod(now.epochSecond, CYCLE_SECONDS)
        val charging = phase < CHARGE_PHASE_SECONDS
        val elapsed = min(phase, CHARGE_PHASE_SECONDS)
        val progress = elapsed.toDouble() / CHARGE_PHASE_SECONDS
        val soc = (LIVE_START_SOC + (CHARGE_LIMIT_SOC - LIVE_START_SOC) * progress).roundToInt()
        val energyAdded = (soc - LIVE_START_SOC) / 100.0 * CAPACITY_NOW_KWH
        // The session started when this cycle did, and ended when the charging phase of the
        // cycle ran out — the car has been sitting at its limit ever since.
        val start = now.minusSeconds(phase)
        val end = start.plusSeconds(elapsed)
        val remainingHours = (CHARGE_PHASE_SECONDS - elapsed) / 3600.0

        return LiveSession(
            id = liveChargeId,
            charging = charging,
            start = start,
            end = end,
            soc = soc,
            energyAddedKwh = energyAdded,
            powerKw = DemoChargers.HOME.peakPowerKw,
            hoursToFull = remainingHours,
            outsideTemp = outsideTempAt(now),
            odometer = driveRecords.maxByOrNull { it.start }?.odometerEnd ?: ODOMETER_START_KM
        )
    }

    private fun LiveSession.toChargeRecord() = ChargeRecord(
        id = id,
        site = DemoChargers.HOME,
        start = start,
        durationMin = Duration.between(start, end).toMinutes().toInt().coerceAtLeast(1),
        startSoc = LIVE_START_SOC,
        endSoc = soc,
        odometer = odometer,
        outsideTemp = outsideTemp
    )

    private fun LiveSession.toSummary() = toChargeRecord().toSummary()

    private fun LiveSession.toDetail() = toChargeRecord().toDetail(isCharging = charging)

    // ---------------------------------------------------------------- simulation

    @Suppress("CyclomaticComplexMethod")
    private fun simulateHistory() {
        val today = anchor.atZone(zone).toLocalDate()
        val firstDay = today.minusDays(HISTORY_DAYS)

        var soc = 74
        var odometer = ODOMETER_START_KM
        var driveId = 1
        var chargeId = 1

        // Border runs are planned up front so both legs of a trip land on adjacent days.
        val franceTrips = pickTripDays(firstDay, today, count = 6)
        val andorraTrips = pickTripDays(firstDay, today, count = 3, avoid = franceTrips)

        var day = firstDay
        while (!day.isAfter(today)) {
            val legs = planDay(day, franceTrips, andorraTrips)

            for (leg in legs) {
                // A leg that names a charger always stops at it — that is what makes the
                // trip coherent. Otherwise the car only plugs in when the charge would not
                // comfortably cover what is ahead.
                val plannedSite = leg.chargeSiteBefore
                val lowForLeg = soc < (leg.route.distanceKm * 0.20).roundToInt() + 14
                if (plannedSite != null || lowForLeg || soc < PLUG_IN_BELOW_SOC) {
                    val site = plannedSite ?: DemoChargers.HOME
                    val target = chargeTargetFor(site, leg.route.distanceKm)
                    if (target > soc + 2) {
                        val chargeStart = leg.departure.minus(
                            durationMinutes(site, soc, target).toLong() + 15, ChronoUnit.MINUTES
                        )
                        chargeRecords += ChargeRecord(
                            id = chargeId++,
                            site = site,
                            start = chargeStart,
                            durationMin = durationMinutes(site, soc, target),
                            startSoc = soc,
                            endSoc = target,
                            odometer = odometer,
                            outsideTemp = outsideTempAt(chargeStart)
                        )
                        soc = target
                    }
                }

                val outside = outsideTempAt(leg.departure)
                val consumption = leg.route.baseConsumptionWhKm * seasonalFactor(outside) *
                    (0.94 + random.nextDouble() * 0.12)
                val energy = leg.route.distanceKm * consumption / 1000.0
                val drop = (energy / CAPACITY_NOW_KWH * 100).roundToInt().coerceAtLeast(1)
                val endSoc = (soc - drop).coerceAtLeast(6)

                driveRecords += DriveRecord(
                    id = driveId++,
                    route = leg.route,
                    start = leg.departure,
                    durationMin = (leg.route.durationMin * random.nextDouble(0.94, 1.18))
                        .roundToInt().coerceAtLeast(2),
                    startSoc = soc,
                    endSoc = endSoc,
                    odometerStart = odometer,
                    energyKwh = energy,
                    outsideTemp = outside,
                    insideTemp = (max(19.0, min(24.5, outside + 3)) + random.nextDouble(-0.8, 0.8))
                )

                soc = endSoc
                odometer += leg.route.distanceKm
            }

            // Plug in overnight once the car is home and low enough to bother.
            val lastLeg = legs.lastOrNull()
            if (lastLeg != null && lastLeg.endsAtHome && soc < PLUG_IN_BELOW_SOC + 12) {
                val chargeStart = day.atTime(22, 40).atZone(zone).toInstant()
                    .plusSeconds(random.nextLong(0, 3600))
                if (chargeStart.isBefore(anchor)) {
                    chargeRecords += ChargeRecord(
                        id = chargeId++,
                        site = DemoChargers.HOME,
                        start = chargeStart,
                        durationMin = durationMinutes(DemoChargers.HOME, soc, CHARGE_LIMIT_SOC),
                        startSoc = soc,
                        endSoc = CHARGE_LIMIT_SOC,
                        odometer = odometer,
                        outsideTemp = outsideTempAt(chargeStart)
                    )
                    soc = CHARGE_LIMIT_SOC
                }
            }

            day = day.plusDays(1)
        }

        // Anything the loop placed past "now" (a late leg on the final day) is not history yet.
        driveRecords.removeAll { !it.end.isBefore(anchor) }
        chargeRecords.removeAll { !it.end.isBefore(anchor) }
    }

    /** One leg of a day's driving, with where the car would have charged before setting off. */
    private data class Leg(
        val route: DemoRoute,
        val departure: Instant,
        val chargeSiteBefore: DemoChargerSite? = null,
        val endsAtHome: Boolean = true
    )

    @Suppress("CyclomaticComplexMethod")
    private fun planDay(
        day: LocalDate,
        franceTrips: Set<LocalDate>,
        andorraTrips: Set<LocalDate>
    ): List<Leg> {
        fun at(hour: Int, minute: Int): Instant =
            day.atTime(hour, minute).atZone(zone).toInstant()
                .plusSeconds(random.nextLong(-900, 900))

        // Border runs: out on the first day, back on the next.
        if (day in franceTrips) {
            return listOf(Leg(DemoRoutes.FRANCE_OUT, at(9, 30), endsAtHome = false))
        }
        if (day.minusDays(1) in franceTrips) {
            return listOf(
                Leg(
                    DemoRoutes.FRANCE_BACK, at(17, 15),
                    chargeSiteBefore = DemoChargers.PERPIGNAN
                )
            )
        }

        if (day in andorraTrips) {
            return listOf(Leg(DemoRoutes.ANDORRA_OUT, at(8, 45), endsAtHome = false))
        }
        if (day.minusDays(1) in andorraTrips) {
            return listOf(
                Leg(
                    DemoRoutes.ANDORRA_BACK, at(16, 30),
                    chargeSiteBefore = DemoChargers.ANDORRA_HOTEL
                )
            )
        }

        val legs = mutableListOf<Leg>()
        val weekday = day.dayOfWeek.value <= 5
        val roll = random.nextDouble()

        if (weekday) {
            when {
                roll < 0.52 -> {
                    legs += Leg(DemoRoutes.COMMUTE_OUT, at(7, 50), endsAtHome = false)
                    // Now and then the way home takes in a Supercharger stop at Maçanet,
                    // which is why the return trip is two legs rather than one.
                    if (random.nextDouble() < 0.22) {
                        legs += Leg(DemoRoutes.COMMUTE_BACK_TO_MACANET, at(17, 45), endsAtHome = false)
                        legs += Leg(
                            DemoRoutes.MACANET_TO_HOME, at(19, 5),
                            chargeSiteBefore = DemoChargers.MACANET
                        )
                    } else {
                        legs += Leg(DemoRoutes.COMMUTE_BACK, at(17, 50))
                    }
                }
                roll < 0.78 -> {
                    legs += Leg(DemoRoutes.ERRAND, at(18, 20), endsAtHome = false)
                    legs += Leg(DemoRoutes.ERRAND_BACK, at(19, 10))
                }
            }
        } else {
            when {
                roll < 0.46 -> {
                    legs += Leg(DemoRoutes.BEACH_OUT, at(10, 15), endsAtHome = false)
                    if (random.nextDouble() < 0.55) {
                        legs += Leg(DemoRoutes.COASTAL, at(12, 40), endsAtHome = false)
                    }
                    legs += Leg(DemoRoutes.BEACH_BACK, at(19, 20))
                }
                roll < 0.68 -> {
                    legs += Leg(DemoRoutes.ERRAND, at(11, 10), endsAtHome = false)
                    legs += Leg(DemoRoutes.ERRAND_BACK, at(12, 5))
                }
            }
        }

        return legs
    }

    private fun pickTripDays(
        from: LocalDate,
        to: LocalDate,
        count: Int,
        avoid: Set<LocalDate> = emptySet()
    ): Set<LocalDate> {
        val span = ChronoUnit.DAYS.between(from, to).toInt()
        val picked = mutableSetOf<LocalDate>()
        var guard = 0
        while (picked.size < count && guard++ < count * 20) {
            // Leave room for the return leg the next day.
            val candidate = from.plusDays(random.nextInt(0, max(1, span - 2)).toLong())
            val clashes = candidate in avoid || candidate.plusDays(1) in avoid ||
                candidate.minusDays(1) in avoid ||
                picked.any { abs(ChronoUnit.DAYS.between(it, candidate)) < 14 }
            if (!clashes) picked += candidate
        }
        return picked
    }

    private fun chargeTargetFor(site: DemoChargerSite, upcomingKm: Double): Int {
        val needed = (upcomingKm * 0.20).roundToInt() + 22
        // Nobody sits at a Supercharger to 100%; at home the car just charges to its limit.
        val cap = if (site.isDc) 80 else CHARGE_LIMIT_SOC
        return min(cap, max(needed, if (site.isDc) 72 else CHARGE_LIMIT_SOC))
    }

    // ---------------------------------------------------------------- mapping

    private fun DriveRecord.toSummary(): DriveData {
        val trace = trace(this)
        return DriveData(
            driveId = id,
            startDate = start.format(),
            endDate = end.format(),
            startAddress = route.startAddress,
            endAddress = route.endAddress,
            odometerDetails = DriveOdometerDetails(
                odometerStart = odometerStart.round(2),
                odometerEnd = odometerEnd.round(2),
                distance = route.distanceKm.round(2)
            ),
            durationMin = durationMin,
            durationStr = durationMin.toDurationString(),
            speedMax = trace.speeds.max().roundToInt(),
            speedAvg = (route.distanceKm / durationMin * 60).round(2),
            powerMax = trace.powers.max(),
            powerMin = trace.powers.min(),
            batteryDetails = DriveBatteryDetails(
                startBatteryLevel = startSoc,
                endBatteryLevel = endSoc,
                isRangeIdeal = false
            ),
            rangeIdeal = driveRange(),
            rangeRated = driveRange(),
            outsideTempAvg = outsideTemp.round(1),
            insideTempAvg = insideTemp.round(1),
            energyConsumedNet = energyKwh.round(3),
            consumptionNet = (energyKwh * 1000 / route.distanceKm).round(2)
        )
    }

    private fun DriveRecord.driveRange(): DriveRange {
        val startRange = socToRangeKm(startSoc)
        val endRange = socToRangeKm(endSoc)
        return DriveRange(
            startRange = startRange.round(2),
            endRange = endRange.round(2),
            rangeDiff = (startRange - endRange).round(2)
        )
    }

    private fun DriveRecord.toDetail(): DriveDetail {
        val summary = toSummary()
        return DriveDetail(
            driveId = id,
            startDate = summary.startDate,
            endDate = summary.endDate,
            startAddress = summary.startAddress,
            endAddress = summary.endAddress,
            odometerDetails = summary.odometerDetails,
            durationMin = summary.durationMin,
            durationStr = summary.durationStr,
            speedMax = summary.speedMax,
            speedAvg = summary.speedAvg,
            powerMax = summary.powerMax,
            powerMin = summary.powerMin,
            batteryDetails = summary.batteryDetails,
            rangeIdeal = summary.rangeIdeal,
            rangeRated = summary.rangeRated,
            outsideTempAvg = summary.outsideTempAvg,
            insideTempAvg = summary.insideTempAvg,
            energyConsumedNet = summary.energyConsumedNet,
            consumptionNet = summary.consumptionNet,
            positions = positions(this)
        )
    }

    private fun ChargeRecord.toSummary(): ChargeData {
        val added = (endSoc - startSoc) / 100.0 * CAPACITY_NOW_KWH
        val used = added / chargingEfficiency(site)
        return ChargeData(
            chargeId = id,
            startDate = start.format(),
            endDate = end.format(),
            address = site.address,
            chargeEnergyAdded = added.round(2),
            chargeEnergyUsed = used.round(2),
            cost = (used * site.pricePerKwh).round(2),
            durationMin = durationMin,
            durationStr = durationMin.toDurationString(),
            batteryDetails = ChargeBatteryDetails(
                startBatteryLevel = startSoc,
                endBatteryLevel = endSoc
            ),
            rangeIdeal = chargeRange(),
            rangeRated = chargeRange(),
            outsideTempAvg = outsideTemp.round(1),
            odometer = odometer.round(2),
            latitude = site.point.lat,
            longitude = site.point.lon
        )
    }

    private fun ChargeRecord.chargeRange() = ChargeRange(
        startRange = socToRangeKm(startSoc).round(2),
        endRange = socToRangeKm(endSoc).round(2)
    )

    private fun ChargeRecord.toDetail(isCharging: Boolean = false): ChargeDetail {
        val summary = toSummary()
        return ChargeDetail(
            chargeId = id,
            startDate = summary.startDate,
            endDate = summary.endDate,
            address = summary.address,
            chargeEnergyAdded = summary.chargeEnergyAdded,
            chargeEnergyUsed = summary.chargeEnergyUsed,
            cost = summary.cost,
            durationMin = summary.durationMin,
            durationStr = summary.durationStr,
            batteryDetails = ChargeBatteryDetails(
                startBatteryLevel = startSoc,
                endBatteryLevel = if (isCharging) null else endSoc,
                currentBatteryLevel = if (isCharging) endSoc else null
            ),
            rangeIdeal = summary.rangeIdeal,
            rangeRated = summary.rangeRated,
            outsideTempAvg = summary.outsideTempAvg,
            odometer = summary.odometer,
            latitude = summary.latitude,
            longitude = summary.longitude,
            chargePoints = chargePoints(this),
            isCharging = isCharging
        )
    }

    // ---------------------------------------------------------------- traces

    /**
     * The position trace behind the map and the drive charts.
     *
     * Speed comes first, from a trapezoid that ramps out of the start and brakes into the
     * end; distance is then the running integral of that speed, and the car is placed at
     * that distance along the polyline. Doing it in that order is what keeps the map, the
     * speed chart and the reported distance describing the same drive.
     */
    /**
     * The sampled trace behind a drive: where the car was, how fast, and what it was drawing.
     *
     * Kept as plain numbers so a drive summary can read its speed and power extremes without
     * building the position objects it would only throw away — the summary list is built for
     * every drive in the history at once, and that adds up.
     */
    private class DriveTrace(
        val stepSeconds: Double,
        val routeKm: Double,
        val alongKm: DoubleArray,
        val speeds: DoubleArray,
        val powers: IntArray,
        val points: List<Interpolated>
    ) {
        val size get() = speeds.size
    }

    private fun trace(record: DriveRecord): DriveTrace {
        val route = record.route
        val totalSeconds = record.durationMin * 60
        val steps = (totalSeconds / DRIVE_SAMPLE_SECONDS).coerceIn(8, 500)
        val stepSeconds = totalSeconds.toDouble() / steps
        val jitter = Random(record.id * 7919L)

        val cumulative = DoubleArray(route.waypoints.size)
        var running = 0.0
        route.waypoints.zipWithNext().forEachIndexed { index, (a, b) ->
            running += haversineKm(a, b)
            cumulative[index + 1] = running
        }
        val routeKm = running

        // Speed first: a trapezoid that pulls away from the start and brakes into the end,
        // dipped by a slowdown every 25 km or so. The dips are what make the drive look
        // driven rather than simulated — and they are where regen comes from, because
        // braking out of cruising speed gives back more than drag was taking.
        val slowdowns = List(max(1, (routeKm / 25).roundToInt())) {
            jitter.nextDouble(0.12, 0.88) to jitter.nextDouble(0.28, 0.6)
        }
        val slowdownWidth = SLOWDOWN_SECONDS / totalSeconds
        val rampWidth = min(0.25, PULL_AWAY_SECONDS / totalSeconds)
        val rawSpeeds = DoubleArray(steps + 1) { step ->
            val f = step.toDouble() / steps
            val ramp = min(1.0, min(f / rampWidth, (1.0 - f) / rampWidth))
            // Every journey starts and ends in a town. Holding the first and last stretch
            // down to town speed is what pulls the average below the open-road speed — and
            // the joins at each end are the hardest acceleration in the drive.
            val edge = min(f, 1.0 - f)
            val urban = if (edge < URBAN_TAIL_FRACTION) {
                val ramped = min(1.0, (URBAN_TAIL_FRACTION - edge) / URBAN_TAIL_JOIN)
                1.0 - (1.0 - TOWN_SPEED_FRACTION) * ramped
            } else {
                1.0
            }
            val traffic = slowdowns.fold(1.0) { acc, (centre, depth) ->
                val d = (f - centre) / slowdownWidth
                acc * (1.0 - (1.0 - depth) * exp(-d * d))
            }
            val wobble = 1.0 + sin(f * PI * 6) * 0.04 + jitter.nextDouble(-0.03, 0.03)
            max(0.0, route.cruiseKmh * ramp * urban * traffic * wobble)
        }

        // Scale the profile so its integral is exactly the route distance: the map line, the
        // speed chart and the distance on the drive card then all describe the same drive.
        val rawKm = rawSpeeds.sum() * stepSeconds / 3600.0
        val speedScale = if (rawKm > 0) routeKm / rawKm else 1.0
        val speeds = DoubleArray(steps + 1) { rawSpeeds[it] * speedScale }

        var travelled = 0.0
        val alongKm = DoubleArray(steps + 1) { step ->
            if (step > 0) travelled += speeds[step] * stepSeconds / 3600.0
            min(travelled, routeKm)
        }
        val points = alongKm.map { pointAlong(route, cumulative, it) }

        // Demand at each sample: rolling resistance and aero drag at that speed, plus what
        // the hill and the accelerator are asking for, plus the car's own hotel load.
        val resistanceKw = DoubleArray(steps + 1) { step ->
            val v = speeds[step] / 3.6
            (ROLLING_RESISTANCE_N * v + AERO_DRAG_N_S2_M2 * v * v * v) / 1000.0 + AUX_LOAD_KW
        }
        val dynamicKw = DoubleArray(steps + 1) { step ->
            if (step == 0) return@DoubleArray 0.0
            val climb = (points[step].elevation - points[step - 1].elevation) / stepSeconds *
                KERB_WEIGHT_KG * GRAVITY / 1000.0
            // Power to accelerate is mass × acceleration × speed, and the speed that belongs
            // in it is the average over the step. Using the speed at the *end* of the step
            // reads zero at a standstill, which silently erased the regen from every stop.
            val accelMs2 = (speeds[step] - speeds[step - 1]) / 3.6 / stepSeconds
            val meanMs = (speeds[step] + speeds[step - 1]) / 2 / 3.6
            climb + accelMs2 * meanMs * KERB_WEIGHT_KG / 1000.0
        }

        // Trim the resistance side until the trace integrates to the energy the summary
        // reports, so the power chart and the consumption figure cannot disagree.
        val hours = stepSeconds / 3600.0
        val resistanceKwh = resistanceKw.sum() * hours
        val dynamicKwh = dynamicKw.sum() * hours
        val energyScale = if (resistanceKwh > 0) {
            ((record.energyKwh - dynamicKwh) / resistanceKwh).coerceIn(0.6, 1.6)
        } else {
            1.0
        }

        val powers = IntArray(steps + 1) { step ->
            (resistanceKw[step] * energyScale + dynamicKw[step]).roundToInt().coerceIn(-75, 275)
        }

        return DriveTrace(stepSeconds, routeKm, alongKm, speeds, powers, points)
    }

    private fun positions(record: DriveRecord): List<DrivePosition> {
        val trace = trace(record)
        return (0 until trace.size).map { step ->
            val point = trace.points[step]
            val fraction = if (trace.routeKm > 0) trace.alongKm[step] / trace.routeKm else 0.0

            DrivePosition(
                date = record.start.plusSeconds((step * trace.stepSeconds).toLong()).format(),
                latitude = point.lat.round(6),
                longitude = point.lon.round(6),
                speed = trace.speeds[step].roundToInt(),
                power = trace.powers[step],
                batteryLevel = (record.startSoc - (record.startSoc - record.endSoc) * fraction)
                    .roundToInt(),
                elevation = point.elevation.roundToInt(),
                climateInfo = DriveClimateInfo(
                    insideTemp = record.insideTemp.round(1),
                    outsideTemp = (record.outsideTemp + sin(fraction * PI) * 1.2).round(1),
                    isClimateOn = abs(record.outsideTemp - 21) > 5,
                    fanStatus = 3,
                    driverTempSetting = 21.0,
                    passengerTempSetting = 21.0
                ),
                batteryInfo = DriveBatteryInfo(
                    batteryHeater = record.outsideTemp < 6,
                    batteryHeaterOn = record.outsideTemp < 6,
                    batteryHeaterNoPower = false
                )
            )
        }
    }

    private data class Interpolated(val lat: Double, val lon: Double, val elevation: Double)

    private fun pointAlong(
        route: DemoRoute,
        cumulative: DoubleArray,
        alongKm: Double
    ): Interpolated {
        val last = route.waypoints.lastIndex
        if (alongKm <= 0) {
            val p = route.waypoints.first()
            return Interpolated(p.lat, p.lon, p.elevationM.toDouble())
        }
        for (i in 0 until last) {
            if (alongKm <= cumulative[i + 1] || i == last - 1) {
                val segment = cumulative[i + 1] - cumulative[i]
                val t = if (segment > 0) ((alongKm - cumulative[i]) / segment).coerceIn(0.0, 1.0) else 1.0
                val a = route.waypoints[i]
                val b = route.waypoints[i + 1]
                return Interpolated(
                    lat = a.lat + (b.lat - a.lat) * t,
                    lon = a.lon + (b.lon - a.lon) * t,
                    elevation = a.elevationM + (b.elevationM - a.elevationM) * t
                )
            }
        }
        val p = route.waypoints.last()
        return Interpolated(p.lat, p.lon, p.elevationM.toDouble())
    }

    /** The power/voltage/temperature trace behind the charge detail charts. */
    private fun chargePoints(record: ChargeRecord): List<ChargePoint> {
        val totalSeconds = record.durationMin * 60
        val steps = (totalSeconds / CHARGE_SAMPLE_SECONDS).coerceIn(12, 240)
        val jitter = Random(record.id * 6151L)
        var energyAdded = 0.0
        var previousSoc = record.startSoc.toDouble()

        return (0..steps).map { step ->
            val f = step.toDouble() / steps
            val soc = record.startSoc + (record.endSoc - record.startSoc) * f
            energyAdded += (soc - previousSoc) / 100.0 * CAPACITY_NOW_KWH
            previousSoc = soc

            val power = powerAtSoc(record.site, soc) * (1.0 + jitter.nextDouble(-0.03, 0.03))
            val voltage = record.site.voltage + jitter.nextInt(-4, 5)
            val current = if (record.site.isDc) {
                (power * 1000 / voltage).roundToInt()
            } else {
                // Three-phase AC: the reported current is per phase.
                (power * 1000 / (voltage * 3)).roundToInt()
            }

            ChargePoint(
                date = record.start.plusSeconds((f * totalSeconds).toLong()).format(),
                batteryLevel = soc.roundToInt(),
                chargeEnergyAdded = energyAdded.round(2),
                chargerDetails = ChargerDetails(
                    chargerPower = power.roundToInt(),
                    chargerVoltage = voltage,
                    chargerActualCurrent = current,
                    chargerPhases = record.site.phases,
                    fastChargerPresent = record.site.isDc,
                    fastChargerBrand = if (record.site.isDc) "Tesla" else "<invalid>",
                    fastChargerType = if (record.site.isDc) "Combo" else "<invalid>"
                ),
                outsideTemp = (record.outsideTemp + sin(f * PI) * 0.9).round(1),
                batteryInfo = ChargeBatteryInfo(
                    idealBatteryRangeKm = socToRangeKm(soc).round(2),
                    ratedBatteryRangeKm = socToRangeKm(soc).round(2),
                    usableBatteryLevel = soc.roundToInt()
                )
            )
        }
    }

    // ---------------------------------------------------------------- physics-ish helpers

    /** DC power tapers hard with state of charge; AC holds flat until the last few percent. */
    private fun powerAtSoc(site: DemoChargerSite, soc: Double): Double {
        if (!site.isDc) {
            val taper = if (soc > CHARGE_LIMIT_SOC - 3) {
                max(0.18, 1.0 - (soc - (CHARGE_LIMIT_SOC - 3)) / 4.0)
            } else {
                1.0
            }
            return site.peakPowerKw * taper
        }
        val curve = 1.0 - ((soc - 8).coerceAtLeast(0.0) / 72.0).pow(1.7)
        return site.peakPowerKw * curve.coerceIn(0.14, 1.0)
    }

    private fun durationMinutes(site: DemoChargerSite, fromSoc: Int, toSoc: Int): Int {
        if (toSoc <= fromSoc) return 1
        var minutes = 0.0
        for (soc in fromSoc until toSoc) {
            minutes += (CAPACITY_NOW_KWH / 100.0) / powerAtSoc(site, soc + 0.5) * 60
        }
        return minutes.roundToInt().coerceAtLeast(1)
    }

    private fun chargingEfficiency(site: DemoChargerSite): Double = if (site.isDc) 0.965 else 0.905

    private fun socToRangeKm(soc: Number): Double = soc.toDouble() / 100.0 * RANGE_NOW_KM

    /** Girona's year: about 8 °C in mid-January, about 24 °C in mid-July, warmer after lunch. */
    private fun outsideTempAt(instant: Instant): Double {
        val local = OffsetDateTime.ofInstant(instant, zone)
        val seasonal = 16.0 - 8.0 * cos(2 * PI * (local.dayOfYear - 15) / 365.0)
        val daily = 4.0 * sin(2 * PI * (local.hour - 9) / 24.0)
        val noise = Random(local.toLocalDate().toEpochDay() * 31 + local.hour).nextDouble(-1.6, 1.6)
        return seasonal + daily + noise
    }

    /** Cold costs range: consumption climbs once it drops below shirtsleeve weather. */
    private fun seasonalFactor(outsideTemp: Double): Double =
        1.0 + max(0.0, 16.0 - outsideTemp) * 0.018

    private fun buildUpdates(): List<UpdateData> {
        val installs = mutableListOf<Pair<Instant, UpdateData>>()
        val today = anchor.atZone(zone).toLocalDate()
        var day = today.minusDays(HISTORY_DAYS - 12)
        var id = 1
        while (day.isBefore(today.minusDays(3))) {
            val start = day.atTime(2, 14).atZone(zone).toInstant()
                .plusSeconds(random.nextLong(0, 5400))
            val minutes = random.nextLong(18, 47)
            installs += start to UpdateData(
                id = id++,
                version = versionFor(day),
                startDate = start.format(),
                endDate = start.plus(minutes, ChronoUnit.MINUTES).format()
            )
            day = day.plusDays(random.nextLong(26, 52))
        }
        return installs.sortedByDescending { it.first }.map { it.second }
    }

    /**
     * Tesla-style `YYYY.WW.P` derived from the install date, so the demo never advertises a
     * version from a year that has already gone by.
     */
    private fun versionFor(day: LocalDate): String {
        val week = ((day.dayOfYear - 1) / 7 + 1).coerceIn(1, 52)
        val patch = Random(day.toEpochDay()).nextInt(1, 9)
        return "${day.year}.$week.$patch"
    }

    // ---------------------------------------------------------------- formatting

    private fun Instant.format(): String =
        OffsetDateTime.ofInstant(this, zone).truncatedTo(ChronoUnit.SECONDS).format(TIMESTAMP)

    private fun Int.toDurationString(): String = "%02d:%02d".format(this / 60, this % 60)

    private fun Double.round(decimals: Int): Double {
        val factor = 10.0.pow(decimals)
        return (this * factor).roundToInt() / factor
    }
}
