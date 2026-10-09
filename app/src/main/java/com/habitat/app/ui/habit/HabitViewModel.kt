package com.habitat.app.ui.habit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitat.app.domain.AppClock
import com.habitat.app.domain.logic.ReplayEngine
import com.habitat.app.domain.logic.ReplayResult
import com.habitat.app.domain.model.CompletionState
import com.habitat.app.domain.model.Habit
import com.habitat.app.domain.model.LoggedCompletion
import com.habitat.app.domain.model.OccurrenceState
import com.habitat.app.domain.repository.CompletionRepository
import com.habitat.app.domain.repository.HabitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val todayState: OccurrenceState,
    val todayCompletion: LoggedCompletion?
)

data class HabitListUiState(
    val habitItems: List<HabitItemUiState> = emptyList(),
    val overallConsistency: Double? = null,
    val currentLogicalDate: LocalDate = LocalDate.now()
)

@HiltViewModel
class HabitViewModel @Inject constructor(
    private val habitRepository: HabitRepository,
    private val completionRepository: CompletionRepository,
    private val clock: AppClock
) : ViewModel() {

    val uiState: StateFlow<HabitListUiState> = combine(
        habitRepository.getActiveHabits(),
        completionRepository.getAllCompletions()
    ) { habits, completions ->
        val today = clock.logicalDate()
        val completionsByHabit = completions.groupBy { it.habitId }

        val items = habits.map { habit ->
            val habitCompletions = completionsByHabit[habit.id] ?: emptyList()
            val replay = ReplayEngine.evaluate(
                habit = habit,
                loggedCompletions = habitCompletions,
                currentLogicalDate = today
            )
            val todayComp = habitCompletions.find { it.logicalDate == today }
            val todayState = replay.occurrenceStates[today] ?: OccurrenceState.PENDING

            HabitItemUiState(
                habit = habit,
                replayResult = replay,
                todayState = todayState,
                todayCompletion = todayComp
            )
        }

        val scores = items.mapNotNull { it.replayResult.consistencyScore }
        val overall = if (scores.isNotEmpty()) scores.average() else null

        HabitListUiState(
            habitItems = items,
            overallConsistency = overall,
            currentLogicalDate = today
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HabitListUiState()
    )

    fun toggleCheckOff(habitId: String, state: CompletionState, date: LocalDate = clock.logicalDate()) {
        viewModelScope.launch {
            val current = completionRepository.getAllCompletions()
            // We fetch current completion via repository
            completionRepository.logCompletion(
                LoggedCompletion(
                    habitId = habitId,
                    logicalDate = date,
                    state = state
                )
            )
        }
    }

    fun removeCompletion(habitId: String, date: LocalDate = clock.logicalDate()) {
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
}
