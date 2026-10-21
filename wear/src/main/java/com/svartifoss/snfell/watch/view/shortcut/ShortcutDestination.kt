package com.svartifoss.snfell.watch.view.shortcut

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/**
 * What the watch can tell about a streaming destination from its launch target alone - the list
 * entry id or a menu entry's remote URI, both either a link or the `targetPackage|link` form the
 * phone sends. Pure, so the recognition is pinned by a JVM test.
 *
 * The phone owns the full link recognition (`StreamingShortcutLinks`, `StreamingCollection`);
 * these are the two questions the shortcut screen has to answer on its own, mirroring the phone's
 * rules for them: which service's mark to draw when the phone sent no glyph, and whether this is
 * YouTube Music's Liked Music.
 */
internal object ShortcutDestination {

    /** The services the watch carries a mark of its own for (`res/drawable/service_mark_*`). */
    enum class Service { YOUTUBE_MUSIC, SPOTIFY, SOUNDCLOUD, DEEZER }

    /** The package of the `targetPackage|link` form, or null for a plain link. */
    fun packageOf(target: String): String? {
        val separator = target.indexOf('|')
        if (separator <= 0 || separator == target.lastIndex) return null
        return target.substring(0, separator).takeIf { ANDROID_PACKAGE_NAME.matches(it) }
    }

    /** The link inside [target], whichever form it is in. */
    fun linkOf(target: String): String =
            if (packageOf(target) != null) target.substringAfter('|') else target

    /**
     * The service [target] plays from: read from its link, which names the service whatever app
     * opens it, and from the package only when the link says nothing - a client of a service
     * still shows that service's mark.
     */
    fun serviceOf(target: String): Service? =
            serviceOfLink(linkOf(target)) ?: packageOf(target)?.let(PACKAGES::get)

    /**
     * Whether [target] is YouTube Music's Liked Music - the playlist id `LM`, which every account
     * has under that one id. The phone's `StreamingCollection.forLink` makes the same call.
     */
    fun isYouTubeMusicLikedMusic(target: String): Boolean {
        val uri = parse(linkOf(target)) ?: return false
        return hostMatches(uri.host.orEmpty().lowercase(Locale.US), "youtube.com") &&
                queryParameter(uri.rawQuery, "list") == "LM"
    }

    private fun serviceOfLink(link: String): Service? {
        val lower = link.trim().lowercase(Locale.US)
        val scheme = lower.substringBefore(':', "")
        when (scheme) {
            "spotify" -> return Service.SPOTIFY
            "deezer" -> return Service.DEEZER
            "soundcloud" -> return Service.SOUNDCLOUD
        }
        val host = parse(link.trim())?.host?.lowercase(Locale.US) ?: return null
        return when {
            hostMatches(host, "youtube.com") || hostMatches(host, "youtu.be") ->
                Service.YOUTUBE_MUSIC
            hostMatches(host, "spotify.com") || hostMatches(host, "spotify.link") ->
                Service.SPOTIFY
            hostMatches(host, "soundcloud.com") -> Service.SOUNDCLOUD
            hostMatches(host, "deezer.com") || hostMatches(host, "deezer.page.link") ->
                Service.DEEZER
            else -> null
        }
    }

    private val PACKAGES = mapOf(
            "com.google.android.apps.youtube.music" to Service.YOUTUBE_MUSIC,
            "com.spotify.music" to Service.SPOTIFY,
            "com.soundcloud.android" to Service.SOUNDCLOUD,
            "deezer.android.app" to Service.DEEZER)

    private val ANDROID_PACKAGE_NAME = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")

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
