package com.feedcap.app.data

import com.feedcap.app.budget.SleepTime
import com.feedcap.app.detect.HomeFeedMode
import com.feedcap.app.detect.Rules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrictRulesTest {

    private val cur = AppSettings(strict = true, sleep = SleepTime(enabled = true))

    @Test
    fun turningStrictOffIsImmediate() {
        assertFalse(cur.copy(strict = false).loosens(cur))
    }

    @Test
    fun sleepChanges() {
        assertTrue(cur.copy(sleep = cur.sleep.copy(enabled = false)).loosens(cur))
        assertTrue(cur.copy(sleep = cur.sleep.copy(startMinute = 23 * 60 + 30)).loosens(cur))
        assertFalse(cur.copy(sleep = cur.sleep.copy(startMinute = 22 * 60)).loosens(cur))
    }

    @Test
    fun homeFeedModeOrder() {
        val following = cur.copy(homeFeed = HomeFeedMode.FOLLOWING)
        assertTrue(following.copy(homeFeed = HomeFeedMode.CAUGHT_UP).loosens(following))
        assertTrue(following.copy(homeFeed = HomeFeedMode.ALLOW).loosens(following))
        val caughtUp = cur.copy(homeFeed = HomeFeedMode.CAUGHT_UP)
        assertFalse(caughtUp.copy(homeFeed = HomeFeedMode.FOLLOWING).loosens(caughtUp))
    }

    @Test
    fun separateLimitsPerApp() {
        val ig = Rules.instagram.pkg
        val yt = Rules.youtube.pkg
        val shared = AppSettings()
        assertEquals(AppSettings.SHARED_BUCKET, shared.bucketFor(ig))
        assertEquals(shared.limits, shared.limitsFor(yt))

        val separate = shared.withSeparateLimits(true).withLimits(yt) { it.copy(dailyMinutes = 10) }
        assertEquals(ig, separate.bucketFor(ig))
        assertEquals(10, separate.limitsFor(yt).dailyMinutes)
        assertEquals(20, separate.limitsFor(ig).dailyMinutes)
        // Two budgets allow more than one shared budget: loosening. Going back to shared tightens...
        assertTrue(separate.loosens(shared))
        assertFalse(shared.loosens(shared.withSeparateLimits(true)))
        // ...unless an app had a lower limit of its own: shared then gives it more.
        assertTrue(shared.loosens(separate))
        // Raising one app's limit loosens; lowering doesn't.
        assertTrue(separate.withLimits(yt) { it.copy(dailyMinutes = 15) }.loosens(separate))
        assertFalse(separate.withLimits(ig) { it.copy(dailyMinutes = 5) }.loosens(separate))
    }

    @Test
    fun unblockingSharedSwipeLoosens() {
        assertFalse(cur.copy(blockSharedSwipe = true).loosens(cur))
        val blocked = cur.copy(blockSharedSwipe = true)
        assertTrue(blocked.copy(blockSharedSwipe = false).loosens(blocked))
    }
}
