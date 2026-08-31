package com.svartifoss.snfell.common

/**
 * How the title/artist text should be cased on screen, independent of whatever case the playing
 * app actually reports.
 *
 * Deliberately not folded into a face's composition or into [WatchTypography.TextSpec]'s
 * consumers directly - it lives beside the other per-element typography controls and is applied
 * the same way: a default of [NORMAL] preserves whatever a face already does to its own text (a
 * curated face that hardcodes `.uppercase()` for a micro-caps look keeps that look untouched),
 * and only a value the user actually chose overrides it. [apply] runs last, after any such
 * face-authored transform, so the user's choice is always the final word.
 */
enum class TextCase(val preferenceValue: String) {
    NORMAL("normal"),
    UPPERCASE("uppercase"),
    LOWERCASE("lowercase"),
    TITLE_CASE("title_case");

    fun apply(text: String): String = when (this) {
        NORMAL -> text
        UPPERCASE -> text.uppercase()
        LOWERCASE -> text.lowercase()
        TITLE_CASE -> text.lowercase().replace(TITLE_CASE_WORD_START) { match ->
            match.groupValues[1] + match.groupValues[2] + match.groupValues[3].uppercase()
        }
    }

    companion object {
        private val TITLE_CASE_WORD_START = Regex("""(^|\s+)([^\p{L}\s]*)(\p{L})""")

        fun fromPreference(value: String?): TextCase =
                entries.firstOrNull { it.preferenceValue == value } ?: NORMAL
    }
}
