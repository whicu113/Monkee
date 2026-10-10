package com.habitat.app.domain.logic

import app.cash.turbine.test
import com.habitat.app.domain.AppClock
import com.habitat.app.domain.model.AppSettings
import com.habitat.app.domain.model.CompletionState
import com.habitat.app.domain.model.Habit
import com.habitat.app.domain.model.LoggedCompletion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class WidgetStateFlowTest {
    @Test
    fun `repository and refresh changes emit current state and logical date`() = runTest {
        val date = LocalDate.of(2026, 10, 10)
        var instant = date.atTime(12, 0).atZone(ZoneId.of("Europe/Berlin")).toInstant()
        val clock = object : AppClock {
            override fun instant() = instant
            override fun zoneId() = ZoneId.of("Europe/Berlin")
        }
        val habits = MutableStateFlow(listOf(Habit(id = "habit-1", name = "Read")))
        val completions = MutableStateFlow(emptyList<LoggedCompletion>())
        val settings = MutableStateFlow(AppSettings(startOfWeekDay = DayOfWeek.MONDAY))
        val refreshGeneration = MutableStateFlow(0L)

        WidgetStateFlow.build(clock, habits, completions, settings, refreshGeneration).test {
            val initial = awaitItem()
            assertEquals("initial pending row", 0, (initial.displayState as WidgetDisplayState.Habits).completedCount)
            assertEquals("initial logical date", date, initial.renderedLogicalDate)

            completions.value = listOf(
                LoggedCompletion(
                    id = "completion-1",
                    habitId = "habit-1",
                    logicalDate = date,
                    state = CompletionState.DONE,
                    completedAt = Instant.parse("2026-10-10T10:00:00Z")
                )
            )
            val completed = awaitItem()
            assertEquals("new completion row", 1, (completed.displayState as WidgetDisplayState.Habits).completedCount)

            refreshGeneration.value = 1L
            val refreshed = awaitItem()
            assertEquals("refresh trigger row", 1L, refreshed.refreshGeneration)
            assertEquals("refresh unchanged display state", completed.displayState, refreshed.displayState)

            instant = date.plusDays(1).atTime(12, 0).atZone(ZoneId.of("Europe/Berlin")).toInstant()
            refreshGeneration.value = 2L
            val rolledOver = awaitItem()
            assertEquals("day rollover row", date.plusDays(1), rolledOver.renderedLogicalDate)
        }
    }
}
