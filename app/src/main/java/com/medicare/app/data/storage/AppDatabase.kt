package com.medicare.app.data.storage

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.withTransaction
import com.medicare.app.data.models.DoseEvent
import com.medicare.app.data.models.DoseStatus
import com.medicare.app.data.models.Frequency
import com.medicare.app.data.models.Medication
import com.medicare.app.data.models.MedicineSession
import com.medicare.app.data.models.SessionStatus
import com.medicare.app.data.repositories.DoseDao
import com.medicare.app.data.repositories.MedicationDao
import com.medicare.app.data.repositories.SessionDao
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class RoomConverters {
    private val json = Json
    @TypeConverter fun encodeStrings(value: List<String>): String = json.encodeToString(value)
    @TypeConverter fun decodeStrings(value: String): List<String> = runCatching { json.decodeFromString<List<String>>(value) }.getOrDefault(emptyList())
    @TypeConverter fun frequency(value: Frequency): String = value.name
    @TypeConverter fun frequency(value: String): Frequency = runCatching { Frequency.valueOf(value) }.getOrDefault(Frequency.DAILY)
    @TypeConverter fun doseStatus(value: DoseStatus): String = value.name
    @TypeConverter fun doseStatus(value: String): DoseStatus = runCatching { DoseStatus.valueOf(value) }.getOrDefault(DoseStatus.SCHEDULED)
    @TypeConverter fun sessionStatus(value: SessionStatus): String = value.name
    @TypeConverter fun sessionStatus(value: String): SessionStatus = runCatching { SessionStatus.valueOf(value) }.getOrDefault(SessionStatus.PENDING)
    @TypeConverter @JvmName("encodeLongList") fun encodeLongs(value: List<Long>): String = value.joinToString(",")
    @TypeConverter @JvmName("decodeLongList") fun decodeLongs(value: String): List<Long> = value.split(',').mapNotNull { it.toLongOrNull() }
}

@Database(entities = [Medication::class, MedicineSession::class, DoseEvent::class], version = 1, exportSchema = true)
@TypeConverters(RoomConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun medications(): MedicationDao
    abstract fun sessions(): SessionDao
    abstract fun doses(): DoseDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "medicare.db")
                .build().also { instance = it }
        }
    }
}

/** Imports legacy files once, preserving the source files and every original photo URI. */
class LegacyDataImporter(private val db: AppDatabase, private val filesDir: File) {
    companion object { private const val MARKER = "room_import_v1.complete" }

    fun importIfNeeded() = runBlocking {
        if (File(filesDir, MARKER).exists()) return@runBlocking
        val storage = JsonStorage(filesDir)
        val medsFile = File(filesDir, "medications.json")
        val sessionsFile = File(filesDir, "medicine_sessions.json")
        val dosesFile = File(filesDir, "dose_events.json")
        listOf(medsFile, sessionsFile, dosesFile).filter { it.exists() }.forEach { source ->
            val backup = File(filesDir, "${source.name}.pre-room.bak")
            if (!backup.exists()) Files.copy(source.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES)
        }

        val meds = storage.load<List<Medication>>("medications.json", emptyList())
        val oldSessions = storage.load<List<MedicineSession>>("medicine_sessions.json", emptyList())
        val oldDoses = storage.load<List<DoseEvent>>("dose_events.json", emptyList())
        val validMeds = meds.distinctBy { it.id }
        val medIds = validMeds.map { it.id }.toSet()
        val validDosesById = oldDoses.filter { it.medicineId in medIds }.distinctBy { it.eventId }
        val validDoses = validDosesById.groupBy { Triple(it.medicineId, it.scheduledDate, it.scheduledTime) }
            .values.map { duplicates ->
                duplicates.maxWithOrNull(
                    compareBy<DoseEvent> { if (it.status == DoseStatus.TAKEN) 4 else 0 }
                        .thenBy { if (!it.photoPath.isNullOrBlank()) 2 else 0 }
                        .thenBy { it.submissionTime ?: 0L }
                        .thenBy { it.captureTime ?: 0L }
                )!!
            }.sortedWith(compareBy<DoseEvent> { it.scheduledDate }.thenBy { it.scheduledTime }.thenBy { it.medicineId })
        val skippedMedicines = meds.size - validMeds.size
        val skippedDoses = oldDoses.size - validDoses.size
        val mergedOccurrences = validDosesById.size - validDoses.size
        if (skippedMedicines > 0 || skippedDoses > 0 || mergedOccurrences > 0) {
            Log.e("MediCareMigration", "Legacy records need manual review: duplicate medicines=$skippedMedicines, doses merged by scheduled occurrence=$mergedOccurrences, doses without medicines or duplicate dose IDs=${oldDoses.size - validDosesById.size}. All original JSON and pre-room backups remain preserved.")
        }

        val sessionsByDateTime = oldSessions.distinctBy { it.scheduledDate to it.scheduledTime }
            .associateBy { it.scheduledDate to it.scheduledTime }.toMutableMap()
        val normalizedDoses = validDoses.map { dose ->
            val key = dose.scheduledDate to dose.scheduledTime
            val session = sessionsByDateTime[key] ?: MedicineSession(
                sessionId = dose.sessionId,
                scheduledDate = dose.scheduledDate,
                scheduledTime = dose.scheduledTime,
                doseEventIds = emptyList()
            ).also { sessionsByDateTime[key] = it }
            dose.copy(sessionId = session.sessionId)
        }
        val sessions = sessionsByDateTime.values.map { session ->
            val events = normalizedDoses.filter { it.sessionId == session.sessionId }
            val done = events.count { it.status == DoseStatus.TAKEN || it.status == DoseStatus.SKIPPED }
            session.copy(
                doseEventIds = events.map { it.eventId },
                totalMedicineCount = events.size.coerceAtLeast(session.totalMedicineCount),
                submittedCount = done,
                status = when {
                    events.isEmpty() -> session.status
                    done == events.size -> SessionStatus.COMPLETED
                    done > 0 -> SessionStatus.PARTIAL
                    else -> SessionStatus.PENDING
                }
            )
        }

        db.withTransaction {
            db.medications().upsertAll(validMeds)
            db.sessions().upsertAll(sessions)
            db.doses().upsertAll(normalizedDoses)
            val importedMeds = db.medications().all().size
            val importedSessions = db.sessions().all().size
            val importedDoses = db.doses().all().size
            check(importedMeds == validMeds.size && importedSessions == sessions.size && importedDoses == normalizedDoses.size) {
                "Legacy import count verification failed: meds $importedMeds/${validMeds.size}, sessions $importedSessions/${sessions.size}, doses $importedDoses/${normalizedDoses.size}"
            }
        }
        val report = "Imported medicines=${validMeds.size}/${meds.size}; sessions=${sessions.size}/${oldSessions.size}; dose occurrences=${normalizedDoses.size}/${oldDoses.size}; merged duplicate occurrences=$mergedOccurrences; skipped invalid/duplicate IDs=${oldDoses.size - validDosesById.size}; duplicate medicines=$skippedMedicines"
        File(filesDir, "room_import_report.txt").writeText(report)
        Log.i("MediCareMigration", report)
        File(filesDir, MARKER).writeText(report)
    }
}
