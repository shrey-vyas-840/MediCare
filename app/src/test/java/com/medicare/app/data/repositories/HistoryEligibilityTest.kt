package com.medicare.app.data.repositories

import com.medicare.app.data.models.DoseEvent
import com.medicare.app.data.models.DoseStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryEligibilityTest {
    private val today = "2026-11-11"

    @Test fun historyRequiresTakenAcknowledgedAndSubmittedDose() {
        val confirmed = DoseEvent("taken", "s", "m", today, "08:00", submissionTime = 10L, status = DoseStatus.TAKEN, userAcknowledged = true)
        assertTrue(HistoryRepository.eligibleForHistory(confirmed, today))
        assertFalse(HistoryRepository.eligibleForHistory(confirmed.copy(userAcknowledged = false), today))
        assertFalse(HistoryRepository.eligibleForHistory(confirmed.copy(submissionTime = null), today))
        assertFalse(HistoryRepository.eligibleForHistory(confirmed.copy(status = DoseStatus.SCHEDULED), today))
    }

    @Test fun historyExcludesFutureSessionsWithoutChangingTheRecords() {
        val future = DoseEvent("future", "s", "m", "2026-11-12", "08:00", submissionTime = 10L, status = DoseStatus.TAKEN, userAcknowledged = true)
        assertFalse(HistoryRepository.eligibleForHistory(future, today))
        assertEquals(DoseStatus.TAKEN, future.status)
    }

    private fun assertEquals(expected: Any, actual: Any) = org.junit.Assert.assertEquals(expected, actual)
}
