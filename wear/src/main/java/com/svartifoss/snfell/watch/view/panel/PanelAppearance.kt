package com.svartifoss.snfell.watch.view.panel

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import com.matejdro.wearutils.preferences.definition.PreferenceDefinition
import com.svartifoss.snfell.common.AccentFloorStyle
import com.svartifoss.snfell.common.AlbumAccentSource
import com.svartifoss.snfell.common.AppearanceContext
import com.svartifoss.snfell.common.AppearanceNumericRanges
import com.svartifoss.snfell.common.ColorModifier
import com.svartifoss.snfell.common.FrostedEdges
import com.svartifoss.snfell.common.FaceScopedPreferences
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.common.OverlayBackdrop
import com.svartifoss.snfell.common.OverlayBackdropResolver
import com.svartifoss.snfell.common.PaletteTransforms
import com.svartifoss.snfell.common.BackgroundLayerColor
import com.svartifoss.snfell.common.BackgroundLayerStack
import com.svartifoss.snfell.common.PlayerBackgroundStyle
import com.svartifoss.snfell.common.ResolvedBackgroundLayer
import com.svartifoss.snfell.common.resolveLayers
import com.svartifoss.snfell.common.SHADING_MAX_MULTIPLIER
import com.svartifoss.snfell.common.SHADING_MAX_PERCENT
import com.svartifoss.snfell.common.PlayerShadingIntensity
import com.svartifoss.snfell.common.PlayerShadingStyle
import com.svartifoss.snfell.common.AlbumArtFilter
import com.svartifoss.snfell.common.resolveAlbumArtFilter
import com.svartifoss.snfell.common.SurfaceColorTreatment
import com.svartifoss.snfell.common.SurfacePaletteResolver
import com.svartifoss.snfell.common.SwatchInfo
import com.svartifoss.snfell.common.ThemeAppearance
import com.svartifoss.snfell.common.selectPrimaryAccent
import com.svartifoss.snfell.watch.theme.selectAlbumCompanionColors

/** Primary/secondary/tertiary for one panel surface, after every colour setting has been applied. */
data class PanelTriad(val primary: Int, val secondary: Int, val tertiary: Int)

/** Which panel a screen is drawing. The two differ only in which colour-mode and style keys they
 *  read, which is exactly the distinction the settings screen makes under *Panels*. */
enum class PanelSurface { VOLUME, SEEK }

/**
 * Everything a panel surface needs to draw itself, resolved from the user's settings once.
 *
 * [backdrop] is the "Shared panel appearance" background; the style/layout pairs are the per-panel
 * *Volume* and *Progress* choices; [triad] is that surface's colour after the album palette, the
 * colour treatment, the modifier and the hue shift.
 */
data class PanelAppearance(
        val backdrop: OverlayBackdrop,
        val triad: PanelTriad,
        /** [com.svartifoss.snfell.watch.view.VolumeStyle] preference value. */
        val volumeStyle: String,
        /** [com.svartifoss.snfell.watch.view.VolumeLayout] preference value. */
        val volumeLayout: String,
        /** [com.svartifoss.snfell.watch.view.RingStyle] preference value. */
        val progressStyle: String,
        /** [com.svartifoss.snfell.watch.view.ProgressRingLayout] preference value. */
        val progressLayout: String,
        val progressGradient: Boolean,
        /** Readout treatment - `MiscPreferences.WEAR_SEEK_STYLE`. */
        val readoutStyle: String,
        /** Meter geometry - `MiscPreferences.WEAR_SEEK_LAYOUT`. */
        val seekLayout: String
) {
    /** True while the backdrop composition includes the blurred cover; the caller supplies it. */
    val usesAlbumBlur: Boolean get() = backdrop.usesAlbumBlur
}

