package com.medicare.app.alarms

import com.medicare.app.data.models.DoseEvent
import com.medicare.app.data.models.DoseStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class EmergencyTimingTest {
    @Test fun checkIsExactlyOneHourAfterSessionInTheSelectedZone() {
        val zone = ZoneId.of("Asia/Kolkata")
        val base = java.time.LocalDateTime.of(2026, 11, 11, 8, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(base + 60 * 60_000L, EmergencyTiming.checkAt("2026-11-11", "08:00", zone))
    }

    @Test fun onlyExplicitlyConfirmedTakenDosesCountAsComplete() {
        val taken = DoseEvent("d", "s", "m", "2026-11-11", "08:00", submissionTime = 10L, status = DoseStatus.TAKEN, userAcknowledged = true)
        assertFalse(EmergencyTiming.isIncomplete(listOf(taken)))
        assertTrue(EmergencyTiming.isIncomplete(listOf(taken.copy(userAcknowledged = false))))
        assertTrue(EmergencyTiming.isIncomplete(listOf(taken.copy(status = DoseStatus.SCHEDULED))))
        assertFalse(EmergencyTiming.isIncomplete(listOf(taken.copy(status = DoseStatus.CANCELLED))))
    }
}
