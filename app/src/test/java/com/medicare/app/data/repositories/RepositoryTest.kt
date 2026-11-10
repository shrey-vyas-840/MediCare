package com.medicare.app.data.repositories

import com.medicare.app.data.models.DoseStatus
import com.medicare.app.data.models.Frequency
import com.medicare.app.data.models.Medication
import com.medicare.app.data.storage.JsonStorage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class RepositoryTest {

    private lateinit var tempDir: File
    private lateinit var jsonStorage: JsonStorage
    private lateinit var medicationRepository: MedicationRepository
    private lateinit var sessionRepository: SessionRepository
    private lateinit var historyRepository: HistoryRepository

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("repo_test").toFile()
        jsonStorage = JsonStorage(tempDir)
        medicationRepository = MedicationRepository(jsonStorage)
        sessionRepository = SessionRepository(jsonStorage)
        historyRepository = HistoryRepository(jsonStorage)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testMedicationCrud() = runBlocking {
        val med = Medication(
            id = "med-1",
            name = "Paracetamol",
            dosage = 500.0,
            unit = "mg",
            times = listOf("09:00"),
            frequency = Frequency.DAILY
        )

        medicationRepository.saveMedication(med)
        val loaded = medicationRepository.getMedicationById("med-1")
        assertNotNull(loaded)
        assertEquals("Paracetamol", loaded?.name)

        medicationRepository.toggleActive("med-1")
        assertEquals(false, medicationRepository.getMedicationById("med-1")?.active)

        medicationRepository.deleteMedication("med-1")
        assertEquals(0, medicationRepository.getMedications().size)
    }

    @Test
    fun testSessionGenerationAndDoseUpdate() = runBlocking {
        val med = Medication(
            id = "med-1",
            name = "Ibuprofen",
            dosage = 400.0,
            unit = "mg",
            times = listOf("08:00"),
            frequency = Frequency.DAILY,
            active = true
        )

        medicationRepository.saveMedication(med)
        val date = "2025-05-20"

        sessionRepository.generateSessionsForDate(date, medicationRepository.getMedications())
        val sessions = sessionRepository.getSessionsForDate(date)
        assertEquals(1, sessions.size)

        val session = sessions[0]
        assertEquals("08:00", session.scheduledTime)
        assertEquals(1, session.totalMedicineCount)

        val events = sessionRepository.getDoseEventsForSession(session.sessionId)
        assertEquals(1, events.size)

        val event = events[0]
        assertEquals(DoseStatus.SCHEDULED, event.status)

        sessionRepository.updateDoseEventStatus(
            eventId = event.eventId,
            status = DoseStatus.TAKEN,
            quantityRecorded = 1
        )

        val updatedEvent = sessionRepository.getDoseEvent(event.eventId)
        assertNotNull(updatedEvent)
        assertEquals(DoseStatus.TAKEN, updatedEvent?.status)
        assertEquals(1, updatedEvent?.quantityRecorded)
    }

    @Test
    fun testHistoryAdherence() = runBlocking {
        val med = Medication(
            id = "med-1",
            name = "Vitamin C",
            dosage = 1000.0,
            unit = "mg",
            times = listOf("08:00"),
            frequency = Frequency.DAILY
        )
        medicationRepository.saveMedication(med)
        val date = "2025-05-20"
        sessionRepository.generateSessionsForDate(date, medicationRepository.getMedications())

        val events = sessionRepository.getDoseEventsForSession(sessionRepository.getSessionsForDate(date)[0].sessionId)
        sessionRepository.updateDoseEventStatus(events[0].eventId, DoseStatus.TAKEN)

        val adherence = historyRepository.calculateAdherenceRate("2025-05-19", "2025-05-21")
        assertEquals(1.0f, adherence, 0.001f)
    }

    @Test
    fun testInventoryTrackingAndReconciliation() = runBlocking {
        val med = Medication(
            id = "med-2",
            name = "Aspirin",
            dosage = 81.0,
            unit = "mg",
            times = listOf("08:00"),
            initialQuantity = 30,
            currentInventory = 30,
            lowStockThreshold = 5
        )
        medicationRepository.saveMedication(med)

        // Test decrement inventory
        medicationRepository.decrementInventory("med-2", 2)
        var loaded = medicationRepository.getMedicationById("med-2")
        assertEquals(28, loaded?.currentInventory)

        // Test manual reconciliation
        medicationRepository.reconcileInventory("med-2", 25)
        loaded = medicationRepository.getMedicationById("med-2")
        assertEquals(25, loaded?.currentInventory)
        assertTrue(loaded?.manualAdjustments?.isNotEmpty() == true)
    }
}
