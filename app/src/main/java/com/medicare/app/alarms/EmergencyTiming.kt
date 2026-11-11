package com.medicare.app.alarms

import com.medicare.app.data.models.DoseEvent
import com.medicare.app.data.models.DoseStatus
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object EmergencyTiming {
    fun checkAt(date: String, time: String, zone: ZoneId = ZoneId.systemDefault()): Long =
        LocalDateTime.parse("$date $time", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            .plusHours(1).atZone(zone).toInstant().toEpochMilli()

    fun isIncomplete(doses: List<DoseEvent>): Boolean = doses
        .filter { it.status != DoseStatus.CANCELLED }
        .let { active -> active.isNotEmpty() && active.any { it.status != DoseStatus.TAKEN || !it.userAcknowledged || it.submissionTime == null } }
}
