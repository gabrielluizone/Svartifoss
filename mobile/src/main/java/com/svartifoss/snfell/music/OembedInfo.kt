package com.svartifoss.snfell.music

import org.json.JSONException
import org.json.JSONObject

/**
 * What a streaming service's public oEmbed answer says about a link: its [title] (the name a user
 * would give the shortcut) and the [thumbnailUrl] of its cover. One request carries both, which is
 * why naming a link and fetching its cover cost the same single round trip.
 */
data class OembedInfo(val title: String?, val thumbnailUrl: String?)

/** Pure decoding of an oEmbed response, kept apart from the fetcher so it can be pinned by a JVM test. */
object OembedParser {
    /** Long enough for any real playlist name, short enough not to swamp a watch row. */
    const val MAX_TITLE_LENGTH = 80

    /** Returns null for anything that is not a JSON object; a missing field is a null member. */
    fun parse(json: String): OembedInfo? = try {
        val root = JSONObject(json)
        OembedInfo(
                title = cleanTitle(root.optString("title")),
                thumbnailUrl = root.optString("thumbnail_url").takeIf { it.isNotBlank() }
        )
    } catch (_: JSONException) {
        null
    }

    /**
     * Turns a service's title into something fit for a list row: the handful of HTML entities
     * SoundCloud and Deezer leave in it decoded, runs of whitespace collapsed, and a ceiling on the
     * length. Null when nothing is left, so "the service knew no name" and "the name was empty" are
     * one case for the caller.
     */
    fun cleanTitle(raw: String?): String? {
        if (raw == null) return null
        val decoded = raw
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                // Last, so "&amp;lt;" decodes to the literal text "&lt;" rather than to "<".
                .replace("&amp;", "&")
        val collapsed = decoded.split(WHITESPACE).filter(String::isNotEmpty).joinToString(" ")
        if (collapsed.isEmpty()) return null
        return if (collapsed.length <= MAX_TITLE_LENGTH) {
            collapsed
        } else {
            collapsed.take(MAX_TITLE_LENGTH - 1).trimEnd() + "…"
        }
    }

    private val WHITESPACE = Regex("\\s+")
}
