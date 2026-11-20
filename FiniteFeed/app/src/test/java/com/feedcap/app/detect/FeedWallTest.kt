package com.feedcap.app.detect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedWallTest {

    @Test
    fun followedPostsScrollFreely() {
        val w = FeedWall()
        for (i in 0..30) assertFalse(w.onScroll(i, i + 2, 100, false))
    }

    @Test
    fun indexWallBlocksOncePastTheCaughtUpRow() {
        val w = FeedWall()
        assertFalse(w.onScroll(20, 22, 100, true)) // row appears at the bottom
        assertEquals(22, w.wallIndex)
        assertFalse(w.onScroll(22, 24, 100, true)) // row at the top, still on screen
        assertTrue(w.onScroll(23, 25, 100, null)) // row gone off the top
        assertTrue(w.onScroll(40, 42, 100, null)) // fast fling far into suggestions
        assertFalse(w.onScroll(21, 23, -100, true)) // pushed back up
    }

    @Test
    fun backAtTopFindsTheWallAgain() {
        val w = FeedWall()
        w.onScroll(20, 22, 100, true)
        assertFalse(w.onScroll(0, 2, -100, false))
        assertEquals(-1, w.wallIndex)
        // After a refresh with new posts, the row is further down.
        assertFalse(w.onScroll(23, 25, 100, false))
        w.onScroll(30, 32, 100, true)
        assertEquals(32, w.wallIndex)
    }

    @Test
    fun directionWallWithoutIndexes() {
        val w = FeedWall()
        assertFalse(w.onScroll(-1, -1, 100, true))
        assertTrue(w.onScroll(-1, -1, 100, false)) // left through the top
        assertTrue(w.onScroll(-1, -1, -100, false)) // a push back up doesn't clear it yet
        assertFalse(w.onScroll(-1, -1, -100, true)) // row back on screen
        assertFalse(w.onScroll(-1, -1, -100, false)) // left through the bottom: above it
        assertFalse(w.onScroll(-1, -1, 100, false))
    }
}
