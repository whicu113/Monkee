package com.habitat.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.habitat.app.ui.habit.HabitListScreen
import com.habitat.app.ui.habit.HabitViewModel
import com.habitat.app.ui.settings.SettingsScreen
import com.habitat.app.ui.settings.SettingsViewModel
import com.habitat.app.ui.theme.MonkeeTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val habitViewModel: HabitViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
}
