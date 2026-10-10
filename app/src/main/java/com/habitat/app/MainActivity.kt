package com.habitat.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.habitat.app.platform.NotificationHelper
import com.habitat.app.domain.WidgetUpdater
import com.habitat.app.domain.updateSafely
import com.habitat.app.ui.habit.HabitListScreen
import com.habitat.app.ui.habit.HabitViewModel
import com.habitat.app.ui.settings.SettingsScreen
import com.habitat.app.ui.settings.SettingsViewModel
import com.habitat.app.ui.theme.MonkeeTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import java.time.LocalDate

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var widgetUpdater: WidgetUpdater

    private val habitViewModel: HabitViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleNotificationIntent(intent)

        setContent {
            MonkeeTheme {
                var currentScreen by remember { mutableStateOf("home") }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    when (currentScreen) {
                        "settings" -> {
                            SettingsScreen(
                                viewModel = settingsViewModel,
                                onNavigateBack = { currentScreen = "home" },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                        else -> {
                            HabitListScreen(
                                viewModel = habitViewModel,
                                onNavigateToSettings = { currentScreen = "settings" },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            widgetUpdater.updateSafely { exception ->
                Log.e("MainActivity", "Widget refresh failed on app open", exception)
            }
        }
    }

    private fun handleNotificationIntent(intent: Intent?) {
        intent?.getStringExtra(NotificationHelper.EXTRA_LOGICAL_DATE)?.let { dateStr ->
            try {
                val date = LocalDate.parse(dateStr)
                habitViewModel.setSelectedDate(date)
            } catch (_: Exception) {}
        }
    }
}
