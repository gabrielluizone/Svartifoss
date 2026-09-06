package com.svartifoss.snfell.watch.theme

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Typeface
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import com.svartifoss.snfell.common.ColorHarmony
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.FaceScopedPreferences
import com.svartifoss.snfell.common.DeviceLocalAppearance
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.common.ThemeAppearance

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
    /** The same value a colourless accent is promoted to at the source, so this clamp is a no-op
     *  on the covers that used to depend on it - see ColorHarmony.NEUTRAL_ACCENT_SATURATION. */
    private const val SURFACE_ACCENT_MIN_SATURATION = ColorHarmony.NEUTRAL_ACCENT_SATURATION
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

/**
 * Google Sans Flex at its own default instance (wght 400, every other axis at its `fvar` default -
 * see [com.svartifoss.snfell.common.WatchTypography.IDENTITY_FLEX_AXES]). Used wherever a single,
 * non-variable [FontFamily] is enough - the font catalog fallback and the awake clock, which
 * deliberately does not follow the per-element weight/axis controls (see
 * [com.svartifoss.snfell.watch.view.face.NowPlayingFaceState.clockFont]).
 *
 * Title/artist text needs a *different* instance per element (their own weight/slant plus the
 * shared width/optical-size/grade/roundness axes), which a single static [FontFamily] cannot
 * express - see [flexFontFamily] for that path.
 */
val GoogleSansFlexFamily = FontFamily(Font(R.font.google_sans_flex, FontWeight.Normal))

/**
 * A Google Sans Flex [FontFamily] pinned to one element's resolved axis values. Built fresh per
 * call rather than cached: title and artist typically differ (their own weight/slant), and the
 * four shared axes can change independently, so there is no single stable instance to reuse the
 * way [GoogleSansFlexFamily] is for axis-agnostic callers.
 *
 * `wght`/`slnt` use [spec]'s own weight/italic - the same pair every other font's [Typeface.create]
 * weight-matching path already reads - so Flex does not introduce a second, conflicting weight
 * control; `wdth`/`opsz`/`GRAD`/`ROND` come from the shared [axes]. The classic View face resolves
 * the equivalent instance via `Typeface.Builder.setFontVariationSettings`
 * ([com.svartifoss.snfell.common.WatchTypography.flexVariationSettings]), built from the same
 * [spec]/[axes] pair, so both rendering paths agree.
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
fun flexFontFamily(
        spec: com.svartifoss.snfell.common.WatchTypography.TextSpec,
        axes: com.svartifoss.snfell.common.WatchTypography.FlexAxes
): FontFamily {
    val weight = spec.weight.coerceIn(1, 1000)
    val slant = if (spec.italic) {
        com.svartifoss.snfell.common.WatchTypography.FLEX_SLANT_ITALIC
    } else {
        com.svartifoss.snfell.common.WatchTypography.FLEX_SLANT_UPRIGHT
    }
    return FontFamily(
            Font(
                    resId = R.font.google_sans_flex,
                    weight = FontWeight(weight),
                    // Declared to match what callers (AdaptiveTitleText/ArtistLineText) request at
                    // the Text() level - an exact match skips Compose's synthetic italic, which
                    // would otherwise double up on top of the real `slnt` axis already below.
                    style = if (spec.italic) {
                        androidx.compose.ui.text.font.FontStyle.Italic
                    } else {
                        androidx.compose.ui.text.font.FontStyle.Normal
                    },
                    variationSettings = FontVariation.Settings(
                            FontVariation.weight(weight),
                            FontVariation.width(axes.width),
                            FontVariation.Setting("opsz", axes.opticalSize),
                            FontVariation.slant(slant),
                            FontVariation.grade(axes.grade.toInt()),
                            FontVariation.Setting("ROND", axes.roundness)
                    )
            )
    )
}

/** Special Elite — a distressed typewriter-style font for an aged, analog look. Also the fallback
 *  for the retired "typewriter" key: that value used to select Mom's Typewriter, bundled with no
 *  redistribution license this project ever held, so the font file was removed. The key itself is
 *  kept as a legacy alias - the same treatment "love_letter" already gets below - so an old saved
 *  config or downloaded theme still renders in a typewriter-style family instead of silently
 *  falling back to Google Sans. */
