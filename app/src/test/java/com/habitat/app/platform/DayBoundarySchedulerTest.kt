package com.habitat.app.platform

import android.content.Context
import android.content.Intent
import com.habitat.app.data.FakeCompletionDao
import com.habitat.app.data.repository.CompletionRepositoryImpl
import com.habitat.app.domain.AppClock
import com.habitat.app.domain.WidgetUpdater
import com.habitat.app.domain.model.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Tests for [DayBoundaryScheduler] and [DayBoundaryReceiver].
 * Verifies arm on start, re-arm on fire, and re-arm on hour change with [FakeAlarmGateway].
 * SPEC Section 4.3 & 8.7.
 */
class DayBoundarySchedulerTest {

    private val zoneBerlin = ZoneId.of("Europe/Berlin")

    private val clock = object : AppClock {
        // Monday May 12, 2025 at 02:00 Berlin time
        override fun instant(): Instant = ZonedDateTime.parse("2025-05-12T02:00:00+02:00").toInstant()
        override fun zoneId(): ZoneId = zoneBerlin
        override fun logicalDate(dayBoundaryHour: Int): LocalDate = LocalDate.of(2025, 5, 11)
    }

    private lateinit var fakeAlarmGateway: FakeAlarmGateway
    private lateinit var fakeSettingsRepo: FakeSettingsRepository

    @Before
    fun setUp() {
        fakeAlarmGateway = FakeAlarmGateway()
        DayBoundaryScheduler.alarmGateway = fakeAlarmGateway
        fakeSettingsRepo = FakeSettingsRepository()
    }

    @Test
    fun `arm on start schedules next boundary based on repository settings`() = runTest {
        // App starts at 02:00 with default hour 4 -> next boundary is 04:00 today (May 12)
        DayBoundaryScheduler.scheduleNext(DummyContext(), fakeSettingsRepo, clock)

        assertTrue(fakeAlarmGateway.isDayBoundaryAlarmScheduled(DummyContext()))
        val scheduledMillis = fakeAlarmGateway.scheduledDayBoundaryMillis
        assertNotNull(scheduledMillis)

        val expectedInstant = ZonedDateTime.parse("2025-05-12T04:00:00+02:00").toInstant()
        assertEquals(expectedInstant.toEpochMilli(), scheduledMillis)
    }

    @Test
    fun `re-arm on fire schedules next boundary`() = runTest {
        // 1. Initial arm
        DayBoundaryScheduler.scheduleNext(DummyContext(), fakeSettingsRepo, clock)
        assertEquals(
            ZonedDateTime.parse("2025-05-12T04:00:00+02:00").toInstant().toEpochMilli(),
            fakeAlarmGateway.scheduledDayBoundaryMillis
        )

        // 2. Alarm fires at 04:00. Advance clock to 04:00 (exact boundary)
        val fireClock = object : AppClock {
            override fun instant(): Instant = ZonedDateTime.parse("2025-05-12T04:00:00+02:00").toInstant()
            override fun zoneId(): ZoneId = zoneBerlin
        }

        var widgetRefreshAttempts = 0
        val receiver = DayBoundaryReceiver().apply {
            this.settingsRepository = fakeSettingsRepo
            this.clock = fireClock
            this.widgetUpdater = WidgetUpdater {
                widgetRefreshAttempts++
                throw IllegalStateException("row 'day boundary' widget update failed")
            }
        }

        receiver.executeBoundaryTransition(DummyContext())

        // Re-armed for tomorrow at 04:00 (May 13)
        val expectedTomorrow = ZonedDateTime.parse("2025-05-13T04:00:00+02:00").toInstant()
        assertEquals(expectedTomorrow.toEpochMilli(), fakeAlarmGateway.scheduledDayBoundaryMillis)
        assertEquals("row 'day boundary' updater attempted", 1, widgetRefreshAttempts)
    }

    @Test
    fun `re-arm when dayBoundaryHour changes updates scheduled alarm instant`() = runTest {
        // 1. Start with hour 4
        DayBoundaryScheduler.scheduleNext(DummyContext(), fakeSettingsRepo, clock)
        val initialMillis = fakeAlarmGateway.scheduledDayBoundaryMillis
        val expectedInitial = ZonedDateTime.parse("2025-05-12T04:00:00+02:00").toInstant()
        assertEquals(expectedInitial.toEpochMilli(), initialMillis)

        // 2. Change dayBoundaryHour to 6
        fakeSettingsRepo.updateSettings(AppSettings(dayBoundaryHour = 6))

        // 3. Re-arm
        DayBoundaryScheduler.scheduleNext(DummyContext(), fakeSettingsRepo, clock)
        val updatedMillis = fakeAlarmGateway.scheduledDayBoundaryMillis
        val expectedUpdated = ZonedDateTime.parse("2025-05-12T06:00:00+02:00").toInstant()
        assertEquals(expectedUpdated.toEpochMilli(), updatedMillis)
    }

