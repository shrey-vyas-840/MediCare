package com.medicare.app.alarms

/** Pure time calculation shared by alarm scheduling and tests. */
object AlarmTiming {
    const val SNOOZE_MINUTES = 5
    const val MINUTE_MILLIS = 60_000L

    fun snoozeTriggerAt(nowMillis: Long, snoozeMinutes: Int = SNOOZE_MINUTES): Long {
        require(snoozeMinutes > 0)
        return nowMillis + snoozeMinutes * MINUTE_MILLIS
    }

    fun deliveryDelayMillis(expectedAtMillis: Long, deliveredAtMillis: Long): Long = deliveredAtMillis - expectedAtMillis
}
