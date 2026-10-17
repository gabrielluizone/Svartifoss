package com.svartifoss.snfell.music

import java.text.Normalizer
import java.util.Locale

/**
 * Decides whether an online search result is the record a player is playing, for the lookup that
 * stands in for a cover the player never published.
 *
 * Deezer's `search?q=` ranks by relevance and always answers: a query nobody has heard of still
 * returns whatever matched best, which is wrong for most videos. [OnlineArtworkFetcher]'s two
 * sources accept the first result without checking, and that is right for them - the user chose a
 * face source whose whole point is a picture of *something* about this artist - but not here, where
 * nobody asked and a confidently wrong sleeve on a face is worse than the plain one it replaces.
 * So a result is used only when it can be tied to what is playing.
 *
 * ## What counts as a tie
 *
 * A YouTube video is the case that shaped this: its "artist" is the *channel* and its title is
 * whatever the uploader wrote, so neither is a clean credit. Two ties are accepted.
 *
 *  - **The channel is the performer.** An official channel ("Radiohead"), a label's artist channel
 *    ("Taylor Swift VEVO") or an auto-generated one ("Radiohead - Topic") carries the performer's
 *    name, and the result's artist names the same one.
 *  - **The title names both the performer and the track.** A compilation or a live stream hosted
 *    by somebody else never matches the first, and a result for one artist it happens to mention
 *    (a stream that lists ten bands) matches the title only by coincidence. Naming the *track* as
 *    well is what separates a real hit from a band that merely appears in the title.
 *
 * Anything else is no tie: the caller draws its own cover instead.
 *
 * Pure and free of `android.*`, so the policy is pinned by a JVM test.
 */
object OnlineCoverMatch {

    /** Shorter names match inside too many unrelated words to mean anything. */
    private const val MIN_NAME_LENGTH = 3

    /** A track title this short ("Run", "Up") appears in too many sentences to be evidence. */
    private const val MIN_TRACK_LENGTH = 4

    /** The longest query sent: a title is user-written and can run to a paragraph. */
    private const val MAX_QUERY_LENGTH = 120

    /** Lower-case, accent-free, letters and digits only, single spaces. */
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

    /**
     * The channel's name as a performer's: the suffixes channels add to a name that is otherwise a
     * credit ("- Topic", "VEVO", "Official") are not part of it.
     */
    fun performerFrom(channel: String): String {
        val raw = channel.trim()
        // "TaylorSwiftVEVO" is one word to normalize(), which lower-cases before anything can see
        // where the capitals were. VEVO's channels are the only ones named that way, so the split
        // is made for them alone: "McDonald" must stay one word.
        val spaced = if (raw.endsWith("VEVO")) {
            raw.removeSuffix("VEVO").replace(CAMEL_BOUNDARY, " ")
        } else {
            raw
        }
        var name = normalize(spaced)
        for (suffix in CHANNEL_SUFFIXES) {
            if (name.endsWith(" $suffix")) name = name.removeSuffix(" $suffix").trim()
        }
        return name
    }

    private val CHANNEL_SUFFIXES = listOf("topic", "official", "oficial")

    private val CAMEL_BOUNDARY = Regex("(?<=[a-z])(?=[A-Z])")

    /**
     * What to ask the search for: the channel and the title with the parts that only say what kind
     * of upload this is - brackets ("(Official Video)", "[HD]") and anything after a "|" -
     * removed, because the catalogue has never heard of "Official Video".
     */
    fun searchQuery(channel: String, title: String): String {
        val cleanTitle = title
                .substringBefore('|')
                .replace(BRACKETED, " ")
                .trim()
        val query = "${performerFrom(channel)} $cleanTitle".trim().replace(Regex("\\s+"), " ")
        return query.take(MAX_QUERY_LENGTH).trim()
    }

    private val BRACKETED = Regex("""\([^)]*\)|\[[^\]]*\]""")

    /**
     * Whether a result by [hitArtist] called [hitTrack] is the record behind [channel]'s upload
     * called [title].
     */
    fun accepts(channel: String, title: String, hitArtist: String, hitTrack: String): Boolean {
        val performer = performerFrom(channel)
        val artist = normalize(hitArtist)
        if (artist.length < MIN_NAME_LENGTH) return false

        if (performer.length >= MIN_NAME_LENGTH &&
                (containsPhrase(performer, artist) || containsPhrase(artist, performer))) {
            return true
        }

        val words = normalize(title)
        val track = normalize(hitTrack)
        return containsPhrase(words, artist) &&
                track.length >= MIN_TRACK_LENGTH &&
                containsPhrase(words, track)
    }

    /** [needle] as whole words inside [haystack] - "bread" is in "bread and butter", not in "breadth". */
    private fun containsPhrase(haystack: String, needle: String): Boolean =
            needle.isNotEmpty() && " $haystack ".contains(" $needle ")
}