    @Test
    fun `BootReceiver re-arms day boundary alarm on boot or time change`() = runTest {
        val habitRepo = FakeHabitRepository()
        val completionRepo = CompletionRepositoryImpl(FakeCompletionDao())

        var widgetRefreshAttempts = 0
        val bootReceiver = BootReceiver().apply {
            this.habitRepository = habitRepo
            this.completionRepository = completionRepo
            this.settingsRepository = fakeSettingsRepo
            this.clock = this@DayBoundarySchedulerTest.clock
            this.widgetUpdater = WidgetUpdater {
                widgetRefreshAttempts++
                throw IllegalStateException("row 'boot receiver' widget update failed")
            }
        }

        // Reconcile on boot
        bootReceiver.reconcileAll(DummyContext())

        assertTrue(fakeAlarmGateway.isDayBoundaryAlarmScheduled(DummyContext()))
        val expectedMillis = ZonedDateTime.parse("2025-05-12T04:00:00+02:00").toInstant().toEpochMilli()
        assertEquals(expectedMillis, fakeAlarmGateway.scheduledDayBoundaryMillis)
        assertEquals("row 'boot receiver' updater attempted", 1, widgetRefreshAttempts)
    }

    @Test
    fun `BootReceiver handles TIMEZONE_CHANGED for day-boundary alarm`() = runTest {
        val habitRepo = FakeHabitRepository()
        val completionRepo = CompletionRepositoryImpl(FakeCompletionDao())

        // Initial state: alarm cleared
        fakeAlarmGateway.cancelDayBoundaryAlarm(DummyContext())

        // Device changes timezone to America/New_York (UTC-4 in May) at 02:00 EDT
        val nyZone = ZoneId.of("America/New_York")
        val nyClock = object : AppClock {
            override fun instant(): Instant = ZonedDateTime.parse("2025-05-12T02:00:00-04:00").toInstant()
            override fun zoneId(): ZoneId = nyZone
        }

        val bootReceiver = BootReceiver().apply {
            this.habitRepository = habitRepo
            this.completionRepository = completionRepo
            this.settingsRepository = fakeSettingsRepo
            this.clock = nyClock
            this.widgetUpdater = WidgetUpdater { }
        }

        bootReceiver.handleAction(DummyContext(), Intent.ACTION_TIMEZONE_CHANGED)

        // Boundary alarm re-armed for 04:00 EDT in America/New_York
        assertTrue(fakeAlarmGateway.isDayBoundaryAlarmScheduled(DummyContext()))
        val expectedMillis = ZonedDateTime.parse("2025-05-12T04:00:00-04:00").toInstant().toEpochMilli()
        assertEquals(expectedMillis, fakeAlarmGateway.scheduledDayBoundaryMillis)
    }

    @Test
    fun `day-boundary alarm is re-armed when reminder reconciliation throws`() = runTest {
        val bootReceiver = BootReceiver()
        val throwingReminderReconciler: suspend () -> Unit = {
            throw IllegalStateException("Reminder scheduling failed")
        }

        bootReceiver.reconcileAll(
            reminderReconcile = throwingReminderReconciler,
            dayBoundaryRearm = {
                DayBoundaryScheduler.scheduleNext(DummyContext(), fakeSettingsRepo, clock)
            }
        )

        assertTrue(fakeAlarmGateway.isDayBoundaryAlarmScheduled(DummyContext()))
        val expectedMillis = ZonedDateTime.parse("2025-05-12T04:00:00+02:00").toInstant().toEpochMilli()
        assertEquals(expectedMillis, fakeAlarmGateway.scheduledDayBoundaryMillis)
    }

    @Test
    fun `BootReceiver ignores unrelated action`() = runTest {
        val habitRepo = FakeHabitRepository()
        val completionRepo = CompletionRepositoryImpl(FakeCompletionDao())
        fakeAlarmGateway.cancelDayBoundaryAlarm(DummyContext())

        val bootReceiver = BootReceiver().apply {
            this.habitRepository = habitRepo
            this.completionRepository = completionRepo
            this.settingsRepository = fakeSettingsRepo
            this.clock = this@DayBoundarySchedulerTest.clock
            this.widgetUpdater = WidgetUpdater { }
        }

        bootReceiver.handleAction(DummyContext(), "android.intent.action.AIRPLANE_MODE")

        // Not scheduled because action was ignored
        assertEquals(false, fakeAlarmGateway.isDayBoundaryAlarmScheduled(DummyContext()))
    }

    @Test
    fun `cancel day boundary alarm removes pending trigger`() {
        fakeAlarmGateway.scheduleDayBoundaryAlarm(DummyContext(), 12345L)
        assertTrue(DayBoundaryScheduler.isScheduled(DummyContext()))

        DayBoundaryScheduler.cancel(DummyContext())
        assertEquals(false, DayBoundaryScheduler.isScheduled(DummyContext()))
    }
}

