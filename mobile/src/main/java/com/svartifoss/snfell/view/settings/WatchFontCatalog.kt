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
     * Resource ids for the expanded bundled catalog.
     *
     * These must stay as static `R.font` references instead of name lookups: release APKs enable
     * `shrinkResources`, which cannot see a resource reached only through `getIdentifier`. The
     * Wear-side `ExpandedBundledFontResources` mirrors this exact key/resource contract so the
     * picker preview, Compose faces and the classic face never choose different families.
     */
    private val expandedBundledFontResources: Map<String, Int> = mapOf(
            "abeezee" to R.font.abeezee_regular,
            "abril_fatface" to R.font.abril_fatface_regular,
            "acme" to R.font.acme_regular,
            "alata" to R.font.alata_regular,
            "aleo" to R.font.aleo_regular,
            "alfa_slab_one" to R.font.alfa_slab_one_regular,
            "amatic_sc" to R.font.amatic_sc_regular,
            "anton" to R.font.anton_regular,
            "arvo" to R.font.arvo_regular,
            "bangers" to R.font.bangers_regular,
            "black_ops_one" to R.font.black_ops_one_regular,
            "bree_serif" to R.font.bree_serif_regular,
            "cabin" to R.font.cabin_regular,
            "chivo" to R.font.chivo_regular,
            "courier_prime" to R.font.courier_prime_regular,
            "crete_round" to R.font.crete_round_regular,
            "crimson_pro" to R.font.crimson_pro_regular,
            "dm_sans" to R.font.dm_sans_regular,
            "domine" to R.font.domine_regular,
            "exo_2" to R.font.exo_2_regular,
            "fira_code" to R.font.fira_code_regular,
            "inconsolata" to R.font.inconsolata_regular,
            "indie_flower" to R.font.indie_flower_regular,
            "josefin_sans" to R.font.josefin_sans_regular,
            "jost" to R.font.jost_regular,
            "kanit" to R.font.kanit_regular,
            "lexend" to R.font.lexend_regular,
            "lobster" to R.font.lobster_regular,
            "manrope" to R.font.manrope_regular,
            "mulish" to R.font.mulish_regular,
            "nunito" to R.font.nunito_regular,
            "outfit" to R.font.outfit_regular,
            "oxanium" to R.font.oxanium_regular,
            "play" to R.font.play_regular,
            "plus_jakarta_sans" to R.font.plus_jakarta_sans_regular,
            "press_start_2p" to R.font.press_start_2p_regular,
            "quicksand" to R.font.quicksand_regular,
            "rajdhani" to R.font.rajdhani_regular,
            "righteous" to R.font.righteous_regular,
            "roboto_mono" to R.font.roboto_mono_regular,
            "rowdies" to R.font.rowdies_regular,
            "russo_one" to R.font.russo_one_regular,
            "shrikhand" to R.font.shrikhand_regular,
            "silkscreen" to R.font.silkscreen_regular,
            "source_code_pro" to R.font.source_code_pro_regular,
            "staatliches" to R.font.staatliches_regular,
            "teko" to R.font.teko_regular,
            "titillium_web" to R.font.titillium_web_regular,
            "varela_round" to R.font.varela_round_regular,
            "yanone_kaffeesatz" to R.font.yanone_kaffeesatz_regular,
            "zilla_slab" to R.font.zilla_slab_regular
    )

    /**
     * The typeface for [key], or null when the caller's own preloaded Google Sans should be used
     * (the default, and the fallback for unknown keys).
     *
     * Bundled fonts load through [ResourcesCompat]; system families through [Typeface.create],
     * which silently substitutes the default family on a device that lacks one - that is also the
     * honest preview, since the watch would do exactly the same.
     */
    fun typefaceFor(context: Context, key: String?): Typeface? =
            key?.let(expandedBundledFontResources::get)
                    ?.let { ResourcesCompat.getFont(context, it) }
                    ?: when (key) {
                        "roboto" -> Typeface.DEFAULT
                        "typewriter" -> ResourcesCompat.getFont(context, R.font.moms_typewriter)
                        // Keep the legacy key so saved preferences and imported themes continue to work.
                        "love_letter" -> ResourcesCompat.getFont(context, R.font.special_elite_regular)
                        "poppins" -> ResourcesCompat.getFont(context, R.font.poppins_regular)
                        "montserrat" -> ResourcesCompat.getFont(context, R.font.montserrat_regular)
                        "marcellus" -> ResourcesCompat.getFont(context, R.font.marcellus_regular)
                        "bebas_neue" -> ResourcesCompat.getFont(context, R.font.bebas_neue_regular)
                        "playfair" -> ResourcesCompat.getFont(context, R.font.playfair_display_regular)
                        "space_grotesk" -> ResourcesCompat.getFont(context, R.font.space_grotesk_regular)
                        "orbitron" -> ResourcesCompat.getFont(context, R.font.orbitron_regular)
                        "caveat" -> ResourcesCompat.getFont(context, R.font.caveat_regular)
                        "inter" -> ResourcesCompat.getFont(context, R.font.inter_regular)
                        "atkinson_hyperlegible" -> ResourcesCompat.getFont(context, R.font.atkinson_hyperlegible_regular)
                        "rubik" -> ResourcesCompat.getFont(context, R.font.rubik_regular)
                        "barlow_condensed" -> ResourcesCompat.getFont(context, R.font.barlow_condensed_regular)
                        "oswald" -> ResourcesCompat.getFont(context, R.font.oswald_regular)
                        "lora" -> ResourcesCompat.getFont(context, R.font.lora_regular)
                        "fraunces" -> ResourcesCompat.getFont(context, R.font.fraunces_regular)
                        "space_mono" -> ResourcesCompat.getFont(context, R.font.space_mono_regular)
                        "archivo_black" -> ResourcesCompat.getFont(context, R.font.archivo_black_regular)
                        "dancing_script" -> ResourcesCompat.getFont(context, R.font.dancing_script_regular)
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
