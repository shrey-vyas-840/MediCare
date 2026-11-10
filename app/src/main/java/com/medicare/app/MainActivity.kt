package com.medicare.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.medicare.app.ui.camera.CameraScreen
import com.medicare.app.ui.camera.CameraViewModel
import com.medicare.app.ui.history.HistoryScreen
import com.medicare.app.ui.history.HistoryViewModel
import com.medicare.app.ui.inventory.InventoryDashboardScreen
import com.medicare.app.ui.inventory.InventoryViewModel
import com.medicare.app.ui.medication.AddEditMedicationScreen
import com.medicare.app.ui.medication.MedicationListScreen
import com.medicare.app.ui.medication.MedicationViewModel
import com.medicare.app.ui.session.HomeScreen
import com.medicare.app.ui.session.SessionScreen
import com.medicare.app.ui.session.SessionViewModel
import com.medicare.app.ui.settings.SettingsScreen
import com.medicare.app.ui.settings.SettingsViewModel
import com.medicare.app.ui.theme.MediCareTheme

sealed class Screen {
    data object Home : Screen()
    data object MedicationList : Screen()
    data class AddEditMedication(val medicationId: String? = null) : Screen()
    data object Sessions : Screen()
    data object Inventory : Screen()
    data object History : Screen()
    data object Settings : Screen()
    data class Camera(val doseEventId: String, val sessionId: String) : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val initialSessionId = intent.getStringExtra("extra_session_id")
        val initialDoseEventId = intent.getStringExtra("extra_dose_event_id")
        val initialScreen = if (initialSessionId != null && initialDoseEventId != null) {
            Screen.Camera(initialDoseEventId, initialSessionId)
        } else {
            Screen.Home
        }

        setContent {
            MediCareTheme {
                var currentScreen by remember { mutableStateOf<Screen>(initialScreen) }
                val medicationViewModel: MedicationViewModel = viewModel()
                val sessionViewModel: SessionViewModel = viewModel()
                val inventoryViewModel: InventoryViewModel = viewModel()
                val historyViewModel: HistoryViewModel = viewModel()
                val settingsViewModel: SettingsViewModel = viewModel()

                val showBottomNav = currentScreen !is Screen.Camera && currentScreen !is Screen.AddEditMedication

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        if (showBottomNav) {
                            NavigationBar {
                                NavigationBarItem(
                                    selected = currentScreen is Screen.Home,
                                    onClick = { currentScreen = Screen.Home },
                                    icon = { Icon(Icons.Rounded.Home, contentDescription = "Home") },
                                    label = { Text("Home") }
                                )
                                NavigationBarItem(
                                    selected = currentScreen is Screen.Sessions,
                                    onClick = { currentScreen = Screen.Sessions },
                                    icon = { Icon(Icons.Rounded.Schedule, contentDescription = "Today's Medicines") },
                                    label = { Text("Medicines") }
                                )
                                NavigationBarItem(
                                    selected = currentScreen is Screen.Inventory,
                                    onClick = { currentScreen = Screen.Inventory },
                                    icon = { Icon(Icons.Rounded.Inventory2, contentDescription = "Tablets Left") },
                                    label = { Text("Tablets Left") }
                                )
                                NavigationBarItem(
                                    selected = currentScreen is Screen.History,
                                    onClick = { currentScreen = Screen.History },
                                    icon = { Icon(Icons.Rounded.History, contentDescription = "Previous Doses") },
                                    label = { Text("History") }
                                )
                                NavigationBarItem(
                                    selected = currentScreen is Screen.Settings,
                                    onClick = { currentScreen = Screen.Settings },
                                    icon = { Icon(Icons.Rounded.Settings, contentDescription = "Settings") },
                                    label = { Text("Settings") }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (currentScreen) {
                            is Screen.Home -> HomeScreen(
                                sessionViewModel = sessionViewModel,
                                onNavigateToMedications = { currentScreen = Screen.MedicationList },
                                onNavigateToSessions = { currentScreen = Screen.Sessions },
                                onNavigateToInventory = { currentScreen = Screen.Inventory },
                                onNavigateToHistory = { currentScreen = Screen.History },
                                onNavigateToSettings = { currentScreen = Screen.Settings }
                            )
                            is Screen.MedicationList -> MedicationListScreen(
                                viewModel = medicationViewModel,
                                onNavigateToAdd = { currentScreen = Screen.AddEditMedication() },
                                onNavigateToEdit = { id -> currentScreen = Screen.AddEditMedication(medicationId = id) }
                            )
                            is Screen.AddEditMedication -> AddEditMedicationScreen(
                                medicationId = (currentScreen as Screen.AddEditMedication).medicationId,
                                viewModel = medicationViewModel,
                                onNavigateBack = { currentScreen = Screen.MedicationList }
                            )
                            is Screen.Sessions -> SessionScreen(
                                viewModel = sessionViewModel,
                                onVerifyDose = { doseEventId, sessionId ->
                                    currentScreen = Screen.Camera(doseEventId, sessionId)
                                },
                                onNavigateBack = { currentScreen = Screen.Home }
                            )
                            is Screen.Inventory -> InventoryDashboardScreen(
                                viewModel = inventoryViewModel,
                                onNavigateBack = { currentScreen = Screen.Home }
                            )
                            is Screen.History -> HistoryScreen(
                                viewModel = historyViewModel,
                                onNavigateBack = { currentScreen = Screen.Home }
                            )
                            is Screen.Settings -> SettingsScreen(
                                viewModel = settingsViewModel,
                                onNavigateBack = { currentScreen = Screen.Home }
                            )
                            is Screen.Camera -> {
                                val screen = currentScreen as Screen.Camera
                                val cameraViewModel: CameraViewModel = viewModel(
                                    factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                                        @Suppress("UNCHECKED_CAST")
                                        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                                            return CameraViewModel(application, screen.doseEventId, screen.sessionId) as T
                                        }
                                    }
                                )
                                CameraScreen(
                                    viewModel = cameraViewModel,
                                    onNavigateBack = { currentScreen = Screen.Sessions }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
