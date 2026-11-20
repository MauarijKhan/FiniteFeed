package com.feedcap.app.budget

import java.time.Instant
import java.time.ZoneId

/**
 * Hours when Reels and Shorts are off. Times are minutes after midnight, and the window may
 * cross midnight (23:00 to 07:00). Shared reels (DMs, links) still open, as they do for other limits.
 */
data class SleepTime(
    val enabled: Boolean = false,
    val startMinute: Int = 23 * 60,
    val endMinute: Int = 7 * 60,
) {
    fun contains(minuteOfDay: Int): Boolean = when {
        !enabled || startMinute == endMinute -> false
        startMinute < endMinute -> minuteOfDay in startMinute until endMinute
        else -> minuteOfDay >= startMinute || minuteOfDay < endMinute
    }

    /** True when every minute covered by [other] is covered by this too (used by Strict mode). */
    fun covers(other: SleepTime): Boolean = (0 until MINUTES_PER_DAY).all { !other.contains(it) || contains(it) }

    /** A block until the window ends, or null when [now] is outside sleep time. */
    fun block(now: Long, zone: ZoneId): Block? {
        val t = Instant.ofEpochMilli(now).atZone(zone)
        if (!contains(t.hour * 60 + t.minute)) return null
        var end = t.toLocalDate().atStartOfDay(zone).plusMinutes(endMinute.toLong())
        if (!end.isAfter(t)) end = end.plusDays(1)
        return Block(BlockReason.SLEEP, end.toInstant().toEpochMilli())
    }

    companion object {
        const val MINUTES_PER_DAY = 24 * 60
    }
}
