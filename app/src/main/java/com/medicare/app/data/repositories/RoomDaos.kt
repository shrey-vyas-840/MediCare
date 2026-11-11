package com.medicare.app.data.repositories

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.medicare.app.data.models.DoseEvent
import com.medicare.app.data.models.DoseStatus
import com.medicare.app.data.models.Medication
import com.medicare.app.data.models.MedicineSession
import com.medicare.app.data.models.EmergencyAlert
import com.medicare.app.data.models.AsNeededUsage
import com.medicare.app.data.models.SessionStatus
import kotlinx.coroutines.flow.Flow

@Dao interface MedicationDao {
    @Query("SELECT * FROM medicines ORDER BY name COLLATE NOCASE") fun observeAll(): Flow<List<Medication>>
    @Query("SELECT * FROM medicines ORDER BY name COLLATE NOCASE") suspend fun all(): List<Medication>
    @Query("SELECT * FROM medicines WHERE id = :id") suspend fun byId(id: String): Medication?
    @Upsert suspend fun upsert(medicine: Medication)
    @Upsert suspend fun upsertAll(medicines: List<Medication>)
    @Query("DELETE FROM medicines") suspend fun deleteAll()
    @Query("DELETE FROM medicines WHERE id = :id") suspend fun deleteById(id: String)
    @Query("UPDATE medicines SET active = 0 WHERE id = :id") suspend fun archive(id: String)
    @Query("UPDATE medicines SET currentInventory = MAX(0, currentInventory - :amount) WHERE id = :id") suspend fun decrement(id: String, amount: Int): Int
    @Query("UPDATE medicines SET currentInventory = :amount, manualAdjustments = :adjustments WHERE id = :id") suspend fun setInventory(id: String, amount: Int, adjustments: List<Long>): Int
}

@Dao interface SessionDao {
    @Query("SELECT * FROM medicine_sessions") suspend fun all(): List<MedicineSession>
    @Query("SELECT * FROM medicine_sessions WHERE scheduledDate = :date ORDER BY scheduledTime") fun observeDate(date: String): Flow<List<MedicineSession>>
    @Query("SELECT * FROM medicine_sessions WHERE scheduledDate = :date ORDER BY scheduledTime") suspend fun forDate(date: String): List<MedicineSession>
    @Query("SELECT * FROM medicine_sessions WHERE sessionId = :id") suspend fun byId(id: String): MedicineSession?
    @Query("SELECT * FROM medicine_sessions WHERE scheduledDate = :date AND scheduledTime = :time LIMIT 1") suspend fun byOccurrence(date: String, time: String): MedicineSession?
    @Upsert suspend fun upsert(session: MedicineSession)
    @Upsert suspend fun upsertAll(sessions: List<MedicineSession>)
    @Query("DELETE FROM medicine_sessions") suspend fun deleteAll()
    @Query("DELETE FROM medicine_sessions WHERE sessionId = :id") suspend fun deleteById(id: String)
    @Query("UPDATE medicine_sessions SET status = :status, submittedCount = :submitted, totalMedicineCount = :total WHERE sessionId = :id") suspend fun updateSummary(id: String, status: SessionStatus, submitted: Int, total: Int)
}

