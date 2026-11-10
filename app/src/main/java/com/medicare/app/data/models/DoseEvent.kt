package com.medicare.app.data.models

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class DoseEvent(
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
    val quantityRecorded: Int? = null
)