/**
 * The player composition a panel background is composited over, resolved from the same preferences
 * `MainActivity` reads for its own backdrop.
 *
 * Everything here belongs to the *player*, not to the panel: it uses the global colour triad and
 * the global colour treatment, because that is what the artwork, background treatment and shading
 * behind the quick panel are drawn with. The panel's own per-surface triad stays in
 * [PanelAppearance].
 */
data class PanelBackdrop(
        val albumArtStyle: PlayerBackgroundStyle,
        val albumArtFilter: AlbumArtFilter,
        val blurRadiusPx: Float,
        val overlayBlurRadiusPx: Float,
        /**
         * Everything drawn over the artwork, bottom first - see
         * [com.svartifoss.snfell.common.BackgroundLayerStack].
         *
         * The dedicated Volume and Progress screens draw *your* panel, which means they have to
         * draw your background too. Reading the same resolved stack the player does is what keeps
         * that promise once the order of these treatments became something the user chooses.
         */
        val layers: List<ResolvedBackgroundLayer>,
        val materialSurfaceSoftened: Boolean,
        val globalTriad: PanelTriad
) {
    /**
     * The frosted-rim composition when that background style is selected, cached against the exact
     * bitmap it was built from - this runs on every render pass, not only on a track change, and
     * the composition costs several allocations plus the blur passes.
     */
    fun frostedArtwork(source: Bitmap?): Bitmap? {
        if (source == null || !albumArtStyle.frostedEdges) return source
        cachedFrostSource?.let { cached ->
            if (cached === source) {
                cachedFrostResult?.takeIf { !it.isRecycled }?.let { return it }
            }
        }
        val frosted = runCatching { FrostedEdges.compose(source, blurRadiusPx) }.getOrNull()
                ?: return source
        cachedFrostSource = source
        cachedFrostResult = frosted
        return frosted
    }

    private companion object {
        // Process-wide rather than per-instance: a new PanelBackdrop is built on every appearance
        // change, so an instance-scoped cache would never be hit.
        private var cachedFrostSource: Bitmap? = null
        private var cachedFrostResult: Bitmap? = null
    }
}

/**
 * Resolves the *Panels* settings for a screen that is not `MainActivity`.
 *
 * The dedicated volume and progress screens are separate Activities, so none of `MainActivity`'s
 * resolved appearance state is reachable from them. Without this they had no palette and no styles
 * at all and shipped hardcoded - a fixed accent on a black background, which is why they looked
 * unrelated to the player they were opened from and ignored every *Panels* and *Shared panel
 * appearance* setting. The pipeline here is deliberately the same one `MainActivity.applyAccentColor`
 * runs (`selectPrimaryAccent` -> `selectAlbumCompanionColors` -> [SurfacePaletteResolver.derive]),
 * through the same `common` functions, rather than an approximation of it: the small pure helpers
 * `MainActivity` needed for it now live here and it calls into them.
 */
object PanelAppearanceResolver {

