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
    val medications by viewModel.medications.collectAsState()
    val existingMed = medications.firstOrNull { it.id == medicationId }
    var initialized by remember(medicationId) { mutableStateOf(medicationId == null) }
    var name by remember(medicationId) { mutableStateOf("") }
    var dosage by remember(medicationId) { mutableStateOf("1.0") }
    var unit by remember(medicationId) { mutableStateOf("mg") }
    var timeInput by remember(medicationId) { mutableStateOf("") }
    var frequency by remember(medicationId) { mutableStateOf(Frequency.DAILY) }
    var initialQuantity by remember(medicationId) { mutableStateOf("14") }
    var currentInventory by remember(medicationId) { mutableStateOf("14") }
    var stripRows by remember(medicationId) { mutableStateOf("2") }
    var stripColumns by remember(medicationId) { mutableStateOf("7") }
    var validationError by remember(medicationId) { mutableStateOf<String?>(null) }

    LaunchedEffect(existingMed) {
        if (!initialized && existingMed != null) {
            name = existingMed.name
            dosage = existingMed.dosage.toString()
            unit = existingMed.unit
            timeInput = existingMed.times.joinToString(", ")
            frequency = existingMed.frequency
            initialQuantity = existingMed.initialQuantity.toString()
            currentInventory = existingMed.currentInventory.toString()
            stripRows = existingMed.stripRows.toString().let { if (it == "0") "2" else it }
            stripColumns = existingMed.stripColumns.toString().let { if (it == "0") "7" else it }
            initialized = true
        }
    }

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

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = stripRows,
                    onValueChange = { stripRows = it.filter(Char::isDigit) },
                    label = { Text("Strip rows") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = stripColumns,
                    onValueChange = { stripColumns = it.filter(Char::isDigit) },
                    label = { Text("Strip columns") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    val timesList = timeInput.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    val fullQty = initialQuantity.toIntOrNull()
                    val leftQty = currentInventory.toIntOrNull()
                    val rows = stripRows.toIntOrNull()
                    val columns = stripColumns.toIntOrNull()
                    val capacity = if (rows != null && columns != null) rows * columns else 0
                    validationError = when {
                        name.isBlank() -> "Enter the medicine name."
                        timeInput.split(",").any { it.isNotBlank() && !it.trim().matches(Regex("([01]\\d|2[0-3]):[0-5]\\d")) } -> "Enter each reminder time as HH:mm."
                        rows !in 1..20 || columns !in 1..20 -> "Strip rows and columns must each be between 1 and 20."
                        fullQty == null || fullQty < 1 || fullQty > capacity -> "Full strip quantity must fit within $capacity positions."
                        leftQty == null || leftQty !in 0..fullQty -> "Tablets left must be between zero and the full strip quantity."
                        else -> null
                    }
                    if (validationError != null) return@Button
                    val medication = Medication(
                        id = existingMed?.id ?: UUID.randomUUID().toString(),
                        name = name.ifBlank { "Unknown Medicine" },
                        dosage = dosage.toDoubleOrNull() ?: 1.0,
                        unit = unit,
                        times = timesList.ifEmpty { listOf("08:00") },
                        frequency = frequency,
                        active = existingMed?.active ?: true,
                        stripRows = rows!!,
                        stripColumns = columns!!,
                        initialQuantity = fullQty!!,
                        currentInventory = leftQty!!
                    )
                    viewModel.saveMedication(medication)
                    onNavigateBack()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Medicine")
            }
            validationError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
