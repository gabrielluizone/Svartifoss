package com.svartifoss.snfell.music

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/**
 * A collection every account of a service has under one fixed address - the account's liked songs,
 * and Deezer's Flow - as opposed to a playlist someone made.
 *
 * These are the destinations no cover lookup can answer: oEmbed describes public items, and a
 * personal collection is not one, so a saved link to it (and the built-in actions that start it)
 * used to be drawn as a bare glyph in whatever colour the playing song happened to be. Each one
 * gets a cover of its own instead, in the style the services draw them - a gradient behind a white
 * mark - drawn on the phone by `ShortcutCoverArt` from [cover], so it works offline and before any
 * setting is touched. Two services also publish that cover as a public image ([officialArtworkUrl]);
 * with the opt-in artwork lookup on it replaces the drawn one, the same as a fetched playlist cover.
 *
 * Pure, so the link recognition is pinned by a JVM test.
 */
enum class StreamingCollection(
        val service: StreamingService,
        /** The service's own artwork for this collection, or null when it publishes none. */
        val officialArtworkUrl: String?,
        /** What the drawn cover looks like. */
        val cover: CoverGradient
) {
    /** YouTube Music's "Liked Music": a thumbs-up over violet fading to pink, top to bottom. */
    YOUTUBE_MUSIC_LIKED(
            StreamingService.YOUTUBE_MUSIC,
            "https://www.gstatic.com/youtube/media/ytm/images/pbg/liked-songs-delhi-1200.png",
            CoverGradient(0xFF8C70F6.toInt(), 0xFFFE5DAD.toInt(), CoverGradient.Direction.VERTICAL)),

    /** Spotify's "Liked Songs": a heart over deep violet fading to mint, corner to corner. */
    SPOTIFY_LIKED(
            StreamingService.SPOTIFY,
            "https://misc.scdn.co/liked-songs/liked-songs-640.png",
            CoverGradient(0xFF4100F5.toInt(), 0xFFC3F0D9.toInt(), CoverGradient.Direction.DIAGONAL)),

    /** SoundCloud's "Likes". SoundCloud draws no cover for it, so this one is ours: its orange. */
    SOUNDCLOUD_LIKES(
            StreamingService.SOUNDCLOUD,
            null,
            CoverGradient(0xFFFF8A1F.toInt(), 0xFFFF3A2E.toInt(), CoverGradient.Direction.DIAGONAL)),

    /** Deezer's Flow, the account-wide endless mix. Also ours: Deezer's violet, warming to coral. */
    DEEZER_FLOW(
            StreamingService.DEEZER,
            null,
            CoverGradient(0xFFA238FF.toInt(), 0xFFFF5F6D.toInt(), CoverGradient.Direction.DIAGONAL));

    /**
     * The key `ShortcutArtworkStore` keeps [officialArtworkUrl]'s image under - one per collection,
     * whichever of its several spellings a link used, so the built-in action and a saved link to
     * the same collection share one download.
     */
    val storeKey: String
        get() = STORE_KEY_PREFIX + name.lowercase(Locale.US)

    companion object {
        private const val STORE_KEY_PREFIX = "svartifoss:collection:"

        /** Every [storeKey], so the artwork store's pruning keeps them. */
        val storeKeys: Set<String>
            get() = entries.mapTo(LinkedHashSet()) { it.storeKey }

        /** The collection [rawLink] points at, or null for anything else. Never throws. */
        fun forLink(rawLink: String): StreamingCollection? {
            val link = rawLink.trim()
            if (link.isEmpty()) return null
            val lower = link.lowercase(Locale.US)

            // Spotify's app URIs are opaque, so they are matched as text: the current Liked Songs
            // URI, and the per-user form older clients shared.
            if (lower.startsWith("spotify:")) {
                return if (lower == "spotify:collection:tracks" ||
                        SPOTIFY_USER_COLLECTION.matches(lower)) SPOTIFY_LIKED else null
            }

            val uri = parse(if ("://" in link) link else "https://$link") ?: return null
            val host = uri.host?.lowercase(Locale.US).orEmpty()
            val path = uri.path?.lowercase(Locale.US).orEmpty().trimEnd('/')
            return when {
                // The playlist id, not the path, names it: `watch?list=LM` plays it and
                // `playlist?list=LM` opens it, and both are the same Liked Music.
                hostMatches(host, "youtube.com") &&
                        queryParameter(uri.rawQuery, "list") == "LM" -> YOUTUBE_MUSIC_LIKED
                hostMatches(host, "spotify.com") &&
                        SPOTIFY_WEB_COLLECTION.matches(path) -> SPOTIFY_LIKED
                // `/you/likes` is the signed-in account's own; `/<name>/likes` is the same
                // collection on a profile, and draws the same.
                hostMatches(host, "soundcloud.com") && SOUNDCLOUD_LIKES_PATH.matches(path) ->
                    SOUNDCLOUD_LIKES
                hostMatches(host, "deezer.com") && DEEZER_FLOW_PATH.matches(path) -> DEEZER_FLOW
                else -> null
            }
        }

        private val SPOTIFY_USER_COLLECTION = Regex("^spotify:user:[^:]+:collection$")
        private val SPOTIFY_WEB_COLLECTION = Regex("^(/intl-[a-z-]+)?/collection/tracks$")
        private val SOUNDCLOUD_LIKES_PATH = Regex("^/[^/]+/likes$")
        private val DEEZER_FLOW_PATH = Regex("^(/[a-z]{2}(-[a-z]{2})?)?/flow$")

        private fun parse(link: String): URI? = try {
            URI(link)
        } catch (_: URISyntaxException) {
            null
        }

        private fun hostMatches(host: String, root: String): Boolean =
                host == root || host.endsWith(".$root")

        private fun queryParameter(rawQuery: String?, name: String): String? =
                rawQuery?.split('&')
                        ?.firstOrNull { it.substringBefore('=').equals(name, ignoreCase = true) }
                        ?.substringAfter('=', "")
    }
}

