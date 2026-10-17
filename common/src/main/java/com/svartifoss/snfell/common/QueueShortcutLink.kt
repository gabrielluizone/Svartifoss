package com.svartifoss.snfell.common

import java.net.URI
import java.net.URLEncoder

/**
 * A media-session URI that can safely be offered as a user-editable shortcut.
 *
 * Queue items from local players commonly carry a `content://` URI. That URI is useful to the
 * phone for artwork or file metadata, but it is not a portable shortcut and must not be opened by
 * the watch. Web links and provider app schemes are safe to pass to the shortcut editor; the
 * editor still performs the provider-specific validation before saving.
 */
object QueueShortcutLink {
    const val YOUTUBE_MUSIC_PACKAGE = "com.google.android.apps.youtube.music"

    private val unsafeSchemes = setOf("content", "data", "file", "intent", "javascript")

    /** Returns the original URI when it is a usable shortcut candidate, otherwise null. */
    fun candidate(raw: String?): String? {
        val value = raw?.trim().takeIf { !it.isNullOrBlank() } ?: return null
        val uri = try {
            URI(value)
        } catch (_: Exception) {
            return null
        }
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme in unsafeSchemes || uri.schemeSpecificPart.isNullOrBlank()) return null
        if ((scheme == "http" || scheme == "https") && uri.host.isNullOrBlank()) return null
        return value
    }

    /**
     * Best-effort fallback for players such as YouTube Music that publish only title and artist
     * in their queue. It is intentionally a search link, not a guessed video URL: the phone uses
     * the same query through MediaSession.playFromSearch when the shortcut is started.
     */
    fun youtubeMusicSearch(title: String?, subtitle: String?): String? {
        val query = listOfNotNull(title?.trim(), subtitle?.trim())
                .filter(String::isNotBlank)
                .joinToString(" ")
                .takeIf { it.isNotBlank() }
                ?: return null
        val encoded = try {
            URLEncoder.encode(query, Charsets.UTF_8.name()).replace("+", "%20")
        } catch (_: Exception) {
            return null
        }
        return "https://music.youtube.com/search?q=$encoded"
    }
}
