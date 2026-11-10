package com.medicare.app.ui.settings

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val darkMode by viewModel.darkMode.collectAsState()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()
    val context = LocalContext.current

    var storageSize by remember { mutableStateOf(viewModel.calculateStorageUsage()) }
    var showClearedSnackbar by remember { mutableStateOf(false) }
    var testAlarmScheduledMessage by remember { mutableStateOf<String?>(null) }

    val notificationPermissionState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(permission = Manifest.permission.POST_NOTIFICATIONS)
    } else {
        null
    }

    var exactAlarmGranted by remember { mutableStateOf(viewModel.canScheduleExactAlarms(context)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings, Permissions & Diagnostics") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Preferences Section
            Text("Preferences", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Dark Mode", style = MaterialTheme.typography.bodyLarge)
                        Switch(checked = darkMode, onCheckedChange = { viewModel.setDarkMode(it) })
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Medication Reminders", style = MaterialTheme.typography.bodyLarge)
                        Switch(checked = notificationsEnabled, onCheckedChange = { viewModel.setNotificationsEnabled(it) })
                    }
                }
            }

            // Vivo / OEM Background Restrictions Guidance Card
            Text("Vivo / OEM Background & Auto-Start Guide", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Important for Vivo & Chinese ROM Devices",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = "Vivo devices (Funtouch OS) enforce strict background power management. To ensure exact audible alarms ring when your phone is locked:\n1. Open App Info / Settings.\n2. Enable 'Auto-start' (if available).\n3. Set Battery Optimization to 'Don't optimize' / Unrestricted.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    OutlinedButton(
                        onClick = { viewModel.openAppSettings(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Open App Settings & Permissions")
                    }
                }
            }

            // Permissions & Diagnostics Section
            Text("Permissions & Alarm Diagnostics", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Notification Permission Status
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Notifications (Android 13+)", style = MaterialTheme.typography.bodyLarge)
                                val granted = notificationPermissionState?.status?.isGranted ?: true
                                Text(if (granted) "Status: Granted" else "Status: Denied / Required", style = MaterialTheme.typography.bodySmall, color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                            }
                        }
                        if (notificationPermissionState != null && !notificationPermissionState.status.isGranted) {
                            OutlinedButton(onClick = { notificationPermissionState.launchPermissionRequest() }) {
                                Text("Grant")
                            }
                        }
                    }

                    // Exact Alarm Permission Status
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Exact Alarms (Android 12+)", style = MaterialTheme.typography.bodyLarge)
                                Text(if (exactAlarmGranted) "Status: Granted" else "Status: Not Granted", style = MaterialTheme.typography.bodySmall, color = if (exactAlarmGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                            }
                        }
                        if (!exactAlarmGranted) {
                            OutlinedButton(onClick = {
                                viewModel.requestExactAlarmPermission(context)
                            }) {
                                Text("Open Settings")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Physical Device Test Alarms", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Text("Trigger a test alarm using the exact same alarm and notification pipeline:", style = MaterialTheme.typography.bodySmall)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.scheduleTestAlarm(context, 1)
                                testAlarmScheduledMessage = "Test alarm scheduled for 1 minute from now!"
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("1 Min")
                        }
                        Button(
                            onClick = {
                                viewModel.scheduleTestAlarm(context, 2)
                                testAlarmScheduledMessage = "Test alarm scheduled for 2 minutes from now!"
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("2 Min")
                        }
                        Button(
                            onClick = {
                                viewModel.scheduleTestAlarm(context, 5)
                                testAlarmScheduledMessage = "Test alarm scheduled for 5 minutes from now!"
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("5 Min")
                        }
                    }

                    if (testAlarmScheduledMessage != null) {
                        Text(
                            text = testAlarmScheduledMessage!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Storage Monitoring Section
            Text("Storage & Low-Stock Monitoring", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("App Storage Usage", style = MaterialTheme.typography.bodyLarge)
                                Text("Includes JSON data and verification photos", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text(storageSize, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.clearPhotoCache()
                            storageSize = viewModel.calculateStorageUsage()
                            showClearedSnackbar = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Clear Verification Photos Cache")
                    }
                }
            }

            if (showClearedSnackbar) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Photo cache cleared successfully!",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}
