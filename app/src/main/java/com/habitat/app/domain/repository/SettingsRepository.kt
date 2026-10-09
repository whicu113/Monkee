package com.habitat.app.domain.repository

import com.habitat.app.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun getSettings(): Flow<AppSettings>
    suspend fun updateSettings(settings: AppSettings)
    suspend fun getPostedHabitIds(logicalDateString: String): Set<String>
    suspend fun addPostedHabitId(logicalDateString: String, habitId: String)
    suspend fun pruneOldPostedHabitIds(cutoffDateString: String)
}
