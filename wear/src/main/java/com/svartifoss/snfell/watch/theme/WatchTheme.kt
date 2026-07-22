package com.svartifoss.snfell.watch.theme

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import com.svartifoss.snfell.R

/**
 * Single source of truth for the watch UI's design constants, shared by all three UI stacks in
 * this module: legacy Views, Compose (queue) and ProtoLayout (Tiles). XML resources can't
 * reference these values, so colors.xml mirrors [ACCENT_DEFAULT] (theme_accent) and
 * [SURFACE_DARK] (queue_pill_fill) - keep them in sync when changing either side.
 */
object WatchTheme {
    /** Default accent (sage) used when no album art palette is available. */
    const val ACCENT_DEFAULT = 0xFF87A89F.toInt()

    const val BACKGROUND_BLACK = 0xFF000000.toInt()

    const val COLOR_WHITE = 0xFFFFFFFF.toInt()

    /** The historical default awake-clock colour (semi-transparent white, matching the old
     *  hardcoded 0x99FFFFFF the View and Compose clocks both used). */
    const val COLOR_WHITE_60 = 0x99FFFFFF.toInt()

    /** Lifted near-black for idle pills/cards/buttons on the OLED-black background. */
    const val SURFACE_DARK = 0xFF1E1E20.toInt()

    /** Primary text/icon color on dark surfaces. */
    const val ON_SURFACE = 0xFFF0F0F0.toInt()

    /** Secondary/subtitle text color on dark surfaces. */
    const val TEXT_SECONDARY = 0xFFB0B0B0.toInt()

    private const val TEXT_ACCENT_MIN_LIGHTNESS = 0.62f
    private const val SURFACE_ACCENT_MIN_SATURATION = 0.45f
    private const val SURFACE_ACCENT_MIN_LIGHTNESS = 0.62f
    private const val SURFACE_ACCENT_MAX_LIGHTNESS = 0.82f

    /**
     * Adapts a raw palette [color] for use as *text* on the dark background: raises lightness so
     * it reads as a soft accent instead of vanishing into black.
     */
    fun accentForText(color: Int): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        hsl[2] = hsl[2].coerceAtLeast(TEXT_ACCENT_MIN_LIGHTNESS)
        return ColorUtils.HSLToColor(hsl)
    }

    /**
     * Adapts a raw palette [color] for use as a *filled surface carrying black text* (queue
     * now-playing row, drawer highlight pill): clamps saturation up and lightness into a band
     * where the fill reads clearly against pure black and black text always reads on the fill -
     * the same way a light theme's accent gets a brighter dark-theme variant instead of being
     * reused as-is.
     */
    fun accentForSurface(color: Int): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        hsl[1] = hsl[1].coerceAtLeast(SURFACE_ACCENT_MIN_SATURATION)
        hsl[2] = hsl[2].coerceIn(SURFACE_ACCENT_MIN_LIGHTNESS, SURFACE_ACCENT_MAX_LIGHTNESS)
        return ColorUtils.HSLToColor(hsl)
    }
}

/** The app-wide Google Sans typeface for Compose screens (View layouts set it via @font/google_sans). */
val GoogleSansFamily = FontFamily(
        Font(R.font.google_sans_regular, FontWeight.Normal),
        Font(R.font.google_sans_bold, FontWeight.Bold)
)

/** Mom's Typewriter — a retro typewriter-style font, available for curated face text. */
val MomsTypewriterFamily = FontFamily(
        Font(R.font.moms_typewriter, FontWeight.Normal),
        Font(R.font.moms_typewriter, FontWeight.Bold),
        Font(R.font.moms_typewriter, FontWeight.Medium),
        Font(R.font.moms_typewriter, FontWeight.Light)
)

/** Love Letter Typewriter — a retro typewriter-style font, used for Lain-themed tracks. */
val LoveLetterTypewriterFamily = FontFamily(
        Font(R.font.love_letter_typewriter, FontWeight.Normal),
        Font(R.font.love_letter_typewriter, FontWeight.Bold),
        Font(R.font.love_letter_typewriter, FontWeight.Medium),
        Font(R.font.love_letter_typewriter, FontWeight.Light)
)

/** Bundled free (OFL) typefaces offered as redistributable alternatives to popular commercial
 *  faces: Poppins/Montserrat are geometric sans in the vein of Circular/Proxima Nova, and
 *  Marcellus is an elegant humanist roman evoking Optima. */
val PoppinsFamily = FontFamily(
        Font(R.font.poppins_regular, FontWeight.Normal),
        Font(R.font.poppins_bold, FontWeight.Bold)
)

val MontserratFamily = FontFamily(
        Font(R.font.montserrat_regular, FontWeight.Normal),
        Font(R.font.montserrat_bold, FontWeight.Bold)
)

val MarcellusFamily = FontFamily(
        Font(R.font.marcellus_regular, FontWeight.Normal),
        // Marcellus ships a single weight; reuse it for bold so the family never falls back.
        Font(R.font.marcellus_regular, FontWeight.Bold)
)

