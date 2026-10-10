package com.habitat.app.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitat.app.domain.AppClock
import com.habitat.app.domain.model.AppSettings
import com.habitat.app.domain.repository.CompletionRepository
import com.habitat.app.domain.repository.HabitRepository
import com.habitat.app.domain.repository.SettingsRepository
import com.habitat.app.platform.DayBoundaryScheduler
import com.habitat.app.platform.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val habitRepository: HabitRepository,
    private val completionRepository: CompletionRepository,
    private val clock: AppClock
) : ViewModel() {

    val settingsState: StateFlow<AppSettings> = settingsRepository.getSettings()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppSettings()
        )

    fun updateSettings(newSettings: AppSettings) {
        viewModelScope.launch {
            val oldSettings = settingsRepository.getSettings().first()
            settingsRepository.updateSettings(newSettings)
            // Reconcile reminders when quiet hours or settings change
            ReminderScheduler.reconcile(context, habitRepository, completionRepository, settingsRepository, clock)
            // Re-arm day boundary when dayBoundaryHour changes in Settings
            if (oldSettings.dayBoundaryHour != newSettings.dayBoundaryHour) {
                DayBoundaryScheduler.scheduleNext(context, settingsRepository, clock)
            }
        }
    }
}
