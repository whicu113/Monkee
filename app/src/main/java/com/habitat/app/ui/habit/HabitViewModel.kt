package com.habitat.app.ui.habit

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitat.app.BuildConfig
import com.habitat.app.domain.AppClock
import com.habitat.app.domain.SystemAppClock
import com.habitat.app.domain.WidgetUpdater
import com.habitat.app.domain.updateSafely
import com.habitat.app.domain.logic.FireDecision
import com.habitat.app.domain.logic.ReminderEngine
import com.habitat.app.domain.logic.ReplayEngine
import com.habitat.app.domain.logic.ReplayResult
import com.habitat.app.domain.model.CompletionState
import com.habitat.app.domain.model.Habit
import com.habitat.app.domain.model.LoggedCompletion
import com.habitat.app.domain.model.OccurrenceState
import com.habitat.app.domain.model.ScheduleType
import com.habitat.app.domain.repository.CompletionRepository
import com.habitat.app.domain.repository.HabitRepository
import com.habitat.app.domain.repository.PlayerEconomyRepository
import com.habitat.app.domain.repository.SettingsRepository
import com.habitat.app.domain.playerXpProgress
import com.habitat.app.domain.usecase.LogCompletionUseCase
import com.habitat.app.platform.NotificationHelper
import com.habitat.app.platform.ReminderScheduler
import android.util.Log
import com.habitat.app.platform.ReminderFulfillmentHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
    val quietHoursEnabled: Boolean = true,
    val debugFireResult: String? = null,
    val seedsBalance: Long = 0L,
    val level: Long = 1L,
    val xpIntoLevel: Long = 0L,
    val xpRequiredForNextLevel: Long = 100L,
    val xpProgressFraction: Float = 0f
)

private data class HabitEconomyUiState(
    val seedsBalance: Long,
    val xpIntoLevel: Long,
    val level: Long,
    val xpRequiredForNextLevel: Long,
    val xpProgressFraction: Float
)

@HiltViewModel
class HabitViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val habitRepository: HabitRepository,
    private val completionRepository: CompletionRepository,
    private val settingsRepository: SettingsRepository,
    private val logCompletionUseCase: LogCompletionUseCase,
    private val reminderFulfillmentHandler: ReminderFulfillmentHandler,
    private val clock: AppClock,
    private val widgetUpdater: WidgetUpdater,
    private val playerEconomyRepository: PlayerEconomyRepository
) : ViewModel() {

    private val selectedDateFlow = MutableStateFlow<LocalDate?>(null)
    private val debugFireResultFlow = MutableStateFlow<String?>(null)

    private val habitUiState: Flow<HabitListUiState> = combine(
        habitRepository.getActiveHabits(),
        completionRepository.getAllCompletions(),
        settingsRepository.getSettings(),
        selectedDateFlow,
        debugFireResultFlow
    ) { habits, completions, settings, customSelectedDate, fireResult ->
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
            val nextFormatter = java.time.format.DateTimeFormatter.ofPattern("MMM d HH:mm")
            val formattedNextTrigger = nextTriggerInstant?.atZone(clock.zoneId())?.format(nextFormatter)

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
            quietHoursEnabled = settings.quietHoursEnabled,
            debugFireResult = fireResult
        )
    }

    private val economyUiState = combine(
        playerEconomyRepository.observeSeedsBalance(),
        playerEconomyRepository.observeXpTotal()
    ) { seedsBalance, xpTotal ->
        val progress = playerXpProgress(xpTotal)
        HabitEconomyUiState(
            seedsBalance = seedsBalance,
            level = progress.level,
            xpIntoLevel = progress.xpIntoLevel,
            xpRequiredForNextLevel = progress.xpRequiredForNextLevel,
            xpProgressFraction = progress.fraction
        )
    }

    val uiState: StateFlow<HabitListUiState> = combine(habitUiState, economyUiState) { habitState, economy ->
        habitState.copy(
            seedsBalance = economy.seedsBalance,
            level = economy.level,
            xpIntoLevel = economy.xpIntoLevel,
            xpRequiredForNextLevel = economy.xpRequiredForNextLevel,
            xpProgressFraction = economy.xpProgressFraction
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
            try {
                habitRepository.saveHabit(habit)
                ReminderScheduler.reconcile(context, habitRepository, completionRepository, settingsRepository, clock)
            } finally {
                widgetUpdater.updateSafely { exception ->
                    Log.e("HabitViewModel", "Widget refresh failed after saving habit '${habit.name}'", exception)
                }
            }
        }
    }

    fun archiveHabit(id: String) {
        viewModelScope.launch {
            try {
                habitRepository.archiveHabit(id)
                NotificationHelper.cancelNotification(context, id)
                ReminderScheduler.cancelAlarm(context, id)
            } finally {
                widgetUpdater.updateSafely { exception ->
                    Log.e("HabitViewModel", "Widget refresh failed after archiving habit '$id'", exception)
                }
            }
        }
    }

    fun reconcileNow() {
        viewModelScope.launch {
            ReminderScheduler.reconcile(context, habitRepository, completionRepository, settingsRepository, clock)
        }
    }

    fun refreshWidget() {
        viewModelScope.launch {
            widgetUpdater.updateSafely { exception ->
                Log.e("HabitViewModel", "Debug widget refresh failed", exception)
            }
        }
    }

    fun shiftDebugTime(days: Long, hours: Long) {
        if (!BuildConfig.DEBUG) return
        if (clock is SystemAppClock) {
            clock.shiftTime(days, hours)
            selectedDateFlow.value = clock.logicalDate()
        }
    }

    fun resetDebugTime() {
        if (!BuildConfig.DEBUG) return
        if (clock is SystemAppClock) {
            clock.resetShift()
            selectedDateFlow.value = clock.logicalDate()
        }
    }

    fun fireRemindersNow(forcePost: Boolean = false) {
        Log.d("HabitatDebug", "fireRemindersNow clicked, forcePost=$forcePost")
        viewModelScope.launch {
            val habits = habitRepository.getActiveHabits().first()
            val habitsWithReminders = habits.filter { it.reminderTime != null && !it.archived && it.schedule !is ScheduleType.Unknown }
            val targetHabits = if (forcePost) habitsWithReminders else habits

            if (targetHabits.isEmpty()) {
                val msg = if (forcePost) "No active habits with reminder set" else "No active habits"
                Log.d("HabitatDebug", msg)
                debugFireResultFlow.value = msg
                return@launch
            }

            val today = clock.logicalDate()
            val postedIds = settingsRepository.getPostedHabitIds(today.toString())
            val results = mutableListOf<String>()

            for (habit in targetHabits) {
                val decision = reminderFulfillmentHandler.execute(
                    context = context,
                    habitId = habit.id,
                    intentDateStr = null,
                    forcePost = forcePost,
                    dryRun = !forcePost
                )

                val summary = when (decision) {
                    is FireDecision.Post -> "Posted"
                    is FireDecision.Summary -> "Summary (cap ${postedIds.size}/3 reached)"
                    is FireDecision.Drop -> "Dropped (${decision.reason})"
                    null -> "Dropped (not found or archived)"
                }
                Log.d("HabitatDebug", "Habit '${habit.name}': $summary")
                results.add("${habit.name}: $summary")
            }

            debugFireResultFlow.value = results.joinToString("\n")
        }
    }

    fun clearPostedIdsToday() {
        viewModelScope.launch {
            val today = clock.logicalDate()
            settingsRepository.clearPostedHabitIds(today.toString())
            Log.d("HabitatDebug", "Cleared posted-ids for $today")
            debugFireResultFlow.value = "Cleared posted-ids for $today"
        }
    }
}
