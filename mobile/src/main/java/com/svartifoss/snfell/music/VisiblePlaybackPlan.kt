package com.svartifoss.snfell.music

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/**
 * One public way of asking an app, from the foreground, to *play* a shortcut rather than merely
 * show it - the last rung of `MusicService.playDeepLink`, used once the media session and the media
 * browser have both declined.
 *
 * Every technique here is an interface the target app itself declares to Android in its manifest,
 * readable by any app through `PackageManager` (and by a person through `adb shell dumpsys
 * package`). None was learned by decompiling anyone's code, and none may be: see
 * `docs/streaming-app-interop.md` for why that line exists and where the evidence for each one came
 * from. The [storageKey] is persisted by [VisiblePlaybackMemory], so it must never change.
 */
enum class VisiblePlayTechnique(val storageKey: String) {
    /**
     * `android.media.action.MEDIA_PLAY_FROM_SEARCH` - Android's documented "play this" voice
     * contract - carrying the app's own content URI as data. Spotify declares exactly this filter
     * (the action, the `spotify:` scheme, and BROWSABLE, which means it is open even to web pages).
     */
    PLAY_FROM_SEARCH_URI("uri_search"),

    /**
     * An ordinary view link to the app's own URI with Spotify's `:play` suffix. Spotify documents
     * `ACTION_VIEW` for its URIs; the suffix itself is not documented, it is what its users found
     * starts playback (Spotify Community, 2019), so it stays behind [PLAY_FROM_SEARCH_URI] and is
     * only kept while it is seen to work on this phone.
     */
    VIEW_AUTOPLAY("view_autoplay"),

    /** An exported action the app declares for playing a collection, e.g. SoundCloud's Likes. */
    APP_PLAY_ACTION("app_action"),

    /**
     * `MEDIA_PLAY_FROM_SEARCH` with a name and Android's media focus extras, no URI: the same
     * close-match-that-plays the media browser route already falls back to.
     */
    PLAY_FROM_SEARCH_QUERY("name_search");

    companion object {
        fun fromStorageKey(key: String): VisiblePlayTechnique? = entries.firstOrNull { it.storageKey == key }
    }
}

/**
 * An intent to start, described as plain data so the plan is testable on the JVM and the phone
 * builds the actual `Intent` in one place (`VisibleLaunchIntents`).
 *
 * [targetPackage] is never null: every launch names the one app it is for, so nothing here can be
 * resolved by some other app that happens to claim the same action.
 */
data class VisibleLaunch(
        val technique: VisiblePlayTechnique?,
        val action: String,
        val data: String?,
        val targetPackage: String,
        val categories: List<String>,
        val extras: Map<String, String>
) {
    companion object {
        /** The plain open every shortcut already had: show the content, start nothing. */
        fun navigation(link: String, targetPackage: String) = VisibleLaunch(
                technique = null,
                action = VisiblePlaybackPlan.ACTION_VIEW,
                data = link,
                targetPackage = targetPackage,
                categories = listOf(VisiblePlaybackPlan.CATEGORY_BROWSABLE),
                extras = emptyMap())
    }
}

/**
 * Which [VisibleLaunch]es can make a shortcut *play*, best first.
 *
 * Deliberately narrow. A technique is listed only for an app whose manifest declares it and only
 * where something better is not already working: YouTube Music plays through its media browser, so
 * it gets nothing here; Spotify and SoundCloud are the two apps whose shortcuts opened without
 * playing. The phone still checks every candidate against the installed app before using it, and
 * [VisiblePlaybackMemory] drops one that is seen not to start anything.
 *
 * Pure: no Android types, pinned by `VisiblePlaybackPlanTest`.
 */
object VisiblePlaybackPlan {
    const val ACTION_VIEW = "android.intent.action.VIEW"
    const val ACTION_MEDIA_PLAY_FROM_SEARCH = "android.media.action.MEDIA_PLAY_FROM_SEARCH"
    const val CATEGORY_BROWSABLE = "android.intent.category.BROWSABLE"

