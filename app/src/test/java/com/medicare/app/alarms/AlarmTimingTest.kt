package com.medicare.app.alarms

import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmTimingTest {
    @Test fun fiveMinuteSnoozeUsesExactlyFiveMinutes() {
        val before = 1_800_000_000_000L
        assertEquals(before + 300_000L, AlarmTiming.snoozeTriggerAt(before))
    }

    @Test fun consecutiveSnoozesRebaseOnEachTapTimestamp() {
        val firstTap = 1_800_000_000_000L
        val firstTrigger = AlarmTiming.snoozeTriggerAt(firstTap)
        val secondTap = firstTrigger + 20_000L
        val secondTrigger = AlarmTiming.snoozeTriggerAt(secondTap)
        assertEquals(300_000L, firstTrigger - firstTap)
        assertEquals(300_000L, secondTrigger - secondTap)
        assertEquals(firstTrigger + 320_000L, secondTrigger)
    }

    @Test fun deliveryDelayIsMeasuredAgainstRequestedTrigger() {
        assertEquals(2_350L, AlarmTiming.deliveryDelayMillis(1_800_000_300_000L, 1_800_000_302_350L))
    }
}
