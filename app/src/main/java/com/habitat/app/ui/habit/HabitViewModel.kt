package com.habitat.app.ui.habit

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitat.app.domain.AppClock
import com.habitat.app.domain.SystemAppClock
import com.habitat.app.domain.logic.ReminderEngine
import com.habitat.app.domain.logic.ReplayEngine
import com.habitat.app.domain.logic.ReplayResult
import com.habitat.app.domain.model.CompletionState
import com.habitat.app.domain.model.Habit
import com.habitat.app.domain.model.LoggedCompletion
import com.habitat.app.domain.model.OccurrenceState
import com.habitat.app.domain.repository.CompletionRepository
import com.habitat.app.domain.repository.HabitRepository
import com.habitat.app.domain.repository.SettingsRepository
import com.habitat.app.domain.usecase.LogCompletionUseCase
import com.habitat.app.platform.NotificationHelper
import com.habitat.app.platform.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    val selectedDateCompletion: LoggedCompletion?,
    val isArmed: Boolean = false,
    val nextTriggerFormatted: String? = null
)

data class HabitListUiState(
    val habitItems: List<HabitItemUiState> = emptyList(),
    val overallConsistency: Double? = null,
    val currentLogicalDate: LocalDate = LocalDate.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    val availableDates: List<LocalDate> = emptyList(),
    val debugShiftedDays: Long = 0,
    val debugShiftedHours: Long = 0,
    val quietHoursEnabled: Boolean = true
)

@HiltViewModel
class HabitViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val habitRepository: HabitRepository,
    private val completionRepository: CompletionRepository,
    private val settingsRepository: SettingsRepository,
    private val logCompletionUseCase: LogCompletionUseCase,
    private val clock: AppClock
) : ViewModel() {

    private val selectedDateFlow = MutableStateFlow<LocalDate?>(null)

    val uiState: StateFlow<HabitListUiState> = combine(
        habitRepository.getActiveHabits(),
        completionRepository.getAllCompletions(),
        settingsRepository.getSettings(),
        selectedDateFlow
    ) { habits, completions, settings, customSelectedDate ->
        val today = clock.logicalDate()
        val selectedDate = customSelectedDate ?: today

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
                currentLogicalDate = today,
                strictStreaks = settings.strictStreaks,
                autoUseRestTokens = settings.autoUseRestTokens,
                startOfWeekDay = settings.startOfWeekDay
            )
            val selectedComp = habitCompletions.find { it.logicalDate == clampedSelectedDate }
            val dateState = replay.occurrenceStates[clampedSelectedDate] ?: OccurrenceState.PENDING

            val isArmed = ReminderScheduler.isAlarmScheduled(context, habit.id)
            val nextTriggerInstant = ReminderEngine.nextTrigger(
                habit = habit,
                fromInstant = clock.instant(),
                zoneId = clock.zoneId(),
                quietHoursEnabled = settings.quietHoursEnabled,
                quietHoursStart = settings.quietHoursStart,
                quietHoursEnd = settings.quietHoursEnd
            )
            val formattedNextTrigger = nextTriggerInstant?.atZone(clock.zoneId())?.toString()

            HabitItemUiState(
                habit = habit,
                replayResult = replay,
                selectedDateState = dateState,
                selectedDateCompletion = selectedComp,
                isArmed = isArmed,
                nextTriggerFormatted = formattedNextTrigger
            )
        }

        val scores = items.mapNotNull { it.replayResult.consistencyScore }
        val overall = if (scores.isNotEmpty()) scores.average() else null

        HabitListUiState(
            habitItems = items,
            overallConsistency = overall,
            currentLogicalDate = today,
            selectedDate = clampedSelectedDate,
            availableDates = availableDates,
            quietHoursEnabled = settings.quietHoursEnabled
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
        viewModelScope.launch {
            logCompletionUseCase.executeLog(habitId, state, date)
        }
    }

    fun removeCompletion(habitId: String, date: LocalDate) {
        viewModelScope.launch {
            logCompletionUseCase.executeRemove(habitId, date)
        }
    }

    fun saveHabit(habit: Habit) {
        viewModelScope.launch {
            habitRepository.saveHabit(habit)
            ReminderScheduler.reconcile(context, habitRepository, completionRepository, settingsRepository, clock)
        }
    }

    fun archiveHabit(id: String) {
        viewModelScope.launch {
            habitRepository.archiveHabit(id)
            NotificationHelper.cancelNotification(context, id)
            ReminderScheduler.cancelAlarm(context, id)
        }
    }

    fun reconcileNow() {
        viewModelScope.launch {
            ReminderScheduler.reconcile(context, habitRepository, completionRepository, settingsRepository, clock)
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
