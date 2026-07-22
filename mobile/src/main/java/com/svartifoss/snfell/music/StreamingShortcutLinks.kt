package com.svartifoss.snfell.music

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/** Streaming services that can be identified from a user-provided share link or deep link. */
enum class StreamingService(val packageName: String?) {
    YOUTUBE_MUSIC("com.google.android.apps.youtube.music"),
    SPOTIFY("com.spotify.music"),
    DEEZER("deezer.android.app"),
    TIDAL("com.aspiro.tidal"),
    APPLE_MUSIC("com.apple.android.music"),
    AMAZON_MUSIC("com.amazon.mp3"),
    SOUNDCLOUD("com.soundcloud.android"),
    GENERIC(null)
}

/** Kind of streaming entity encoded by a share link. This is intentionally independent from a
 * specific provider so the editor and watch can describe a shortcut without service APIs. */
enum class StreamingContentType {
    TRACK,
    PLAYLIST,
    ALBUM,
    ARTIST,
    SHOW,
    EPISODE,
    MIX,
    UNKNOWN;

    /** Entities for which a MediaSession can reasonably accept playFromUri. ARTIST is included:
     *  both Spotify (`spotify:artist:…` plays the artist's top tracks) and most apps interpret an
     *  artist URI as "play this artist" rather than only navigating to the profile page. */
    val isPlayable: Boolean
        get() = this == TRACK || this == PLAYLIST || this == ALBUM || this == ARTIST ||
                this == SHOW || this == EPISODE || this == MIX
}

data class StreamingLinkInfo(
        val service: StreamingService,
        val contentType: StreamingContentType,
        val canonicalLink: String
)

/**
 * Pure link handling shared by the editor, its descriptions and [MusicService]. These are link
 * shortcuts, not account/API integrations: the target streaming app remains responsible for
 * interpreting the link and deciding whether playback starts immediately.
 */
object StreamingShortcutLinks {
    const val OPEN_MODE_KEY = "streaming_open_mode"
    const val OPEN_MODE_APP = "app"
    const val OPEN_MODE_DEFAULT = "default"
    const val OPEN_MODE_CHOOSER = "chooser"
    /** Kept for one-way migration of builds that exposed the old boolean switch. */
    const val PREFER_INSTALLED_APP_KEY = "prefer_streaming_apps"

    private val unsafeSchemes = setOf("content", "data", "file", "intent", "javascript")
    private val sharedLinkPattern = Regex(
        "(?i)(?:https?://|spotify:|deezer:|tidal:|musics:|amzn-music:|soundcloud:)[^\\s<>\\\"]+"
    )

    private val spotifyEntityTypes = setOf(
        "track", "album", "playlist", "artist", "show", "episode"
    )

    fun detect(rawLink: String): StreamingService {
        val link = canonicalize(rawLink)
        val uri = parse(link) ?: return StreamingService.GENERIC
        val scheme = uri.scheme?.lowercase(Locale.US).orEmpty()
        val host = uri.host?.lowercase(Locale.US).orEmpty()
        val path = uri.path?.lowercase(Locale.US).orEmpty()

        return when {
            scheme == "spotify" || hostMatches(host, "spotify.com") ||
                    hostMatches(host, "spotify.link") -> StreamingService.SPOTIFY
            scheme == "deezer" || hostMatches(host, "deezer.com") ||
                    hostMatches(host, "deezer.page.link") -> StreamingService.DEEZER
            scheme == "tidal" || hostMatches(host, "tidal.com") -> StreamingService.TIDAL
            scheme == "musics" || hostMatches(host, "music.apple.com") ->
                StreamingService.APPLE_MUSIC
            scheme == "amzn-music" || host.startsWith("music.amazon.") ||
                    (hostMatches(host, "amazon.com") && path.startsWith("/music")) ->
                StreamingService.AMAZON_MUSIC
            scheme == "soundcloud" || hostMatches(host, "soundcloud.com") ->
                StreamingService.SOUNDCLOUD
            hostMatches(host, "youtube.com") || hostMatches(host, "youtu.be") ->
                StreamingService.YOUTUBE_MUSIC
            else -> StreamingService.GENERIC
        }
    }

