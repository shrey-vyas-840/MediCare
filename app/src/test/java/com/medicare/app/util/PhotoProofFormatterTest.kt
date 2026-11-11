package com.medicare.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

class PhotoProofFormatterTest {
    @Test fun proofContainsScheduledCaptureAndConfirmationTimestamps() {
        val lines = PhotoProofFormatter.lines("Asha", "Medicine", "2026-11-11", "08:00", 1_800_000_000_000L, 1_800_000_005_000L, TimeZone.getTimeZone("UTC"), Locale.US)
        assertEquals("MediCare Photo Proof", lines[0])
        assertEquals("Patient: Asha", lines[1])
        assertEquals("Scheduled at: 2026-11-11 08:00", lines[3])
        assertTrue(lines[4].startsWith("Captured at: "))
        assertTrue(lines[5].startsWith("Submitted at: "))
    }

    @Test fun pendingPreviewIsClearlyNotConfirmed() {
        val lines = PhotoProofFormatter.lines("", "Medicine", "2026-11-11", "08:00", 1_800_000_000_000L, null, TimeZone.getTimeZone("UTC"), Locale.US)
        assertEquals("Patient: Patient", lines[1])
        assertEquals("Awaiting patient confirmation", lines[5])
    }
}