@Dao interface DoseDao {
    @Query("SELECT * FROM dose_events") suspend fun all(): List<DoseEvent>
    @Query("SELECT * FROM dose_events ORDER BY scheduledDate DESC, scheduledTime DESC") fun observeAll(): Flow<List<DoseEvent>>
    @Query("SELECT * FROM dose_events WHERE scheduledDate BETWEEN :start AND :end ORDER BY scheduledDate DESC, scheduledTime DESC") suspend fun between(start: String, end: String): List<DoseEvent>
    @Query("SELECT * FROM dose_events WHERE sessionId = :sessionId ORDER BY medicineId") suspend fun forSession(sessionId: String): List<DoseEvent>
    @Query("SELECT * FROM dose_events WHERE scheduledDate = :date") suspend fun forDate(date: String): List<DoseEvent>
    @Query("SELECT * FROM dose_events WHERE sessionId = :sessionId ORDER BY medicineId") fun observeSession(sessionId: String): Flow<List<DoseEvent>>
    @Query("SELECT * FROM dose_events WHERE eventId = :id") suspend fun byId(id: String): DoseEvent?
    @Query("SELECT * FROM dose_events WHERE medicineId = :medicineId AND scheduledDate = :date AND scheduledTime = :time LIMIT 1") suspend fun occurrence(medicineId: String, date: String, time: String): DoseEvent?
    @Query("SELECT * FROM dose_events WHERE scheduledDate = :date AND scheduledTime = :time AND status IN ('SCHEDULED','PENDING_VERIFICATION') ORDER BY medicineId LIMIT 1") suspend fun nextPending(date: String, time: String): DoseEvent?
    @Query("SELECT * FROM dose_events WHERE status IN ('SCHEDULED','PENDING_VERIFICATION') AND nextReminderAt IS NOT NULL") suspend fun pendingRetries(): List<DoseEvent>
    @Query("SELECT * FROM dose_events WHERE medicineId = :medicineId AND status IN ('SCHEDULED','PENDING_VERIFICATION')") suspend fun pendingForMedicine(medicineId: String): List<DoseEvent>
    @Upsert suspend fun upsert(event: DoseEvent)
    @Upsert suspend fun upsertAll(events: List<DoseEvent>)
    @Query("DELETE FROM dose_events") suspend fun deleteAll()
    @Query("DELETE FROM dose_events WHERE eventId = :id") suspend fun deleteById(id: String)
    @Query("UPDATE dose_events SET photoPath = :uri, captureTime = :capturedAt, status = 'PENDING_VERIFICATION' WHERE eventId = :id AND status IN ('SCHEDULED','PENDING_VERIFICATION')") suspend fun savePhoto(id: String, uri: String, capturedAt: Long): Int
    @Query("UPDATE dose_events SET status = 'TAKEN', submissionTime = :confirmedAt, quantityRecorded = :quantity, userAcknowledged = 1, nextReminderAt = NULL WHERE eventId = :id AND status IN ('SCHEDULED','PENDING_VERIFICATION') AND photoPath IS NOT NULL") suspend fun complete(id: String, confirmedAt: Long, quantity: Int): Int
    @Query("UPDATE dose_events SET status = 'SCHEDULED', nextReminderAt = :retryAt WHERE eventId = :id AND status IN ('SCHEDULED','PENDING_VERIFICATION')") suspend fun retry(id: String, retryAt: Long): Int
    @Query("UPDATE dose_events SET nextReminderAt = NULL WHERE eventId = :id AND status IN ('SCHEDULED','PENDING_VERIFICATION')") suspend fun clearRetry(id: String): Int
    @Query("UPDATE dose_events SET status = 'CANCELLED', nextReminderAt = NULL WHERE eventId = :id AND status IN ('SCHEDULED','PENDING_VERIFICATION')") suspend fun cancelPending(id: String): Int
    @Query("UPDATE dose_events SET status = 'SCHEDULED', nextReminderAt = NULL WHERE eventId = :id AND status = 'CANCELLED'") suspend fun reactivate(id: String): Int
    @Query("UPDATE dose_events SET status = :status, quantityRecorded = :quantity, userAcknowledged = :acknowledged WHERE eventId = :id AND status IN ('SCHEDULED','PENDING_VERIFICATION')") suspend fun setStatus(id: String, status: DoseStatus, quantity: Int?, acknowledged: Boolean): Int
    @Query("UPDATE medicine_sessions SET status = 'PENDING' WHERE sessionId = :sessionId") suspend fun resetSession(sessionId: String)
}

@Dao interface EmergencyAlertDao {
    @Query("SELECT * FROM emergency_alerts ORDER BY checkedAt DESC") fun observeAll(): Flow<List<EmergencyAlert>>
    @Query("SELECT * FROM emergency_alerts WHERE sessionId = :sessionId") suspend fun forSession(sessionId: String): List<EmergencyAlert>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertIfNew(alert: EmergencyAlert): Long
    @Query("UPDATE emergency_alerts SET outcome = :outcome, sentAt = :sentAt WHERE id = :id") suspend fun updateOutcome(id: String, outcome: String, sentAt: Long?)
    @Query("DELETE FROM emergency_alerts") suspend fun deleteAll()
}

@Dao interface AsNeededUsageDao {
    @Query("SELECT * FROM as_needed_usage ORDER BY usedAt DESC") suspend fun all(): List<AsNeededUsage>
    @Query("SELECT * FROM as_needed_usage WHERE medicineId = :medicineId ORDER BY usedAt DESC") fun observeMedicine(medicineId: String): Flow<List<AsNeededUsage>>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(usage: AsNeededUsage)
    @Query("DELETE FROM as_needed_usage") suspend fun deleteAll()
}
