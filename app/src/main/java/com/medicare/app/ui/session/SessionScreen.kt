package com.medicare.app.ui.session

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Camera
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.medicare.app.data.models.DoseStatus
import com.medicare.app.data.models.MedicineSession
import com.medicare.app.data.repositories.MedicationRepository
import com.medicare.app.data.storage.JsonStorage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(
    viewModel: SessionViewModel,
    onVerifyDose: (String, String) -> Unit = { _, _ -> },
    onNavigateBack: () -> Unit = {}
) {
    val selectedDate by viewModel.selectedDate.collectAsState()
    val groupedSessions by viewModel.groupedSessions.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Today's Medicines ($selectedDate)") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            )
        }
    ) { innerPadding ->
        if (groupedSessions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Rounded.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No medicines scheduled for today",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                groupedSessions.forEach { (timeSlot, sessions) ->
                    item {
                        Text(
                            text = timeSlot.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    items(sessions) { session ->
                        SessionCard(
                            session = session,
                            viewModel = viewModel,
                            onSnooze = { viewModel.snoozeSession(context, session) },
                            onVerifyDose = onVerifyDose
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SessionCard(
    session: MedicineSession,
    viewModel: SessionViewModel,
    onSnooze: () -> Unit,
    onVerifyDose: (String, String) -> Unit
) {
    val context = LocalContext.current
    val doseEvents = remember(session.sessionId) { viewModel.getDoseEvents(session.sessionId) }
    val medicationRepository = remember { MedicationRepository(JsonStorage(context)) }
    val medications = remember { medicationRepository.getMedications().associateBy { it.id } }

    val completedCount = doseEvents.count { it.status == DoseStatus.TAKEN || it.status == DoseStatus.SKIPPED }
    val totalCount = doseEvents.size

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (session.status) {
                com.medicare.app.data.models.SessionStatus.COMPLETED -> MaterialTheme.colorScheme.surfaceVariant
                com.medicare.app.data.models.SessionStatus.PARTIAL -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when (session.status) {
                            com.medicare.app.data.models.SessionStatus.COMPLETED -> Icons.Rounded.CheckCircle
                            else -> Icons.Rounded.HourglassEmpty
                        },
                        contentDescription = null,
                        tint = when (session.status) {
                            com.medicare.app.data.models.SessionStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.secondary
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = session.scheduledTime,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (totalCount > 1) {
                            Text(
                                text = "$completedCount of $totalCount medicines completed",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Badge(
                    containerColor = when (session.status) {
                        com.medicare.app.data.models.SessionStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                        com.medicare.app.data.models.SessionStatus.PARTIAL -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.errorContainer
                    }
                ) {
                    Text(
                        text = if (session.status == com.medicare.app.data.models.SessionStatus.COMPLETED) "DONE" else session.status.name,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(12.dp))

            doseEvents.forEach { event ->
                val med = medications[event.medicineId]
                val medName = med?.name ?: "Medicine"
                val isSubmitted = event.status == DoseStatus.TAKEN || event.status == DoseStatus.SKIPPED

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = medName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isSubmitted) "DONE FOR TODAY" else "Not taken yet",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isSubmitted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }

                    if (isSubmitted) {
                        val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
                        val subTimeStr = event.submissionTime?.let { timeFormatter.format(Date(it)) } ?: "Today"
                        val nextDoseText = med?.let { calculateNextDoseText(it) } ?: "Tomorrow"

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Status: DONE FOR TODAY", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                                Text("Today's dose completed at $subTimeStr", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Next dose: $nextDoseText", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { onVerifyDose(event.eventId, session.sessionId) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
                            ) {
                                Icon(Icons.Rounded.Camera, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Take medicine", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))
            if (session.status != com.medicare.app.data.models.SessionStatus.COMPLETED) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onSnooze) {
                        Icon(Icons.Rounded.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Snooze (5m)")
                    }
                }
            }
        }
    }
}

fun calculateNextDoseText(medication: com.medicare.app.data.models.Medication): String {
    val times = medication.times.sorted()
    if (times.isEmpty()) return "Tomorrow"
    val now = java.util.Calendar.getInstance()
    val currentHour = now.get(java.util.Calendar.HOUR_OF_DAY)
    val currentMin = now.get(java.util.Calendar.MINUTE)
    val currentTimeMinutes = currentHour * 60 + currentMin

    for (timeStr in times) {
        val parts = timeStr.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val timeMinutes = h * 60 + m
        if (timeMinutes > currentTimeMinutes) {
            val formattedTime = formatTime12Hour(h, m)
            return "Today at $formattedTime"
        }
    }
    val firstTime = times.first()
    val parts = firstTime.split(":")
    val h = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val formattedTime = formatTime12Hour(h, m)
    return "Tomorrow at $formattedTime"
}

private fun formatTime12Hour(hour: Int, minute: Int): String {
    val amPm = if (hour >= 12) "PM" else "AM"
    val h12 = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    val minStr = if (minute < 10) "0$minute" else "$minute"
    return "$h12:$minStr $amPm"
}
