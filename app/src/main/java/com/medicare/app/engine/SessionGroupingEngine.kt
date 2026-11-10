package com.medicare.app.engine

import com.medicare.app.data.models.MedicineSession

enum class TimeSlot(val displayName: String, val startHour: Int, val endHour: Int) {
    MORNING("Morning", 5, 11),
    AFTERNOON("Afternoon", 12, 16),
    EVENING("Evening", 17, 21),
    NIGHT("Night", 22, 4);

    companion object {
        fun fromTimeString(timeStr: String): TimeSlot {
            val parts = timeStr.split(":")
            val hour = parts.getOrNull(0)?.toIntOrNull() ?: 0
            return when (hour) {
                in 5..11 -> MORNING
                in 12..16 -> AFTERNOON
                in 17..21 -> EVENING
                else -> NIGHT
            }
        }
    }
}

class SessionGroupingEngine {
    fun groupSessionsByTimeSlot(sessions: List<MedicineSession>): Map<TimeSlot, List<MedicineSession>> {
        val grouped = mutableMapOf<TimeSlot, MutableList<MedicineSession>>()
        for (slot in TimeSlot.entries) {
            grouped[slot] = mutableListOf()
        }

        for (session in sessions) {
            val slot = TimeSlot.fromTimeString(session.scheduledTime)
            grouped[slot]?.add(session)
        }

        // Sort sessions within each time slot by scheduledTime
        for ((_, slotSessions) in grouped) {
            slotSessions.sortBy { it.scheduledTime }
        }

        return grouped.filterValues { it.isNotEmpty() }
    }
}
