package com.medicare.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.medicare.app.alarms.AlarmReceiver
import com.medicare.app.alarms.AlarmScheduler
import com.medicare.app.data.models.DoseEvent
import com.medicare.app.data.models.DoseStatus
import com.medicare.app.data.models.Frequency
import com.medicare.app.data.models.EmergencyAlert
import com.medicare.app.data.models.Medication
import com.medicare.app.data.models.MedicineSession
import com.medicare.app.data.models.SessionStatus
import com.medicare.app.data.repositories.SessionRepository
import com.medicare.app.data.storage.AppDatabase
import com.medicare.app.data.storage.JsonStorage
import com.medicare.app.data.storage.LegacyDataImporter
import com.medicare.app.data.storage.MediaStorePhotoStorage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RoomWorkflowInstrumentedTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    }

    @After fun tearDown() { db.close() }

    @Test fun generationKeepsDoseIdsAndCreatesASeparateNextDayOccurrence() = runBlocking {
        val medicine = Medication("stable-med", "Test medicine", 1.0, "tablet", listOf("08:00"), Frequency.DAILY, initialQuantity = 14, currentInventory = 14)
        db.medications().upsert(medicine)
        val repo = SessionRepository(db)
        repo.generateSessionsForDate("2026-11-11", listOf(medicine))
        val firstSession = repo.getSessionsForDate("2026-11-11").single()
        val firstDose = repo.getDoseEventsForSession(firstSession.sessionId).single()
        repo.generateSessionsForDate("2026-11-11", listOf(medicine))
        assertEquals(firstSession.sessionId, repo.getSessionsForDate("2026-11-11").single().sessionId)
        assertEquals(firstDose.eventId, repo.getDoseEventsForSession(firstSession.sessionId).single().eventId)
        repo.generateSessionsForDate("2026-11-12", listOf(medicine))
        val tomorrow = repo.getDoseEventsForSession(repo.getSessionsForDate("2026-11-12").single().sessionId).single()
        assertNotEquals(firstDose.eventId, tomorrow.eventId)
        assertEquals(DoseStatus.SCHEDULED, tomorrow.status)
    }

    @Test fun asNeededUseDeductsOneTabletAndCreatesNoScheduledDoseOrPhoto() = runBlocking {
        val medicine = Medication("prn-med", "As-needed", 1.0, "tablet", emptyList(), Frequency.AS_NEEDED, initialQuantity = 10, currentInventory = 10)
        db.medications().upsert(medicine)
        val repo = SessionRepository(db)
        repo.generateSessionsForDate("2026-11-11", listOf(medicine))
        assertTrue(repo.getSessionsForDate("2026-11-11").isEmpty())
        assertTrue(repo.recordAsNeededUse(medicine.id))
        assertFalse(repo.recordAsNeededUse(medicine.id, 10))
        assertEquals(9, db.medications().byId(medicine.id)?.currentInventory)
        assertEquals(1, db.asNeededUsage().all().size)
        assertTrue(db.doses().all().isEmpty())
    }

    @Test fun weeklyMedicineUsesStartWeekdayAndCustomLegacyImportMapsToDaily() = runBlocking {
        val zone = java.time.ZoneId.systemDefault()
        val monday = java.time.LocalDate.of(2026, 11, 9)
        val mondayStart = monday.atStartOfDay(zone).toInstant().toEpochMilli()
        val weekly = Medication("weekly-med", "Weekly", 1.0, "tablet", listOf("08:00"), Frequency.WEEKLY, startDate = mondayStart, initialQuantity = 8, currentInventory = 8)
        db.medications().upsert(weekly)
        val repo = SessionRepository(db)
        repo.generateSessionsForDate("2026-11-09", listOf(weekly))
        assertEquals(1, repo.getSessionsForDate("2026-11-09").size)
        repo.generateSessionsForDate("2026-11-10", listOf(weekly))
        assertTrue(repo.getSessionsForDate("2026-11-10").isEmpty())

        val folder = File(context.cacheDir, "custom-frequency-${UUID.randomUUID()}").apply { mkdirs() }
        val legacyDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val legacy = weekly.copy(id = "legacy-custom", frequency = Frequency.CUSTOM)
            JsonStorage(folder).save("medications.json", listOf(legacy))
            LegacyDataImporter(legacyDb, folder).importIfNeeded()
            assertEquals(Frequency.DAILY, legacyDb.medications().byId(legacy.id)?.frequency)
        } finally { folder.deleteRecursively(); legacyDb.close() }
    }

    @Test fun emergencyAlertRowsAreIdempotentPerSessionAndRecipient() = runBlocking {
        val medicine = Medication("alert-med", "Scheduled", 1.0, "tablet", listOf("08:00"), initialQuantity = 10, currentInventory = 10)
        db.medications().upsert(medicine)
        val repo = SessionRepository(db)
        repo.generateSessionsForDate("2026-11-11", listOf(medicine))
        val session = repo.getSessionsForDate("2026-11-11").single()
        val alert = EmergencyAlert("session-contact", session.sessionId, "+10000000000", "Check in", 1_800_000_000_000L, null, "PERMISSION_BLOCKED")
        assertNotEquals(-1L, db.emergencyAlerts().insertIfNew(alert))
        assertEquals(-1L, db.emergencyAlerts().insertIfNew(alert.copy(outcome = "SENT")))
        assertEquals("PERMISSION_BLOCKED", db.emergencyAlerts().forSession(session.sessionId).single().outcome)
    }

    @Test fun completionIsAtomicIdempotentAndVisibleToEveryDatabaseObserver() = runBlocking {
        val medicine = Medication("atomic-med", "Test medicine", 1.0, "tablet", listOf("08:00"), initialQuantity = 12, currentInventory = 12)
        db.medications().upsert(medicine)
        val repo = SessionRepository(db)
        repo.generateSessionsForDate("2026-11-11", listOf(medicine))
        val session = repo.getSessionsForDate("2026-11-11").single()
        val dose = repo.getDoseEventsForSession(session.sessionId).single()
        val photo = "content://media/external/images/media/12345"
        val capturedAt = 1_800_000_000_000L
        assertTrue(repo.saveCapturedPhoto(dose.eventId, photo, capturedAt))
        assertTrue(repo.completeDose(dose.eventId, photo, capturedAt, 2))
        assertFalse(repo.completeDose(dose.eventId, photo, capturedAt, 2))
        assertEquals(10, db.medications().byId(medicine.id)?.currentInventory)
        assertEquals(DoseStatus.TAKEN, db.doses().byId(dose.eventId)?.status)
        assertEquals(SessionStatus.COMPLETED, db.sessions().byId(session.sessionId)?.status)
        assertEquals(photo, db.doses().byId(dose.eventId)?.photoPath)
        assertEquals(1, db.doses().observeAll().first().count { it.status == DoseStatus.TAKEN })
    }

    @Test fun groupedSessionRetainsCompletedDoseAndRetriesOnlyPendingDose() = runBlocking {
        val a = Medication("group-a", "A", 1.0, "tablet", listOf("08:00"), initialQuantity = 10, currentInventory = 10)
        val b = Medication("group-b", "B", 1.0, "tablet", listOf("08:00"), initialQuantity = 10, currentInventory = 10)
        db.medications().upsertAll(listOf(a, b))
        val repo = SessionRepository(db)
        repo.generateSessionsForDate("2026-11-11", listOf(a, b))
        val session = repo.getSessionsForDate("2026-11-11").single()
        val events = repo.getDoseEventsForSession(session.sessionId)
        val first = events.first { it.medicineId == a.id }
        val second = events.first { it.medicineId == b.id }
        assertTrue(repo.saveCapturedPhoto(first.eventId, "content://photo/a", 1_800_000_000_001L))
        assertTrue(repo.completeDose(first.eventId, "content://photo/a", 1_800_000_000_001L, 1))
        assertEquals(second.eventId, repo.nextOutstanding(session.sessionId)?.eventId)
        assertTrue(repo.scheduleRetry(second.eventId, 1_800_000_000_500L))
        assertEquals(DoseStatus.TAKEN, db.doses().byId(first.eventId)?.status)
        assertEquals(DoseStatus.SCHEDULED, db.doses().byId(second.eventId)?.status)
        assertEquals(9, db.medications().byId(a.id)?.currentInventory)
        assertEquals(10, db.medications().byId(b.id)?.currentInventory)
        assertEquals(SessionStatus.PARTIAL, db.sessions().byId(session.sessionId)?.status)
    }

    @Test fun importerPreservesIdsStatusesTimestampsUrisAndIsIdempotent() = runBlocking {
        val folder = File(context.cacheDir, "room-import-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val medicine = Medication("legacy-med", "Legacy", 1.0, "tablet", listOf("08:00"), Frequency.DAILY, initialQuantity = 8, currentInventory = 5)
            val session = MedicineSession("legacy-session", "2026-11-10", "08:00", listOf("legacy-dose"), SessionStatus.COMPLETED, 1, 1)
            val dose = DoseEvent("legacy-dose", "legacy-session", "legacy-med", "2026-11-10", "08:00", 1_800_000_000_100L, 1_800_000_000_200L, "content://media/external/images/media/77", DoseStatus.TAKEN, true, 1)
            val storage = JsonStorage(folder)
            storage.save("medications.json", listOf(medicine))
            storage.save("medicine_sessions.json", listOf(session))
            storage.save("dose_events.json", listOf(dose))
            LegacyDataImporter(db, folder).importIfNeeded()
            LegacyDataImporter(db, folder).importIfNeeded()
            assertEquals(1, db.medications().all().size)
            assertEquals(1, db.sessions().all().size)
            assertEquals(1, db.doses().all().size)
            assertEquals(dose.eventId, db.doses().byId(dose.eventId)?.eventId)
            assertEquals(dose.photoPath, db.doses().byId(dose.eventId)?.photoPath)
            assertEquals(dose.captureTime, db.doses().byId(dose.eventId)?.captureTime)
            assertEquals(DoseStatus.TAKEN, db.doses().byId(dose.eventId)?.status)
            assertTrue(File(folder, "dose_events.json.pre-room.bak").exists())
        } finally { folder.deleteRecursively() }
    }

    @Test fun importerMergesDuplicateRowsForOneScheduledOccurrenceAndKeepsTheCompletedPhoto() = runBlocking {
        val folder = File(context.cacheDir, "room-duplicate-import-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val medicine = Medication("duplicate-med", "Legacy", 1.0, "tablet", listOf("08:00"), initialQuantity = 8, currentInventory = 7)
            val session = MedicineSession("duplicate-session", "2026-11-10", "08:00", listOf("old-pending", "old-taken"), SessionStatus.PARTIAL, 1, 2)
            val pending = DoseEvent("old-pending", "duplicate-session", "duplicate-med", "2026-11-10", "08:00")
            val taken = DoseEvent("old-taken", "duplicate-session", "duplicate-med", "2026-11-10", "08:00", 1_800_000_000_100L, 1_800_000_000_200L, "content://media/external/images/media/88", DoseStatus.TAKEN, true, 1)
            val storage = JsonStorage(folder)
            storage.save("medications.json", listOf(medicine))
            storage.save("medicine_sessions.json", listOf(session))
            storage.save("dose_events.json", listOf(pending, taken))
            LegacyDataImporter(db, folder).importIfNeeded()
            val imported = db.doses().all().single()
            assertEquals(taken.eventId, imported.eventId)
            assertEquals(DoseStatus.TAKEN, imported.status)
            assertEquals(taken.photoPath, imported.photoPath)
            assertTrue(File(folder, "dose_events.json").exists())
            assertTrue(File(folder, "dose_events.json.pre-room.bak").exists())
        } finally { folder.deleteRecursively() }
    }

    @Test fun mediaStorePhotoUsesReadableContentUriAndUniqueNames() {
        val storage = MediaStorePhotoStorage(context)
        val bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
        val first = storage.saveBitmapToGallery(bitmap, "MediCare_Test_${UUID.randomUUID()}")
        val second = storage.saveBitmapToGallery(bitmap, "MediCare_Test_${UUID.randomUUID()}")
        try {
            assertNotNull(first.first)
            assertNotNull(second.first)
            assertNotEquals(first.second, second.second)
            val decoded = context.contentResolver.openInputStream(Uri.parse(first.first!!))!!.use { stream -> BitmapFactory.decodeStream(stream) }
            assertNotNull(decoded)
        } finally {
            first.first?.let { context.contentResolver.delete(Uri.parse(it), null, null) }
            second.first?.let { context.contentResolver.delete(Uri.parse(it), null, null) }
            bitmap.recycle()
        }
    }

    @Test fun alarmKindsHaveStableDistinctIdentitiesAndReschedulingLeavesOnlyOneKind() {
        val dose = DoseEvent("alarm-${UUID.randomUUID()}", "alarm-session", "alarm-med", "2027-01-01", "08:00")
        fun pending(kind: AlarmScheduler.AlarmKind) = run {
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                action = AlarmScheduler.ACTION_MEDICATION_ALARM
                data = Uri.parse("medicare://dose/${dose.eventId}/${kind.name.lowercase()}")
            }
            PendingIntent.getBroadcast(context, 31 * dose.eventId.hashCode() + kind.ordinal, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        }
        try {
            val first = System.currentTimeMillis() + 60 * 60_000L
            AlarmScheduler.scheduleDoseAlarm(context, dose, AlarmScheduler.AlarmKind.ORIGINAL, first)
            assertNotNull(pending(AlarmScheduler.AlarmKind.ORIGINAL))
            AlarmScheduler.scheduleDoseAlarm(context, dose, AlarmScheduler.AlarmKind.SNOOZE, first + 300_000L)
            assertNull(pending(AlarmScheduler.AlarmKind.ORIGINAL))
            assertNotNull(pending(AlarmScheduler.AlarmKind.SNOOZE))
            AlarmScheduler.scheduleDoseAlarm(context, dose.copy(nextReminderAt = first + 600_000L), AlarmScheduler.AlarmKind.SNOOZE, first + 600_000L)
            assertNotNull(pending(AlarmScheduler.AlarmKind.SNOOZE))
            AlarmScheduler.scheduleDoseAlarm(context, dose, AlarmScheduler.AlarmKind.RETRY, first + 900_000L)
            assertNull(pending(AlarmScheduler.AlarmKind.SNOOZE))
            assertNotNull(pending(AlarmScheduler.AlarmKind.RETRY))
        } finally {
            AlarmScheduler.cancelDoseAlarms(context, dose.eventId)
            AlarmScheduler.AlarmKind.entries.forEach { assertNull(pending(it)) }
        }
    }
}
