package com.habitat.app.domain.repository

import com.habitat.app.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun getSettings(): Flow<AppSettings>
    suspend fun updateSettings(settings: AppSettings)
    suspend fun getDailyPostedCount(logicalDateString: String): Int
    suspend fun incrementDailyPostedCount(logicalDateString: String)
}