/**
 * The two colours a generated cover fades between, and in which direction. Plain ARGB ints, so the
 * choice stays testable and the renderer is the only part that needs Android.
 */
data class CoverGradient(val start: Int, val end: Int, val direction: Direction) {
    enum class Direction { VERTICAL, DIAGONAL }
}

/**
 * The colours a service's generated cover is drawn in, for a shortcut that has no cover of its own:
 * the service's mark in white (or its launcher icon) over its own colour, so a playlist without
 * artwork is still recognisably a YouTube Music one rather than a tile in an arbitrary colour.
 *
 * Only for [StreamingService.GENERIC] is there no answer - a link to an app this table does not
 * know - and the renderer then takes the colour from that app's own icon.
 */
object ServiceCoverPalette {
    fun gradientFor(service: StreamingService): CoverGradient? = when (service) {
        StreamingService.YOUTUBE_MUSIC -> diagonal(0xFFFF4E45, 0xFF8E0A16)
        StreamingService.SPOTIFY -> diagonal(0xFF1ED760, 0xFF0A5A2C)
        StreamingService.DEEZER -> diagonal(0xFFA238FF, 0xFF4A1590)
        StreamingService.TIDAL -> diagonal(0xFF4A4A4A, 0xFF0B0B0B)
        StreamingService.APPLE_MUSIC -> diagonal(0xFFFB5C74, 0xFFD3122F)
        StreamingService.AMAZON_MUSIC -> diagonal(0xFF25D1DA, 0xFF1B4FA0)
        StreamingService.SOUNDCLOUD -> diagonal(0xFFFF8A1F, 0xFFFF3A2E)
        StreamingService.QOBUZ -> diagonal(0xFF2D6CDF, 0xFF0B1A3D)
        StreamingService.BANDCAMP -> diagonal(0xFF3FB6D8, 0xFF1A5466)
        StreamingService.AUDIOMACK -> diagonal(0xFFFFB000, 0xFFF06400)
        StreamingService.MIXCLOUD -> diagonal(0xFF6A3CFF, 0xFF26137A)
        StreamingService.PANDORA -> diagonal(0xFF3668FF, 0xFF1A2F80)
        StreamingService.GENERIC -> null
    }

    private fun diagonal(start: Long, end: Long) =
            CoverGradient(start.toInt(), end.toInt(), CoverGradient.Direction.DIAGONAL)
}
