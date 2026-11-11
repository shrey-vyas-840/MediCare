package com.medicare.app.ui.settings

import android.Manifest
import android.os.Build
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
    val patientName by viewModel.userName.collectAsState()
    val contacts by viewModel.emergencyContacts.collectAsState()
    val autoSmsEnabled by viewModel.autoSmsEnabled.collectAsState()
    val selectedRingtone by viewModel.ringtoneUri.collectAsState()
    val emergencyAlerts by viewModel.emergencyAlerts.collectAsState()
    val context = LocalContext.current
    var patientNameDraft by rememberSaveable { mutableStateOf(patientName) }
    var contactDrafts by remember { mutableStateOf(contacts) }
    var selectedContactSlot by rememberSaveable { mutableIntStateOf(0) }
    var contactMessage by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(patientName) { patientNameDraft = patientName }
    LaunchedEffect(contacts) { contactDrafts = contacts }

    var storageSize by remember { mutableStateOf(viewModel.calculateStorageUsage()) }
    var testAlarmScheduledMessage by remember { mutableStateOf<String?>(null) }
    var showClearAllConfirmation by remember { mutableStateOf(false) }
    var includeGalleryPhotos by remember { mutableStateOf(false) }

    val notificationPermissionState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(permission = Manifest.permission.POST_NOTIFICATIONS)
    } else {
        null
    }
    val smsPermissionState = rememberPermissionState(Manifest.permission.SEND_SMS)
    val contactsPermissionState = rememberPermissionState(Manifest.permission.READ_CONTACTS)
    val contactPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == android.app.Activity.RESULT_OK && uri != null) {
            runCatching {
                context.contentResolver.query(
                    uri,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                    null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val name = cursor.getString(0).orEmpty()
                        val phone = cursor.getString(1).orEmpty()
                        if (phone.isNotBlank()) {
                            val updated = contactDrafts.toMutableList()
                            updated[selectedContactSlot] = com.medicare.app.data.storage.PreferencesManager.EmergencyContact(name, phone)
                            contactDrafts = updated
                            contactMessage = "${name.ifBlank { phone }} selected. Tap Save emergency contacts to keep it."
                        } else contactMessage = "That contact has no phone number. Choose another contact."
                    } else contactMessage = "No phone number was returned for the selected contact."
                } ?: run { contactMessage = "Could not read the selected contact." }
            }.onFailure { contactMessage = "Could not read the selected contact. Check Contacts permission and try again." }
        }
    }
    val ringtonePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)?.let { viewModel.setRingtoneUri(it.toString()) }
    }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            viewModel.setRingtoneUri(it.toString())
        }
    }

    var exactAlarmGranted by remember { mutableStateOf(viewModel.canScheduleExactAlarms(context)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
                    OutlinedTextField(
                        value = patientNameDraft,
                        onValueChange = { patientNameDraft = it },
                        label = { Text("Patient name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedButton(onClick = { viewModel.setUserName(patientNameDraft.trim()) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Save patient name")
                    }
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

            Card(modifier = Modifier.fillMaxWidth()) {
                Image(
                    painter = painterResource(com.medicare.app.R.drawable.medicare_logo),
                    contentDescription = "MediCare — Never Miss Your Medicine",
                    modifier = Modifier.fillMaxWidth().aspectRatio(1.5f).padding(8.dp),
                    contentScale = ContentScale.Fit
                )
            }

            Text("Emergency contacts", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    contactDrafts.take(2).forEachIndexed { index, contact ->
                        Text("Contact ${index + 1}", style = MaterialTheme.typography.titleSmall)
                        Text(if (contact.phone.isBlank()) "No contact selected" else "${contact.name.ifBlank { "Contact" }} · ${contact.phone}", style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                selectedContactSlot = index
                                if (contactsPermissionState.status.isGranted) {
                                    contactPicker.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI).apply { type = ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE })
                                } else {
                                    contactMessage = "Allow Contacts access, then tap Select contact again."
                                    contactsPermissionState.launchPermissionRequest()
                                }
                            }, modifier = Modifier.weight(1f)) { Text(if (contact.phone.isBlank()) "Select contact" else "Change contact") }
                            if (contact.phone.isNotBlank()) OutlinedButton(onClick = {
                                contactDrafts = contactDrafts.toMutableList().also { it[index] = com.medicare.app.data.storage.PreferencesManager.EmergencyContact("", "") }
                            }) { Text("Remove") }
                        }
                    }
                    if (contactMessage != null) Text(contactMessage!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    Button(onClick = {
                        val selected = contactDrafts.take(2)
                        val phoneKeys = selected.map { it.phone.filter(Char::isDigit) }.filter { it.isNotBlank() }
                        if (phoneKeys.size != phoneKeys.distinct().size) contactMessage = "Choose two different phone numbers before saving."
                        else {
                            viewModel.saveEmergencyContacts(selected)
                            contactMessage = "Emergency contacts saved on this device."
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Save emergency contacts") }
                    Text("Contacts access is used only when you choose a contact. MediCare stores the two selected names and numbers locally so you can change or remove them here.", style = MaterialTheme.typography.bodySmall)
                    Text(
                        if (viewModel.hasTelephonyMessaging()) "This device supports SMS. Permission: ${if (smsPermissionState.status.isGranted) "granted" else "not granted"}." else "This device does not report SMS telephony support; messages can only be prepared for manual sending.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Allow automatic emergency SMS", modifier = Modifier.weight(1f))
                        Switch(checked = autoSmsEnabled, onCheckedChange = { enabled ->
                            if (!enabled) viewModel.setAutoSmsEnabled(false)
                            else if (smsPermissionState.status.isGranted && viewModel.hasTelephonyMessaging()) viewModel.setAutoSmsEnabled(true)
                            else smsPermissionState.launchPermissionRequest()
                        })
                    }
                    Text("If Android or the device blocks SMS, MediCare records the failed attempt and offers a message composer. It never reports a blocked message as sent.", style = MaterialTheme.typography.bodySmall)
                    if (emergencyAlerts.isNotEmpty()) {
                        HorizontalDivider()
                        Text("Emergency alert history", style = MaterialTheme.typography.titleSmall)
                        emergencyAlerts.take(5).forEach { alert ->
                            Text("${alert.outcome} · ${java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault()).format(java.util.Date(alert.checkedAt))}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            Text("Alarm sound", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(selectedRingtone?.substringAfterLast('/') ?: "System default alarm", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            ringtonePicker.launch(Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, selectedRingtone?.let(Uri::parse))
                            })
                        }) { Text("System tones") }
                        OutlinedButton(onClick = { audioPicker.launch(arrayOf("audio/*")) }) { Text("Choose audio") }
                        var preview by remember { mutableStateOf<android.media.Ringtone?>(null) }
                        DisposableEffect(Unit) { onDispose { preview?.stop() } }
                        TextButton(onClick = {
                            if (preview?.isPlaying == true) { preview?.stop(); preview = null }
                            else {
                                val uri = selectedRingtone?.let(Uri::parse) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                                preview = RingtoneManager.getRingtone(context, uri)?.also { it.play() }
                            }
                        }) { Text(if (preview?.isPlaying == true) "Stop" else "Preview") }
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
                    TextButton(onClick = { viewModel.clearAlarmTests { testAlarmScheduledMessage = "Isolated alarm test data and reminders cleared." } }) {
                        Text("End outstanding alarm tests")
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
                                Text("App-private records and cached files", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text(storageSize, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                    }

                }
            }


            OutlinedButton(onClick = { showClearAllConfirmation = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Icon(Icons.Rounded.Delete, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Clear All MediCare Data")
            }
            Text("© 2026 MediCare · Created & Managed by Shrey Vyas", modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }

    if (showClearAllConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirmation = false; includeGalleryPhotos = false },
            title = { Text("Clear all MediCare data?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This permanently removes medicines, dose and usage history, emergency alert records, reminders, settings, and local photo cache. This cannot be undone.")
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = includeGalleryPhotos, onCheckedChange = { includeGalleryPhotos = it })
                        Text("Also delete only MediCare-owned photos in Pictures/MediCare")
                    }
                    Text("Gallery photos stay untouched unless you check this separate option. If Gallery ownership cannot be verified, photos will be kept.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val deletePhotos = includeGalleryPhotos
                    showClearAllConfirmation = false
                    viewModel.clearAllData(deletePhotos) { removed ->
                        testAlarmScheduledMessage = if (deletePhotos && removed == null) "App data cleared; Gallery ownership could not be verified, so photos were kept." else "MediCare data cleared${if (deletePhotos) "; deleted ${removed ?: 0} app-owned Gallery photo(s)" else ""}."
                    }
                }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Clear data") }
            },
            dismissButton = { TextButton(onClick = { showClearAllConfirmation = false; includeGalleryPhotos = false }) { Text("Cancel") }
            }
        )
    }
}
