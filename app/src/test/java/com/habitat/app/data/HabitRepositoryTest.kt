package com.habitat.app.data

import app.cash.turbine.test
import com.habitat.app.data.local.HabitDao
import com.habitat.app.data.local.HabitEntity
import com.habitat.app.data.repository.HabitRepositoryImpl
import com.habitat.app.domain.model.Difficulty
import com.habitat.app.domain.model.Habit
import com.habitat.app.domain.model.ScheduleType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek

class HabitRepositoryTest {

    private lateinit var fakeDao: FakeHabitDao
    private lateinit var repository: HabitRepositoryImpl

    @Before
    fun setUp() {
        fakeDao = FakeHabitDao()
        repository = HabitRepositoryImpl(fakeDao)
    }

    @Test
    fun `save habit and observe active habits`() = runTest {
        val habit = Habit(
            name = "Morning Run",
            emoji = "🏃",
            cue = "After coffee",
            minVersion = "Put shoes on",
            fullVersion = "Run 30 mins",
            schedule = ScheduleType.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)),
            difficulty = Difficulty.MEDIUM
        )

        repository.getActiveHabits().test {
            assertEquals(emptyList<Habit>(), awaitItem())

            repository.saveHabit(habit)

            val active = awaitItem()
            assertEquals(1, active.size)
            assertEquals("Morning Run", active[0].name)
            assertEquals(Difficulty.MEDIUM, active[0].difficulty)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `archive habit soft deletes it from active list`() = runTest {
        val habit = Habit(name = "Read Books")
        repository.saveHabit(habit)

        val retrieved = repository.getHabitById(habit.id)
        assertNotNull(retrieved)

        repository.archiveHabit(habit.id)

        repository.getActiveHabits().test {
            val active = awaitItem()
            assertEquals(0, active.size)
            cancelAndIgnoreRemainingEvents()
        }

        repository.getAllHabits().test {
            val all = awaitItem()
            assertEquals(1, all.size)
            assertEquals(true, all[0].archived)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

class FakeHabitDao : HabitDao {
    private val habitsState = MutableStateFlow<Map<String, HabitEntity>>(emptyMap())

    override fun getActiveHabits(): Flow<List<HabitEntity>> {
        return habitsState.map { map ->
            map.values.filter { !it.archived }.sortedBy { it.sortOrder }
        }
    }

    override fun getAllHabits(): Flow<List<HabitEntity>> {
        return habitsState.map { map ->
            map.values.sortedBy { it.sortOrder }
        }
    }

    override suspend fun getHabitById(id: String): HabitEntity? {
        return habitsState.value[id]
    }

    override suspend fun insertHabit(habit: HabitEntity): Long {
        habitsState.value = habitsState.value + (habit.id to habit)
        return 1L
    }

    override suspend fun updateHabit(habit: HabitEntity): Int {
        habitsState.value = habitsState.value + (habit.id to habit)
        return 1
    }

    override suspend fun archiveHabit(id: String): Int {
        val existing = habitsState.value[id] ?: return 0
        habitsState.value = habitsState.value + (id to existing.copy(archived = true))
        return 1
    }

    override suspend fun deleteHabit(id: String): Int {
        habitsState.value = habitsState.value - id
        return 1
    }
}
