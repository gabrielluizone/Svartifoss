package com.svartifoss.snfell.music

import org.json.JSONObject
import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder

/**
 * The decisions behind a saved playlist's song list on the watch, kept free of Android so they are
 * pinned by `ShortcutTrackListingTest`; [ShortcutTracksResponder] does the asking.
 *
 * Only YouTube playlists (YouTube Music's included, and its albums, which are playlists too) can
 * be listed, through the YouTube Data API's `playlistItems.list`: one quota unit per page of
 * [PAGE_SIZE] songs, public and unlisted playlists only. Measured on 2026-11-14 against the
 * owner's own playlist, someone else's, an `OLAK5uy_` album and a short `PL` id
 * (`docs/streaming-app-interop.md` section 5.9).
 *
 * A song is played as itself *inside* its playlist ([trackLink]): `watch?v=X&list=P` starts at X
 * and goes on with the rest of the playlist, so picking the fifth song is the playlist from the
 * fifth song on - checked on a phone, warm and cold, in the same section.
 */
internal object ShortcutTrackListing {

    /** A `playlistItems.list` page holds at most this many. */
    const val PAGE_SIZE = 50

    /** At most four pages - four quota units - per lookup; longer playlists are cut short. */
    const val MAX_TRACKS = 200

    /** An answer stays fresh for a day: an edited playlist shows its changes by the next day. */
    const val FRESH_MS = 24L * 60 * 60 * 1000

    /**
     * Never kept longer than this. The YouTube API Services policies allow storing API data for
     * 30 days at most; 28 leaves room for a clock that is a little off, as `OnlineArtworkCache`
     * does for the covers.
     */
    const val MAX_AGE_MS = 28L * 24 * 60 * 60 * 1000

    /**
     * What a cached answer is good for, by its age.
     *
     * FRESH is the answer. STALE is good enough to show when a new lookup cannot be made (no
     * consent, no connection) but is replaced when it can. EXPIRED is not shown at all. A negative
     * age - a clock set back since it was stored - says nothing about how old it is, so it is
     * treated as STALE: shown when nothing better can be had, never trusted as fresh.
     */
    enum class CacheUse { FRESH, STALE, EXPIRED }

    fun cacheUse(ageMs: Long): CacheUse = when {
        ageMs < 0 -> CacheUse.STALE
        ageMs < FRESH_MS -> CacheUse.FRESH
        ageMs < MAX_AGE_MS -> CacheUse.STALE
        else -> CacheUse.EXPIRED
    }

    /** Playlists an API key can never read: Liked Music, liked videos and Watch later. */
    private val PRIVATE_LISTS = setOf("LM", "LL", "WL")

    private val PLAYLIST_ID = Regex("^[A-Za-z0-9_-]{2,64}$")
    private val VIDEO_ID = Regex("^[A-Za-z0-9_-]{6,20}$")

    private val YOUTUBE_HOSTS = setOf(
            "music.youtube.com", "youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be")

    /**
     * The YouTube playlist [link] names, or null when it names none that can be listed: another
     * service, no playlist, or one of the private lists. [link] may carry the `targetPackage|uri`
     * envelope the watch is sent.
     */
    fun playlistIdFor(link: String): String? {
        val uri = try {
            URI(StreamingShortcutLinks.unwrapRemoteTarget(link.trim()))
        } catch (_: URISyntaxException) {
            return null
        }
        val host = uri.host?.lowercase() ?: return null
        if (host !in YOUTUBE_HOSTS) return null
        val list = uri.rawQuery.orEmpty().split('&')
                .firstOrNull { it.startsWith("list=") }
                ?.substringAfter('=')
                ?.let { decode(it) }
                ?.trim()
                ?: return null
        if (!PLAYLIST_ID.matches(list) || list in PRIVATE_LISTS) return null
        return list
    }

    /** The song [videoId] inside [playlistId] - what the watch sends back to play it. */
    fun trackLink(videoId: String, playlistId: String): String =
            "https://music.youtube.com/watch?v=$videoId&list=$playlistId"

    /**
     * The channel a song was published by, as the artist to show: YouTube Music publishes its own
     * tracks under "<artist> - Topic", and the suffix is noise on the wrist. Anything else - a
     * fan's upload, a label's channel - is shown as the channel it is.
     */
    fun artistOf(channelTitle: String?): String? {
        val name = channelTitle?.trim().orEmpty()
        if (name.isEmpty()) return null
        return name.removeSuffix(TOPIC_SUFFIX).trim().ifEmpty { null }
    }

    data class Track(val videoId: String, val title: String, val artist: String?)

    data class Page(val tracks: List<Track>, val nextPageToken: String?, val totalResults: Int?)

    /** The fields a page is asked for: the songs' titles, channels, ids and privacy, no more. */
    const val PAGE_FIELDS = "nextPageToken,pageInfo/totalResults," +
            "items(snippet(title,videoOwnerChannelTitle),contentDetails/videoId,status/privacyStatus)"

    /**
     * One `playlistItems.list` page, or null when [json] is not one.
     *
     * A playlist keeps its deleted and private videos as placeholders ("Deleted video", "Private
     * video") that cannot be played by anyone; only public and unlisted songs are listed.
     */
    fun parsePage(json: String): Page? {
        val root = try {
            JSONObject(json)
        } catch (_: Exception) {
            return null
        }
        val items = root.optJSONArray("items") ?: return null
        val tracks = mutableListOf<Track>()
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val privacy = item.optJSONObject("status")?.optString("privacyStatus").orEmpty()
            if (privacy.isNotEmpty() && privacy !in PLAYABLE_PRIVACY) continue
            val videoId = item.optJSONObject("contentDetails")?.optString("videoId").orEmpty()
            if (!VIDEO_ID.matches(videoId)) continue
            val snippet = item.optJSONObject("snippet") ?: continue
            val title = snippet.optString("title").trim()
            if (title.isEmpty()) continue
            val artist = artistOf(snippet.optString("videoOwnerChannelTitle"))
            // No privacy status was asked for (an older request) and no channel: a placeholder.
            if (privacy.isEmpty() && artist == null && title in PLACEHOLDER_TITLES) continue
            tracks += Track(videoId, title, artist)
        }
        val next = root.optString("nextPageToken").takeIf { it.isNotBlank() }
        val total = root.optJSONObject("pageInfo")?.takeIf { it.has("totalResults") }
                ?.optInt("totalResults")
        return Page(tracks, next, total)
    }

    private fun decode(value: String): String? = try {
        URLDecoder.decode(value, "UTF-8")
    } catch (_: IllegalArgumentException) {
        null
    }

    private const val TOPIC_SUFFIX = " - Topic"
    private val PLAYABLE_PRIVACY = setOf("public", "unlisted")
    private val PLACEHOLDER_TITLES = setOf("Deleted video", "Private video")
}
