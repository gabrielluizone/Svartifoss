package com.svartifoss.snfell.watch.view.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.em
import com.svartifoss.snfell.common.MusicGlyphs
import com.svartifoss.snfell.common.R as commonR

/**
 * Draws the note characters that turn up in lyric text as the Svartifoss mark.
 *
 * The mark *is* a musical note - a note head whose stem splits into the basalt columns the
 * waterfall is named for - so a lyric that says `♪` can say it in the app's own hand instead of in
 * whatever the chosen typeface happens to have. Most of the font catalog has no glyph for these at
 * all, so today they arrive from a system fallback face: a different weight, a different colour
 * temperature, sometimes a tofu box, and always visibly not part of the line they sit in. The mark
 * takes the text's own colour and scales with its size.
 *
 * The substitution set and the string splitting live in [MusicGlyphs], on the JVM side, because
 * *what counts as a note* is a decision and *where the boundaries fall* is the part with an easy
 * bug in it (four of the seven are surrogate pairs). Only the drawing needs Compose.
 */
private const val NOTE_INLINE_ID = "svartifoss-note"

/**
 * How large the mark is drawn relative to the surrounding text.
 *
 * Slightly over 1em: the mark is a tall, narrow shape with no side bearings of its own, so matched
 * exactly to the font size it reads as smaller than the letters beside it.
 */
private const val NOTE_SIZE_EM = 1.15f

/** Keeps the mark off the words on either side, since it carries no side bearings of its own. */
private const val NOTE_SIDE_PADDING_EM = 0.08f

/**
 * [text] with every music-note character replaced by an inline placeholder for the mark.
 *
 * Remembered against the text, because the caller re-renders on every position tick and the
 * split would otherwise run four times a second for a string that has not changed.
 *
 * Pair it with [svartifossNoteContent] on the same `Text`; without that map the placeholders draw
 * as blanks.
 */
@Composable
fun rememberLyricText(text: String): AnnotatedString = remember(text) {
    if (!MusicGlyphs.containsNote(text)) {
        AnnotatedString(text)
    } else {
        buildAnnotatedString {
            MusicGlyphs.segments(text).forEach { segment ->
                when (segment) {
                    is MusicGlyphs.Segment.Text -> append(segment.text)
                    // The replaced character is handed over as the alternate text, so a screen
                    // reader still reads the line as written and the original shows through if the
                    // placeholder cannot be resolved.
                    is MusicGlyphs.Segment.Note ->
                        appendInlineContent(NOTE_INLINE_ID, segment.source)
                }
            }
        }
    }
}

/**
 * The inline content that renders those placeholders, tinted to [color].
 *
 * Sized in `em` rather than sp so it follows whatever size the line is being drawn at - the lyrics
 * screen alone draws three different sizes, and the Verse face another two.
 */
@Composable
fun svartifossNoteContent(color: Color): Map<String, InlineTextContent> {
    val painter = painterResource(commonR.drawable.ic_svartifoss_note)
    // Read through a State rather than captured by value, so the map itself never changes
    // identity. Every caller passes an animateColorAsState result - the colour moves on every
    // frame of a 260ms tween - and keying the map on it rebuilt the inline content, and with it
    // the Text's paragraph layout, once per frame for each animating line.
    val tint = rememberUpdatedState(color)
    return remember(painter) {
        mapOf(
                NOTE_INLINE_ID to InlineTextContent(
                        Placeholder(
                                width = (NOTE_SIZE_EM + 2 * NOTE_SIDE_PADDING_EM).em,
                                height = NOTE_SIZE_EM.em,
                                placeholderVerticalAlign = PlaceholderVerticalAlign.Center)
                ) {
                    Image(
                            painter = painter,
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(tint.value),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize())
                }
        )
    }
}
