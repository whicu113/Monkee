package com.habitat.app.domain.logic

import com.habitat.app.domain.AppClock
import com.habitat.app.domain.model.AppSettings
import com.habitat.app.domain.model.CompletionState
import com.habitat.app.domain.model.Habit
import com.habitat.app.domain.model.LoggedCompletion
import com.habitat.app.domain.model.ScheduleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Table-driven tests for [WidgetStateBuilder].
 * Covers all state transitions, clock/boundary edge cases, row ordering, and quota rules.
 */
class WidgetStateBuilderTest {

    // ── Fixtures & Clock Helpers ──────────────────────────────────────────────

    /** Monday 12 May 2025. */
    private val today = LocalDate.of(2025, 5, 12)

    private val settingsMon = AppSettings(startOfWeekDay = DayOfWeek.MONDAY)
    private val settingsSun = AppSettings(startOfWeekDay = DayOfWeek.SUNDAY)

    private fun fixedClock(date: LocalDate, hour: Int = 12): AppClock {
        val zdt = date.atTime(hour, 0).atZone(ZoneId.of("Europe/Berlin"))
        return object : AppClock {
            override fun instant(): Instant = zdt.toInstant()
            override fun zoneId(): ZoneId = zdt.zone
        }
    }

    private fun timeClock(dateTimeStr: String): AppClock {
        val zdt = ZonedDateTime.parse(dateTimeStr)
        return object : AppClock {
            override fun instant(): Instant = zdt.toInstant()
            override fun zoneId(): ZoneId = zdt.zone
        }
    }

    private fun habit(
        id: String,
        schedule: ScheduleType = ScheduleType.Daily,
        archived: Boolean = false,
        groupId: String? = null,
        sortOrder: Int = 0
    ) = Habit(
        id = id,
        name = "Habit $id",
        emoji = "🌱",
        schedule = schedule,
        archived = archived,
        groupId = groupId,
        sortOrder = sortOrder
    )

    private fun comp(habitId: String, date: LocalDate, state: CompletionState) =
        LoggedCompletion(habitId = habitId, logicalDate = date, state = state)

    private fun build(
        habits: List<Habit>,
        completions: List<LoggedCompletion>,
        settings: AppSettings = settingsMon,
        clock: AppClock = fixedClock(today)
    ) = WidgetStateBuilder.build(clock, habits, completions, settings)

    // ── NoHabits ──────────────────────────────────────────────────────────────

    @Test
    fun `no habits at all yields NoHabits`() {
        val state = build(emptyList(), emptyList())
        assertEquals(WidgetDisplayState.NoHabits, state)
    }

    @Test
    fun `all habits archived yields NoHabits`() {
        val state = build(
            habits = listOf(habit("1", archived = true), habit("2", archived = true)),
            completions = emptyList()
        )
        assertEquals(WidgetDisplayState.NoHabits, state)
    }

    @Test
    fun `Unknown schedule excluded yields NoHabits`() {
        val state = build(
            habits = listOf(habit("1", ScheduleType.Unknown("?"))),
            completions = emptyList()
        )
        assertEquals(WidgetDisplayState.NoHabits, state)
    }

    @Test
    fun `mixed archived and unknown habits both excluded yields NoHabits`() {
        val archived = habit("1", archived = true)
        val unknown = habit("2", ScheduleType.Unknown("?"))
        val state = build(habits = listOf(archived, unknown), completions = emptyList())
        assertEquals(WidgetDisplayState.NoHabits, state)
    }

    // ── NothingDueToday ───────────────────────────────────────────────────────

    @Test
    fun `habit scheduled only on Friday when today is Monday yields NothingDueToday`() {
        val state = build(
            habits = listOf(habit("1", ScheduleType.Weekdays(setOf(DayOfWeek.FRIDAY)))),
            completions = emptyList()
        )
        assertEquals(WidgetDisplayState.NothingDueToday, state)
    }