val SpecialEliteFamily = FontFamily(
        Font(R.font.special_elite_regular, FontWeight.Normal),
        Font(R.font.special_elite_regular, FontWeight.Bold),
        Font(R.font.special_elite_regular, FontWeight.Medium),
        Font(R.font.special_elite_regular, FontWeight.Light)
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

/* The second bundled wave, chosen to cover voices the catalog had no answer for at all rather
 * than to add more neutral sans faces: a condensed all-caps display (Bebas Neue), a high-contrast
 * editorial serif (Playfair Display), a technical/geometric sans (Space Grotesk), a squarish
 * sci-fi display (Orbitron) and a handwritten script (Caveat). All OFL, licences under
 * licenses/<font>/.
 *
 * Four of the five are variable masters carrying a `wght` axis, but they are declared here as
 * plain single-instance families: only Google Sans Flex is wired into the axis editor, and a
 * variable font loaded without variation settings renders at its default instance, which is
 * exactly the regular weight wanted here. Bold reuses the same file for the same reason Marcellus
 * does - a missing weight would fall back to the system font mid-screen. */
val BebasNeueFamily = FontFamily(
        Font(R.font.bebas_neue_regular, FontWeight.Normal),
        Font(R.font.bebas_neue_regular, FontWeight.Bold)
)

val PlayfairDisplayFamily = FontFamily(
        Font(R.font.playfair_display_regular, FontWeight.Normal),
        Font(R.font.playfair_display_regular, FontWeight.Bold)
)

val SpaceGroteskFamily = FontFamily(
        Font(R.font.space_grotesk_regular, FontWeight.Normal),
        Font(R.font.space_grotesk_regular, FontWeight.Bold)
)

val OrbitronFamily = FontFamily(
        Font(R.font.orbitron_regular, FontWeight.Normal),
        Font(R.font.orbitron_regular, FontWeight.Bold)
)

val CaveatFamily = FontFamily(
        Font(R.font.caveat_regular, FontWeight.Normal),
        Font(R.font.caveat_regular, FontWeight.Bold)
)

/** The expanded catalog keeps one regular instance per family. Reusing it for bold prevents a
 *  missing weight from silently switching just bold watch text back to a system font. */
val InterFamily = FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_regular, FontWeight.Bold)
)

val AtkinsonHyperlegibleFamily = FontFamily(
        Font(R.font.atkinson_hyperlegible_regular, FontWeight.Normal),
        Font(R.font.atkinson_hyperlegible_regular, FontWeight.Bold)
)

val RubikFamily = FontFamily(
        Font(R.font.rubik_regular, FontWeight.Normal),
        Font(R.font.rubik_regular, FontWeight.Bold)
)

val BarlowCondensedFamily = FontFamily(
        Font(R.font.barlow_condensed_regular, FontWeight.Normal),
        Font(R.font.barlow_condensed_regular, FontWeight.Bold)
)

val OswaldFamily = FontFamily(
        Font(R.font.oswald_regular, FontWeight.Normal),
        Font(R.font.oswald_regular, FontWeight.Bold)
)

val LoraFamily = FontFamily(
        Font(R.font.lora_regular, FontWeight.Normal),
        Font(R.font.lora_regular, FontWeight.Bold)
)

val FrauncesFamily = FontFamily(
        Font(R.font.fraunces_regular, FontWeight.Normal),
        Font(R.font.fraunces_regular, FontWeight.Bold)
)

val SpaceMonoFamily = FontFamily(
        Font(R.font.space_mono_regular, FontWeight.Normal),
        Font(R.font.space_mono_regular, FontWeight.Bold)
)

val ArchivoBlackFamily = FontFamily(
        Font(R.font.archivo_black_regular, FontWeight.Normal),
        Font(R.font.archivo_black_regular, FontWeight.Bold)
)

val DancingScriptFamily = FontFamily(
        Font(R.font.dancing_script_regular, FontWeight.Normal),
        Font(R.font.dancing_script_regular, FontWeight.Bold)
)

