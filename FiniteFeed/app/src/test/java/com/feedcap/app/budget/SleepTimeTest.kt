package com.feedcap.app.budget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class SleepTimeTest {

    private val zone = ZoneOffset.UTC
    private val night = SleepTime(enabled = true, startMinute = 23 * 60, endMinute = 7 * 60)

    private fun at(day: Int, hour: Int, minute: Int = 0) =
        LocalDateTime.of(2026, 11, day, hour, minute).toInstant(zone).toEpochMilli()

    @Test
    fun windowCrossingMidnight() {
        assertTrue(night.contains(23 * 60 + 30))
        assertTrue(night.contains(0))
        assertTrue(night.contains(6 * 60 + 59))
        assertFalse(night.contains(7 * 60))
        assertFalse(night.contains(12 * 60))
        assertFalse(night.contains(22 * 60 + 59))
    }

    @Test
    fun blockLastsUntilMorning() {
        val lateEvening = night.block(at(19, 23, 30), zone)
        assertEquals(BlockReason.SLEEP, lateEvening?.reason)
        assertEquals(at(20, 7), lateEvening?.until)

        assertEquals(at(19, 7), night.block(at(19, 1), zone)?.until)
        assertNull(night.block(at(19, 7), zone))
        assertNull(night.block(at(19, 15), zone))
    }

    @Test
    fun disabledNeverBlocks() {
        assertNull(night.copy(enabled = false).block(at(19, 2), zone))
    }

    @Test
    fun sameDayWindow() {
        val afternoon = SleepTime(enabled = true, startMinute = 13 * 60, endMinute = 15 * 60)
        assertTrue(afternoon.contains(14 * 60))
        assertFalse(afternoon.contains(15 * 60))
        assertEquals(at(19, 15), afternoon.block(at(19, 13), zone)?.until)
    }

    @Test
    fun shrinkingOrDisablingLoosens() {
        assertTrue(night.covers(night))
        assertTrue(night.copy(startMinute = 22 * 60).covers(night))
        assertFalse(night.copy(startMinute = 23 * 60 + 30).covers(night))
        assertFalse(night.copy(endMinute = 6 * 60).covers(night))
        assertFalse(night.copy(enabled = false).covers(night))
        assertTrue(night.covers(night.copy(enabled = false)))
    }
}
