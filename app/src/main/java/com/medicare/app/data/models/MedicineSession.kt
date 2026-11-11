package com.medicare.app.data.models

import kotlinx.serialization.Serializable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Serializable
@Entity(tableName = "medicine_sessions", indices = [Index(value = ["scheduledDate", "scheduledTime"], unique = true)])
data class MedicineSession(
    @PrimaryKey
    val sessionId: String = UUID.randomUUID().toString(),
    val scheduledDate: String, // "YYYY-MM-DD"
    val scheduledTime: String, // "HH:mm"
    val doseEventIds: List<String>,
    val status: SessionStatus = SessionStatus.PENDING,
    val submittedCount: Int = 0,
    val totalMedicineCount: Int = 0
)