/**
 * The large third wave of bundled families.
 *
 * Keep this as resource ids rather than resolving by resource *name*: both APKs enable resource
 * shrinking for release builds, so `Resources.getIdentifier()` would make a font look selectable
 * in debug and then allow the shrinker to discard it from a release APK. Every id below is a
 * static reference and therefore kept by aapt/R8. [ExpandedBundledFontFamilies] feeds Compose
 * while the exact same map feeds [watchFontTypeface] for the classic View face.
 *
 * Each family has one regular master. Declaring that same master at Normal and Bold deliberately
 * prevents a bold title from falling back to a different system family halfway through a screen;
 * it mirrors the existing one-file families above. The mobile preview carries an equivalent
 * resource-id map because it cannot depend on this Wear module.
 */
private val ExpandedBundledFontResources: Map<String, Int> = mapOf(
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
        "zilla_slab" to R.font.zilla_slab_regular,
        "lato" to R.font.lato_regular,
        "merriweather_sans" to R.font.merriweather_sans_regular,
        "source_sans_3" to R.font.source_sans_3_regular,
        "source_serif_4" to R.font.source_serif_4_regular,
        "libre_baskerville" to R.font.libre_baskerville_regular,
        "libre_franklin" to R.font.libre_franklin_regular,
        "ibm_plex_sans" to R.font.ibm_plex_sans_regular,
        "ibm_plex_mono" to R.font.ibm_plex_mono_regular,
        "ibm_plex_serif" to R.font.ibm_plex_serif_regular,
        "work_sans" to R.font.work_sans_regular,
        "karla" to R.font.karla_regular,
        "sora" to R.font.sora_regular,
        "urbanist" to R.font.urbanist_regular,
        "be_vietnam_pro" to R.font.be_vietnam_pro_regular,
        "red_hat_display" to R.font.red_hat_display_regular,
        "red_hat_text" to R.font.red_hat_text_regular,
        "red_hat_mono" to R.font.red_hat_mono_regular,
        "syne" to R.font.syne_regular,
        "instrument_sans" to R.font.instrument_sans_regular,
        "figtree" to R.font.figtree_regular,
        "gabarito" to R.font.gabarito_regular,
        "bricolage_grotesque" to R.font.bricolage_grotesque_regular,
        "dm_serif_display" to R.font.dm_serif_display_regular,
        "cormorant_garamond" to R.font.cormorant_garamond_regular,
        "cormorant" to R.font.cormorant_regular,
        "bitter" to R.font.bitter_regular,
        "spectral" to R.font.spectral_regular,
        "vollkorn" to R.font.vollkorn_regular,
        "raleway" to R.font.raleway_regular,
        "league_spartan" to R.font.league_spartan_regular,
        "saira" to R.font.saira_regular,
        "signika" to R.font.signika_regular,
        "sofia_sans" to R.font.sofia_sans_regular,
        "overpass" to R.font.overpass_regular,
        "public_sans" to R.font.public_sans_regular,
        "questrial" to R.font.questrial_regular,
        "dosis" to R.font.dosis_regular,
        "prompt" to R.font.prompt_regular,
        "unbounded" to R.font.unbounded_regular,
        "recursive" to R.font.recursive_regular,
        "trispace" to R.font.trispace_regular,
        "noto_sans" to R.font.noto_sans_regular,
        "noto_serif" to R.font.noto_serif_regular,
        "noto_sans_mono" to R.font.noto_sans_mono_regular,
        "heebo" to R.font.heebo_regular,
        "hind" to R.font.hind_regular,
        "hind_madurai" to R.font.hind_madurai_regular,
        "kumbh_sans" to R.font.kumbh_sans_regular,
        "alegreya" to R.font.alegreya_regular,
        "assistant" to R.font.assistant_regular
)

