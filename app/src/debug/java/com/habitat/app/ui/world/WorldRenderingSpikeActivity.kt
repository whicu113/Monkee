package com.habitat.app.ui.world

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.habitat.app.ui.theme.MonkeeTheme

class WorldRenderingSpikeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialRenderer = if (intent.getStringExtra(EXTRA_RENDERER) == RENDERER_COMPOSE) {
            WorldSpikeRenderer.COMPOSE_ELEMENTS
        } else {
            WorldSpikeRenderer.CANVAS
        }
        setContent {
            MonkeeTheme {
                WorldRenderingSpikeScreen(initialRenderer)
            }
        }
    }

    companion object {
        const val EXTRA_RENDERER = "world_spike_renderer"
        const val RENDERER_COMPOSE = "compose"
    }
}
