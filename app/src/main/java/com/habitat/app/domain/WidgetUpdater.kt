package com.habitat.app.domain

import kotlinx.coroutines.CancellationException
import java.util.logging.Level
import java.util.logging.Logger

/** Requests a refresh of all installed Habitat widgets. */
fun interface WidgetUpdater {
    suspend fun updateWidget()
}

/** Best-effort refresh for callers whose primary operation must remain successful. */
suspend fun WidgetUpdater.updateSafely(
    onFailure: (Exception) -> Unit = { exception ->
        Logger.getLogger("HabitatWidget").log(Level.SEVERE, "Widget refresh failed", exception)
    }
) {
    try {
        updateWidget()
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        try {
            onFailure(exception)
        } catch (_: Exception) {
            // Reporting a refresh failure must not break the primary operation.
        }
    }
}