    /** Parses both the provider and entity kind locally. No network lookup is performed, so
     * provider-owned redirect links (spotify.link, on.soundcloud.com, ...) remain UNKNOWN until
     * the user opens them, while normal share URLs and app URIs are classified immediately. */
    fun inspect(rawLink: String): StreamingLinkInfo {
        val link = canonicalize(rawLink)
        val service = detect(link)
        return StreamingLinkInfo(service, detectContentType(link, service), link)
    }

    fun detectContentType(rawLink: String): StreamingContentType {
        val link = canonicalize(rawLink)
        return detectContentType(link, detect(link))
    }

    /** Adds https:// to a pasted host while leaving normal web and app URI schemes intact. */
    fun canonicalize(rawLink: String): String {
        val link = rawLink.trim()
        if (link.isEmpty() || SCHEME_PREFIX.containsMatchIn(link)) {
            return link
        }
        val firstSegment = link.substringBefore('/').substringBefore('?').substringBefore('#')
        return if ('.' in firstSegment) "https://$link" else link
    }

    /** Pulls the first usable URL/app URI out of text received from Android's Sharesheet. Share
     * payloads commonly contain a track title followed by a newline and the actual URL. */
    fun extractSharedLink(sharedText: String): String? {
        val matched = sharedLinkPattern.find(sharedText)?.value
                ?.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '}')
        if (!matched.isNullOrBlank()) return canonicalize(matched)

