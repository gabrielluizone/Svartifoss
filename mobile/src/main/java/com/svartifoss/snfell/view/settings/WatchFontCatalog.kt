package com.svartifoss.snfell.view.settings

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.WatchTypography
import java.io.File

/**
 * Phone-side resolver for `MiscPreferences.WEAR_FONT` keys, mirroring the watch's
 * `watchFontTypeface` (mobile cannot depend on wear, so the mapping is duplicated - keep the key
 * sets in step).
 *
 * Extracted out of `WatchPreviewView` so the font *picker* can render each option in the font it
 * names. Before that, choosing a font was blind: the list showed every entry in the phone's own UI
 * font, so the only way to see a typeface was to select it and look at the watch.
 */
object WatchFontCatalog {

    /**
     * The typeface for [key], or null when the caller's own preloaded Google Sans should be used
     * (the default, and the fallback for unknown keys).
     *
     * Bundled fonts load through [ResourcesCompat]; system families through [Typeface.create],
     * which silently substitutes the default family on a device that lacks one - that is also the
     * honest preview, since the watch would do exactly the same.
     */
    fun typefaceFor(context: Context, key: String?): Typeface? = when (key) {
        "roboto" -> Typeface.DEFAULT
        "typewriter" -> ResourcesCompat.getFont(context, R.font.moms_typewriter)
        "love_letter" -> ResourcesCompat.getFont(context, R.font.love_letter_typewriter)
        "poppins" -> ResourcesCompat.getFont(context, R.font.poppins_regular)
        "montserrat" -> ResourcesCompat.getFont(context, R.font.montserrat_regular)
        "marcellus" -> ResourcesCompat.getFont(context, R.font.marcellus_regular)
        "bebas_neue" -> ResourcesCompat.getFont(context, R.font.bebas_neue_regular)
        "playfair" -> ResourcesCompat.getFont(context, R.font.playfair_display_regular)
        "space_grotesk" -> ResourcesCompat.getFont(context, R.font.space_grotesk_regular)
        "orbitron" -> ResourcesCompat.getFont(context, R.font.orbitron_regular)
        "caveat" -> ResourcesCompat.getFont(context, R.font.caveat_regular)
        "rounded" -> Typeface.create("sans-serif-rounded", Typeface.NORMAL)
        "sans_light" -> Typeface.create("sans-serif-light", Typeface.NORMAL)
        "sans_thin" -> Typeface.create("sans-serif-thin", Typeface.NORMAL)
        "sans_medium" -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
        "sans_black" -> Typeface.create("sans-serif-black", Typeface.NORMAL)
        "small_caps" -> Typeface.create("sans-serif-smallcaps", Typeface.NORMAL)
        "casual" -> Typeface.create("casual", Typeface.NORMAL)
        "serif" -> Typeface.SERIF
        "serif_monospace" -> Typeface.create("serif-monospace", Typeface.NORMAL)
        "monospace" -> Typeface.MONOSPACE
        "cursive" -> Typeface.create("cursive", Typeface.NORMAL)
        "condensed" -> Typeface.create("sans-serif-condensed", Typeface.NORMAL)
        "condensed_light" -> Typeface.create("sans-serif-condensed-light", Typeface.NORMAL)
        "condensed_medium" -> Typeface.create("sans-serif-condensed-medium", Typeface.NORMAL)
        else -> null
    }

    /**
     * Like [typefaceFor] but never null, resolving the default and every unknown key to bundled
     * Google Sans. Google Sans Flex resolves to its own variable master at default axes - enough
     * for a picker row, which is not the place to preview axis settings.
     */
    fun previewTypefaceFor(context: Context, key: String?): Typeface {
        if (WatchTypography.isFlexFont(key)) {
            return runCatching { Typeface.createFromFile(flexFontFile(context)) }
                    .getOrNull() ?: Typeface.DEFAULT
        }
        return typefaceFor(context, key)
                ?: ResourcesCompat.getFont(context, R.font.google_sans_regular)
                ?: Typeface.DEFAULT
    }

    private var cachedFlexFontFile: File? = null

    /** Extract-to-cache, same trick `WatchPreviewView`/`WatchTheme` use - `Typeface.Builder` has
     *  no constructor taking a `res/font` resource id. */
    private fun flexFontFile(context: Context): File {
        cachedFlexFontFile?.takeIf { it.length() > 0L }?.let { return it }
        val target = File(context.cacheDir, "google_sans_flex_variable.ttf")
        if (!target.exists() || target.length() == 0L) {
            context.resources.openRawResource(R.font.google_sans_flex).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
        cachedFlexFontFile = target
        return target
    }
}
