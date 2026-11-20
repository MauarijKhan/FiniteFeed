package com.feedcap.app.budget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BudgetTest {

    private val limits = Limits(dailyMinutes = 20, videosPerSession = 15, resetMinutes = 30)
    private val day = "2026-11-19"
    private val midnight = 1_000_000_000L
    private val min = 60_000L

    private fun start() = BudgetState(day = day)

    @Test
    fun fifteenVideosAllowedThenSixteenthBlocked() {
        var s = start()
        repeat(15) { i ->
            val step = Budget.onVideo(s, 1_000L + i, limits, midnight)
            assertNull("video ${i + 1} should be allowed", step.block)
            s = step.state
        }
        assertEquals(15, s.sessionVideos)

        val sixteenth = Budget.onVideo(s, 2_000L, limits, midnight)
        assertEquals(BlockReason.SESSION, sixteenth.block?.reason)
        assertEquals(2_000L + 30 * min, sixteenth.block?.until)
        assertEquals(15, sixteenth.state.videosToday)
    }

    @Test
    fun sessionReopensAfterLockExpires() {
        val locked = start().copy(sessionVideos = 15, sessionLockedUntil = 5_000L)
        val before = Budget.normalize(locked, 4_999L, day, limits)
        assertEquals(15, before.sessionVideos)

        val after = Budget.normalize(locked, 5_000L, day, limits)
        assertEquals(0, after.sessionVideos)
        assertNull(Budget.currentBlock(after, 5_000L, limits, midnight))
    }

    @Test
    fun sessionResetsAfterThirtyMinutesAway() {
        val s = start().copy(sessionVideos = 9, lastCountedAt = 0L + 1)
        assertEquals(9, Budget.normalize(s, 1 + 29 * min, day, limits).sessionVideos)
        assertEquals(0, Budget.normalize(s, 1 + 30 * min, day, limits).sessionVideos)
    }

    @Test
    fun dailyLimitBlocksAtTwentyMinutes() {
        val s = start().copy(usedMs = 20 * min - 1_000)
        val ok = Budget.onWatchTime(s, 10L, 999L, limits, midnight)
        assertNull(ok.block)

        val over = Budget.onWatchTime(ok.state, 11L, 1L, limits, midnight)
        assertEquals(BlockReason.DAILY, over.block?.reason)
        assertEquals(midnight, over.block?.until)

        // A new video is blocked too once the day is used up.
        assertEquals(BlockReason.DAILY, Budget.onVideo(over.state, 12L, limits, midnight).block?.reason)
    }

    @Test
    fun newDayClearsDailyCountersButKeepsLimits() {
        val s = start().copy(usedMs = 20 * min, videosToday = 40, blocksToday = 3)
        val next = Budget.normalize(s, 1L, "2026-11-20", limits)
        assertEquals(0L, next.usedMs)
        assertEquals(0, next.videosToday)
        assertEquals(0, next.blocksToday)
        assertNull(Budget.currentBlock(next, 1L, limits, midnight))
    }

    @Test
    fun loweringSessionCapBelowCurrentCountBlocksNextVideo() {
        val s = start().copy(sessionVideos = 12)
        val step = Budget.onVideo(s, 1L, limits.copy(videosPerSession = 10), midnight)
        assertEquals(BlockReason.SESSION, step.block?.reason)
    }
}
