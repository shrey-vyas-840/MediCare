package com.medicare.app.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object PhotoProofFormatter {
    fun lines(
        patientName: String,
        medicineName: String,
        scheduledDate: String,
        scheduledTime: String,
        capturedAt: Long,
        submittedAt: Long?,
        timeZone: TimeZone = TimeZone.getDefault(),
        locale: Locale = Locale.getDefault()
    ): List<String> {
        val formatter = SimpleDateFormat("dd MMM yyyy, h:mm:ss a", locale).apply { this.timeZone = timeZone }
        val captured = formatter.format(Date(capturedAt))
        return buildList {
            add("MediCare Photo Proof")
            add("Patient: ${patientName.ifBlank { "Patient" }}")
            add("Medicine: $medicineName")
            add("Scheduled at: $scheduledDate $scheduledTime")
            add("Captured at: $captured")
            add(submittedAt?.let { "Submitted at: ${formatter.format(Date(it))}" } ?: "Awaiting patient confirmation")
        }
    }
}
