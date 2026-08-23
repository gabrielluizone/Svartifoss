package com.svartifoss.snfell.music

import com.svartifoss.snfell.common.LyricsParser
import com.svartifoss.snfell.common.LyricsStatus
import timber.log.Timber

/** What the phone answers a lyrics request with; maps straight onto `lyrics.proto`. */
data class LyricsAnswer(
        val status: Int,
        val lrc: String? = null,
        val plain: String? = null,
)

/**
 * Resolves lyrics for a track and remembers the answer for the rest of the process.
 *
 * The cache is **in memory only, and deliberately so**. Lyrics are wanted while a screen is open
 * and worthless afterwards, so persisting them would buy nothing except a folder of other people's
 * copyrighted text sitting on the user's disk and a privacy question to answer about it. Process
 * lifetime is the right amount of "temporarily": re-opening the screen during one listening
 * session is instant, and a phone restart forgets everything.
 *
 * Failures are **not** cached. A lookup that failed because the phone was in a lift should retry
 * the next time the screen opens; caching it would make one bad moment stick for the whole session.
 */
object LyricsRepository {

    private const val MAX_CACHED = 32

    // accessOrder = true, so eviction drops the least recently *used*, not the oldest inserted.
    private val cache = object : LinkedHashMap<String, LyricsAnswer>(0, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LyricsAnswer>?) =
                size > MAX_CACHED
    }

    suspend fun lyricsFor(title: String?, artist: String?, durationMs: Long): LyricsAnswer {
        // LRCLIB matches on both, and a blank one would match some unrelated song rather than
        // nothing at all - the worst outcome available, since it looks like it worked.
        if (title.isNullOrBlank() || artist.isNullOrBlank()) return LyricsAnswer(LyricsStatus.NONE)

        val key = cacheKey(title, artist, durationMs)
        synchronized(cache) { cache[key] }?.let { return it }

        val answer = resolve(title, artist, durationMs)

        if (answer.status != LyricsStatus.FAILED) {
            synchronized(cache) { cache[key] = answer }
        }
        return answer
    }

    private suspend fun resolve(title: String, artist: String, durationMs: Long): LyricsAnswer {
        // Song durations are seconds at LRCLIB and milliseconds in MusicState. Converting once,
        // here, is the point: getting this wrong by 1000x does not fail, it silently matches
        // nothing and the screen just says the track has no lyrics.
        val durationSec = durationMs / 1000

        val exact = try {
            LyricsFetcher.getExact(title, artist, durationSec)
        } catch (e: LyricsFetcher.LookupFailed) {
            Timber.w(e, "Lyrics exact lookup failed for %s - %s", artist, title)
            return LyricsAnswer(LyricsStatus.FAILED)
        }

        // Take the exact answer only when it is genuinely synced *and* parses to something. Plain
        // lyrics from /api/get are not good enough to stop here: another upload of the same song
        // often carries timings, and following along is the whole reason this screen exists.
        exact?.synced?.let { lrc ->
            if (LyricsParser.parseSynced(lrc).isNotEmpty()) {
                return LyricsAnswer(LyricsStatus.SYNCED, lrc = lrc)
            }
        }

        val best = try {
            LyricsFetcher.pickBest(LyricsFetcher.search(title, artist), durationSec)
        } catch (e: LyricsFetcher.LookupFailed) {
            Timber.w(e, "Lyrics search failed for %s - %s", artist, title)
            // The exact endpoint may already have handed us something usable; a failed *fallback*
            // must not throw that away and report the whole lookup as broken.
            exact?.plain?.let { return LyricsAnswer(LyricsStatus.PLAIN, plain = it) }
            return LyricsAnswer(LyricsStatus.FAILED)
        }

        best?.synced?.let { lrc ->
            if (LyricsParser.parseSynced(lrc).isNotEmpty()) {
                return LyricsAnswer(LyricsStatus.SYNCED, lrc = lrc)
            }
        }

        // Fall back through the plain texts in order of trustworthiness: the exact endpoint's own
        // answer first, then the best search candidate's.
        exact?.plain?.let { return LyricsAnswer(LyricsStatus.PLAIN, plain = it) }
        best?.plain?.let { return LyricsAnswer(LyricsStatus.PLAIN, plain = it) }

        // An instrumental record is a real answer - the track has no words - and reaches here with
        // both lyric fields empty, same as a track nobody has transcribed. Both are NONE.
        return LyricsAnswer(LyricsStatus.NONE)
    }

    private fun cacheKey(title: String, artist: String, durationMs: Long): String =
            "$title|$artist|${durationMs / 1000}"
}