        // Clipboard/manual input may be a bare host rather than prose. Do not interpret an
        // arbitrary sentence containing a dot as a URL.
        val trimmed = sharedText.trim()
        if (!trimmed.any(Char::isWhitespace)) {
            val canonical = canonicalize(trimmed)
            if (isSafeLink(canonical)) return canonical
        }
        return null
    }

    /** ACTION_VIEW-safe input accepted by the editor. Generic app schemes remain supported, but
     * schemes that can expose local content or execute intent/javascript payloads are rejected. */
    fun isSafeLink(rawLink: String): Boolean {
        val uri = parse(canonicalize(rawLink)) ?: return false
        val scheme = uri.scheme?.lowercase(Locale.US).orEmpty()
        if (scheme.isBlank() || scheme in unsafeSchemes) return false
        return if (scheme == "http" || scheme == "https") {
            !uri.host.isNullOrBlank()
        } else {
            uri.schemeSpecificPart.isNotBlank()
        }
    }

    /** Suggested title for a shared payload: prefer its subject, otherwise keep the prose before
     * the URL. Returning an empty string lets the editor ask the user instead of inventing a name. */
    fun suggestedName(sharedText: String, subject: String? = null): String {
        subject?.trim()?.takeIf(String::isNotBlank)?.let { return it }
        val link = extractSharedLink(sharedText) ?: return ""
        return sharedText.substringBefore(link, "")
                .trim()
                .trimEnd('-', '–', '—', ':', '·')
                .lineSequence()
                .firstOrNull(String::isNotBlank)
                ?.trim()
                .orEmpty()
    }

    fun supportsShuffle(rawLink: String): Boolean =
        detect(rawLink) == StreamingService.YOUTUBE_MUSIC &&
                detectContentType(rawLink) == StreamingContentType.PLAYLIST

    fun hasShuffle(rawLink: String): Boolean {
        val query = canonicalize(rawLink).substringBefore('#').substringAfter('?', "")
        return query.split('&').any { parameter ->
            parameter.substringBefore('=').equals("shuffle", ignoreCase = true) &&
                    parameter.substringAfter('=', "").equals("true", ignoreCase = true)
        }
    }

    fun stripShuffle(rawLink: String): String {
        val link = canonicalize(rawLink)
        val fragmentIndex = link.indexOf('#')
        val fragment = if (fragmentIndex >= 0) link.substring(fragmentIndex) else ""
        val withoutFragment = if (fragmentIndex >= 0) link.substring(0, fragmentIndex) else link
        val queryIndex = withoutFragment.indexOf('?')
        if (queryIndex < 0) return link

        val keptParameters = withoutFragment.substring(queryIndex + 1)
            .split('&')
            .filter { parameter ->
                !parameter.substringBefore('=').equals("shuffle", ignoreCase = true)
            }
        val base = withoutFragment.substring(0, queryIndex)
        return base + if (keptParameters.isEmpty()) fragment
        else "?${keptParameters.joinToString("&")}$fragment"
    }

    /** Shuffle is deliberately only encoded for YouTube Music, where this parameter is known. */
    fun withYoutubeShuffle(rawLink: String): String {
        val link = stripShuffle(rawLink)
        if (!supportsShuffle(link)) return link
        val fragmentIndex = link.indexOf('#')
        val fragment = if (fragmentIndex >= 0) link.substring(fragmentIndex) else ""
        val withoutFragment = if (fragmentIndex >= 0) link.substring(0, fragmentIndex) else link
        val separator = if ('?' in withoutFragment) "&" else "?"
        return "$withoutFragment${separator}shuffle=true$fragment"
    }

    /** Link for a specifically targeted installed app. Spotify web URLs become app URIs. */
    fun forInstalledApp(rawLink: String): String {
        val link = normalizeYoutubePlaylist(canonicalize(rawLink))
        return if (detect(link) == StreamingService.SPOTIFY) spotifyAppUri(link) ?: link else link
    }

    /** Browser/chooser-safe fallback. A user-entered Spotify app URI is converted back to web. */
    fun forBrowser(rawLink: String): String {
        val link = normalizeYoutubePlaylist(canonicalize(rawLink))
        return spotifyWebUrl(link) ?: link
    }

    /** Converts supported open.spotify.com entity URLs into Spotify's documented URI shape. */
    fun spotifyAppUri(rawLink: String): String? {
        val uri = parse(canonicalize(rawLink)) ?: return null
        if (uri.scheme.equals("spotify", ignoreCase = true)) {
            val parts = uri.schemeSpecificPart.trimStart('/').split(':', '/').filter(String::isNotBlank)
            return spotifyEntity(parts)
        }
        if (!hostMatches(uri.host?.lowercase(Locale.US).orEmpty(), "open.spotify.com")) {
            return null
        }

        val pathParts = uri.path.orEmpty().split('/').filter(String::isNotBlank)
        val entityIndex = pathParts.indexOfFirst { it.lowercase(Locale.US) in spotifyEntityTypes }
        if (entityIndex < 0 || entityIndex + 1 >= pathParts.size) return null
        return spotifyEntity(listOf(pathParts[entityIndex], pathParts[entityIndex + 1]))
    }

    /** URI passed to MediaSession.playFromUri. For YouTube Music this canonicalises share links
     *  (youtu.be short links, plain youtube.com, `/playlist`) into the `music.youtube.com/watch`
     *  form, which is the shape that reliably *starts playback* rather than only opening a page -
     *  the same distinction that makes the Liked Music `watch?list=LM` deep link work. */
    fun forPlayback(rawLink: String): String {
        val link = forInstalledApp(rawLink)
        return if (detect(link) == StreamingService.YOUTUBE_MUSIC) {
            youtubeMusicPlaybackUri(link)
        } else {
            link
        }
    }

    private fun youtubeMusicPlaybackUri(rawLink: String): String {
        val uri = parse(rawLink) ?: return rawLink
        val host = uri.host?.lowercase(Locale.US).orEmpty()
        // youtu.be/<id> short links: the video id is the first path segment.
        if (hostMatches(host, "youtu.be")) {
            val videoId = uri.path.orEmpty().trimStart('/').substringBefore('/')
            if (videoId.isBlank()) return rawLink
            val listParam = parseQuery(uri.rawQuery.orEmpty())["list"]
            val suffix = if (!listParam.isNullOrBlank()) "&list=$listParam" else ""
            return "https://music.youtube.com/watch?v=$videoId$suffix"
        }
        // Plain youtube.com watch/playlist links: retarget the host at the music app.
        if (host == "youtube.com" || host == "www.youtube.com" || host == "m.youtube.com") {
            return try {
                URI("https", "music.youtube.com", uri.path, uri.query, uri.fragment).toString()
            } catch (_: URISyntaxException) {
                rawLink
            }
        }
        return rawLink
    }

    private fun spotifyWebUrl(rawLink: String): String? {
        val uri = parse(rawLink) ?: return null
        if (!uri.scheme.equals("spotify", ignoreCase = true)) return null
        val parts = uri.schemeSpecificPart.trimStart('/').split(':', '/').filter(String::isNotBlank)
        val entity = spotifyEntity(parts) ?: return null
        val entityParts = entity.removePrefix("spotify:").split(':')
        return "https://open.spotify.com/${entityParts[0]}/${entityParts[1]}"
    }

    private fun spotifyEntity(parts: List<String>): String? {
        if (parts.size < 2) return null
        val type = parts[0].lowercase(Locale.US)
        val id = parts[1].substringBefore('?').substringBefore('#')
        if (type !in spotifyEntityTypes || id.isBlank()) return null
        return "spotify:$type:$id"
    }

    private fun detectContentType(
            link: String,
            service: StreamingService
    ): StreamingContentType {
        val uri = parse(link) ?: return StreamingContentType.UNKNOWN
        val pathParts = uri.path.orEmpty().split('/').filter(String::isNotBlank)
                .map { it.lowercase(Locale.US) }
        val schemeParts = uri.schemeSpecificPart.orEmpty().trimStart('/')
                .split(':', '/').filter(String::isNotBlank)
                .map { it.substringBefore('?').substringBefore('#').lowercase(Locale.US) }
        val entityParts = if (uri.scheme.equals("http", ignoreCase = true) ||
                uri.scheme.equals("https", ignoreCase = true)) pathParts else schemeParts
        val query = parseQuery(uri.rawQuery.orEmpty())

        fun entityFrom(parts: List<String>): StreamingContentType {
            val entity = parts.firstOrNull { it in CONTENT_ENTITY_NAMES }
                    ?: return StreamingContentType.UNKNOWN
            return contentTypeForEntity(entity)
        }

        return when (service) {
            StreamingService.SPOTIFY -> {
                if (uri.scheme.equals("spotify", ignoreCase = true)) entityFrom(schemeParts)
                else entityFrom(pathParts)
            }
            StreamingService.YOUTUBE_MUSIC -> when {
                pathParts.firstOrNull() == "playlist" -> StreamingContentType.PLAYLIST
                pathParts.firstOrNull() == "watch" && query["v"].isNullOrBlank() &&
                        !query["list"].isNullOrBlank() -> StreamingContentType.PLAYLIST
                pathParts.firstOrNull() == "watch" ||
                        hostMatches(uri.host?.lowercase(Locale.US).orEmpty(), "youtu.be") ->
                    StreamingContentType.TRACK
                pathParts.firstOrNull() == "channel" ||
                        pathParts.firstOrNull()?.startsWith("@") == true ->
                    StreamingContentType.ARTIST
                pathParts.firstOrNull() == "browse" -> when {
                    pathParts.getOrNull(1)?.startsWith("mpre") == true ->
                        StreamingContentType.ALBUM
                    pathParts.getOrNull(1)?.startsWith("vl") == true ->
                        StreamingContentType.PLAYLIST
                    pathParts.getOrNull(1)?.startsWith("uc") == true ->
                        StreamingContentType.ARTIST
                    else -> StreamingContentType.UNKNOWN
                }
                else -> StreamingContentType.UNKNOWN
            }
            StreamingService.DEEZER,
            StreamingService.TIDAL -> entityFrom(entityParts)
            StreamingService.APPLE_MUSIC -> when {
                "playlist" in entityParts -> StreamingContentType.PLAYLIST
                "artist" in entityParts -> StreamingContentType.ARTIST
                "album" in entityParts && !query["i"].isNullOrBlank() ->
                    StreamingContentType.TRACK
                "album" in entityParts -> StreamingContentType.ALBUM
                else -> entityFrom(entityParts)
            }
            StreamingService.AMAZON_MUSIC -> when {
                entityParts.any { it == "albums" || it == "album" } ->
                    StreamingContentType.ALBUM
                entityParts.any { it == "playlists" || it == "playlist" } ->
                    StreamingContentType.PLAYLIST
                entityParts.any { it == "tracks" || it == "track" } ->
                    StreamingContentType.TRACK
                entityParts.any { it == "artists" || it == "artist" } ->
                    StreamingContentType.ARTIST
                else -> StreamingContentType.UNKNOWN
            }
            StreamingService.SOUNDCLOUD -> when {
                "sets" in pathParts -> StreamingContentType.PLAYLIST
                uri.host?.equals("on.soundcloud.com", ignoreCase = true) == true ->
                    StreamingContentType.UNKNOWN
                pathParts.size >= 2 -> StreamingContentType.TRACK
                pathParts.size == 1 -> StreamingContentType.ARTIST
                else -> StreamingContentType.UNKNOWN
            }
            StreamingService.GENERIC -> entityFrom(entityParts)
        }
    }

    private fun parseQuery(rawQuery: String): Map<String, String> = rawQuery.split('&')
            .mapNotNull { parameter ->
                val key = parameter.substringBefore('=').lowercase(Locale.US)
                if (key.isBlank()) null else key to parameter.substringAfter('=', "")
            }
            .toMap()

    private fun contentTypeForEntity(entity: String): StreamingContentType = when (entity) {
        "track", "tracks", "song", "songs" -> StreamingContentType.TRACK
        "playlist", "playlists", "collection" -> StreamingContentType.PLAYLIST
        "album", "albums" -> StreamingContentType.ALBUM
        "artist", "artists" -> StreamingContentType.ARTIST
        "show", "shows", "podcast", "podcasts" -> StreamingContentType.SHOW
        "episode", "episodes" -> StreamingContentType.EPISODE
        "mix", "mixes", "radio" -> StreamingContentType.MIX
        else -> StreamingContentType.UNKNOWN
    }

    private fun normalizeYoutubePlaylist(rawLink: String): String {
        val uri = parse(rawLink) ?: return rawLink
        if (detect(rawLink) != StreamingService.YOUTUBE_MUSIC || uri.path != "/playlist") {
            return rawLink
        }
        return try {
            URI(
                uri.scheme,
                uri.userInfo,
                uri.host,
                uri.port,
                "/watch",
                uri.query,
                uri.fragment
            ).toString()
        } catch (_: URISyntaxException) {
            rawLink
        }
    }

    private fun parse(link: String): URI? = try {
        URI(link)
    } catch (_: URISyntaxException) {
        null
    }

    private fun hostMatches(host: String, root: String): Boolean =
        host == root || host.endsWith(".$root")

    private val SCHEME_PREFIX = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")

    private val CONTENT_ENTITY_NAMES = setOf(
        "track", "tracks", "song", "songs",
        "playlist", "playlists", "collection",
        "album", "albums", "artist", "artists",
        "show", "shows", "podcast", "podcasts",
        "episode", "episodes", "mix", "mixes", "radio"
    )
}
