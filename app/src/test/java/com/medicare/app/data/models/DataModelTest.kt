package com.medicare.app.data.models

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DataModelTest {

    @Test
    fun testMedicationSerialization() {
        val medication = Medication(
            id = "med-1",
            name = "Aspirin",
            dosage = 100.0,
            unit = "mg",
            times = listOf("08:00", "20:00"),
            frequency = Frequency.DAILY,
            active = true,
            stripRows = 2,
            stripColumns = 5,
            initialQuantity = 20
        )

        val jsonString = Json.encodeToString(medication)
        val decoded = Json.decodeFromString<Medication>(jsonString)

        assertEquals(medication.id, decoded.id)
        assertEquals(medication.name, decoded.name)
        assertEquals(medication.dosage, decoded.dosage, 0.001)
        assertEquals(medication.unit, decoded.unit)
        assertEquals(medication.times, decoded.times)
        assertEquals(medication.frequency, decoded.frequency)
        assertTrue(decoded.active)
        assertEquals(2, decoded.stripRows)
        assertEquals(5, decoded.stripColumns)
        assertEquals(20, decoded.initialQuantity)
    }

    @Test
    fun testDoseEventSerialization() {
        val event = DoseEvent(
            eventId = "evt-1",
            sessionId = "ses-1",
            medicineId = "med-1",
            scheduledDate = "2025-05-20",
            scheduledTime = "08:00",
            status = DoseStatus.SCHEDULED,
            userAcknowledged = false
        )

        val jsonString = Json.encodeToString(event)
        val decoded = Json.decodeFromString<DoseEvent>(jsonString)

        assertEquals(event.eventId, decoded.eventId)
        assertEquals(event.sessionId, decoded.sessionId)
        assertEquals(event.medicineId, decoded.medicineId)
        assertEquals(event.scheduledDate, decoded.scheduledDate)
        assertEquals(event.scheduledTime, decoded.scheduledTime)
        assertEquals(event.status, decoded.status)
    }

    @Test
    fun testMedicineSessionSerialization() {
        val session = MedicineSession(
            sessionId = "ses-1",
            scheduledDate = "2025-05-20",
            scheduledTime = "08:00",
            doseEventIds = listOf("evt-1", "evt-2"),
            status = SessionStatus.PENDING,
            submittedCount = 0,
            totalMedicineCount = 2
        )

        val jsonString = Json.encodeToString(session)
        val decoded = Json.decodeFromString<MedicineSession>(jsonString)

        assertEquals(session.sessionId, decoded.sessionId)
        assertEquals(session.scheduledDate, decoded.scheduledDate)
        assertEquals(session.doseEventIds, decoded.doseEventIds)
        assertEquals(session.status, decoded.status)
        assertEquals(session.totalMedicineCount, decoded.totalMedicineCount)
    }
}
