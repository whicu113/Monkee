package com.habitat.app.platform.widget

import com.habitat.app.domain.AppClock
import com.habitat.app.domain.WidgetRefreshSignal
import com.habitat.app.domain.WidgetUpdater
import com.habitat.app.domain.repository.CompletionRepository
import com.habitat.app.domain.repository.HabitRepository
import com.habitat.app.domain.repository.SettingsRepository
import com.habitat.app.domain.usecase.LogCompletionUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface HabitWidgetEntryPoint {
    fun habitRepository(): HabitRepository
    fun completionRepository(): CompletionRepository
    fun settingsRepository(): SettingsRepository
    fun appClock(): AppClock
    fun logCompletionUseCase(): LogCompletionUseCase
    fun widgetUpdater(): WidgetUpdater
    fun widgetRefreshSignal(): WidgetRefreshSignal
}
