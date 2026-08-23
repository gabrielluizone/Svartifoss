package com.svartifoss.snfell.music

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs

/** One record as LRCLIB returns it. Either lyric field may be absent. */
data class LyricsCandidate(
        val trackName: String,
        val artistName: String,
        val durationSec: Long,
        val instrumental: Boolean,
        val plain: String?,
        val synced: String?,
)

/**
 * Reads lyrics from [LRCLIB](https://lrclib.net), on the **phone**.
 *
 * This runs here rather than on the watch for a reason that is easy to get backwards: a
 * Bluetooth-only watch has no internet route of its own, so a watch-side fetch would work on the
 * LTE models and quietly fail on everything else. Keeping it here also keeps the app's entire
 * network surface on one device, which is what `docs/privacy-policy.md` describes.
 *
 * LRCLIB was picked because it matches on **metadata** - track, artist, duration - and needs no API
 * key, no account and no per-service track id. That is what lets lyrics work while the phone plays
 * Spotify, YouTube Music, a local file or anything else: the phone already knows the title, artist
 * and duration, and nothing about the playing app has to be understood.
 */
object LyricsFetcher {

    private const val BASE_URL = "https://lrclib.net"

    /** LRCLIB asks clients to identify themselves; anonymous traffic is what gets rate-limited. */
    private const val USER_AGENT = "Svartifoss (https://github.com/gabrielluizone/Svartifoss)"

    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 15_000

    /** A lyric far larger than this is a bad match or an abuse case, not a song. Also keeps the
     *  reply inside MessageClient's payload ceiling with room to spare. */
    private const val MAX_LYRIC_CHARS = 60_000

    /** Thrown for the failures worth distinguishing from "this track has no lyrics". */
    class LookupFailed(message: String, cause: Throwable? = null) : IOException(message, cause)

    /**
     * The exact-match endpoint. Returns null when LRCLIB has no record for this metadata (404),
     * which is an ordinary answer rather than a failure.
     *
     * @throws LookupFailed when the lookup could not be completed at all.
     */
    suspend fun getExact(title: String, artist: String, durationSec: Long): LyricsCandidate? =
            withContext(Dispatchers.IO) {
                val url = buildString {
                    append(BASE_URL).append("/api/get")
                    append("?track_name=").append(encode(title))
                    append("&artist_name=").append(encode(artist))
                    // Duration is what makes this endpoint "exact"; omit it when the playing app
                    // never reported one rather than sending a 0 that matches nothing.
                    if (durationSec > 0) append("&duration=").append(durationSec)
                }
                when (val response = get(url)) {
                    null -> null
                    else -> parseObject(JSONObject(response))
                }
            }

    /**
     * The fuzzy search endpoint, used only as a fallback.
     *
     * It exists because [getExact] is strict in two ways that both end in a worse screen: a
     * duration that drifts by a couple of seconds - ordinary, since the phone reports whatever the
     * playing app claims - makes it a 404, and the single record it does return may carry only
     * plain lyrics while a different upload of the same song has timings.
     */
    suspend fun search(title: String, artist: String): List<LyricsCandidate> =
            withContext(Dispatchers.IO) {
                val url = buildString {
                    append(BASE_URL).append("/api/search")
                    append("?track_name=").append(encode(title))
                    append("&artist_name=").append(encode(artist))
                }
                val response = get(url) ?: return@withContext emptyList()
                parseArray(response)
            }

    /**
     * Best candidate for a track of [durationSec], or null when none carries lyrics at all.
     *
     * A synced candidate always beats an unsynced one, even a perfectly-timed-duration one:
     * preferring a closer duration over having timings optimises the wrong thing, since finding
     * timings is the entire reason this fallback runs. Within each group the closest duration wins,
     * which is what stops a remix or an extended edit being picked over the actual recording.
     *
     * A [durationSec] of 0 means the phone never reported one; every candidate then ties on
     * distance and LRCLIB's own relevance order decides.
     *
     * Pure, and tested as such - the ordering here is exactly the kind of thing that looks right
     * and silently prefers the wrong upload.
     */
    fun pickBest(candidates: List<LyricsCandidate>, durationSec: Long): LyricsCandidate? =
            candidates
                    .filter { it.synced != null || it.plain != null }
                    .minWithOrNull(
                            compareByDescending<LyricsCandidate> { it.synced != null }
                                    .thenBy {
                                        if (durationSec <= 0) 0L else abs(it.durationSec - durationSec)
                                    })

    /** @return the body, or null on 404. @throws LookupFailed on anything else. */
    private fun get(url: String): String? {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "application/json")
            // Same defence ApkDownloader documents: Android's HttpURLConnection can silently
            // truncate a pooled connection's body across a cross-host redirect.
            connection.setRequestProperty("Connection", "close")

            return when (val status = connection.responseCode) {
                HttpURLConnection.HTTP_NOT_FOUND -> null
                in 200..299 -> connection.inputStream.bufferedReader().use { it.readText() }
                // 429 and 5xx are the retryable ones, but the caller only needs "could not
                // complete" - it never retries within a single screen open.
                else -> throw LookupFailed("LRCLIB returned HTTP $status")
            }
        } catch (e: LookupFailed) {
            throw e
        } catch (e: IOException) {
            throw LookupFailed("LRCLIB unreachable", e)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseArray(body: String): List<LyricsCandidate> = try {
        val array = JSONArray(body)
        (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let(::parseObject)
        }
    } catch (e: Exception) {
        Timber.w(e, "Could not parse LRCLIB search response")
        emptyList()
    }

    private fun parseObject(item: JSONObject): LyricsCandidate = LyricsCandidate(
            trackName = item.optString("trackName"),
            artistName = item.optString("artistName"),
            // Sent as a number that may carry a fraction.
            durationSec = item.optDouble("duration", 0.0).toLong(),
            instrumental = item.optBoolean("instrumental", false),
            plain = item.optString("plainLyrics").takeIf { it.isNotBlank() }?.take(MAX_LYRIC_CHARS),
            synced = item.optString("syncedLyrics").takeIf { it.isNotBlank() }?.take(MAX_LYRIC_CHARS))

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
