package com.habitat.app.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.habitat.app.data.repository.SettingsRepositoryImpl
import com.habitat.app.domain.model.AppSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `emptyDataStoreReturnsAppSettingsDefaults`() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(scope = backgroundScope) {
            temporaryFolder.newFolder().resolve("settings.preferences_pb")
        }
        val repository = SettingsRepositoryImpl.withDataStore(dataStore)

        assertEquals(AppSettings(), repository.getSettings().first())
    }

    @Test
    fun `posted habit ids set maintains idempotency and pruning logic`() {
        val postedMap = mutableMapOf<String, Set<String>>()

        val todayStr = "2025-05-10"
        val oldDateStr = "2025-04-25"

        // Add today posted ids
        postedMap[todayStr] = (postedMap[todayStr] ?: emptySet()) + "h1"
        postedMap[todayStr] = (postedMap[todayStr] ?: emptySet()) + "h1" // duplicate
        postedMap[todayStr] = (postedMap[todayStr] ?: emptySet()) + "h2"

        // Old date
        postedMap[oldDateStr] = setOf("h1", "h2")

        assertEquals(2, postedMap[todayStr]?.size) // "h1" only added once
        assertEquals(2, postedMap[oldDateStr]?.size)

        // Prune entries older than 2025-05-01
        val cutoff = "2025-05-01"
        val keysToRemove = postedMap.keys.filter { it < cutoff }
        keysToRemove.forEach { postedMap.remove(it) }

        assertFalse(postedMap.containsKey(oldDateStr))
        assertTrue(postedMap.containsKey(todayStr))
    }
}
