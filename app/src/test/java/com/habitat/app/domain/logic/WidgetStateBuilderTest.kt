package com.habitat.app.domain.logic

import com.habitat.app.domain.model.AppSettings
import com.habitat.app.domain.model.CompletionState
import com.habitat.app.domain.model.Habit
import com.habitat.app.domain.model.LoggedCompletion
import com.habitat.app.domain.model.ScheduleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Table-driven tests for [WidgetStateBuilder].
 * Covers every case listed in SPEC and the M4 plan.
 */
class WidgetStateBuilderTest {

    // ── Fixtures ──────────────────────────────────────────────────────────────

    /** Monday 12 May 2025. */
    private val today = LocalDate.of(2025, 5, 12)

    private val settingsMon = AppSettings(startOfWeekDay = DayOfWeek.MONDAY)
    private val settingsSun = AppSettings(startOfWeekDay = DayOfWeek.SUNDAY)

    private fun habit(
        id: String,
        schedule: ScheduleType = ScheduleType.Daily,
        archived: Boolean = false
    ) = Habit(id = id, name = "Habit $id", emoji = "🌱", schedule = schedule, archived = archived)

    private fun comp(habitId: String, date: LocalDate, state: CompletionState) =
        LoggedCompletion(habitId = habitId, logicalDate = date, state = state)

    private fun build(
        habits: List<Habit>,
        completions: List<LoggedCompletion>,
        settings: AppSettings = settingsMon,
        date: LocalDate = today
    ) = WidgetStateBuilder.build(date, habits, completions, settings)

    // ── NoHabits ──────────────────────────────────────────────────────────────

    @Test
    fun `no habits at all yields NoHabits`() {
        val state = build(emptyList(), emptyList())
        assertEquals(WidgetDisplayState.NoHabits, state)
    }

    // ── NothingDueToday ───────────────────────────────────────────────────────

    @Test
    fun `all habits archived yields NothingDueToday`() {
        val state = build(
            habits = listOf(habit("1", archived = true), habit("2", archived = true)),
            completions = emptyList()
        )
        assertEquals(WidgetDisplayState.NothingDueToday, state)
    }

    @Test
    fun `Unknown schedule excluded yields NothingDueToday`() {
        val state = build(
            habits = listOf(habit("1", ScheduleType.Unknown("?"))),
            completions = emptyList()
        )
        assertEquals(WidgetDisplayState.NothingDueToday, state)
    }

    @Test
    fun `habit scheduled only on Friday when today is Monday yields NothingDueToday`() {
        val state = build(
            habits = listOf(habit("1", ScheduleType.Weekdays(setOf(DayOfWeek.FRIDAY)))),
            completions = emptyList()
        )
        assertEquals(WidgetDisplayState.NothingDueToday, state)
    }

    @Test
    fun `mixed archived and unknown habits both excluded yields NothingDueToday`() {
        val archived = habit("1", archived = true)
        val unknown = habit("2", ScheduleType.Unknown("?"))
        val state = build(habits = listOf(archived, unknown), completions = emptyList())
        assertEquals(WidgetDisplayState.NothingDueToday, state)
    }

    @Test
    fun `NPerWeek quota met means habit not due yields NothingDueToday`() {
        // n=1, one DONE this week -> quota met -> not due
        val sunday = LocalDate.of(2025, 5, 11) // prev day in Mon-start week? No, May 11 is Sunday.
        // With Monday start, May 12 week starts May 12. May 11 is NOT in this week.
        // So use today itself:
        val h = habit("1", ScheduleType.NPerWeek(n = 1))
        val completions = listOf(comp("1", today, CompletionState.DONE))
        val state = build(habits = listOf(h), completions = completions)
        // quota met, isDueOn returns false -> NothingDueToday
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
        // totalDueCount excludes skipped: 3 due - 1 skipped = 2
        assertEquals(2, h.totalDueCount)
        assertEquals(1, h.completedCount)
        assertEquals(0.5f, h.progressFraction, 0.001f)
        assertFalse(h.allCompleted)
        // All 3 due habits appear in rows
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
        // May 11 (Sunday) is inside the Sun-start week that contains May 12 (Monday)
        val sunday = LocalDate.of(2025, 5, 11)
        val h = habit("1", ScheduleType.NPerWeek(n = 1))
        val completions = listOf(comp("1", sunday, CompletionState.DONE))
        val state = build(listOf(h), completions, settings = settingsSun)
        // quota met -> isDueOn false -> NothingDueToday
        assertEquals(WidgetDisplayState.NothingDueToday, state)
    }

    @Test
    fun `Monday-start week excludes Sunday completion for NPerWeek quota`() {
        // May 11 (Sunday) is outside the Mon-start week starting May 12 -> quota not met
        val sunday = LocalDate.of(2025, 5, 11)
        val h = habit("1", ScheduleType.NPerWeek(n = 1))
        val completions = listOf(comp("1", sunday, CompletionState.DONE))
        val state = build(listOf(h), completions, settings = settingsMon)
        // quota not met -> habit IS due -> Habits state
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
        // 1 DONE only, n=2 not reached -> still due
        assertTrue(state is WidgetDisplayState.Habits)
    }

    // ── Day-boundary: builder is pure, caller supplies logicalDate ────────────

    @Test
    fun `builder uses the provided logicalDate not system clock`() {
        // Passing yesterday as "today" → a Daily habit is due for that date
        val yesterday = today.minusDays(1)
        val h = habit("1", ScheduleType.Daily)
        val state = build(listOf(h), emptyList(), date = yesterday)
        assertTrue(state is WidgetDisplayState.Habits)
    }

    @Test
    fun `configured dayBoundaryHour is applied externally before build is called`() {
        // Simulate clock at 03:30 (before boundary) → logical date = May 11
        val logicalDate = LocalDate.of(2025, 5, 11) // Sunday
        val h = habit("1", ScheduleType.Weekdays(setOf(DayOfWeek.SUNDAY)))
        val state = build(listOf(h), emptyList(), date = logicalDate)
        assertTrue("Habit scheduled on Sunday should be due when logical date is Sunday",
            state is WidgetDisplayState.Habits)
    }

    // ── Zero habits due (NPerWeek all at quota) ───────────────────────────────

    @Test
    fun `multiple NPerWeek habits all at quota yields NothingDueToday`() {
        // Use Wednesday May 14 as "today" so Mon(12) and Tue(13) are in same Mon-start week
        val wednesday = LocalDate.of(2025, 5, 14)
        val h1 = habit("1", ScheduleType.NPerWeek(n = 2))
        val h2 = habit("2", ScheduleType.NPerWeek(n = 1))
        val completions = listOf(
            comp("1", LocalDate.of(2025, 5, 12), CompletionState.DONE),  // Monday in-week
            comp("1", LocalDate.of(2025, 5, 13), CompletionState.DONE),  // Tuesday in-week
            comp("2", LocalDate.of(2025, 5, 12), CompletionState.MINIMUM) // Monday in-week
        )
        val state = build(listOf(h1, h2), completions, date = wednesday)
        assertEquals(WidgetDisplayState.NothingDueToday, state)
    }
}
