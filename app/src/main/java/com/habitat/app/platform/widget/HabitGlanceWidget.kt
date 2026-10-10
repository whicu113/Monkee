package com.habitat.app.platform.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Box
import androidx.glance.text.Text

/**
 * Minimal Glance widget rendering placeholder text.
 * Full layout (ring, responsive size, habit rows) is implemented in commit 6.
 * SPEC Section 8.7.
 */
class HabitGlanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            Box(modifier = GlanceModifier) {
                Text(text = "Habitat")
            }
        }
    }
}

/**
 * BroadcastReceiver for the Habitat Glance widget.
 * SPEC Section 8.7.
 */
class HabitWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HabitGlanceWidget()
}