    @Test
    fun `NPerWeek quota met means habit not due yields NothingDueToday`() {
        // n=1, one DONE this week -> quota met -> not due
        val h = habit("1", ScheduleType.NPerWeek(n = 1))
        val completions = listOf(comp("1", today, CompletionState.DONE))
        val state = build(habits = listOf(h), completions = completions)
        assertEquals(WidgetDisplayState.NothingDueToday, state)
    }

    @Test
    fun `multiple NPerWeek habits all at quota yields NothingDueToday`() {
        val wednesday = LocalDate.of(2025, 5, 14)
        val h1 = habit("1", ScheduleType.NPerWeek(n = 2))
        val h2 = habit("2", ScheduleType.NPerWeek(n = 1))
        val completions = listOf(
            comp("1", LocalDate.of(2025, 5, 12), CompletionState.DONE),
            comp("1", LocalDate.of(2025, 5, 13), CompletionState.DONE),
            comp("2", LocalDate.of(2025, 5, 12), CompletionState.MINIMUM)
        )
        val state = build(listOf(h1, h2), completions, clock = fixedClock(wednesday))
        assertEquals(WidgetDisplayState.NothingDueToday, state)
    }

    // ── AllSkipped ────────────────────────────────────────────────────────────

    @Test
    fun `all due habits skipped yields AllSkipped`() {
        val habits = listOf(habit("1"), habit("2"))
        val completions = listOf(
            comp("1", today, CompletionState.SKIPPED),
            comp("2", today, CompletionState.SKIPPED)
        )
        val state = build(habits, completions)
        assertEquals(WidgetDisplayState.AllSkipped, state)
    }

    // ── Habits (normal) ───────────────────────────────────────────────────────

    @Test
    fun `all completed yields full progress and allCompleted true`() {
        val habits = listOf(habit("1"), habit("2"))
        val completions = listOf(
            comp("1", today, CompletionState.DONE),
            comp("2", today, CompletionState.MINIMUM)
        )
        val state = build(habits, completions)
        assertTrue(state is WidgetDisplayState.Habits)
        val h = state as WidgetDisplayState.Habits
        assertEquals(2, h.completedCount)
        assertEquals(2, h.totalDueCount)
        assertEquals(1.0f, h.progressFraction, 0.001f)
        assertTrue(h.allCompleted)
        assertEquals(0, h.remainingCount)
        assertTrue(h.rows.all { it.isCompleted })
    }

    @Test
    fun `mixed one done one skipped one pending - skipped excluded from denominator`() {
        val habits = listOf(habit("1"), habit("2"), habit("3"))
        val completions = listOf(
            comp("1", today, CompletionState.DONE),
            comp("2", today, CompletionState.SKIPPED)
            // habit "3" pending
        )
        val state = build(habits, completions)
        assertTrue(state is WidgetDisplayState.Habits)
        val h = state as WidgetDisplayState.Habits
        assertEquals(2, h.totalDueCount)
        assertEquals(1, h.completedCount)
        assertEquals(0.5f, h.progressFraction, 0.001f)
        assertFalse(h.allCompleted)
        assertEquals(3, h.rows.size)
        assertTrue(h.rows.any { it.habitId == "2" && it.isSkipped })
    }

    @Test
    fun `due habits stay in rows when completed`() {
        val habits = listOf(habit("1"), habit("2"))
        val completions = listOf(comp("1", today, CompletionState.DONE))
        val state = build(habits, completions)
        assertTrue(state is WidgetDisplayState.Habits)
        val h = state as WidgetDisplayState.Habits
        assertEquals(2, h.rows.size)
        assertTrue(h.rows.any { it.habitId == "1" && it.isCompleted })
        assertTrue(h.rows.any { it.habitId == "2" && !it.isCompleted && !it.isSkipped })
    }

    @Test
    fun `10 habits caps rows at 5 with correct remainingCount`() {
        val habits = (1..10).map { habit(it.toString()) }
        val state = build(habits, emptyList())
        assertTrue(state is WidgetDisplayState.Habits)
        val h = state as WidgetDisplayState.Habits
        assertEquals(5, h.rows.size)
        assertEquals(5, h.remainingCount)
        assertEquals(10, h.totalDueCount)
        assertEquals(0, h.completedCount)
        assertEquals(0.0f, h.progressFraction, 0.001f)
    }

