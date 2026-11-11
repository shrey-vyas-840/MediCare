package com.medicare.app.ui.inventory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.medicare.app.data.models.Medication
import com.medicare.app.ui.components.StripVisualization

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryDashboardScreen(
    viewModel: InventoryViewModel,
    onNavigateBack: () -> Unit
) {
    val medications by viewModel.medications.collectAsState()
    val lowStockMeds = medications.filter { it.currentInventory <= it.lowStockThreshold && it.active }

    var reconcilingMed by remember { mutableStateOf<Medication?>(null) }
    var reconciliationInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tablets Left Dashboard") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
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
            // Low-stock warning banner
            if (lowStockMeds.isNotEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Low Tablets Warning!",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = "${lowStockMeds.size} medication(s) are running low on tablets (<= ${lowStockMeds.first().lowStockThreshold} left).",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }

            item { Text("Scheduled medicines", style = MaterialTheme.typography.titleLarge) }
            items(medications.filter { it.frequency != com.medicare.app.data.models.Frequency.AS_NEEDED }) { med ->
                InventoryCard(
                    medication = med,
                    onUseOne = {},
                    usage = emptyList(),
                    onReconcileClick = {
                        reconcilingMed = med
                        reconciliationInput = med.currentInventory.toString()
                    }
                )
            }
            item { Text("As-needed medicines", style = MaterialTheme.typography.titleLarge) }
            items(medications.filter { it.frequency == com.medicare.app.data.models.Frequency.AS_NEEDED }) { med ->
                val usage by viewModel.observeUsage(med.id).collectAsState(initial = emptyList())
                InventoryCard(medication = med, onUseOne = { viewModel.markOneTabletUsed(med.id) }, usage = usage.map { it.usedAt }, onReconcileClick = {
                    reconcilingMed = med
                    reconciliationInput = med.currentInventory.toString()
                })
            }
        }
    }

    // Reconciliation Dialog
    if (reconcilingMed != null) {
        val med = reconcilingMed!!
        AlertDialog(
            onDismissRequest = { reconcilingMed = null },
            title = { Text("Set Tablets Left: ${med.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter the actual number of tablets left in the strip right now:")
                    OutlinedTextField(
                        value = reconciliationInput,
                        onValueChange = { reconciliationInput = it },
                        label = { Text("Tablets left right now") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newCount = reconciliationInput.toIntOrNull() ?: med.currentInventory
                        viewModel.reconcileInventory(med.id, newCount)
                        reconcilingMed = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { reconcilingMed = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun InventoryCard(
    medication: Medication,
    onUseOne: () -> Unit,
    usage: List<Long>,
    onReconcileClick: () -> Unit
) {
    val isLowStock = medication.currentInventory <= medication.lowStockThreshold && medication.active

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isLowStock) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = medication.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "How many tablets: ${medication.dosage} ${medication.unit} • Status: ${if (medication.active) "Active" else "Inactive"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = onReconcileClick) {
                    Text("Set remaining tablets")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (medication.frequency == com.medicare.app.data.models.Frequency.AS_NEEDED) {
                Text("${medication.currentInventory} tablets available", style = MaterialTheme.typography.titleMedium)
                Button(onClick = onUseOne, enabled = medication.active && medication.currentInventory > 0, modifier = Modifier.fillMaxWidth()) {
                    Text("Mark 1 Tablet Used")
                }
                if (usage.isNotEmpty()) Text("${usage.size} recorded use(s) · latest ${java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault()).format(java.util.Date(usage.first()))}", style = MaterialTheme.typography.bodySmall)
            } else StripVisualization(
                rows = medication.stripRows,
                columns = medication.stripColumns,
                initialQuantity = medication.initialQuantity,
                currentInventory = medication.currentInventory,
                manualAdjustments = medication.manualAdjustments
            )
        }
    }
}
