package com.svartifoss.snfell.common

/**
 * Shared control-style contract used by both the phone preview and the Wear renderer. Keeping the
 * preference decoding and tokens here prevents a style from looking correct in the preview while
 * producing different (or invisible) controls on the watch.
 */
enum class ScreenTheme(val preferenceValue: String, val tokens: ScreenThemeTokens) {
    DEFAULT("default", ScreenThemeTokens()),
    MINIMAL("minimal", ScreenThemeTokens(iconAlpha = 0.72f, iconScale = 0.82f)),
    COMPACT("compact", ScreenThemeTokens(iconScale = 0.78f)),
    // Legacy values remain decodable so old backups keep their appearance. The phone picker no
    // longer offers Cinema/Vivid/AMOLED because each duplicated Minimal or Contrast closely, nor
    // Hidden because the dedicated Show player controls switch is the unambiguous replacement.
    CINEMA("cinema", ScreenThemeTokens(iconAlpha = 0.45f, iconScale = 0.90f)),
    VIVID("vivid", ScreenThemeTokens(iconScale = 1.08f)),
    CONTRAST("contrast", ScreenThemeTokens(iconScale = 1.05f)),
    AMOLED("amoled", ScreenThemeTokens(iconAlpha = 0.85f, iconScale = 0.86f)),
    HIDDEN("hidden", ScreenThemeTokens(iconAlpha = 0f));

    companion object {
        fun fromPreference(value: String?): ScreenTheme =
                entries.firstOrNull { it.preferenceValue == value } ?: DEFAULT
    }
}

data class ScreenThemeTokens(
        val iconAlpha: Float = 1f,
        val iconScale: Float = 1f
)