    /** `SearchManager.QUERY`. */
    const val EXTRA_QUERY = "query"
    /** `MediaStore.EXTRA_MEDIA_FOCUS` and the per-focus extras `MediaStore` defines with it. */
    const val EXTRA_MEDIA_FOCUS = "android.intent.extra.focus"
    const val EXTRA_MEDIA_ARTIST = "android.intent.extra.artist"
    const val EXTRA_MEDIA_ALBUM = "android.intent.extra.album"
    const val EXTRA_MEDIA_TITLE = "android.intent.extra.title"
    const val EXTRA_MEDIA_PLAYLIST = "android.intent.extra.playlist"
    const val FOCUS_ARTIST = "vnd.android.cursor.item/artist"
    const val FOCUS_ALBUM = "vnd.android.cursor.item/album"
    const val FOCUS_AUDIO = "vnd.android.cursor.item/audio"
    const val FOCUS_PLAYLIST = "vnd.android.cursor.item/playlist"
    /** `MediaStore`'s "unstructured" focus: the kind of thing is not known. */
    const val FOCUS_ANY = "vnd.android.cursor.item/*"

    /**
     * SoundCloud's own "Play Likes" action. Its manifest exports it (category DEFAULT) for the
     * launcher shortcut of the same name, which is what makes it a public entry point rather than
     * an internal one; not BROWSABLE, so only an app can send it, and only a launch this phone
     * planned ever does (see `VisiblePlaybackRegistry`).
     */
    const val SOUNDCLOUD_PLAY_LIKES = "com.soundcloud.android.action.SHORTCUT_PLAY_LIKES"

    /** The suffix [VisiblePlayTechnique.VIEW_AUTOPLAY] appends to a Spotify URI. */
    const val SPOTIFY_AUTOPLAY_SUFFIX = ":play"

    private const val SPOTIFY_LIKED_SONGS_URI = "spotify:collection:tracks"

    /**
     * The launches worth trying for [link] in [targetPackage], best first; empty when this rung has
     * nothing better than the plain open.
     *
     * @param name the shortcut's saved name, used only by the name-search techniques.
     */
    fun candidates(
            link: String,
            targetPackage: String,
            contentType: StreamingContentType,
            name: String?
    ): List<VisibleLaunch> {
        val service = StreamingShortcutLinks.detect(link)
        val query = name?.let { StreamingShortcutLinks.searchableName(it) }
        val launches = mutableListOf<VisibleLaunch>()

        if (service == StreamingService.SPOTIFY && targetPackage == StreamingService.SPOTIFY.packageName) {
            // The official app only: its URI form is Spotify's, and an alternative client that
            // opens open.spotify.com links has declared nothing about either technique.
            spotifyPlayableUri(link)?.let { uri ->
                launches += VisibleLaunch(VisiblePlayTechnique.PLAY_FROM_SEARCH_URI,
                        ACTION_MEDIA_PLAY_FROM_SEARCH, uri, targetPackage,
                        listOf(CATEGORY_BROWSABLE), emptyMap())
                launches += VisibleLaunch(VisiblePlayTechnique.VIEW_AUTOPLAY,
                        ACTION_VIEW, uri + SPOTIFY_AUTOPLAY_SUFFIX, targetPackage,
                        listOf(CATEGORY_BROWSABLE), emptyMap())
            }
        }

        if (service == StreamingService.SOUNDCLOUD &&
                targetPackage == StreamingService.SOUNDCLOUD.packageName && isOwnSoundCloudLikes(link)) {
            launches += VisibleLaunch(VisiblePlayTechnique.APP_PLAY_ACTION,
                    SOUNDCLOUD_PLAY_LIKES, null, targetPackage, emptyList(), emptyMap())
        }

        // Never for a personal collection: "Likes" or "Liked Songs" searched by name finds some
        // stranger's playlist of that name, which would play confidently and be wrong.
        if (query != null && StreamingCollection.forLink(link) == null &&
                wantsNameSearch(service, targetPackage, contentType)) {
            launches += nameSearch(query, contentType, targetPackage)
        }
        return launches
    }

