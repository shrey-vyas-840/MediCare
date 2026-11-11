package com.medicare.app.ui.history

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.medicare.app.data.models.DoseEvent
import com.medicare.app.data.models.DoseStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onNavigateBack: () -> Unit
) {
    val doseEvents by viewModel.doseEvents.collectAsState()
    val medications by viewModel.medications.collectAsState()
    val adherenceRate = remember(doseEvents) { viewModel.calculate60DayAdherence() }

    var selectedPhotoPath by remember { mutableStateOf<String?>(null) }
    var selectedPhotoEvent by remember { mutableStateOf<DoseEvent?>(null) }

    val medMap = remember(medications) { medications.associateBy { it.id } }
    val groupedEvents = remember(doseEvents) {
        doseEvents.sortedByDescending { it.scheduledDate + it.scheduledTime }
            .groupBy { it.scheduledDate }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("60-Day Adherence History") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Adherence Summary Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "60-Day Adherence Rate",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${(adherenceRate * 100).toInt()}%",
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Calculated over the past 60 days of recorded doses",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            if (groupedEvents.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No dose history records found.", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            } else {
                groupedEvents.forEach { (dateStr, events) ->
                    item {
                        Text(
                            text = dateStr,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    items(events) { event ->
                        val med = medMap[event.medicineId]
                        HistoryEventCard(
                            event = event,
                            medicationName = med?.name ?: "Medication",
                            onPhotoClick = { path ->
                                selectedPhotoPath = path
                                selectedPhotoEvent = event
                            }
                        )
                    }
                }
            }
        }
    }

    // PhotoViewer Dialog
    if (selectedPhotoPath != null) {
        PhotoViewerDialog(
            photoPath = selectedPhotoPath!!,
            event = selectedPhotoEvent,
            medicationName = selectedPhotoEvent?.let { medMap[it.medicineId]?.name } ?: "Medication",
            onDismiss = {
                selectedPhotoPath = null
                selectedPhotoEvent = null
            }
        )
    }
}

@Composable
fun HistoryEventCard(
    event: DoseEvent,
    medicationName: String,
    onPhotoClick: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = medicationName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Scheduled: ${event.scheduledTime} • Status: ${event.status.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (event.submissionTime != null) {
                    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.ROOT) }
                    val timeFormatted = timeFormatter.format(Date(event.submissionTime))
                    Text(
                        text = "Submitted at: $timeFormatted",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (!event.photoPath.isNullOrBlank()) {
                IconButton(onClick = { onPhotoClick(event.photoPath) }) {
                    Icon(
                        Icons.Rounded.Image,
                        contentDescription = "View Photo",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                Badge(
                    containerColor = when (event.status) {
                        DoseStatus.TAKEN -> MaterialTheme.colorScheme.primary
                        DoseStatus.SKIPPED -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.error
                    }
                ) {
                    Text(event.status.name, modifier = Modifier.padding(6.dp))
                }
            }
        }
    }
}

@Composable
fun PhotoViewerDialog(
    photoPath: String,
    event: DoseEvent?,
    medicationName: String,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val bitmap = remember(photoPath) {
        try {
            context.contentResolver.openInputStream(android.net.Uri.parse(photoPath))?.use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream)
            } ?: android.graphics.BitmapFactory.decodeFile(photoPath)
        } catch (_: Exception) {
            null
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "$medicationName Verification Photo",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Medicine Verification Photo",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Photo not found or corrupted", color = MaterialTheme.colorScheme.error)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                if (event != null) {
                    Text(
                        text = "Date: ${event.scheduledDate} | Time: ${event.scheduledTime}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    }
}