private val ExpandedBundledFontFamilies: Map<String, FontFamily> by lazy {
    ExpandedBundledFontResources.mapValues { (_, resourceId) ->
        FontFamily(
                Font(resourceId, FontWeight.Normal),
                Font(resourceId, FontWeight.Bold)
        )
    }
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
            "roboto_thin" to "sans-serif-thin",
            "roboto_light" to "sans-serif-light",
            "roboto_medium" to "sans-serif-medium",
            "roboto_black" to "sans-serif-black",
            "roboto_condensed" to "sans-serif-condensed",
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
        "roboto_thin" to "sans-serif-thin",
        "roboto_light" to "sans-serif-light",
        "roboto_medium" to "sans-serif-medium",
        "roboto_black" to "sans-serif-black",
        "roboto_condensed" to "sans-serif-condensed",
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
fun watchFontFamily(key: String?): FontFamily =
        key?.let(ExpandedBundledFontFamilies::get) ?: when (key) {
            "roboto" -> FontFamily.Default
            // The user's imported typeface, sent from the phone as an asset. It resolves through
            // the ordinary fallback below when this watch has not been sent one - which is the
            // right answer both before the first delivery and after the phone clears it.
            DeviceLocalAppearance.USER_FONT_KEY -> UserFont.fontFamily() ?: GoogleSansFamily
            "google_sans_flex" -> GoogleSansFlexFamily
            // Keep both legacy keys so saved preferences and imported themes continue to work.
            // "typewriter" (Mom's Typewriter) lost its bundled font - see SpecialEliteFamily's doc.
            "typewriter" -> SpecialEliteFamily
            "love_letter" -> SpecialEliteFamily
            "poppins" -> PoppinsFamily
            "montserrat" -> MontserratFamily
            "marcellus" -> MarcellusFamily
            "bebas_neue" -> BebasNeueFamily
            "playfair" -> PlayfairDisplayFamily
            "space_grotesk" -> SpaceGroteskFamily
            "orbitron" -> OrbitronFamily
            "caveat" -> CaveatFamily
            "inter" -> InterFamily
            "atkinson_hyperlegible" -> AtkinsonHyperlegibleFamily
            "rubik" -> RubikFamily
            "barlow_condensed" -> BarlowCondensedFamily
            "oswald" -> OswaldFamily
            "lora" -> LoraFamily
            "fraunces" -> FrauncesFamily
            "space_mono" -> SpaceMonoFamily
            "archivo_black" -> ArchivoBlackFamily
            "dancing_script" -> DancingScriptFamily
            "serif" -> FontFamily.Serif
            "monospace" -> FontFamily.Monospace
            "cursive" -> FontFamily.Cursive
            "condensed" -> CondensedFamily
            else -> key
                    ?.takeIf(modernSystemTypefaceNames::containsKey)
                    ?.let { ModernSystemFamilies.getValue(it) }
                    ?: GoogleSansFamily
        }

/**
 * The typeface every *non-player* watch surface draws with - the menu, the queue, the shared
 * chrome. Defaults to Google Sans, which is what those screens hardcoded before
 * [MiscPreferences.WEAR_FONT_ALL_SCREENS] existed.
 *
 * A CompositionLocal rather than a parameter threaded through each screen: the font is ambient
 * styling that every text in those trees wants, and passing it explicitly would mean touching
 * every intermediate composable for something none of them decide.
 */
val LocalWatchUiFontFamily = staticCompositionLocalOf { GoogleSansFamily }

/**
 * Resolves what [LocalWatchUiFontFamily] should provide: the active theme's chosen font when it
 * opts into the surrounding watch surfaces, otherwise Google Sans.
 *
 * The choice is read through the face scope because [MiscPreferences.WEAR_FONT] is a per-face
 * key - the menu and queue are not faces, so they follow whichever face is currently active,
 * which is the same font the player they were opened from is using.
 */
fun watchUiFontFamily(preferences: SharedPreferences?): FontFamily {
    if (preferences == null) return GoogleSansFamily
    val appearanceContext = ThemeAppearance.resolve(preferences)
    if (!FaceScopedPreferences.getBoolean(
                    preferences, MiscPreferences.WEAR_FONT_ALL_SCREENS, appearanceContext)) {
        return GoogleSansFamily
    }
    return watchFontFamily(FaceScopedPreferences.getString(
            preferences, MiscPreferences.WEAR_FONT, appearanceContext))
}

/**
 * [watchUiFontFamily]'s [Typeface] counterpart, for the chrome that is drawn with Views rather than
 * Compose - the seek/volume readout being the one that matters.
 *
 * That readout was the last piece of watch chrome still rendering in the platform default while
 * everything around it followed the chosen font, which showed up most clearly on the dedicated
 * volume and progress screens: a Compose title in the theme's font directly above a percentage in
 * Roboto.
 *
 * A null key is deliberately how Google Sans is asked for: [watchFontTypeface]'s fallback is that
 * font, so this cannot drift from whatever [watchUiFontFamily] treats as its own default.
 */
fun watchUiTypeface(context: Context, preferences: SharedPreferences?): Typeface {
    if (preferences == null) return watchFontTypeface(context, null)
    val appearanceContext = ThemeAppearance.resolve(preferences)
    if (!FaceScopedPreferences.getBoolean(
                    preferences, MiscPreferences.WEAR_FONT_ALL_SCREENS, appearanceContext)) {
        return watchFontTypeface(context, null)
    }
    return watchFontTypeface(context, FaceScopedPreferences.getString(
            preferences, MiscPreferences.WEAR_FONT, appearanceContext))
}

/** [watchFontFamily]'s [Typeface] counterpart for the View-based classic face - keep the key set
 *  and fallback identical to it so classic and Compose faces render the exact same choice. */
fun watchFontTypeface(context: Context, key: String?): Typeface =
        key?.let(ExpandedBundledFontResources::get)
                ?.let { ResourcesCompat.getFont(context, it) }
                ?: when (key) {
                    "roboto" -> Typeface.DEFAULT
                    // See watchFontFamily: same key, same fallback, so the classic and Compose
                    // faces can never render different families for one choice.
                    DeviceLocalAppearance.USER_FONT_KEY ->
                        UserFont.typeface() ?: ResourcesCompat.getFont(context, R.font.google_sans_regular)
                    // The plain (non-variable) instance - callers that need per-element axis
                    // control use WatchTheme.flexTypeface instead, which every classic-face draw
                    // site already does (applyClassicFont / styledClassicTypeface in MainActivity).
                    "google_sans_flex" -> ResourcesCompat.getFont(context, R.font.google_sans_flex)
                    // Keep both legacy keys so saved preferences and imported themes continue to
                    // work. "typewriter" (Mom's Typewriter) lost its bundled font - see
                    // SpecialEliteFamily's doc above.
                    "typewriter" -> ResourcesCompat.getFont(context, R.font.special_elite_regular)
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
                    "serif" -> Typeface.SERIF
                    "monospace" -> Typeface.MONOSPACE
                    "cursive" -> Typeface.create("cursive", Typeface.NORMAL)
                    "condensed" -> Typeface.create("sans-serif-condensed", Typeface.NORMAL)
                    else -> key?.let(modernSystemTypefaceNames::get)
                            ?.let { Typeface.create(it, Typeface.NORMAL) }
                            ?: ResourcesCompat.getFont(context, R.font.google_sans_regular)
                } ?: Typeface.DEFAULT

/** Cached copy of the bundled Flex font, extracted once per process. `Typeface.Builder` has no
 *  constructor for a `res/font` resource id directly (only `File`, `FileDescriptor`, an asset path,
 *  or a raw filesystem path) - see [flexTypeface]. */
private var cachedFlexFontFile: java.io.File? = null

private fun flexFontFile(context: Context): java.io.File {
    cachedFlexFontFile?.takeIf { it.length() > 0L }?.let { return it }
    val target = java.io.File(context.applicationContext.cacheDir, "google_sans_flex_variable.ttf")
    if (!target.exists() || target.length() == 0L) {
        context.resources.openRawResource(R.font.google_sans_flex).use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
    }
    cachedFlexFontFile = target
    return target
}

/**
 * [flexFontFamily]'s [Typeface] counterpart for the classic View face, via
 * `Typeface.Builder(File).setFontVariationSettings(String)` (API 26+, matching this module's
 * minSdk). Falls back to the plain (non-variable) instance if the builder ever throws, which the
 * platform's own docs note can happen for a malformed variation string - defensive here since a
 * bad axis combination must never crash the now-playing screen.
 */
fun flexTypeface(
        context: Context,
        spec: com.svartifoss.snfell.common.WatchTypography.TextSpec,
        axes: com.svartifoss.snfell.common.WatchTypography.FlexAxes
): Typeface {
    val settings = com.svartifoss.snfell.common.WatchTypography.flexVariationSettings(spec, axes)
    return try {
        Typeface.Builder(flexFontFile(context))
                .setFontVariationSettings(settings)
                .build() ?: ResourcesCompat.getFont(context, R.font.google_sans_flex) ?: Typeface.DEFAULT
    } catch (e: Exception) {
        timber.log.Timber.w(e, "Flex variation settings rejected: %s", settings)
        ResourcesCompat.getFont(context, R.font.google_sans_flex) ?: Typeface.DEFAULT
    }
}
