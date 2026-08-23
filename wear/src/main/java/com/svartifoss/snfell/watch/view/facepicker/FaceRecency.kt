package com.svartifoss.snfell.watch.view.facepicker

import android.content.SharedPreferences

/**
 * Remembers which faces and themes were used most recently, so the picker offers them first.
 *
 * The list is otherwise in registry order, which is the order the faces happened to be written and
 * means nothing to a user. Someone who alternates between two faces has to scroll past sixteen they
 * never touch to reach the second one - and the more faces ship, the worse that gets.
 *
 * **Watch-local, deliberately.** These timestamps are never sent to the phone and never appear in
 * `MiscPreferences.EXPORTABLE`. Two reasons: the order a wrist-sized picker should use is a
 * property of how this watch is used rather than of the theme, and - the load-bearing one - the
 * preference sync only deletes keys a previous phone snapshot declared as phone-owned, so a key the
 * phone has never heard of survives every sync. Adding these to the synced set would make them
 * phone-owned and put a write on every face change onto the Bluetooth link for no benefit.
 *
 * Both halves are pure functions over a plain map, so the ordering is pinned by a JVM test rather
 * than being something only a wrist can check.
 */
object FaceRecency {

    /** Prefix kept distinct from every real preference key so a stored timestamp can never be
     *  mistaken for a setting. */
    private const val KEY_PREFIX = "face_last_used::"

    fun recordUse(prefs: SharedPreferences, faceKey: String, nowMs: Long = System.currentTimeMillis()) {
        if (faceKey.isBlank()) return
        prefs.edit().putLong(KEY_PREFIX + faceKey, nowMs).apply()
    }

    /** Last-used timestamps for [keys], omitting the ones never picked on this watch. */
    fun timestamps(prefs: SharedPreferences, keys: Collection<String>): Map<String, Long> =
            keys.mapNotNull { key ->
                prefs.getLong(KEY_PREFIX + key, 0L).takeIf { it > 0L }?.let { key to it }
            }.toMap()

    /**
     * [options] with the recently used first, most recent leading.
     *
     * Anything never picked keeps its **registry order** behind them rather than being sorted
     * arbitrarily: that order is the curated one the collection was designed in, and a list that
     * reshuffled the untouched faces every time one was used would be unlearnable.
     *
     * Applied per section by the caller. Mixing the user's own themes into the built-in ordering
     * would put a saved theme between two faces on the strength of a timestamp, and the sections
     * exist precisely because those are different kinds of thing.
     */
    fun ordered(
            options: List<WatchFaceOption>,
            lastUsed: Map<String, Long>,
    ): List<WatchFaceOption> {
        val (used, unused) = options.partition { lastUsed.containsKey(it.key) }
        return used.sortedByDescending { lastUsed.getValue(it.key) } + unused
    }
}
