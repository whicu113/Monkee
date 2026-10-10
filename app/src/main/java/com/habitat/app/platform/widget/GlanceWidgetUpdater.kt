package com.habitat.app.platform.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import com.habitat.app.domain.WidgetUpdater
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GlanceWidgetUpdater @Inject constructor(
    @param:ApplicationContext private val context: Context
) : WidgetUpdater {
    override suspend fun updateWidget() {
        try {
            HabitGlanceWidget().updateAll(context)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Log.e(TAG, "Unable to refresh Habitat widgets", exception)
        }
    }

    private companion object {
        const val TAG = "GlanceWidgetUpdater"
    }
}
