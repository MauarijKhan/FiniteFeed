package com.feedcap.app.insights

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class InsightsTest {

    private val ig = "com.instagram.android"
    private val yt = "com.google.android.youtube"
    private val today = LocalDate.of(2026, 11, 20)

    @Test
    fun recordsPerAppPerDay() {
        var h = emptyMap<String, Map<String, DayStats>>()
        h = Insights.record(h, today, ig) { it.copy(videos = it.videos + 1) }
        h = Insights.record(h, today, ig) { it.copy(videos = it.videos + 1) }
        h = Insights.record(h, today, yt) { it.copy(usedMs = it.usedMs + 60_000) }
        assertEquals(2, Insights.day(h, today, ig).videos)
        assertEquals(0, Insights.day(h, today, yt).videos)
        assertEquals(1, Insights.day(h, today, yt).minutes)
    }

    @Test
    fun dropsOldDays() {
        var h = Insights.record(emptyMap(), today.minusDays(40), ig) { it.copy(videos = 5) }
        h = Insights.record(h, today, ig) { it.copy(videos = 1) }
        assertFalse(h.containsKey(today.minusDays(40).toString()))
        assertTrue(h.containsKey(today.toString()))
    }

    @Test
    fun weekTotalsAndAverageIgnoreEmptyDays() {
        var h = emptyMap<String, Map<String, DayStats>>()
        h = Insights.record(h, today, ig) { it.copy(usedMs = 10 * 60_000L) }
        h = Insights.record(h, today.minusDays(1), yt) { it.copy(usedMs = 20 * 60_000L) }
        val week = Insights.week(today)
        assertEquals(7, week.size)
        assertEquals(today, week.last())
        assertEquals(30L, Insights.total(h, week, listOf(ig, yt)).minutes)
        // Two days with data, 30 minutes: 15 a day, not 30/7.
        assertEquals(15L, Insights.dailyAverageMinutes(h, week, listOf(ig, yt)))
        assertEquals(10L, Insights.dailyAverageMinutes(h, week, listOf(ig)))
    }
}
