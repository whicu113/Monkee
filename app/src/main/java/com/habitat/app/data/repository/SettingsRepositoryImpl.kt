package com.habitat.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.habitat.app.domain.model.AppSettings
import com.habitat.app.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

@Singleton
class SettingsRepositoryImpl private constructor(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    @Inject
    constructor(@ApplicationContext context: Context) : this(context.dataStore)

    companion object {
        internal fun withDataStore(dataStore: DataStore<Preferences>): SettingsRepositoryImpl =
            SettingsRepositoryImpl(dataStore)
    }

    private object Keys {
        val DAY_BOUNDARY = intPreferencesKey("day_boundary_hour")
        val QUIET_ENABLED = booleanPreferencesKey("quiet_hours_enabled")
        val QUIET_START = stringPreferencesKey("quiet_hours_start")
        val QUIET_END = stringPreferencesKey("quiet_hours_end")
        val SNOOZE_LENGTH = intPreferencesKey("snooze_length_minutes")
        val STRICT_STREAKS = booleanPreferencesKey("strict_streaks")
        val AUTO_REST_TOKENS = booleanPreferencesKey("auto_rest_tokens")
        val START_OF_WEEK = stringPreferencesKey("start_of_week")
    }

    override fun getSettings(): Flow<AppSettings> {
        return dataStore.data.map { prefs ->
            val defaults = AppSettings()
            AppSettings(
                dayBoundaryHour = prefs[Keys.DAY_BOUNDARY] ?: defaults.dayBoundaryHour,
                quietHoursEnabled = prefs[Keys.QUIET_ENABLED] ?: defaults.quietHoursEnabled,
                quietHoursStart = prefs[Keys.QUIET_START]?.let { LocalTime.parse(it) } ?: defaults.quietHoursStart,
                quietHoursEnd = prefs[Keys.QUIET_END]?.let { LocalTime.parse(it) } ?: defaults.quietHoursEnd,
                snoozeLengthMinutes = prefs[Keys.SNOOZE_LENGTH] ?: defaults.snoozeLengthMinutes,
                strictStreaks = prefs[Keys.STRICT_STREAKS] ?: defaults.strictStreaks,
                autoUseRestTokens = prefs[Keys.AUTO_REST_TOKENS] ?: defaults.autoUseRestTokens,
                startOfWeekDay = prefs[Keys.START_OF_WEEK]?.let { DayOfWeek.valueOf(it) } ?: defaults.startOfWeekDay
            )
        }
    }

    override suspend fun updateSettings(settings: AppSettings) {
        dataStore.edit { prefs ->
            prefs[Keys.DAY_BOUNDARY] = settings.dayBoundaryHour
            prefs[Keys.QUIET_ENABLED] = settings.quietHoursEnabled
            prefs[Keys.QUIET_START] = settings.quietHoursStart.toString()
            prefs[Keys.QUIET_END] = settings.quietHoursEnd.toString()
            prefs[Keys.SNOOZE_LENGTH] = settings.snoozeLengthMinutes
            prefs[Keys.STRICT_STREAKS] = settings.strictStreaks
            prefs[Keys.AUTO_REST_TOKENS] = settings.autoUseRestTokens
            prefs[Keys.START_OF_WEEK] = settings.startOfWeekDay.name
        }
    }

    override suspend fun getPostedHabitIds(logicalDateString: String): Set<String> {
        val key = stringSetPreferencesKey("posted_ids_$logicalDateString")
        return dataStore.data.first()[key] ?: emptySet()
    }

    override suspend fun addPostedHabitId(logicalDateString: String, habitId: String) {
        val key = stringSetPreferencesKey("posted_ids_$logicalDateString")
        dataStore.edit { prefs ->
            val current = prefs[key] ?: emptySet()
            prefs[key] = current + habitId
        }
    }

    override suspend fun clearPostedHabitIds(logicalDateString: String) {
        val key = stringSetPreferencesKey("posted_ids_$logicalDateString")
        dataStore.edit { prefs ->
            prefs.remove(key)
        }
    }

    override suspend fun pruneOldPostedHabitIds(cutoffDateString: String) {
        dataStore.edit { prefs ->
            val keysToRemove = prefs.asMap().keys.filter { key ->
                key.name.startsWith("posted_ids_") && key.name.removePrefix("posted_ids_") < cutoffDateString
            }
            keysToRemove.forEach { key ->
                prefs.remove(key)
            }
        }
    }
}
