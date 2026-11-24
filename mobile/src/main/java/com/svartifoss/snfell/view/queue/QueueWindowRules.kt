package com.svartifoss.snfell.view.queue

import com.svartifoss.snfell.notifications.MediaActionSemantics
import java.text.Normalizer
import java.util.Locale

/**
 * The decisions of the notification's queue window ([PlaybackQueueActivity]) that do not need a
 * screen. Pure, so `QueueWindowRulesTest` pins them.
 */
object QueueWindowRules {

    /**
     * Whether a queue row matches what was typed in the window's search field: every word of
     * [query] appears in the title or the artist, ignoring case and accents.
     */
    fun matches(query: String, title: String?, artist: String?): Boolean {
        val words = normalize(query).split(NON_WORD).filter { it.isNotEmpty() }
        if (words.isEmpty()) return true
        val haystack = normalize("${title.orEmpty()} ${artist.orEmpty()}")
        return words.all { haystack.contains(it) }
    }

    /** One button the window could show beside the transport, with what it does. */
    data class Extra<T>(val item: T, val semantic: String)

    /** The extra buttons chosen, split around the transport row. */
    data class Placement<T>(val left: List<T>, val right: List<T>)

    private val TRANSPORT = setOf(MediaActionSemantics.PREVIOUS, MediaActionSemantics.PLAY,
            MediaActionSemantics.PAUSE, MediaActionSemantics.NEXT)
    private val MODES = setOf(MediaActionSemantics.SHUFFLE, MediaActionSemantics.REPEAT)

    /** Which of a session's own actions are worth a place, best first. */
    private val SESSION_PRIORITY = listOf(MediaActionSemantics.SHUFFLE, MediaActionSemantics.REPEAT,
            MediaActionSemantics.LIKE)

    /**
     * The buttons beside previous / play / next, at most one on each side.
     *
     * [fromPlayer] are the non-transport buttons of the player's media notification, in its order;
     * [fromSession] the custom actions of its media session. The notification's come first: they
     * are what the player chose to show. The session's fill what it left out, because a player can
     * draw its notification with a layout of its own whose buttons no other app can read - Retro
     * Music shows shuffle in the shade, while its notification lists only favourite, previous,
     * play and next, and shuffle exists for other apps only as a session action. Nothing appears
     * twice (by what it does), dislike is left out as on the watch's panel, and shuffle and repeat
     * go on the left with anything else on the right, the arrangement the system's media controls
     * use, so a player's heart stays where it put it.
     */
    fun <T> placeExtras(fromPlayer: List<Extra<T>>, fromSession: List<Extra<T>>): Placement<T> {
        val usable = { extra: Extra<T> ->
            extra.semantic !in TRANSPORT && extra.semantic != MediaActionSemantics.DISLIKE
        }
        val chosen = mutableListOf<Extra<T>>()
        fun take(extra: Extra<T>) {
            if (chosen.size >= MAX_EXTRAS || !usable(extra)) return
            val known = extra.semantic != MediaActionSemantics.UNKNOWN
            if (known && chosen.any { it.semantic == extra.semantic }) return
            chosen += extra
        }
        fromPlayer.forEach(::take)
        fromSession.sortedBy { extra ->
            SESSION_PRIORITY.indexOf(extra.semantic).let { if (it < 0) SESSION_PRIORITY.size else it }
        }.forEach(::take)

        val modes = chosen.filter { it.semantic in MODES }
        val others = chosen.filter { it.semantic !in MODES }
        return when {
            modes.size == 2 -> {
                val shuffleFirst = modes.sortedBy { if (it.semantic == MediaActionSemantics.SHUFFLE) 0 else 1 }
                Placement(listOf(shuffleFirst[0].item), listOf(shuffleFirst[1].item))
            }
            modes.size == 1 -> Placement(listOf(modes[0].item), others.map { it.item })
            others.size == 2 -> Placement(listOf(others[0].item), listOf(others[1].item))
            else -> Placement(emptyList(), others.map { it.item })
        }
    }

    /** Beside the three transport buttons: five in all, as the system's media controls show. */
    private const val MAX_EXTRAS = 2

    /**
     * The HSL saturation below which a cover counts as having no colour (black, white, grey): its
     * hue is noise, so the play button takes a neutral light tone instead of tinting it.
     */
    const val NEUTRAL_SATURATION = 0.12f

    /** Lightness of the play button's fill and of its glyph: always light, glyph always dark. */
    const val CONTAINER_LIGHTNESS = 0.86f
    const val GLYPH_LIGHTNESS = 0.16f

    private fun normalize(text: String): String = Normalizer
            .normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")

    private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")
    private val COMBINING_MARKS = Regex("\\p{M}+")
}
