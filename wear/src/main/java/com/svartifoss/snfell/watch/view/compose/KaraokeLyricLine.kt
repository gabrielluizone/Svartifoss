package com.svartifoss.snfell.watch.view.compose

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Text
import com.svartifoss.snfell.common.LyricWord
import com.svartifoss.snfell.common.LyricsParser

/** Gap between words, and between wrapped rows. Fixed rather than derived from [TextUnit] font
 *  size - the two call sites only ever differ by a few sp, and a word chip's own side bearings
 *  already carry most of the visual separation. */
private val WORD_GAP = 4.dp

/**
 * The current lyric line, word by word: each word turns [sungColor] once its own time has
 * arrived, easing up from [upcomingColor] over the span [LyricsParser.wordProgress] measures -
 * the same "gap to the next one" timing the line's own progress bar already uses, one level
 * deeper.
 *
 * Only worth calling for a line whose [LyricWord] list is non-empty - both call sites keep their
 * ordinary single-`Text` line for everything else (no word data, which is most lines even on a
 * track LRCLIB has synced, or ambient, where the display barely refreshes and a per-word
 * treatment would only ever be caught mid-transition).
 *
 * A `FlowRow` of individually-coloured words rather than colour spans on one `AnnotatedString`:
 * each word keeps [rememberLyricText]'s own note-glyph substitution and gets its own
 * [animateColorAsState], which is what makes a newly-active word ease in rather than snap - the
 * same tween length the whole-line highlight already animates with.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KaraokeLyricLine(
        words: List<LyricWord>,
        positionMs: Long,
        lineEndMs: Long,
        sungColor: Color,
        upcomingColor: Color,
        fontFamily: FontFamily,
        fontSize: TextUnit,
        fontWeight: FontWeight? = null,
        maxLines: Int = Int.MAX_VALUE,
        modifier: Modifier = Modifier,
) {
    val activeIndex = LyricsParser.wordIndexAt(words, positionMs)
    val activeFraction = if (activeIndex >= 0) {
        LyricsParser.wordProgress(words, activeIndex, positionMs, lineEndMs)
    } else {
        0f
    }

    FlowRow(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(WORD_GAP, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(WORD_GAP),
            maxLines = maxLines,
    ) {
        words.forEachIndexed { index, word ->
            val target = when {
                index < activeIndex -> sungColor
                index > activeIndex -> upcomingColor
                // The word being sung right now: eased from upcoming to sung across its own
                // span, rather than a hard switch the instant its timestamp arrives.
                else -> lerp(upcomingColor, sungColor, activeFraction)
            }
            val color by animateColorAsState(
                    targetValue = target,
                    animationSpec = tween(durationMillis = 240),
                    label = "karaokeWord")
            Text(
                    text = rememberLyricText(word.text),
                    color = color,
                    fontFamily = fontFamily,
                    inlineContent = svartifossNoteContent(color),
                    fontSize = fontSize,
                    fontWeight = fontWeight)
        }
    }
}
