package com.habitat.app.platform.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import com.habitat.app.BuildConfig
import com.habitat.app.domain.WidgetRefreshSignal
import com.habitat.app.domain.WidgetUpdater
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GlanceWidgetUpdater @Inject constructor(
    @param:ApplicationContext private val context: Context
) : WidgetUpdater, WidgetRefreshSignal {
    private val mutableRefreshGeneration = MutableStateFlow(0L)
    override val refreshGeneration: StateFlow<Long> = mutableRefreshGeneration.asStateFlow()

    override suspend fun updateWidget() {
        try {
            mutableRefreshGeneration.update { it + 1 }
            val widget = habitWidgetForUpdate()
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "updateWidget invoked widgetClass=${widget::class.java.name}")
            }
            val glanceIds = GlanceAppWidgetManager(context).getGlanceIds(widget::class.java)
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "updateWidget found glanceIdCount=${glanceIds.size} widgetClass=${widget::class.java.name}")
            }
            widget.updateAll(context)
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "updateAll completed widgetClass=${widget::class.java.name} glanceIdCount=${glanceIds.size}")
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Unable to refresh Habitat widgets; stack trace follows", exception)
            }
        }
    }

    private companion object {
        const val TAG = "GlanceWidgetUpdater"
    }
}

internal fun habitWidgetForUpdate() = HabitWidgetReceiver().glanceAppWidget
