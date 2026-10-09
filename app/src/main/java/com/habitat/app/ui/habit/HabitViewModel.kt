package com.habitat.app.ui.habit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitat.app.domain.AppClock
import com.habitat.app.domain.SystemAppClock
import com.habitat.app.domain.logic.ReplayEngine
import com.habitat.app.domain.logic.ReplayResult
import com.habitat.app.domain.model.CompletionState
import com.habitat.app.domain.model.Habit
import com.habitat.app.domain.model.LoggedCompletion
import com.habitat.app.domain.model.OccurrenceState
import com.habitat.app.domain.repository.CompletionRepository
import com.habitat.app.domain.repository.HabitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class HabitItemUiState(
    val habit: Habit,
    val replayResult: ReplayResult,
    val selectedDateState: OccurrenceState,
    val selectedDateCompletion: LoggedCompletion?
)

data class HabitListUiState(
    val habitItems: List<HabitItemUiState> = emptyList(),
    val overallConsistency: Double? = null,
    val currentLogicalDate: LocalDate = LocalDate.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    val availableDates: List<LocalDate> = emptyList(),
    val debugShiftedDays: Long = 0,
    val debugShiftedHours: Long = 0
)

@HiltViewModel
class HabitViewModel @Inject constructor(
    private val habitRepository: HabitRepository,
    private val completionRepository: CompletionRepository,
    private val clock: AppClock
) : ViewModel() {

    private val selectedDateFlow = MutableStateFlow<LocalDate?>(null)

    val uiState: StateFlow<HabitListUiState> = combine(
        habitRepository.getActiveHabits(),
        completionRepository.getAllCompletions(),
        selectedDateFlow
    ) { habits, completions, customSelectedDate ->
        val today = clock.logicalDate()
        val selectedDate = customSelectedDate ?: today

        // Restrict backfill to trailing 7 days, block future dates
        val availableDates = (0L..7L).map { today.minusDays(it) }.reversed()
        val clampedSelectedDate = if (selectedDate > today) {
            today
        } else if (selectedDate < today.minusDays(7)) {
            today.minusDays(7)
        } else {
            selectedDate
        }

        val completionsByHabit = completions.groupBy { it.habitId }

        val items = habits.map { habit ->
            val habitCompletions = completionsByHabit[habit.id] ?: emptyList()
            val replay = ReplayEngine.evaluate(
                habit = habit,
                loggedCompletions = habitCompletions,
                currentLogicalDate = today
            )
            val selectedComp = habitCompletions.find { it.logicalDate == clampedSelectedDate }
            val dateState = replay.occurrenceStates[clampedSelectedDate] ?: OccurrenceState.PENDING

            HabitItemUiState(
                habit = habit,
                replayResult = replay,
                selectedDateState = dateState,
                selectedDateCompletion = selectedComp
            )
        }

        val scores = items.mapNotNull { it.replayResult.consistencyScore }
        val overall = if (scores.isNotEmpty()) scores.average() else null

        HabitListUiState(
            habitItems = items,
            overallConsistency = overall,
            currentLogicalDate = today,
            selectedDate = clampedSelectedDate,
            availableDates = availableDates
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HabitListUiState()
    )

    fun setSelectedDate(date: LocalDate) {
        val today = clock.logicalDate()
        if (date <= today && date >= today.minusDays(7)) {
            selectedDateFlow.value = date
        }
    }

    fun toggleCheckOff(habitId: String, state: CompletionState, date: LocalDate) {
        val today = clock.logicalDate()
        if (date > today) return // Block future logging

        viewModelScope.launch {
            completionRepository.logCompletion(
                LoggedCompletion(
                    habitId = habitId,
                    logicalDate = date,
                    state = state
                )
            )
        }
    }

    fun removeCompletion(habitId: String, date: LocalDate) {
        viewModelScope.launch {
            completionRepository.removeCompletion(habitId, date)
        }
    }

    fun saveHabit(habit: Habit) {
        viewModelScope.launch {
            habitRepository.saveHabit(habit)
        }
    }

    fun archiveHabit(id: String) {
        viewModelScope.launch {
            habitRepository.archiveHabit(id)
        }
    }

    fun shiftDebugTime(days: Long, hours: Long) {
        if (clock is SystemAppClock) {
            clock.shiftTime(days, hours)
            selectedDateFlow.value = clock.logicalDate()
        }
    }

    fun resetDebugTime() {
        if (clock is SystemAppClock) {
            clock.resetShift()
            selectedDateFlow.value = clock.logicalDate()
        }
    }
}
