package com.svartifoss.snfell.music

import com.svartifoss.snfell.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** The extra fields a lookup can add. Any of them may be absent; none is ever invented. */
data class ExternalTrackFacts(
        val isrc: String? = null,
        val label: String? = null,
        val releaseDate: String? = null,
        val recordingMbid: String? = null,
        val releaseMbid: String? = null,
        val releaseCountry: String? = null,
        /** The release this recording appears on. Fills a *gap* only - see the merge rule in
         *  `MusicService.sendTrackMetadataToWatch`. */
        val album: String? = null,
        /** From MusicBrainz's community tags, most-voted first. */
        val genre: String? = null,
        val trackCount: Long? = null,
        val durationMs: Long? = null,
) {
    val isEmpty: Boolean
        get() = listOf(isrc, label, releaseDate, recordingMbid, releaseMbid, releaseCountry,
                album, genre).all { it == null } && trackCount == null && durationMs == null
}

/**
 * Fills in the catalogue facts a player does not publish, from
 * [MusicBrainz](https://musicbrainz.org).
 *
 * **Why this source.** It is free, needs no API key and no account, and it is the open database the
 * rest of the industry's identifiers are keyed to - which is what makes it the only realistic place
 * to get an ISRC, a label and a real release date without signing up for something. Last.fm and
 * Discogs both want a key; the streaming services' own APIs want an app registration and only cover
 * their own catalogue.
 *
 * **What it costs, stated plainly, because it is why this is off by default.** The match is
 * *fuzzy*: all the phone has to search with is a title and an artist name, and that is genuinely
 * ambiguous for live versions, remixes, re-releases and anything with inconsistent punctuation. A
 * wrong match here does not look wrong - it produces a confident, plausible, incorrect ISRC. The
 * service also asks clients to stay under one request a second and to identify themselves, which
 * this does.
 *
 * **What it is not allowed to do.** Nothing here is on the path to drawing the screen: the phone
 * answers the watch with the local metadata first and sends this as a second message if and when it
 * arrives (see `MusicService.sendTrackMetadataToWatch`). A failure, a timeout or an unrecognised
 * track simply means the table has the rows it always had.
 *
 * Runs on the **phone**, like every other network call in this app, for the reason
 * [LyricsFetcher] documents: a Bluetooth-only watch has no route to the internet of its own.
 */
object MusicBrainzMetadata {

    private const val BASE_URL = "https://musicbrainz.org/ws/2"

    /**
     * MusicBrainz requires a contactable User-Agent and rate-limits or blocks anonymous traffic.
     * The version is included so a badly-behaved build can be identified and blocked specifically
     * rather than the whole app being.
     */
    private val USER_AGENT =
            "Svartifoss/${BuildConfig.VERSION_NAME} ( https://github.com/gabrielluizone/Svartifoss )"

    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 10_000

    /**
     * How close the returned recording must be before its facts are used.
     *
     * MusicBrainz scores every result 0-100 against the query. Anything below this is a guess
     * dressed as an answer, and on this screen a guess is worse than a blank: the user cannot tell
     * a wrong ISRC from a right one. Deliberately strict.
     */
    private const val MIN_SCORE = 60

    /**
     * Looks [title] by [artist] up, returning only what the service was confident about.
     *
     * Returns null for every failure mode there is - no match, a weak match, an error, no network -
     * because the caller treats all of them identically: send nothing more, leave the table alone.
     */
    suspend fun lookup(title: String?, artist: String?): ExternalTrackFacts? =
            withContext(Dispatchers.IO) {
                val track = title?.trim()?.takeIf { it.isNotEmpty() } ?: return@withContext null
                val performer = artist?.trim()?.takeIf { it.isNotEmpty() } ?: return@withContext null
                try {
                    val query = "recording:${quote(track)} AND artist:${quote(performer)}"
                    // tags come back with the recording, releases with their label and
                    // media (which is where a track count lives). Asked for in one request rather
                    // than following the ids: the service rate-limits to a request a second, and a
                    // second round trip to add a genre row is not a trade worth making.
                    val url = "$BASE_URL/recording?query=" +
                            URLEncoder.encode(query, "UTF-8") +
                            "&limit=1&inc=isrcs+releases+labels+tags+media&fmt=json"
                    parse(getJson(url) ?: return@withContext null)
                } catch (e: Exception) {
                    // Offline, rate limited, the service down, a schema change. All the same to the
                    // caller: the table keeps the rows it already had.
                    Timber.v(e, "MusicBrainz lookup failed")
                    null
                }
            }

    /** Lucene special characters would otherwise turn a title like `Hello (Live)` into a syntax
     *  error rather than a search. */
    private fun quote(value: String): String =
            "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private fun getJson(url: String): JSONObject? {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "application/json")
            // The same hardening ApkDownloader documents - Android's connection pooling can
            // truncate a redirected response when the socket is reused.
            connection.setRequestProperty("Connection", "close")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Timber.v("MusicBrainz answered %d", connection.responseCode)
                return null
            }
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(body: JSONObject): ExternalTrackFacts? {
        val recording = body.optJSONArray("recordings")?.optJSONObject(0) ?: return null
        if (recording.optInt("score", 0) < MIN_SCORE) {
            Timber.v("Discarding a MusicBrainz match scoring %d", recording.optInt("score", 0))
            return null
        }

        val isrc = recording.optJSONArray("isrcs")?.optString(0)?.takeIf { it.isNotBlank() }
        val release = recording.optJSONArray("releases")?.optJSONObject(0)
        val label = release?.optJSONArray("label-info")
                ?.optJSONObject(0)
                ?.optJSONObject("label")
                ?.optString("name")
                ?.takeIf { it.isNotBlank() }

        // Most-voted tag, which is as close to a genre as an open database gets. Ties are broken
        // by whatever order the service returned, which is the honest answer to "these are equally
        // popular" - inventing a tiebreak would only make an arbitrary pick look considered.
        val genre = recording.optJSONArray("tags")
                ?.let { tags -> (0 until tags.length()).mapNotNull { tags.optJSONObject(it) } }
                ?.maxByOrNull { it.optInt("count", 0) }
                ?.optString("name")
                ?.takeIf { it.isNotBlank() }

        val trackCount = release?.optJSONArray("media")
                ?.optJSONObject(0)
                ?.optInt("track-count", 0)
                ?.takeIf { it > 0 }
                ?.toLong()

        val facts = ExternalTrackFacts(
                isrc = isrc,
                label = label,
                releaseDate = release?.optString("date")?.takeIf { it.isNotBlank() },
                recordingMbid = recording.optString("id")?.takeIf { it.isNotBlank() },
                releaseMbid = release?.optString("id")?.takeIf { it.isNotBlank() },
                releaseCountry = release?.optString("country")?.takeIf { it.isNotBlank() },
                album = release?.optString("title")?.takeIf { it.isNotBlank() },
                genre = genre,
                trackCount = trackCount,
                durationMs = recording.optLong("length", 0L).takeIf { it > 0L })

        // A match that adds nothing is reported as no match, so the caller never sends a second
        // message identical to the first.
        return facts.takeUnless { it.isEmpty }
    }
}
