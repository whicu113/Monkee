package com.habitat.app.ui.habit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habitat.app.domain.model.Habit
import com.habitat.app.domain.repository.HabitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HabitViewModel @Inject constructor(
    private val repository: HabitRepository
) : ViewModel() {

    val activeHabits: StateFlow<List<Habit>> = repository.getActiveHabits()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun saveHabit(habit: Habit) {
        viewModelScope.launch {
            repository.saveHabit(habit)
        }
    }

    fun archiveHabit(id: String) {
        viewModelScope.launch {
            repository.archiveHabit(id)
        }
    }
}
