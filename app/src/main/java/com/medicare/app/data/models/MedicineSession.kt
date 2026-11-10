package com.medicare.app.data.models

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class MedicineSession(
    val sessionId: String = UUID.randomUUID().toString(),
    val scheduledDate: String, // "YYYY-MM-DD"
    val scheduledTime: String, // "HH:mm"
    val doseEventIds: List<String>,
    val status: SessionStatus = SessionStatus.PENDING,
    val submittedCount: Int = 0,
    val totalMedicineCount: Int = 0
)
