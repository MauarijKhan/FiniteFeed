package com.feedcap.app.detect

/**
 * How to recognise a limited short-video surface inside one app.
 *
 * View IDs come from the target app's own layouts and change when it updates. Check them on a
 * real phone with the screen inspector in the app's Test tools, then fix them here. (The PRD
 * plans to move these into a remote, signed rule set.)
 *
 * Note: inside Instagram, "reel_viewer" is the Stories viewer and "clips" is Reels. Only
 * clips_* IDs are limited, so Stories are never counted.
 */
data class AppRule(
    val pkg: String,
    val label: String,
    /** What users call the surface: "Reels", "Shorts". */
    val surfaceName: String,
    /** Any of these visible on screen = the user is on the limited surface. */
    val surfaceIds: List<String>,
    /** The vertical pager whose page changes count as "next video". */
    val pagerIds: List<String>,
    /** Visible on a DM thread. A surface opened right after one = a shared video, free. */
    val dmIds: List<String> = emptyList(),
    /** Start of the DM composer hint ("Message..."), a fallback DM marker. */
    val dmHint: String? = null,
    /** Any of these visible (and [feedTabId] selected) = the main home feed. Empty = no home-feed rules. */
    val feedScreenIds: List<String> = emptyList(),
    /** Any of these visible = the main home feed on its own (its top bar, hidden while scrolling). */
    val feedTopBarIds: List<String> = emptyList(),
    /** Bottom-bar tab that is selected while the home feed is open. */
    val feedTabId: String? = null,
    /** Full IDs ("pkg:id/x" or "android:id/x") of the home feed's scrolling list. */
    val feedListIds: List<String> = emptyList(),
    /** Text of the row that ends posts from people you follow; suggested posts come after it. */
    val feedEndTexts: List<String> = emptyList(),
    /** Home feed title; tapping it opens the feed menu (Following, Favorites). */
    val feedTitleId: String? = null,
    /** Menu item / screen title of the feed of people you follow. */
    val followingLabel: String? = null,
    /** Title view of the Following feed screen (its text is [followingLabel]). */
    val followingTitleId: String? = null,
    /** Bottom-bar tab that opens messages. During sleep time, feed scrolling goes here. */
    val dmTabId: String? = null,
    /**
     * Fallback when [surfaceIds] miss after an app update: a full-screen view whose ID contains
     * one of these is the limited surface.
     */
    val surfaceIdHints: List<String> = emptyList(),
) {
    fun fullId(id: String) = "$pkg:id/$id"
}

/** What to do about Instagram's main (algorithmic) home feed. Ordered from loosest to strictest. */
enum class HomeFeedMode {
    /** No limit. */
    ALLOW,
    /** Stop at "You're all caught up": no endless suggested posts. */
    CAUGHT_UP,
    /** Scrolling the main feed opens the Following feed (people you follow, newest first). */
    FOLLOWING,
}

object Rules {
    val instagram = AppRule(
        pkg = "com.instagram.android",
        label = "Instagram",
        surfaceName = "Reels",
        surfaceIds = listOf("clips_viewer_view_pager", "clips_viewer_container", "root_clips_layout"),
        pagerIds = listOf("clips_viewer_view_pager"),
        // Checked on a Pixel 7 (Nov 2026): the DM thread shows these.
        dmIds = listOf("row_thread_composer_edittext", "direct_thread_header", "message_list"),
        dmHint = "Message",
        // Checked on a Pixel 7 (Nov 2026, Instagram 457): the main feed is android:id/list inside
        // sticky_header_list with the feed_tab selected. The top bar (main_feed_action_bar) hides
        // while scrolling. The Following feed uses layout_listview_parent_container and no tab bar.
        feedScreenIds = listOf("sticky_header_list"),
        feedTopBarIds = listOf("main_feed_action_bar"),
        feedTabId = "feed_tab",
        feedListIds = listOf("android:id/list"),
        feedEndTexts = listOf("You're all caught up", "You’re all caught up", "Suggested posts"),
        feedTitleId = "action_bar_title_view",
        followingLabel = "Following",
        followingTitleId = "action_bar_title",
        dmTabId = "direct_tab",
    )

    val youtube = AppRule(
        pkg = "com.google.android.youtube",
        label = "YouTube",
        surfaceName = "Shorts",
        surfaceIds = listOf(
            "reel_recycler",
            "reel_player_page_container",
            "reel_watch_player",
            "reel_watch_fragment_root",
            "shorts_container",
        ),
        pagerIds = listOf("reel_recycler"),
        // YouTube calls Shorts "reel" internally; the exact IDs move between versions.
        surfaceIdHints = listOf("reel", "shorts"),
    )

    val all = listOf(instagram, youtube)

    fun forPackage(pkg: String): AppRule? = all.firstOrNull { it.pkg == pkg }
}
