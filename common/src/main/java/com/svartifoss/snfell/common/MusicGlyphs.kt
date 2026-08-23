package com.svartifoss.snfell.common

/**
 * The music-note characters that get drawn as the Svartifoss mark instead of as text.
 *
 * Lyric files are full of them. LRCLIB records mark instrumental passages with `♪`, plenty of
 * transcriptions open and close a chorus with `🎵`, and this app's own "this line is timed but has
 * no words" placeholder was one too. Rendered as text they are whatever the chosen typeface happens
 * to have - which for most of the catalog is nothing, so they arrive as a fallback glyph from some
 * other font, at some other weight, occasionally as a tofu box. The mark is a stylised note, it is
 * always available, and it takes the colour of the line it sits in.
 *
 * Pure and free of `android.*`: what counts as a note, and where the boundaries fall in a string,
 * are decisions worth pinning on the JVM. The rendering half lives in the watch module, since only
 * Compose can put an image inside a line of text.
 */
object MusicGlyphs {

    /** ♩ U+2669 quarter note. */
    const val QUARTER_NOTE: Int = 0x2669

    /** ♪ U+266A eighth note - also this app's own instrumental-line placeholder. */
    const val EIGHTH_NOTE: Int = 0x266A

    /** ♫ U+266B beamed eighth notes. */
    const val BEAMED_EIGHTH_NOTES: Int = 0x266B

    /** ♬ U+266C beamed sixteenth notes. */
    const val BEAMED_SIXTEENTH_NOTES: Int = 0x266C

    /** 🎵 U+1F3B5 musical note. */
    const val MUSICAL_NOTE: Int = 0x1F3B5

    /**
     * 🎶 U+1F3B6 multiple musical notes.
     *
     * Not on the original list, and included deliberately: it is the plural of [MUSICAL_NOTE], it
     * turns up in lyric text just as often, and leaving it out would mean one emoji in the family
     * silently keeping its old rendering next to five that changed - which reads as a bug rather
     * than as a decision.
     */
    const val MULTIPLE_MUSICAL_NOTES: Int = 0x1F3B6

    /** 🎼 U+1F3BC musical score. */
    const val MUSICAL_SCORE: Int = 0x1F3BC

    /**
     * Everything replaced by the mark.
     *
     * Accidentals - ♭ ♮ ♯ - are deliberately absent. They are notation *about* a note rather than a
     * note, they appear inside real song titles ("C♯ minor"), and swapping one for a logo would
     * corrupt the text rather than decorate it.
     */
    val CODE_POINTS: Set<Int> = setOf(
            QUARTER_NOTE,
            EIGHTH_NOTE,
            BEAMED_EIGHTH_NOTES,
            BEAMED_SIXTEENTH_NOTES,
            MUSICAL_NOTE,
            MULTIPLE_MUSICAL_NOTES,
            MUSICAL_SCORE
    )

    /** The placeholder this app writes for a timed line that has no words. Substituted like any
     *  other note, so the marker and a note inside a lyric render as the same thing. */
    val INSTRUMENTAL_MARKER: String = String(Character.toChars(EIGHTH_NOTE))

    fun isNote(codePoint: Int): Boolean = codePoint in CODE_POINTS

    /** Whether [text] holds anything worth substituting - lets a caller skip the split entirely,
     *  which is the overwhelmingly common case. */
    fun containsNote(text: String): Boolean {
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (isNote(cp)) return true
            i += Character.charCount(cp)
        }
        return false
    }

    /** One run of [segments]: either literal text, or one note to draw as the mark. */
    sealed interface Segment {
        data class Text(val text: String) : Segment

        /** [source] is the character being replaced, kept so the renderer can offer it as the
         *  accessible reading of the image and as a fallback if the mark cannot be drawn. */
        data class Note(val source: String) : Segment
    }

    /**
     * Splits [text] into literal runs and single notes, in order.
     *
     * Iterated by **code point**, not by char: four of the seven live outside the basic plane and
     * are two `Char`s each in a Kotlin string, so a per-char walk would split them down the middle
     * and emit two broken halves - one of which is a lone surrogate that renders as a tofu box
     * beside the mark that replaced its partner.
     *
     * Adjacent notes stay separate segments: `♪♪♪` is three marks, which is what a file writing it
     * that way means.
     */
    fun segments(text: String): List<Segment> {
        if (text.isEmpty()) return emptyList()
        if (!containsNote(text)) return listOf(Segment.Text(text))

        val out = mutableListOf<Segment>()
        val literal = StringBuilder()
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val width = Character.charCount(cp)
            if (isNote(cp)) {
                if (literal.isNotEmpty()) {
                    out.add(Segment.Text(literal.toString()))
                    literal.setLength(0)
                }
                out.add(Segment.Note(text.substring(i, i + width)))
            } else {
                literal.appendCodePoint(cp)
            }
            i += width
        }
        if (literal.isNotEmpty()) out.add(Segment.Text(literal.toString()))
        return out
    }
}
