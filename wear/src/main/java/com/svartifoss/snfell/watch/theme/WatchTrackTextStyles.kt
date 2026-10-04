package com.svartifoss.snfell.watch.theme

import android.content.SharedPreferences
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import com.svartifoss.snfell.common.FaceScopedPreferences
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.common.ThemeAppearance
import com.svartifoss.snfell.common.WatchTypography

/** Track metadata keeps its own title/artist fonts; buttons and captions keep the UI font.
 * Sizes and colours belong to each surface, but requesting a different weight from a configured
 * variable font makes Compose synthesize bold on top of the font's real weight axis. */
data class WatchTrackTextStyles(
        val title: TextStyle = TextStyle(fontFamily = GoogleSansFamily, fontWeight = FontWeight.Bold),
        val artist: TextStyle = TextStyle(fontFamily = GoogleSansFamily, fontWeight = FontWeight.Normal)
)

val LocalWatchTrackTextStyles = staticCompositionLocalOf { WatchTrackTextStyles() }

fun watchTrackTextStyles(preferences: SharedPreferences): WatchTrackTextStyles {
    val appearance = ThemeAppearance.resolve(preferences)
    if (!FaceScopedPreferences.getBoolean(
                    preferences, MiscPreferences.WEAR_FONT_ALL_SCREENS, appearance)) {
        return WatchTrackTextStyles()
    }
    val globalKey = FaceScopedPreferences.getString(preferences, MiscPreferences.WEAR_FONT, appearance)
    val titlePreference = FaceScopedPreferences.getString(
            preferences, MiscPreferences.WEAR_TITLE_FONT, appearance)
    val artistPreference = FaceScopedPreferences.getString(
            preferences, MiscPreferences.WEAR_ARTIST_FONT, appearance)

    fun style(
            key: String?,
            preference: String?,
            spec: WatchTypography.TextSpec,
            target: WatchTypography.FlexAxesTarget,
            designedWeight: FontWeight
    ): TextStyle {
        val family = if (WatchTypography.isFlexFont(key)) {
            flexFontFamily(spec, WatchTypography.flexAxes(
                    preferences, appearance,
                    if (preference == WatchTypography.FLEX_FONT_KEY) target
                    else WatchTypography.FlexAxesTarget.GLOBAL))
        } else {
            watchFontFamily(key)
        }
        return TextStyle(
                fontFamily = family,
                fontWeight = if (spec.weight == 400) designedWeight else FontWeight(spec.weight),
                fontStyle = if (spec.italic) FontStyle.Italic else FontStyle.Normal,
                letterSpacing = spec.trackingEm.em)
    }

    return WatchTrackTextStyles(
            title = style(
                    WatchTypography.titleFontKey(titlePreference, globalKey), titlePreference,
                    WatchTypography.titleSpec(preferences, appearance),
                    WatchTypography.FlexAxesTarget.TITLE, FontWeight.Bold),
            artist = style(
                    WatchTypography.artistFontKey(artistPreference, globalKey), artistPreference,
                    WatchTypography.artistSpec(preferences, appearance),
                    WatchTypography.FlexAxesTarget.ARTIST, FontWeight.Normal))
}
