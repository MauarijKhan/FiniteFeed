package com.feedcap.app.detect

/**
 * Tracks the "You're all caught up" row in a home feed and says when the user has scrolled past
 * it into suggested posts. Pure logic, fed from scroll events.
 *
 * Two ways to know where the wall is:
 * - Item index (RecyclerView scroll events carry fromIndex/toIndex): the wall is the last visible
 *   item when the row was first seen. Survives fast flings.
 * - Direction, when there are no indexes: the row left the screen while scrolling down.
 */
class FeedWall {
    var wallIndex = -1
        private set
    private var markerSeen = false
    private var pastByDirection = false

    fun reset() {
        wallIndex = -1
        markerSeen = false
        pastByDirection = false
    }

    /**
     * One scroll event on the feed list. [endVisible] is null when the row wasn't checked this time.
     * [dy] > 0 means scrolling down (towards older posts). Returns true if the user is past the wall.
     */
    fun onScroll(from: Int, to: Int, dy: Int, endVisible: Boolean?): Boolean {
        // Back at the top: a refresh may have added posts, so the wall moves. Find it again.
        if (from == 0) reset()
        when (endVisible) {
            true -> {
                markerSeen = true
                pastByDirection = false
                if (to >= 0 && wallIndex < 0) wallIndex = to
            }
            false -> if (markerSeen && !pastByDirection) {
                if (dy > 0) pastByDirection = true // left through the top: now in suggested posts
                else if (dy < 0) markerSeen = false // left through the bottom: back above it
            }
            null -> Unit
        }
        return if (wallIndex >= 0 && from >= 0) from > wallIndex else pastByDirection
    }
}
