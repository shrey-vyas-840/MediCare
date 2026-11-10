package com.medicare.app.engine

import com.medicare.app.data.models.MedicineSession
import com.medicare.app.data.models.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionGroupingEngineTest {

    @Test
    fun testGroupSessionsByTimeSlot() {
        val engine = SessionGroupingEngine()
        val sessions = listOf(
            MedicineSession(sessionId = "1", scheduledDate = "2025-05-20", scheduledTime = "08:00", doseEventIds = emptyList(), status = SessionStatus.PENDING),
            MedicineSession(sessionId = "2", scheduledDate = "2025-05-20", scheduledTime = "14:00", doseEventIds = emptyList(), status = SessionStatus.PENDING),
            MedicineSession(sessionId = "3", scheduledDate = "2025-05-20", scheduledTime = "19:00", doseEventIds = emptyList(), status = SessionStatus.PENDING),
            MedicineSession(sessionId = "4", scheduledDate = "2025-05-20", scheduledTime = "23:00", doseEventIds = emptyList(), status = SessionStatus.PENDING)
        )

        val grouped = engine.groupSessionsByTimeSlot(sessions)

        assertEquals(4, grouped.size)
        assertTrue(grouped.containsKey(TimeSlot.MORNING))
        assertTrue(grouped.containsKey(TimeSlot.AFTERNOON))
        assertTrue(grouped.containsKey(TimeSlot.EVENING))
        assertTrue(grouped.containsKey(TimeSlot.NIGHT))

        assertEquals("08:00", grouped[TimeSlot.MORNING]?.get(0)?.scheduledTime)
        assertEquals("14:00", grouped[TimeSlot.AFTERNOON]?.get(0)?.scheduledTime)
        assertEquals("19:00", grouped[TimeSlot.EVENING]?.get(0)?.scheduledTime)
        assertEquals("23:00", grouped[TimeSlot.NIGHT]?.get(0)?.scheduledTime)
    }
}
