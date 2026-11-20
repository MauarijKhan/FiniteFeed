package com.feedcap.app.budget

/** The short-video budget, shared by every limited app (PRD 5.4). */
data class Limits(
    val dailyMinutes: Int = 20,
    val videosPerSession: Int = 15,
    val resetMinutes: Int = 30,
) {
    val dailyMs: Long get() = dailyMinutes * 60_000L
    val resetMs: Long get() = resetMinutes * 60_000L
}

data class BudgetState(
    /** Local date (yyyy-MM-dd) the daily counters belong to. */
    val day: String = "",
    val usedMs: Long = 0,
    val sessionVideos: Int = 0,
    /** Last time a video or watch time was counted; the session resets after resetMinutes away. */
    val lastCountedAt: Long = 0,
    /** Set when the session cap is hit; the session reopens at this time. */
    val sessionLockedUntil: Long = 0,
    val videosToday: Int = 0,
    val blocksToday: Int = 0,
)

enum class BlockReason {
    SESSION,
    DAILY,
    SLEEP,
    /** Swiped on from a reel opened from a DM or link while that's turned off. */
    SHARED,
}

data class Block(val reason: BlockReason, val until: Long)

data class Step(val state: BudgetState, val block: Block? = null)

/** Pure budget rules, no Android types, so they can be unit tested. */
object Budget {

    /** Rolls the day over and ends a session that has expired. Call before reading or counting. */
    fun normalize(s: BudgetState, now: Long, today: String, limits: Limits): BudgetState {
        val st = if (s.day != today) s.copy(day = today, usedMs = 0, videosToday = 0, blocksToday = 0) else s
        return when {
            st.sessionLockedUntil > 0 && now >= st.sessionLockedUntil ->
                st.copy(sessionVideos = 0, sessionLockedUntil = 0)
            st.sessionLockedUntil == 0L && st.lastCountedAt > 0 && now - st.lastCountedAt >= limits.resetMs ->
                st.copy(sessionVideos = 0)
            else -> st
        }
    }

    fun currentBlock(s: BudgetState, now: Long, limits: Limits, nextMidnight: Long): Block? = when {
        s.usedMs >= limits.dailyMs -> Block(BlockReason.DAILY, nextMidnight)
        s.sessionLockedUntil > now -> Block(BlockReason.SESSION, s.sessionLockedUntil)
        else -> null
    }

    /** A new video appeared on a limited surface: entering it, or swiping to the next one. */
    fun onVideo(s: BudgetState, now: Long, limits: Limits, nextMidnight: Long): Step {
        currentBlock(s, now, limits, nextMidnight)?.let { return Step(s, it) }
        if (s.sessionVideos >= limits.videosPerSession) {
            val locked = s.copy(sessionLockedUntil = now + limits.resetMs)
            return Step(locked, Block(BlockReason.SESSION, locked.sessionLockedUntil))
        }
        return Step(
            s.copy(
                sessionVideos = s.sessionVideos + 1,
                videosToday = s.videosToday + 1,
                lastCountedAt = now,
            ),
        )
    }

    /** Time spent on a limited surface (screen on, not a free shared video). */
    fun onWatchTime(s: BudgetState, now: Long, elapsedMs: Long, limits: Limits, nextMidnight: Long): Step {
        val st = s.copy(usedMs = s.usedMs + elapsedMs.coerceAtLeast(0), lastCountedAt = now)
        return Step(st, currentBlock(st, now, limits, nextMidnight))
    }
}