    @Test
    fun `12 habits caps rows at 5 with remainingCount 7`() {
        val habits = (1..12).map { habit(it.toString()) }
        val state = build(habits, emptyList())
        assertTrue(state is WidgetDisplayState.Habits)
        val h = state as WidgetDisplayState.Habits
        assertEquals(5, h.rows.size)
        assertEquals(7, h.remainingCount)
    }

    // ── Week-start and quota tests ────────────────────────────────────────────

    @Test
    fun `Sunday-start week includes Sunday completion for NPerWeek quota`() {
        // May 11 (Sunday) is inside the Sun-start week containing May 12 (Monday)
        val sunday = LocalDate.of(2025, 5, 11)
        val h = habit("1", ScheduleType.NPerWeek(n = 1))
        val completions = listOf(comp("1", sunday, CompletionState.DONE))
        val state = build(listOf(h), completions, settings = settingsSun)
        assertEquals(WidgetDisplayState.NothingDueToday, state)
    }

    @Test
    fun `Monday-start week excludes Sunday completion for NPerWeek quota`() {
        // May 11 (Sunday) is outside the Mon-start week starting May 12 -> quota not met
        val sunday = LocalDate.of(2025, 5, 11)
        val h = habit("1", ScheduleType.NPerWeek(n = 1))
        val completions = listOf(comp("1", sunday, CompletionState.DONE))
        val state = build(listOf(h), completions, settings = settingsMon)
        assertTrue(state is WidgetDisplayState.Habits)
    }

    @Test
    fun `SKIPPED completion does not count toward NPerWeek quota`() {
        // n=2, one DONE + one SKIPPED this week -> quota not met -> habit still due
        val h = habit("1", ScheduleType.NPerWeek(n = 2))
        val completions = listOf(
            comp("1", today, CompletionState.DONE),
            comp("1", today.minusDays(1), CompletionState.SKIPPED)
        )
        val state = build(listOf(h), completions)
        assertTrue(state is WidgetDisplayState.Habits)
    }

    // ── Clock & Day Boundary Tests ────────────────────────────────────────────

    @Test
    fun `clock at 0330 with default boundary yields yesterday's logical date`() {
        // 03:30 on Monday May 12 -> with boundary 04:00, logical date is Sunday May 11
        val clock0330 = timeClock("2025-05-12T03:30:00+02:00")
        val sundayHabit = habit("sunday", ScheduleType.Weekdays(setOf(DayOfWeek.SUNDAY)))
        val state = build(listOf(sundayHabit), emptyList(), clock = clock0330)

        // Sunday habit is due because logical date is Sunday May 11
        assertTrue(state is WidgetDisplayState.Habits)
        val h = state as WidgetDisplayState.Habits
        assertEquals("sunday", h.rows.first().habitId)
    }

    @Test
    fun `clock at 0430 with default boundary yields today's logical date`() {
        // 04:30 on Monday May 12 -> with boundary 04:00, logical date is Monday May 12
        val clock0430 = timeClock("2025-05-12T04:30:00+02:00")
        val sundayHabit = habit("sunday", ScheduleType.Weekdays(setOf(DayOfWeek.SUNDAY)))
        val mondayHabit = habit("monday", ScheduleType.Weekdays(setOf(DayOfWeek.MONDAY)))
        val state = build(listOf(sundayHabit, mondayHabit), emptyList(), clock = clock0430)

        // Monday habit is due, Sunday habit is not
        assertTrue(state is WidgetDisplayState.Habits)
        val h = state as WidgetDisplayState.Habits
        assertEquals(1, h.rows.size)
        assertEquals("monday", h.rows.first().habitId)
    }

    @Test
    fun `custom dayBoundaryHour of 6 with clock at 0530 yields yesterday's logical date`() {
        // 05:30 on Monday May 12 -> with boundary 06:00, hour 5 < 6 -> logical date is Sunday May 11
        val clock0530 = timeClock("2025-05-12T05:30:00+02:00")
        val customSettings = AppSettings(dayBoundaryHour = 6, startOfWeekDay = DayOfWeek.MONDAY)
        val sundayHabit = habit("sunday", ScheduleType.Weekdays(setOf(DayOfWeek.SUNDAY)))

        val state = build(listOf(sundayHabit), emptyList(), settings = customSettings, clock = clock0530)
        assertTrue(state is WidgetDisplayState.Habits)
        val h = state as WidgetDisplayState.Habits
        assertEquals("sunday", h.rows.first().habitId)
    }