    /** Same-hue fallback for monochromatic covers/static accents. It varies only luminance and
     * saturation, never inventing a second hue that was absent from the artwork. */
    fun albumToneFallback(color: Int, lightness: Float): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        hsl[1] = hsl[1].coerceIn(.25f, .82f)
        hsl[2] = lightness
        return ColorUtils.HSLToColor(hsl)
    }

    /** parseColor throws StringIndexOutOfBounds (not IllegalArgument) on an empty string - and
     *  empty is every custom-color preference's "not picked yet" default. */
    fun parseHexColorOrNull(hex: String): Int? = if (hex.isBlank()) null else try {
        Color.parseColor(hex)
    } catch (ignored: Exception) {
        null
    }

    private fun faceString(
            prefs: SharedPreferences,
            context: AppearanceContext,
            def: PreferenceDefinition<String>
    ): String = FaceScopedPreferences.getString(prefs, def, context)

    /** Runtime migration for a watch paired to an older phone or a config restored before the
     * rewritten Colors page has been opened - the same rule `MainActivity` applies. */
    fun colorTreatmentPreference(prefs: SharedPreferences, context: AppearanceContext): String {
        if (FaceScopedPreferences.containsExplicitValue(
                        prefs, MiscPreferences.WEAR_COLOR_TREATMENT.key, context)) {
            return faceString(prefs, context, MiscPreferences.WEAR_COLOR_TREATMENT)
        }
        return if (FaceScopedPreferences.getBoolean(
                        prefs, MiscPreferences.WEAR_DYNAMIC_ACCENT, context)) {
            "expressive"
        } else {
            "normal"
        }
    }

    fun normalColorPreference(prefs: SharedPreferences, context: AppearanceContext): String =
            if (FaceScopedPreferences.containsExplicitValue(
                            prefs, MiscPreferences.WEAR_NORMAL_COLOR.key, context)) {
                faceString(prefs, context, MiscPreferences.WEAR_NORMAL_COLOR)
            } else {
                ""
            }

    /**
     * The raw album triad, before any colour treatment.
     *
     * Answers via [onResolved] rather than returning, because `Palette.generate` is a callback -
     * but [AlbumPaletteCache] means the common case answers *synchronously*, before this function
     * returns, since the player has almost always already extracted this exact cover. Callers that
     * can seed their state from the cache should do so ([rememberPanelPalette]); this remains the
     * path for the case where nothing has extracted it yet.
     */
    fun albumTriad(
            art: Bitmap?,
            source: AlbumAccentSource,
            fallback: Int,
            onResolved: (PanelTriad) -> Unit
    ) {
        if (art == null || art.isRecycled) {
            onResolved(PanelTriad(
                    fallback,
                    albumToneFallback(fallback, .42f),
                    albumToneFallback(fallback, .68f)))
            return
        }
        AlbumPaletteCache.get(art, source)?.let {
            onResolved(it)
            return
        }
        Palette.from(art).generate { palette ->
            // Named tonal swatches first (Palette picks them to be distinct from each other),
            // population-ranked raw swatches only as a fallback - the ordering
            // `MainActivity.updateAccentFromArt` settled on and for the same reason.
            val preferredColors = palette?.let { p ->
                listOfNotNull(
                        p.getVibrantSwatch(),
                        p.getMutedSwatch(),
                        p.getLightVibrantSwatch(),
                        p.getDarkVibrantSwatch(),
                        p.getLightMutedSwatch(),
                        p.getDarkMutedSwatch(),
                        p.dominantSwatch
                ).map { it.rgb }.distinct()
            }.orEmpty()
            val swatchInfos = palette?.swatches.orEmpty()
                    .map { SwatchInfo(it.rgb, it.population) }
            val primary = selectPrimaryAccent(
                    palette?.getVibrantSwatch()?.let { SwatchInfo(it.rgb, it.population) },
                    swatchInfos,
                    source
            ) ?: preferredColors.firstOrNull() ?: fallback
            val ranked = swatchInfos.sortedByDescending { it.population }.map { it.rgb }
            val companions = selectAlbumCompanionColors(primary, preferredColors + ranked)
            val triad = PanelTriad(
                    primary,
                    companions.secondary ?: albumToneFallback(primary, .42f),
                    companions.tertiary ?: albumToneFallback(primary, .68f))
            // Shared, so the *next* screen to open on this cover paints it on its first frame.
            AlbumPaletteCache.put(art, source, triad)
            onResolved(triad)
        }
    }

    /**
     * Applies the colour settings for [surface] to a raw album [triad].
     *
     * A component's saved Normal colour wins only when that component explicitly selected Normal;
     * Follow uses the global Normal colour, so an old hidden custom value cannot leak back in.
     */
    fun surfaceTriad(
            prefs: SharedPreferences,
            context: AppearanceContext,
            surface: PanelSurface,
            triad: PanelTriad,
            fallbackAccent: Int
    ): PanelTriad {
        val mode: String
        val customColor: String
        val legacyDesaturated: Boolean
        when (surface) {
            PanelSurface.VOLUME -> {
                mode = faceString(prefs, context, MiscPreferences.WEAR_VOLUME_COLOR_MODE)
                customColor = faceString(prefs, context, MiscPreferences.WEAR_VOLUME_CUSTOM_COLOR)
                legacyDesaturated = false
            }
            PanelSurface.SEEK -> {
                mode = faceString(prefs, context, MiscPreferences.WEAR_PROGRESS_COLOR_MODE)
                customColor = faceString(prefs, context, MiscPreferences.WEAR_PROGRESS_CUSTOM_COLOR)
                legacyDesaturated = FaceScopedPreferences.getBoolean(
                        prefs, MiscPreferences.WEAR_PROGRESS_DESATURATED, context)
            }
        }
        val normalColor = normalColorPreference(prefs, context)
        val selected = SurfaceColorTreatment.fromPreference(mode, legacyDesaturated)
        val globalTreatment = SurfaceColorTreatment.fromPreference(
                colorTreatmentPreference(prefs, context),
                default = SurfaceColorTreatment.EXPRESSIVE)
        val treatment = selected.resolveAgainst(globalTreatment)
        val fixed = (if (selected == SurfaceColorTreatment.FOLLOW) null
                else parseHexColorOrNull(customColor))
                ?: parseHexColorOrNull(normalColor)
                ?: fallbackAccent
        val derived = SurfacePaletteResolver.derive(
                treatment,
                ColorModifier.fromPreference(
                        faceString(prefs, context, MiscPreferences.WEAR_COLOR_MODIFIER)),
                triad.primary,
                triad.secondary,
                triad.tertiary,
                fixed,
                FaceScopedPreferences.getInt(
                        prefs, MiscPreferences.WEAR_COLOR_HUE_SHIFT, context).toFloat(),
                FaceScopedPreferences.getBoolean(
                        prefs, MiscPreferences.WEAR_NORMAL_COLOR_MULTI, context))
        return PanelTriad(derived.primary, derived.secondary, derived.tertiary)
    }

    /**
     * Resolves the player composition the panel background sits on. Mirrors `MainActivity`'s
     * `applyPlayerBackground` / `applyAlbumArtScrim` / `resolveShadingMultiplier`, reading the same
     * keys through the same face scope.
     */
    fun resolveBackdrop(
            prefs: SharedPreferences,
            context: AppearanceContext,
            triad: PanelTriad,
            fallbackAccent: Int
    ): PanelBackdrop {
        val albumArtStyle = PlayerBackgroundStyle.fromPreference(
                faceString(prefs, context, MiscPreferences.ALBUM_ART_STYLE))
        val albumArtFilter = resolveAlbumArtFilter(
                faceString(prefs, context, MiscPreferences.ALBUM_ART_FILTER), albumArtStyle)
        val shadingStyle = PlayerShadingStyle.fromPreference(
                faceString(prefs, context, MiscPreferences.WEAR_PLAYER_SHADING_STYLE))
        val dimAlbumArt = FaceScopedPreferences.getBoolean(
                prefs, MiscPreferences.DIM_ALBUM_ART, context)
        val intensity = shadingMultiplier(prefs, context)
        val globalTriad = globalTriad(prefs, context, triad, fallbackAccent)

        val accentFloor = AccentFloorStyle.fromPreference(
                faceString(prefs, context, MiscPreferences.WEAR_ACCENT_FLOOR))
        val shadeTint = shadingColor(prefs, context, globalTriad.primary)
        val floorTint = accentFloorColor(prefs, context, globalTriad)
        val face = prefs.getString(
                MiscPreferences.WEAR_SCREEN_FACE.key,
                MiscPreferences.WEAR_SCREEN_FACE.defaultValue).orEmpty()

        return PanelBackdrop(
                albumArtStyle = albumArtStyle,
                albumArtFilter = albumArtFilter,
                blurRadiusPx = FaceScopedPreferences.getInt(
                        prefs, MiscPreferences.ALBUM_ART_BLUR_RADIUS, context)
                        .coerceIn(5, 120).toFloat(),
                // Clamped like MainActivity's own read: these screens draw the same backdrop,
                // and a bound applied on one of them only is how the two drift apart.
                overlayBlurRadiusPx = AppearanceNumericRanges.clamp(
                        MiscPreferences.WEAR_OVERLAY_BLUR_RADIUS.key,
                        FaceScopedPreferences.getInt(
                                prefs, MiscPreferences.WEAR_OVERLAY_BLUR_RADIUS, context))
                        .toFloat(),
                layers = BackgroundLayerStack.resolve(
                        raw = faceString(prefs, context, MiscPreferences.WEAR_BACKGROUND_LAYERS),
                        background = albumArtStyle,
                        dimEnabled = dimAlbumArt,
                        dimPercent = (intensity * 100f).toInt(),
                        shading = shadingStyle,
                        shadingColor = BackgroundLayerColor.fromPreference(
                                faceString(prefs, context, MiscPreferences.WEAR_SHADING_COLOR_MODE)),
                        floor = accentFloor,
                        floorColor = BackgroundLayerColor.fromPreference(
                                faceString(
                                        prefs,
                                        context,
                                        MiscPreferences.WEAR_ACCENT_FLOOR_COLOR_MODE)),
                        baseWashDrawn = face !in BackgroundLayerStack.SELF_BACKDROP_FACES)
                        .resolveLayers(
                                // These panels are their own Activities with no live palette of
                                // their own, so a layer's colour is resolved from the same two
                                // tones the single legacy rows already produce here.
                                shadeColor = { shadeTint },
                                floorColor = { floorTint }),
                materialSurfaceSoftened =
                        colorTreatmentPreference(prefs, context) == "desaturated",
                globalTriad = globalTriad)
    }

    /**
     * The watch-wide triad: the album colours after the *global* colour treatment, which is what
     * the artwork background and shading are drawn with. The per-panel modes in [surfaceTriad]
     * deliberately do not reach this layer.
     */
    fun globalTriad(
            prefs: SharedPreferences,
            context: AppearanceContext,
            triad: PanelTriad,
            fallbackAccent: Int
    ): PanelTriad {
        val derived = SurfacePaletteResolver.derive(
                SurfaceColorTreatment.fromPreference(
                        colorTreatmentPreference(prefs, context),
                        default = SurfaceColorTreatment.EXPRESSIVE),
                ColorModifier.fromPreference(
                        faceString(prefs, context, MiscPreferences.WEAR_COLOR_MODIFIER)),
                triad.primary,
                triad.secondary,
                triad.tertiary,
                parseHexColorOrNull(normalColorPreference(prefs, context)) ?: fallbackAccent,
                FaceScopedPreferences.getInt(
                        prefs, MiscPreferences.WEAR_COLOR_HUE_SHIFT, context).toFloat(),
                FaceScopedPreferences.getBoolean(
                        prefs, MiscPreferences.WEAR_NORMAL_COLOR_MULTI, context))
        return PanelTriad(derived.primary, derived.secondary, derived.tertiary)
    }

    /** The numeric strength, falling back to the retired named preference only for an install that
     *  never wrote the numeric one - the same migration `MainActivity` applies. */
    private fun shadingMultiplier(
            prefs: SharedPreferences,
            context: AppearanceContext
    ): Float {
        val hasNumeric = FaceScopedPreferences.containsExplicitValue(
                prefs, MiscPreferences.ALBUM_ART_DIM_STRENGTH.key, context)
        val hasNamed = FaceScopedPreferences.containsExplicitValue(
                prefs, MiscPreferences.WEAR_PLAYER_SHADING_INTENSITY.key, context)
        val percent = if (!hasNumeric && hasNamed) {
            PlayerShadingIntensity.percentFor(
                    faceString(prefs, context, MiscPreferences.WEAR_PLAYER_SHADING_INTENSITY))
        } else {
            FaceScopedPreferences.getInt(
                    prefs, MiscPreferences.ALBUM_ART_DIM_STRENGTH, context)
        }
        return percent.coerceIn(0, SHADING_MAX_PERCENT) / 100f
    }

    private fun shadingColor(
            prefs: SharedPreferences,
            context: AppearanceContext,
            accent: Int
    ): Int = when (faceString(prefs, context, MiscPreferences.WEAR_SHADING_COLOR_MODE)) {
        "album" -> PaletteTransforms.shadingTone(accent)
        "desaturated" ->
            PaletteTransforms.shadingTone(PaletteTransforms.softenedAlbumAccent(accent))
        "custom" -> parseHexColorOrNull(
                faceString(prefs, context, MiscPreferences.WEAR_SHADING_CUSTOM_COLOR))
                ?.let { PaletteTransforms.shadingTone(it) } ?: Color.BLACK
        else -> Color.BLACK
    }

    private fun accentFloorColor(
            prefs: SharedPreferences,
            context: AppearanceContext,
            triad: PanelTriad
    ): Int = when (faceString(prefs, context, MiscPreferences.WEAR_ACCENT_FLOOR_COLOR_MODE)) {
        "secondary" -> triad.secondary
        "tertiary" -> triad.tertiary
        "custom" -> parseHexColorOrNull(
                faceString(prefs, context, MiscPreferences.WEAR_ACCENT_FLOOR_CUSTOM_COLOR))
                ?: triad.primary
        else -> triad.primary
    }

    fun accentSource(prefs: SharedPreferences, context: AppearanceContext): AlbumAccentSource =
            AlbumAccentSource.fromPreference(
                    faceString(prefs, context, MiscPreferences.WEAR_ALBUM_ACCENT_SOURCE))

    fun appearanceContext(prefs: SharedPreferences): AppearanceContext =
            ThemeAppearance.resolve(prefs)

    /**
     * Reads every *Panels* choice for [surface] and pairs it with the surface's [triad].
     *
     * The backdrop resolves through the shared [OverlayBackdropResolver], including the
     * compatibility "follow" option, so the screen and the transient overlay of the same panel
     * cannot answer differently for the same setting.
     */
    fun resolve(
            prefs: SharedPreferences,
            context: AppearanceContext,
            surface: PanelSurface,
            triad: PanelTriad
    ): PanelAppearance {
        val volumeStyle = faceString(prefs, context, MiscPreferences.WEAR_VOLUME_STYLE)
        val readoutStyle = faceString(prefs, context, MiscPreferences.WEAR_SEEK_STYLE)
        val contentStyle = when (surface) {
            PanelSurface.VOLUME -> volumeStyle
            PanelSurface.SEEK -> OverlayBackdropResolver.seekContentStyle(readoutStyle)
        }
        return PanelAppearance(
                backdrop = OverlayBackdropResolver.resolve(
                        faceString(prefs, context, MiscPreferences.WEAR_OVERLAY_BACKDROP_STYLE),
                        contentStyle),
                triad = triad,
                volumeStyle = volumeStyle,
                volumeLayout = faceString(prefs, context, MiscPreferences.WEAR_VOLUME_LAYOUT),
                progressStyle = faceString(prefs, context, MiscPreferences.WEAR_PROGRESS_STYLE),
                progressLayout = faceString(prefs, context, MiscPreferences.WEAR_PROGRESS_LAYOUT),
                progressGradient = FaceScopedPreferences.getBoolean(
                        prefs, MiscPreferences.WEAR_PROGRESS_GRADIENT, context),
                readoutStyle = readoutStyle,
                seekLayout = faceString(prefs, context, MiscPreferences.WEAR_SEEK_LAYOUT))
    }
}
