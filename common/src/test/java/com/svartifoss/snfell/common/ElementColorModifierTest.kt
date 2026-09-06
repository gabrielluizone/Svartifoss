package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins [ColorModifier.resolveElement] - the per-element Tone's fallback.
 *
 * The whole point of the `follow` sentinel is that Title, Artist and Clock could be given a Tone
 * of their own without changing a single saved theme, published theme or existing install. That
 * promise lives entirely in this one function, and it is the kind of fallback the project keeps
 * extracting and pinning precisely because getting one wrong is silent: the setting still stores,
 * the picker still reads back correctly, and only the colour on the wrist is quietly wrong.
 */
class ElementColorModifierTest {

    @Test
    fun followUsesTheWatchWideTone() {
        assertEquals(
                ColorModifier.PASTEL,
                ColorModifier.resolveElement("follow", ColorModifier.PASTEL))
    }

    @Test
    fun anExplicitToneOverridesTheWatchWideOne() {
        assertEquals(
                ColorModifier.WARM,
                ColorModifier.resolveElement("warm", ColorModifier.PASTEL))
    }

    /** "none" is a decision - the element is deliberately untinted - not an absent value. */
    @Test
    fun noneIsAnOverrideRatherThanAFallback() {
        assertEquals(
                ColorModifier.NONE,
                ColorModifier.resolveElement("none", ColorModifier.VIBRANT))
    }

    /**
     * A value can arrive from an imported backup, a community theme or a newer build. Stripping
     * someone's watch-wide tone off one element is the more surprising answer than keeping it, so
     * an unreadable value resolves the same way `follow` does - never to [ColorModifier.NONE].
     */
    @Test
    fun unknownEmptyAndMissingValuesAllFollow() {
        listOf(null, "", "   ", "sepia").forEach { value ->
            assertEquals(
                    "resolveElement($value) must fall back to the watch-wide tone",
                    ColorModifier.COOL,
                    ColorModifier.resolveElement(value, ColorModifier.COOL))
        }
    }

    /** The default stored on all three preferences has to be the sentinel this function honours. */
    @Test
    fun theShippedDefaultIsTheFollowSentinel() {
        listOf(
                MiscPreferences.WEAR_TITLE_COLOR_MODIFIER,
                MiscPreferences.WEAR_ARTIST_COLOR_MODIFIER,
                MiscPreferences.WEAR_CLOCK_COLOR_MODIFIER
        ).forEach { definition ->
            assertEquals(
                    "${definition.key} must default to the follow sentinel",
                    ColorModifier.FOLLOW,
                    definition.defaultValue)
        }
    }
}