    @Test
    fun `custom dayBoundaryHour of 6 with clock at 0630 yields today's logical date`() {
        // 06:30 on Monday May 12 -> with boundary 06:00, hour 6 >= 6 -> logical date is Monday May 12
        val clock0630 = timeClock("2025-05-12T06:30:00+02:00")
        val customSettings = AppSettings(dayBoundaryHour = 6, startOfWeekDay = DayOfWeek.MONDAY)
        val sundayHabit = habit("sunday", ScheduleType.Weekdays(setOf(DayOfWeek.SUNDAY)))
        val mondayHabit = habit("monday", ScheduleType.Weekdays(setOf(DayOfWeek.MONDAY)))

        val state = build(listOf(sundayHabit, mondayHabit), emptyList(), settings = customSettings, clock = clock0630)
        assertTrue(state is WidgetDisplayState.Habits)
        val h = state as WidgetDisplayState.Habits
        assertEquals(1, h.rows.size)
        assertEquals("monday", h.rows.first().habitId)
    }

    // ── WidgetHabitRow completionState distinction ────────────────────────────

    @Test
    fun `WidgetHabitRow preserves individual completion states DONE MINIMUM SKIPPED and pending`() {
        val hDone = habit("1")
        val hMin = habit("2")
        val hSkip = habit("3")
        val hPending = habit("4")

        val completions = listOf(
            comp("1", today, CompletionState.DONE),
            comp("2", today, CompletionState.MINIMUM),
            comp("3", today, CompletionState.SKIPPED)
        )

        val state = build(listOf(hDone, hMin, hSkip, hPending), completions)
        assertTrue(state is WidgetDisplayState.Habits)
        val h = state as WidgetDisplayState.Habits

        val rowDone = h.rows.first { it.habitId == "1" }
        assertEquals(CompletionState.DONE, rowDone.completionState)
        assertTrue(rowDone.isCompleted)
        assertFalse(rowDone.isSkipped)

        val rowMin = h.rows.first { it.habitId == "2" }
        assertEquals(CompletionState.MINIMUM, rowMin.completionState)
        assertTrue(rowMin.isCompleted)
        assertFalse(rowMin.isSkipped)

        val rowSkip = h.rows.first { it.habitId == "3" }
        assertEquals(CompletionState.SKIPPED, rowSkip.completionState)
        assertFalse(rowSkip.isCompleted)
        assertTrue(rowSkip.isSkipped)

        val rowPending = h.rows.first { it.habitId == "4" }
        assertNull(rowPending.completionState)
        assertFalse(rowPending.isCompleted)
        assertFalse(rowPending.isSkipped)
    }

    // ── Row Ordering (group nulls-first, then sortOrder) ──────────────────────

    @Test
    fun `due habits ordered by group nulls-first then sortOrder matching app list`() {
        val h1 = habit("h1", groupId = "morning", sortOrder = 2)
        val h2 = habit("h2", groupId = "morning", sortOrder = 1)
        val h3 = habit("h3", groupId = null, sortOrder = 1)
        val h4 = habit("h4", groupId = "evening", sortOrder = 1)

        val state = build(listOf(h1, h2, h3, h4), emptyList())
        assertTrue(state is WidgetDisplayState.Habits)
        val h = state as WidgetDisplayState.Habits

        // Expected order:
        // 1. groupId null (""), sortOrder 1 -> h3
        // 2. groupId "evening", sortOrder 1 -> h4
        // 3. groupId "morning", sortOrder 1 -> h2
        // 4. groupId "morning", sortOrder 2 -> h1
        val orderedIds = h.rows.map { it.habitId }
        assertEquals(listOf("h3", "h4", "h2", "h1"), orderedIds)
    }
}
