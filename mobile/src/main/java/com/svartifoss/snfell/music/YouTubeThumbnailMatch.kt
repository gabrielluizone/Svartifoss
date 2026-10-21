package com.svartifoss.snfell.music

import java.text.Normalizer

/**
 * The deliberately strict identity rule for a YouTube thumbnail fallback.
 *
 * Search results are accepted only when both the channel and the video title identify the same
 * thing the media session reported. Punctuation, case and accents are presentation differences;
 * extra or missing words are not. A wrong thumbnail is more surprising than no thumbnail.
 */
object YouTubeThumbnailMatch {

    private const val MAX_QUERY_LENGTH = 120

    /**
     * What every cache key for a YouTube thumbnail starts with. [OnlineArtworkCache] reads it to
     * tell these entries apart, because they are the one kind it may not keep indefinitely.
     */
    const val KEY_PREFIX = "youtube|"

    fun cacheKey(channel: String, title: String): String? {
        if (normalize(channel).isBlank() || normalize(title).isBlank()) return null
        return "$KEY_PREFIX${channel.trim()}|${title.trim()}"
    }

    fun searchQuery(channel: String, title: String): String? {
        val cleanChannel = channel.trim()
        val cleanTitle = title.trim()
        if (cleanChannel.isEmpty() || cleanTitle.isEmpty()) return null
        // The channel is the stable identity of a live stream. Putting it first makes YouTube
        // rank videos from that channel before similarly worded compilation titles, while the
        // exact title check below still prevents accepting a wrong video. Search punctuation and
        // emoji are presentation, not useful query terms; some live titles otherwise make the API
        // return an empty result set even though the same title is searchable without them.
        return normalize("$cleanChannel $cleanTitle")
                .take(MAX_QUERY_LENGTH)
                .trim()
                .takeIf { it.isNotEmpty() }
    }

    fun accepts(
            expectedChannel: String,
            expectedTitle: String,
            resultChannel: String,
            resultTitle: String
    ): Boolean = normalize(expectedChannel) == normalize(resultChannel) &&
            normalize(expectedTitle) == normalize(resultTitle)

    /** Lower-case, accent-free, letters/digits only, separated by single spaces. */
    fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
        return buildString(decomposed.length) {
            var lastWasSpace = true
            for (character in decomposed) {
                when {
                    Character.getType(character) == Character.NON_SPACING_MARK.toInt() -> Unit
                    Character.isLetterOrDigit(character) -> {
                        append(character.lowercaseChar())
                        lastWasSpace = false
                    }
                    !lastWasSpace -> {
                        append(' ')
                        lastWasSpace = true
                    }
                }
            }
        }.trim()
    }
}
