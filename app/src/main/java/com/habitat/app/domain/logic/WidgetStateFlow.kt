package com.habitat.app.domain.logic

import com.habitat.app.domain.AppClock
import com.habitat.app.domain.model.AppSettings
import com.habitat.app.domain.model.Habit
import com.habitat.app.domain.model.LoggedCompletion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A single widget render, including the logical date used by its row actions. */
data class WidgetRenderState(
    val displayState: WidgetDisplayState,
    val renderedLogicalDate: LocalDate,
    val refreshGeneration: Long
)

/** Builds a fresh widget state whenever repository data or a widget refresh changes. */
object WidgetStateFlow {
    fun build(
        clock: AppClock,
        habits: Flow<List<Habit>>,
        completions: Flow<List<LoggedCompletion>>,
        settings: Flow<AppSettings>,
        refreshGeneration: Flow<Long>
    ): Flow<WidgetRenderState> = combine(habits, completions, settings, refreshGeneration) { habitList, completionList, appSettings, generation ->
        val renderInstant = clock.instant()
        val renderZone = clock.zoneId()
        val renderClock = object : AppClock {
            override fun instant(): Instant = renderInstant
            override fun zoneId(): ZoneId = renderZone
        }
        WidgetRenderState(
            displayState = WidgetStateBuilder.build(renderClock, habitList, completionList, appSettings),
            renderedLogicalDate = renderClock.logicalDate(appSettings.dayBoundaryHour),
            refreshGeneration = generation
        )
    }
}
