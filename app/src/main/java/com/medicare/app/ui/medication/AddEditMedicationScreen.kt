package com.medicare.app.ui.medication

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.medicare.app.data.models.Frequency
import com.medicare.app.data.models.Medication
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditMedicationScreen(
    medicationId: String? = null,
    viewModel: MedicationViewModel,
    onNavigateBack: () -> Unit
) {
    val existingMed = medicationId?.let { viewModel.getMedicationById(it) }

    var name by remember { mutableStateOf(existingMed?.name ?: "") }
    var dosage by remember { mutableStateOf(existingMed?.dosage?.toString() ?: "1.0") }
    var unit by remember { mutableStateOf(existingMed?.unit ?: "mg") }
    var timeInput by remember { mutableStateOf(existingMed?.times?.joinToString(", ") ?: "") }
    var frequency by remember { mutableStateOf(existingMed?.frequency ?: Frequency.DAILY) }
    var initialQuantity by remember { mutableStateOf(existingMed?.initialQuantity?.toString() ?: "14") }
    var currentInventory by remember { mutableStateOf(existingMed?.currentInventory?.toString() ?: "14") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (medicationId == null) "Add Medicine" else "Edit Medicine") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
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
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Medicine Name") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = dosage,
                    onValueChange = { dosage = it },
                    label = { Text("How many tablets") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    label = { Text("Unit (mg, ml, etc.)") },
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = timeInput,
                onValueChange = { timeInput = it },
                label = { Text("Times (Comma separated, e.g. 08:00, 20:00)") },
                modifier = Modifier.fillMaxWidth()
            )

            Text("Frequency", style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Frequency.entries.forEach { freq ->
                    FilterChip(
                        selected = frequency == freq,
                        onClick = { frequency = freq },
                        label = { Text(freq.name) }
                    )
                }
            }

            HorizontalDivider()

            Text("Pill Strip Setup", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = initialQuantity,
                onValueChange = { initialQuantity = it },
                label = { Text("How many tablets are in one full strip? (e.g. 14)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = currentInventory,
                onValueChange = { currentInventory = it },
                label = { Text("How many tablets are left in the strip right now? (e.g. 10)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    val timesList = timeInput.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    val fullQty = initialQuantity.toIntOrNull() ?: 14
                    val leftQty = currentInventory.toIntOrNull() ?: fullQty
                    val medication = Medication(
                        id = existingMed?.id ?: UUID.randomUUID().toString(),
                        name = name.ifBlank { "Unknown Medicine" },
                        dosage = dosage.toDoubleOrNull() ?: 1.0,
                        unit = unit,
                        times = timesList.ifEmpty { listOf("08:00") },
                        frequency = frequency,
                        active = existingMed?.active ?: true,
                        stripRows = 2,
                        stripColumns = 7,
                        initialQuantity = fullQty,
                        currentInventory = leftQty
                    )
                    viewModel.saveMedication(medication)
                    onNavigateBack()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Medicine")
            }
        }
    }
}
