package com.svartifoss.snfell.util

import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.BulletSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.URLSpan
import android.graphics.Typeface
import androidx.core.text.inSpans
import java.util.regex.Pattern

/**
 * Renders a small GFM subset - the style GitHub release notes and this repo's own CHANGELOG.md
 * are written in - to a [Spannable] for display in a plain TextView, instead of showing the raw
 * `**`/`-`/`#` syntax. Handles per line: `#`/`##`/`###` headers, `- `/`* ` bullets, and inline
 * `**bold**`, `*italic*`/`_italic_`, `` `code` `` and `[text](url)` (rendered as a clickable link,
 * text only). Anything else passes through as plain text - this is a display convenience, not a
 * spec-complete parser.
 */
object SimpleMarkdown {
    private val INLINE_PATTERN = Pattern.compile(
            "\\*\\*(.+?)\\*\\*" +           // **bold**
                    "|`(.+?)`" +             // `code`
                    "|\\[(.+?)]\\((.+?)\\)" + // [text](url)
                    "|\\*(.+?)\\*" +         // *italic*
                    "|_(.+?)_"               // _italic_
    )

    private const val BULLET_INDENT_PX = 28

    fun render(markdown: String, bulletColor: Int): Spannable {
        val builder = SpannableStringBuilder()
        val lines = markdown.trim().lines()

        var previousBlank = true // suppresses a leading blank line
        for (rawLine in lines) {
            val line = rawLine.trimEnd()
            if (line.isBlank()) {
                if (!previousBlank && builder.isNotEmpty()) {
                    builder.append('\n')
                }
                previousBlank = true
                continue
            }
            if (builder.isNotEmpty()) {
                builder.append('\n')
            }
            previousBlank = false
            appendLine(builder, line, bulletColor)
        }
        return builder
    }

    private fun appendLine(builder: SpannableStringBuilder, line: String, bulletColor: Int) {
        val headerMatch = Regex("^(#{1,3})\\s+(.*)$").find(line)
        if (headerMatch != null) {
            val level = headerMatch.groupValues[1].length
            val start = builder.length
            appendInline(builder, headerMatch.groupValues[2])
            val sizeSpan = when (level) {
                1 -> RelativeSizeSpan(1.15f)
                2 -> RelativeSizeSpan(1.08f)
                else -> RelativeSizeSpan(1.0f)
            }
            builder.setSpan(sizeSpan, start, builder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            builder.setSpan(StyleSpan(Typeface.BOLD), start, builder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            return
        }

        val bulletMatch = Regex("^[-*]\\s+(.*)$").find(line)
        if (bulletMatch != null) {
            val start = builder.length
            appendInline(builder, bulletMatch.groupValues[1])
            builder.setSpan(
                    BulletSpan(BULLET_INDENT_PX, bulletColor),
                    start, builder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            return
        }

        appendInline(builder, line)
    }

    /** Applies bold/italic/code/link spans within a single line's text. */
    private fun appendInline(builder: SpannableStringBuilder, text: String) {
        val matcher = INLINE_PATTERN.matcher(text)
        var last = 0
        while (matcher.find()) {
            if (matcher.start() > last) {
                builder.append(text, last, matcher.start())
            }
            when {
                matcher.group(1) != null -> // **bold**
                    builder.inSpans(StyleSpan(Typeface.BOLD)) { append(matcher.group(1)) }
                matcher.group(2) != null -> // `code`
                    builder.inSpans(TypefaceSpan("monospace")) { append(matcher.group(2)) }
                matcher.group(3) != null -> { // [text](url)
                    val linkStart = builder.length
                    builder.append(matcher.group(3))
                    builder.setSpan(
                            URLSpan(matcher.group(4)),
                            linkStart, builder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                matcher.group(5) != null -> // *italic*
                    builder.inSpans(StyleSpan(Typeface.ITALIC)) { append(matcher.group(5)) }
                matcher.group(6) != null -> // _italic_
                    builder.inSpans(StyleSpan(Typeface.ITALIC)) { append(matcher.group(6)) }
            }
            last = matcher.end()
        }
        if (last < text.length) {
            builder.append(text, last, text.length)
        }
    }
}