/**
 * Keywords that trigger the [LoveLetterTypewriterFamily] override on title/artist text.
 * The match is case-insensitive and only requires the keyword to be a substring
 * (e.g. "wiredlau" → true for "wired").
 */
private val LAIN_KEYWORDS = setOf("iwakura", "lain", "wired", "breakcore", "serial experiments")

/** Returns [LoveLetterTypewriterFamily] if [text] contains any [LAIN_KEYWORDS], null otherwise. */
fun lainFont(text: String): FontFamily? {
    val lower = text.lowercase()
    return if (LAIN_KEYWORDS.any { lower.contains(it) }) LoveLetterTypewriterFamily else null
}

/** System condensed sans — present on every device, so it costs no bundled asset. `by lazy`
 *  defers the platform Typeface.create call until first use (not this file's class-init), so
 *  JVM unit tests that never select "condensed" are unaffected by the unmocked Android call. */
val CondensedFamily: FontFamily by lazy {
    FontFamily(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.NORMAL))
}

/** Extra faces come from Android's own system font catalog, so they add no APK weight and stay
 * legally redistributable. Typeface.create gracefully falls back on older watches that do not
 * ship one of the optional aliases. The lazy map also keeps stable FontFamily instances across
 * Compose recompositions and avoids touching android.graphics.Typeface in host JVM tests unless
 * one of these choices is explicitly exercised. */
private val ModernSystemFamilies: Map<String, FontFamily> by lazy {
    mapOf(
            "rounded" to "sans-serif-rounded",
            "sans_light" to "sans-serif-light",
            "sans_thin" to "sans-serif-thin",
            "sans_medium" to "sans-serif-medium",
            "sans_black" to "sans-serif-black",
            "small_caps" to "sans-serif-smallcaps",
            "casual" to "casual",
            "serif_monospace" to "serif-monospace",
            "condensed_light" to "sans-serif-condensed-light",
            "condensed_medium" to "sans-serif-condensed-medium"
    ).mapValues { (_, familyName) ->
        FontFamily(android.graphics.Typeface.create(familyName, android.graphics.Typeface.NORMAL))
    }
}

private val modernSystemTypefaceNames = mapOf(
        "rounded" to "sans-serif-rounded",
        "sans_light" to "sans-serif-light",
        "sans_thin" to "sans-serif-thin",
        "sans_medium" to "sans-serif-medium",
        "sans_black" to "sans-serif-black",
        "small_caps" to "sans-serif-smallcaps",
        "casual" to "casual",
        "serif_monospace" to "serif-monospace",
        "condensed_light" to "sans-serif-condensed-light",
        "condensed_medium" to "sans-serif-condensed-medium"
)

/**
 * Maps the user's MiscPreferences.WEAR_FONT choice to a Compose [FontFamily]. The bundled
 * typewriter fonts and the always-available system families make up the catalog; unknown or
 * missing values fall back to Google Sans so old configs keep rendering unchanged. Keep the keys
 * in sync with the phone's `wear_font_values` array and `WatchPreviewView`'s typeface mapping.
 */
fun watchFontFamily(key: String?): FontFamily = when (key) {
    "roboto" -> FontFamily.Default
    "typewriter" -> MomsTypewriterFamily
    "love_letter" -> LoveLetterTypewriterFamily
    "poppins" -> PoppinsFamily
    "montserrat" -> MontserratFamily
    "marcellus" -> MarcellusFamily
    "serif" -> FontFamily.Serif
    "monospace" -> FontFamily.Monospace
    "cursive" -> FontFamily.Cursive
    "condensed" -> CondensedFamily
    else -> key
            ?.takeIf(modernSystemTypefaceNames::containsKey)
            ?.let { ModernSystemFamilies.getValue(it) }
            ?: GoogleSansFamily
}

/** [watchFontFamily]'s [Typeface] counterpart for the View-based classic face - keep the key set
 *  and fallback identical to it so classic and Compose faces render the exact same choice. */
fun watchFontTypeface(context: Context, key: String?): Typeface = when (key) {
    "roboto" -> Typeface.DEFAULT
    "typewriter" -> ResourcesCompat.getFont(context, R.font.moms_typewriter)
    "love_letter" -> ResourcesCompat.getFont(context, R.font.love_letter_typewriter)
    "poppins" -> ResourcesCompat.getFont(context, R.font.poppins_regular)
    "montserrat" -> ResourcesCompat.getFont(context, R.font.montserrat_regular)
    "marcellus" -> ResourcesCompat.getFont(context, R.font.marcellus_regular)
    "serif" -> Typeface.SERIF
    "monospace" -> Typeface.MONOSPACE
    "cursive" -> Typeface.create("cursive", Typeface.NORMAL)
    "condensed" -> Typeface.create("sans-serif-condensed", Typeface.NORMAL)
    else -> key?.let(modernSystemTypefaceNames::get)?.let { Typeface.create(it, Typeface.NORMAL) }
            ?: ResourcesCompat.getFont(context, R.font.google_sans_regular)
} ?: Typeface.DEFAULT
