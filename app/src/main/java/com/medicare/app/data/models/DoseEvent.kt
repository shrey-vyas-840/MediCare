package com.medicare.app.data.models

import kotlinx.serialization.Serializable
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Serializable
@Entity(
    tableName = "dose_events",
    foreignKeys = [
        ForeignKey(entity = Medication::class, parentColumns = ["id"], childColumns = ["medicineId"], onDelete = ForeignKey.NO_ACTION),
        ForeignKey(entity = MedicineSession::class, parentColumns = ["sessionId"], childColumns = ["sessionId"], onDelete = ForeignKey.NO_ACTION)
    ],
    indices = [Index("medicineId"), Index("sessionId"), Index(value = ["medicineId", "scheduledDate", "scheduledTime"], unique = true)]
)
data class DoseEvent(
    @PrimaryKey
    val eventId: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val medicineId: String,
    val scheduledDate: String, // "YYYY-MM-DD"
    val scheduledTime: String, // "HH:mm"
    val captureTime: Long? = null,
    val submissionTime: Long? = null,
    val photoPath: String? = null,
    val status: DoseStatus = DoseStatus.SCHEDULED,
    val userAcknowledged: Boolean = false,
    val quantityRecorded: Int? = null,
    val nextReminderAt: Long? = null
)