    /**
     * Where a technique's outcome is remembered: per app and per kind of thing, because one app can
     * honour a technique for an album and ignore it for a playlist - the 2019 Spotify reports say
     * exactly that about play-from-search.
     */
    fun memoryKey(targetPackage: String, link: String, contentType: StreamingContentType): String {
        val kind = if (StreamingCollection.forLink(link) != null) "collection"
                else contentType.name.lowercase(Locale.US)
        return "$targetPackage/$kind"
    }

    /** A Spotify URI that names something playable, or null (a short spotify.link, say). */
    internal fun spotifyPlayableUri(link: String): String? {
        if (StreamingCollection.forLink(link) == StreamingCollection.SPOTIFY_LIKED) {
            return SPOTIFY_LIKED_SONGS_URI
        }
        return StreamingShortcutLinks.spotifyAppUri(link)
    }

    /**
     * The signed-in account's own Likes - `/you/likes` - and not somebody else's profile Likes,
     * which the "Play Likes" action could only answer with the wrong account's tracks.
     */
    internal fun isOwnSoundCloudLikes(link: String): Boolean {
        val uri = parse(StreamingShortcutLinks.canonicalize(link)) ?: return false
        val host = uri.host?.lowercase(Locale.US).orEmpty()
        if (host != "soundcloud.com" && !host.endsWith(".soundcloud.com")) return false
        return uri.path.orEmpty().lowercase(Locale.US).trimEnd('/') == "/you/likes"
    }

    /**
     * Name search is a close match, never an exact one, so it is offered only where the exact
     * request is known not to play: an artist (whose page only ever navigates, in every app) and a
     * SoundCloud set or track (SoundCloud accepts a collection URI and ignores it). Elsewhere a
     * same-named playlist of somebody else's would be a wrong answer dressed as a right one.
     *
     * SoundCloud's share sheet hands out short `on.soundcloud.com` links, which name neither a set
     * nor a track until they are opened ([StreamingContentType.UNKNOWN]); those are as often one
     * as the other, so they get the same search with the unstructured focus.
     */
    private fun wantsNameSearch(
            service: StreamingService,
            targetPackage: String,
            contentType: StreamingContentType
    ): Boolean = when {
        contentType == StreamingContentType.ARTIST -> true
        service == StreamingService.SOUNDCLOUD && targetPackage == StreamingService.SOUNDCLOUD.packageName ->
            contentType == StreamingContentType.PLAYLIST || contentType == StreamingContentType.TRACK ||
                    contentType == StreamingContentType.UNKNOWN
        else -> false
    }

    private fun nameSearch(
            query: String,
            contentType: StreamingContentType,
            targetPackage: String
    ): VisibleLaunch {
        val focus = when (contentType) {
            StreamingContentType.ARTIST -> FOCUS_ARTIST to EXTRA_MEDIA_ARTIST
            StreamingContentType.ALBUM -> FOCUS_ALBUM to EXTRA_MEDIA_ALBUM
            StreamingContentType.PLAYLIST -> FOCUS_PLAYLIST to EXTRA_MEDIA_PLAYLIST
            StreamingContentType.UNKNOWN -> FOCUS_ANY to null
            else -> FOCUS_AUDIO to EXTRA_MEDIA_TITLE
        }
        val extras = linkedMapOf(EXTRA_QUERY to query, EXTRA_MEDIA_FOCUS to focus.first)
        focus.second?.let { extras[it] = query }
        return VisibleLaunch(VisiblePlayTechnique.PLAY_FROM_SEARCH_QUERY,
                ACTION_MEDIA_PLAY_FROM_SEARCH, null, targetPackage, emptyList(), extras)
    }

    private fun parse(link: String): URI? = try {
        URI(link)
    } catch (_: URISyntaxException) {
        null
    }
}
