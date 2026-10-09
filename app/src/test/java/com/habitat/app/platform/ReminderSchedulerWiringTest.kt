package com.habitat.app.platform

import android.content.Context
import com.habitat.app.data.FakeCompletionDao
import com.habitat.app.data.repository.CompletionRepositoryImpl
import com.habitat.app.domain.AppClock
import com.habitat.app.domain.model.AppSettings
import com.habitat.app.domain.model.Habit
import com.habitat.app.domain.model.ScheduleType
import com.habitat.app.domain.repository.HabitRepository
import com.habitat.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

class ReminderSchedulerWiringTest {

    private val zoneUtc = ZoneId.of("UTC")
    private val clock = object : AppClock {
        override fun instant() = Instant.parse("2025-05-10T20:00:00Z") // 20:00 UTC
        override fun zoneId() = zoneUtc
    }

    @Test
    fun `reconcile fetches settings from repository and moves alarms when quiet hours toggled`() = runTest {
        val fakeSettingsRepo = FakeSettingsRepository()
        val fakeHabitRepo = FakeHabitRepository()
        val fakeGateway = FakeAlarmGateway()

        ReminderScheduler.alarmGateway = fakeGateway

        val habit = Habit(
            id = "h1",
            name = "Late Habit",
            schedule = ScheduleType.Daily,
            reminderTime = LocalTime.of(22, 30)
        )
        fakeHabitRepo.saveHabit(habit)

        val completionRepo = CompletionRepositoryImpl(FakeCompletionDao())

        // 1. Initial reconcile with quietHoursEnabled = true (default)
        ReminderScheduler.reconcile(
            context = DummyContext(),
            habitRepository = fakeHabitRepo,
            completionRepository = completionRepo,
            settingsRepository = fakeSettingsRepo,
            clock = clock
        )

        // Deferred to 07:00 next day (May 11 07:00 UTC = 1746946800000)
        val scheduled1 = fakeGateway.scheduledAlarms["h1:alarm"]
        assertNotNull(scheduled1)
        assertEquals(Instant.parse("2025-05-11T07:00:00Z").toEpochMilli(), scheduled1)

        // 2. Disable quiet hours in settings and reconcile again
        val currentSettings = fakeSettingsRepo.getSettingsValue()
        fakeSettingsRepo.updateSettings(currentSettings.copy(quietHoursEnabled = false))

        ReminderScheduler.reconcile(
            context = DummyContext(),
            habitRepository = fakeHabitRepo,
            completionRepository = completionRepo,
            settingsRepository = fakeSettingsRepo,
            clock = clock
        )

        // Re-armed at 22:30 same day (May 10 22:30 UTC = 1746916200000)
        val scheduled2 = fakeGateway.scheduledAlarms["h1:alarm"]
        assertNotNull(scheduled2)
        assertEquals(Instant.parse("2025-05-10T22:30:00Z").toEpochMilli(), scheduled2)
    }
}

class FakeAlarmGateway : AlarmGateway {
    val scheduledAlarms = mutableMapOf<String, Long>()

    override fun scheduleAlarm(context: Context, habitId: String, actionPath: String, triggerAtMillis: Long, logicalDateExtra: String?) {
        scheduledAlarms["$habitId:$actionPath"] = triggerAtMillis
    }

    override fun cancelAlarm(context: Context, habitId: String, actionPath: String) {
        scheduledAlarms.remove("$habitId:$actionPath")
    }

    override fun isAlarmScheduled(context: Context, habitId: String, actionPath: String): Boolean {
        return scheduledAlarms.containsKey("$habitId:$actionPath")
    }
}

class FakeSettingsRepository : SettingsRepository {
    private val state = MutableStateFlow(AppSettings())
    private val postedMap = mutableMapOf<String, MutableSet<String>>()

    fun getSettingsValue(): AppSettings = state.value

    override fun getSettings(): Flow<AppSettings> = state

    override suspend fun updateSettings(settings: AppSettings) {
        state.value = settings
    }

    override suspend fun getPostedHabitIds(logicalDateString: String): Set<String> {
        return postedMap[logicalDateString]?.toSet() ?: emptySet()
    }

    override suspend fun addPostedHabitId(logicalDateString: String, habitId: String) {
        postedMap.getOrPut(logicalDateString) { mutableSetOf() }.add(habitId)
    }

    override suspend fun pruneOldPostedHabitIds(cutoffDateString: String) {
        postedMap.keys.retainAll { it >= cutoffDateString }
    }
}

class FakeHabitRepository : HabitRepository {
    private val map = mutableMapOf<String, Habit>()

    override fun getActiveHabits(): Flow<List<Habit>> = MutableStateFlow(map.values.toList())

    override fun getAllHabits(): Flow<List<Habit>> = MutableStateFlow(map.values.toList())

    override suspend fun getHabitById(id: String): Habit? = map[id]

    override suspend fun saveHabit(habit: Habit) {
        map[habit.id] = habit
    }

    override suspend fun archiveHabit(id: String) {
        map[id]?.let { map[id] = it.copy(archived = true) }
    }

    override suspend fun deleteHabit(id: String) {
        map.remove(id)
    }
}

class DummyContext : android.content.ContextWrapper(null) {
    override fun getSystemService(name: String): Any? = null
}
