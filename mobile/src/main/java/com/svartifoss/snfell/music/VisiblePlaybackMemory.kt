package com.svartifoss.snfell.music

import android.content.Context
import android.content.SharedPreferences

/**
 * What this phone has seen each [VisiblePlayTechnique] do, per app and kind of content.
 *
 * It exists because none of the techniques is documented as *playing*: Spotify says what its URIs
 * open, not that anything starts, and the reports of what does start are years old and disagree
 * with each other. So rather than guess once for every phone, each phone keeps the technique that
 * started something for it and stops repeating one that did not - Spotify ships every week, and a
 * technique that works today can stop working with no notice, which [RETRY_AFTER_MS] and the
 * two-strikes rule for a proven technique are there to absorb.
 *
 * The rules are pure and pinned by `VisiblePlaybackMemoryTest`; [Store] is the persistence.
 */
object VisiblePlaybackMemory {
    /** A technique that failed is tried again after this long: the app may have been updated. */
    const val RETRY_AFTER_MS = 7L * 24 * 60 * 60 * 1000

    data class Record(val successAtMs: Long = 0L, val failureAtMs: Long = 0L, val failures: Int = 0) {
        val provenGood: Boolean get() = successAtMs > 0L && failures == 0

        /**
         * One failure condemns a technique that never worked; one that has worked here gets a
         * second chance, because a single miss (no network, a slow cold start) says less than the
         * success before it did.
         */
        fun benched(nowMs: Long): Boolean {
            val strikes = if (successAtMs > 0L) 2 else 1
            return failures >= strikes && nowMs - failureAtMs in 0 until RETRY_AFTER_MS
        }
    }

    /**
     * The technique to use among [candidates] (best first): one proven on this phone, else the
     * first not benched, else none - and none means the plain open, which is what a shortcut did
     * before any of this and is never worse than a technique seen to fail.
     */
    fun choose(
            candidates: List<VisiblePlayTechnique>,
            records: Map<VisiblePlayTechnique, Record>,
            nowMs: Long
    ): VisiblePlayTechnique? =
            candidates.firstOrNull { records[it]?.provenGood == true }
                    ?: candidates.firstOrNull { records[it]?.benched(nowMs) != true }

    fun recorded(
            records: Map<VisiblePlayTechnique, Record>,
            technique: VisiblePlayTechnique,
            played: Boolean,
            nowMs: Long
    ): Map<VisiblePlayTechnique, Record> {
        val previous = records[technique] ?: Record()
        val next = if (played) {
            Record(successAtMs = nowMs, failureAtMs = previous.failureAtMs, failures = 0)
        } else {
            Record(successAtMs = previous.successAtMs, failureAtMs = nowMs, failures = previous.failures + 1)
        }
        return records + (technique to next)
    }

    /** `key:success:failureAt:failures` per technique, `|` between them. */
    fun encode(records: Map<VisiblePlayTechnique, Record>): String =
            records.entries.joinToString("|") { (technique, record) ->
                "${technique.storageKey}:${record.successAtMs}:${record.failureAtMs}:${record.failures}"
            }

    /** Tolerant: anything it cannot read is treated as never tried, never as an error. */
    fun decode(text: String?): Map<VisiblePlayTechnique, Record> {
        if (text.isNullOrBlank()) return emptyMap()
        return text.split('|').mapNotNull { entry ->
            val parts = entry.split(':')
            if (parts.size != 4) return@mapNotNull null
            val technique = VisiblePlayTechnique.fromStorageKey(parts[0]) ?: return@mapNotNull null
            val success = parts[1].toLongOrNull() ?: return@mapNotNull null
            val failureAt = parts[2].toLongOrNull() ?: return@mapNotNull null
            val failures = parts[3].toIntOrNull()?.coerceAtLeast(0) ?: return@mapNotNull null
            technique to Record(success, failureAt, failures)
        }.toMap()
    }

    /**
     * Its own preferences file, deliberately not the default one: that file is mirrored to the
     * watch and exported by the backup, and how this phone's copy of Spotify behaves is neither a
     * watch setting nor something to restore onto another phone.
     */
    class Store(context: Context) {
        private val preferences: SharedPreferences =
                context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

        fun choose(key: String, candidates: List<VisiblePlayTechnique>, nowMs: Long): VisiblePlayTechnique? =
                choose(candidates, decode(preferences.getString(key, null)), nowMs)

        fun record(key: String, technique: VisiblePlayTechnique, played: Boolean, nowMs: Long) {
            val updated = recorded(decode(preferences.getString(key, null)), technique, played, nowMs)
            preferences.edit().putString(key, encode(updated)).apply()
        }

        /** Everything remembered for [packageName], by kind of content, for the developer report. */
        fun recordsFor(packageName: String): Map<String, Map<VisiblePlayTechnique, Record>> =
                preferences.all.entries
                        .filter { it.key.startsWith("$packageName/") }
                        .associate { it.key.substringAfter('/') to decode(it.value as? String) }

        private companion object {
            const val FILE_NAME = "visible_playback"
        }
    }
}
