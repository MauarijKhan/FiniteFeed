package com.feedcap.app.insights

import java.time.LocalDate

/** One app's numbers for one day. */
data class DayStats(
    /** Time on Reels/Shorts that counted toward a limit. */
    val usedMs: Long = 0,
    /** Videos counted (entering, or swiping to the next). */
    val videos: Int = 0,
    /** Times FeedCap stepped in: limit screens, sleep-time and home-feed redirects. */
    val blocks: Int = 0,
    /** Reels/Shorts opened from a DM or link (free, never counted). */
    val shared: Int = 0,
) {
    operator fun plus(o: DayStats) = DayStats(usedMs + o.usedMs, videos + o.videos, blocks + o.blocks, shared + o.shared)
    val minutes: Long get() = usedMs / 60_000
}

/**
 * Per-day, per-app history: date (yyyy-MM-dd) → package → stats. Pure functions over it, so the
 * Insights page and tests share one set of rules.
 */
object Insights {
    const val KEEP_DAYS = 28L

    /** Adds to one app's stats for [day], dropping days older than [KEEP_DAYS]. */
    fun record(
        history: Map<String, Map<String, DayStats>>,
        day: LocalDate,
        pkg: String,
        change: (DayStats) -> DayStats,
    ): Map<String, Map<String, DayStats>> {
        val key = day.toString()
        val dayLog = history[key].orEmpty()
        val updated = dayLog + (pkg to change(dayLog[pkg] ?: DayStats()))
        val oldest = day.minusDays(KEEP_DAYS - 1).toString()
        return (history + (key to updated)).filterKeys { it >= oldest }
    }

    fun day(history: Map<String, Map<String, DayStats>>, day: LocalDate, pkg: String): DayStats =
        history[day.toString()]?.get(pkg) ?: DayStats()

    /** The 7 days ending today, oldest first. */
    fun week(today: LocalDate): List<LocalDate> = (6 downTo 0).map { today.minusDays(it.toLong()) }

    /** Sum of [pkgs] over [days]. */
    fun total(history: Map<String, Map<String, DayStats>>, days: List<LocalDate>, pkgs: Collection<String>): DayStats =
        days.fold(DayStats()) { acc, d -> pkgs.fold(acc) { a, p -> a + day(history, d, p) } }

    /** Average minutes a day over the days that have any data (so a new install isn't averaged down). */
    fun dailyAverageMinutes(history: Map<String, Map<String, DayStats>>, days: List<LocalDate>, pkgs: Collection<String>): Long {
        val active = days.filter { d -> pkgs.any { history[d.toString()]?.containsKey(it) == true } }
        if (active.isEmpty()) return 0
        return total(history, active, pkgs).usedMs / active.size / 60_000
    }
}
