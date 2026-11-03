package com.matedroid.data.sync

import android.content.Context
import com.matedroid.data.api.models.ChargeDetail
import com.matedroid.data.local.dao.AggregateDao
import com.matedroid.data.local.dao.ChargeSummaryDao
import com.matedroid.data.local.dao.DriveSummaryDao
import com.matedroid.data.local.entity.ChargeDetailAggregate
import com.matedroid.data.local.entity.ChargeSummary
import com.matedroid.data.local.entity.DriveDetailAggregate
import com.matedroid.data.local.entity.SyncState
import com.matedroid.data.repository.ApiResult
import com.matedroid.data.repository.GeocodingRepository
import com.matedroid.data.repository.TeslamateRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Detail sync must not get stuck on a single drive: a drive the server has no positions for
 * used to fail on every sync, and since charge details only ran after all drives succeeded,
 * no charge detail (DC/AC type, trip detection) was ever synced again.
 */
class SyncRepositoryTest {

    private lateinit var api: TeslamateRepository
    private lateinit var driveSummaryDao: DriveSummaryDao
    private lateinit var chargeSummaryDao: ChargeSummaryDao
    private lateinit var aggregateDao: AggregateDao
    private lateinit var syncManager: SyncManager
    private lateinit var repository: SyncRepository

    private val noRows = ApiResult.Error("No drive detail returned", noData = true)

    @Before
    fun setup() {
        api = mockk()
        driveSummaryDao = mockk(relaxed = true)
        chargeSummaryDao = mockk(relaxed = true)
        aggregateDao = mockk(relaxed = true)
        syncManager = mockk(relaxed = true)
        repository = SyncRepository(
            context = mockk<Context>(relaxed = true),
            teslamateRepository = api,
            driveSummaryDao = driveSummaryDao,
            chargeSummaryDao = chargeSummaryDao,
            aggregateDao = aggregateDao,
            syncManager = syncManager,
            logCollector = mockk(relaxed = true),
            geocodingRepository = mockk<GeocodingRepository>(relaxed = true)
        )
        coEvery { syncManager.getOrCreateSyncState(1) } returns SyncState(carId = 1)
        coEvery { syncManager.areDetailsSynced(1) } returns false
        coEvery { api.getDrives(1, any(), any(), any(), any()) } returns ApiResult.Success(emptyList())
        coEvery { api.getCharges(1, any(), any(), any(), any()) } returns ApiResult.Success(emptyList())
        coEvery { chargeSummaryDao.getAllForCar(1) } returns emptyList()
    }

    private fun chargeSummary(id: Int, energyKwh: Double, durationMin: Int) = ChargeSummary(
        chargeId = id, carId = 1,
        startDate = "2026-10-11T12:45:10+02:00", endDate = "2026-10-11T13:06:10+02:00",
        durationMin = durationMin, address = "", latitude = 0.0, longitude = 0.0,
        energyAdded = energyKwh, energyUsed = null, cost = null,
        startBatteryLevel = 20, endBatteryLevel = 80, outsideTempAvg = null, odometer = 0.0
    )

    @Test
    fun `a drive without positions is stored empty instead of failing`() = runTest {
        coEvery { driveSummaryDao.getUnprocessedDriveIds(1, any()) } returns listOf(5)
        coEvery { api.getDriveDetail(1, 5) } returns noRows
        val stored = slot<List<DriveDetailAggregate>>()
        coEvery { aggregateDao.upsertDriveAggregates(capture(stored)) } returns Unit

        assertEquals(0, repository.syncDriveDetails(1))
        assertEquals(listOf(5), stored.captured.map { it.driveId })
        assertEquals(0, stored.captured.single().positionCount)
    }

    @Test
    fun `charge details still sync when a drive keeps failing`() = runTest {
        coEvery { driveSummaryDao.getUnprocessedDriveIds(1, any()) } returns listOf(6)
        coEvery { api.getDriveDetail(1, 6) } returns ApiResult.Error("timeout")
        coEvery { chargeSummaryDao.getUnprocessedChargeIds(1, any()) } returns listOf(100)
        coEvery { api.getChargeDetail(1, 100) } returns ApiResult.Success(ChargeDetail(chargeId = 100))

        assertFalse(repository.syncCar(1))

        coVerify { aggregateDao.upsertChargeAggregates(match { list -> list.any { it.chargeId == 100 } }) }
        verify { syncManager.markSyncError(1, "1 drive details failed — will retry") }
    }

    @Test
    fun `a charge without detail points gets its type from average power`() = runTest {
        coEvery { chargeSummaryDao.getUnprocessedChargeIds(1, any()) } returns listOf(200, 201)
        coEvery { chargeSummaryDao.getAllForCar(1) } returns listOf(
            chargeSummary(200, energyKwh = 35.0, durationMin = 21),   // ~100 kW: DC
            chargeSummary(201, energyKwh = 14.0, durationMin = 427)   // ~2 kW: AC
        )
        coEvery { api.getChargeDetail(1, any()) } returns noRows
        val stored = slot<List<ChargeDetailAggregate>>()
        coEvery { aggregateDao.upsertChargeAggregates(capture(stored)) } returns Unit

        assertEquals(0, repository.syncChargeDetails(1))
        val byId = stored.captured.associateBy { it.chargeId }
        assertTrue(byId.getValue(200).isFastCharger)
        assertFalse(byId.getValue(201).isFastCharger)
    }
}
