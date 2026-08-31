package com.svartifoss.snfell.common

/**
 * The ordered stack of treatments drawn between the artwork and a face's own content.
 *
 * Until this existed the player background was three fixed slots in a fixed order: the artwork's
 * authored wash, then the accent floor, then one shading. Each could hold exactly one value, and
 * the order was whatever the renderers happened to be written in. That is a shape the settings
 * screen could describe but not a shape anybody chose - wanting Bottom corner *and* Bottom fade,
 * or an accent floor *under* a wash rather than over it, was not a strange request, it was simply
 * unreachable, and no amount of adding styles to the three pickers would have reached it.
 *
 * A stack replaces the slots. It is still data - every layer is one enumerated style from a
 * vocabulary this file validates, an integer strength and a colour choice, so it stays inside the
 * rule that makes hosting user-generated themes tractable here ("a theme is data, never code").
 * There is no expression to evaluate and nothing a hostile stack can do but look bad.
 *
 * ## What is *not* a layer
 *
 * The artwork itself. [MiscPreferences.ALBUM_ART_STYLE] keeps owning how the bitmap is treated -
 * blurred, monochrome, frosted, filtered, square-fit or hidden - because that facet is read far
 * outside this file: `albumArtHidden` decides what `AdaptiveTextContrast` measures, `usesBlurRadius`
 * decides whether the blur row is even enabled, and the host ImageView is what actually holds the
 * bitmap. A photograph is also opaque, so there is nothing meaningful to put underneath it. The
 * base is the floor of the stack, always, and everything else is above it.
 *
 * The *wash* half of that same preference is a layer, though - Poster's gradients, Aurora's ribbon,
 * Ocean's rise. Which is why [implicitStack] hands it back as a [BackgroundLayerKind.WASH] entry:
 * an authored background a user cannot move under or over anything is exactly the limitation this
 * removes, and re-expressing it as the bottom layer costs nothing and makes it reorderable.
 *
 * ## Absent, empty and explicit
 *
 * Three states, and the difference between the first two is load-bearing. An **absent** value
 * (`""`, or anything this file refuses to parse) means the user has never touched the stack, so
 * every renderer keeps its pre-stack path and the legacy keys stay in charge - which is also what
 * an older watch build does with a preference it has never heard of, so a phone that has moved on
 * degrades to the old look rather than to no look. An **empty** stack (`"1"`) is a decision: no
 * overlay at all, bare artwork. And an explicit stack replaces all three legacy slots at once,
 * never some of them - a half-applied stack would put a shading the user removed back on screen.
 *
 * Fail-closed parsing is what makes that safe. A value from a newer build, a corrupted sync or an
 * imported backup resolves to "absent", never to a partial stack: dropping the one layer a reader
 * did not understand would silently publish a different composition than the one that was saved.
 */
object BackgroundLayerStack {

    /**
     * The grammar's own version, so a future field can be added without a newer phone's value
     * being read as a subtly different composition by an older watch. Anything else fails closed.
     */
    const val FORMAT_VERSION = "1"

    /**
     * Enough depth for any composition a 1.4-inch round screen can carry, and small enough that
     * the encoded value stays a rounding error in the phone->watch preference payload (see
     * `WatchPreferenceSyncCoordinator` - that snapshot has a real ceiling and this key is stored
     * per face).
     */
    const val MAX_LAYERS = 8

    /**
     * The public contract's declared ceiling for this key's encoded value.
     *
     * Every other public string setting is capped at 128 characters; a full eight-layer stack with
     * custom colours throughout does not fit in that, so the community-theme contract declares this
     * length on the key's own rule rather than raising the shared cap for every setting. Length is
     * not the boundary here in any case - [parse] is stricter than any length check, since it
     * accepts only enumerated styles, a bounded integer and a fixed colour grammar.
     */
    const val MAX_ENCODED_LENGTH = 384

    /** A layer at its style's designed depth. */
    const val DEFAULT_OPACITY_PERCENT = 100

    /**
     * Strongest a layer may be, which is deliberately above the 150 % the editor's slider offers.
     *
     * An authored wash's depth has always been `album_art_dim_strength / 0.8` - the shipped 80 %
     * meaning "exactly as designed" - so the legacy ceiling of 150 % maps to 188 here. Clamping to
     * 150 instead would make one particular setting (a dim strength above 120 %) come out visibly
     * gentler after the stack than before it, which is the one thing the implicit mapping exists
     * to avoid. Nobody can *choose* a value above 150; it only ever arrives by migration.
     */
    const val MAX_OPACITY_PERCENT = 188

    private const val LAYER_SEPARATOR = '|'
    private const val FIELD_SEPARATOR = '.'
    private val HEX_COLOR = Regex("^#[0-9A-F]{6}$")

    /**
     * Styles a [BackgroundLayerKind.WASH] layer may carry: the authored treatments, and the two
     * that paint an opaque field.
     *
     * The plain artwork treatments are deliberately absent. They describe what happens *to the
     * bitmap* and draw nothing on top of it, so as a layer each one would be an entry that
     * silently does nothing - a control that appears to be broken rather than one that is
     * inapplicable, which is the distinction `updatePlayerCapabilityVisibility` already draws.
     */
    val washStyles: List<PlayerBackgroundStyle> =
            PlayerBackgroundStyle.entries.filterNot { it.isPlainArtworkTreatment }

    /**
     * Styles a [BackgroundLayerKind.SHADE] layer may carry: every shading but `follow`.
     *
     * `follow` is not a treatment, it is the absence of a choice - "whatever the background style
     * already decided". A layer *is* the choice, so there is nothing for it to follow.
     */
    val shadeStyles: List<PlayerShadingStyle> =
            PlayerShadingStyle.entries.filterNot { it == PlayerShadingStyle.FOLLOW }

    /** Styles a [BackgroundLayerKind.FLOOR] layer may carry: every floor but `off`, for the same
     *  reason - removing the layer is how a floor is turned off now. */
    val floorStyles: List<AccentFloorStyle> = AccentFloorStyle.entries.filter { it.isVisible }

    /**
     * Faces that paint their own opaque backdrop, so the base treatment never reaches the screen.
     *
     * Split alone: its two-band panel *is* its background, and the shared layer underneath is
     * simply covered. That is why [implicitStack] must be told - writing the base wash into the
     * seed for a face that has never shown it would redesign that face the first time somebody
     * reordered a shading.
     */
    val SELF_BACKDROP_FACES: Set<String> = setOf("split")

    /**
     * The explicit stack [raw] encodes, or null when there is none.
     *
     * Null means "fall back to the legacy keys", which is why this refuses a value it only partly
     * understands instead of returning what it managed to read - see the class doc.
     */
    fun parse(raw: String?): List<BackgroundLayer>? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty() || value.length > MAX_ENCODED_LENGTH) return null
        val parts = value.split(LAYER_SEPARATOR)
        if (parts.first() != FORMAT_VERSION) return null
        val encodedLayers = parts.drop(1)
        if (encodedLayers.size > MAX_LAYERS) return null
        val layers = ArrayList<BackgroundLayer>(encodedLayers.size)
        encodedLayers.forEach { encoded -> layers += parseLayer(encoded) ?: return null }
        return layers
    }

    /** True when [raw] is a stack this build renders, i.e. [parse] accepts it. */
    fun isExplicit(raw: String?): Boolean = parse(raw) != null

    /**
     * The persisted form of [layers], always parseable by [parse].
     *
     * An empty list encodes to the bare version marker rather than to `""`: an empty *stack* and
     * an *absent* stack render differently and the persisted value has to keep them apart.
     */
    fun encode(layers: List<BackgroundLayer>): String =
            (listOf(FORMAT_VERSION) + layers.take(MAX_LAYERS).map(::encodeLayer))
                    .joinToString(LAYER_SEPARATOR.toString())

    /**
     * What the fixed three-slot arrangement is, said as a stack.
     *
     * Two jobs, and they pull in the same direction. The Background page renders this when nothing
     * explicit is stored, so a user opening it sees their current look already described as layers
     * rather than an empty list next to controls that clearly do something; and it is what the
     * first edit is seeded from, so adopting the stack is visually a no-op. That second job is why
     * the conditions below mirror the renderers exactly rather than being tidied - including the
     * one that reads oddly, that an authored wash is suppressed entirely the moment an explicit
     * shading is chosen. Tidying it would silently redraw every face whose owner touched shading.
     */
    fun implicitStack(
            background: PlayerBackgroundStyle,
            dimEnabled: Boolean,
            dimPercent: Int,
            shading: PlayerShadingStyle,
            shadingColor: BackgroundLayerColor,
            floor: AccentFloorStyle,
            floorColor: BackgroundLayerColor,
            baseWashDrawn: Boolean = true
    ): List<BackgroundLayer> {
        val layers = mutableListOf<BackgroundLayer>()
        val plain = background.isPlainArtworkTreatment
        val follows = shading == PlayerShadingStyle.FOLLOW

        // An authored background draws its designed look whatever "Dim album art" says - that
        // switch governs the separate legibility scrim over plain artwork. The strength slider
        // still modulates its depth, and its 80% default is what "100% of the designed depth"
        // means, so the two ways of saying the same thing keep agreeing.
        if (baseWashDrawn && !plain && follows) {
            layers += BackgroundLayer(
                    kind = BackgroundLayerKind.WASH,
                    style = background.preferenceValue,
                    opacityPercent = washPercentFor(dimPercent))
        }

        if (floor.isVisible) {
            layers += BackgroundLayer(
                    kind = BackgroundLayerKind.FLOOR,
                    style = floor.preferenceValue,
                    color = floorColor)
        }

        // Drawn last in the fixed arrangement - the shading pass ran after the background
        // layer - so it is the top layer here. `follow` over plain artwork resolves to the neutral bottom fade, which
        // is a real treatment rather than a default, and has to survive being written down.
        if (dimEnabled && (!follows || plain)) {
            layers += BackgroundLayer(
                    kind = BackgroundLayerKind.SHADE,
                    style = (if (follows) PlayerShadingStyle.BOTTOM_FADE else shading).preferenceValue,
                    opacityPercent = dimPercent.coerceIn(0, SHADING_MAX_PERCENT),
                    color = shadingColor)
        }

        return layers
    }

    /**
     * The stack a renderer should draw: the explicit one, else [implicitStack].
     *
     * Every consumer goes through this rather than testing the raw value itself, so "is there a
     * stack" is answered the same way on the watch, in the phone's preview and on the settings
     * page. They already disagreed once about which swatch of a cover becomes the accent; a second
     * disagreement about whether a background exists at all would be worse.
     */
    fun resolve(
            raw: String?,
            background: PlayerBackgroundStyle,
            dimEnabled: Boolean,
            dimPercent: Int,
            shading: PlayerShadingStyle,
            shadingColor: BackgroundLayerColor,
            floor: AccentFloorStyle,
            floorColor: BackgroundLayerColor,
            baseWashDrawn: Boolean = true
    ): List<BackgroundLayer> = parse(raw) ?: implicitStack(
            background = background,
            dimEnabled = dimEnabled,
            dimPercent = dimPercent,
            shading = shading,
            shadingColor = shadingColor,
            floor = floor,
            floorColor = floorColor,
            baseWashDrawn = baseWashDrawn)

    /**
     * The percentage that reproduces today's authored-wash depth for a given shading strength.
     *
     * `authoredStrength = dimStrength / .8f` is written into all three renderers, so the shipped
     * 80% default has always meant "exactly the designed depth". Stating that as 100% is what lets
     * a wash layer's number mean the same thing as a shading layer's - its own style's depth -
     * instead of carrying an unexplained 0.8 into the settings UI.
     */
    fun washPercentFor(dimPercent: Int): Int =
            ((dimPercent.coerceIn(0, SHADING_MAX_PERCENT) / 0.8f) + 0.5f).toInt()
                    .coerceIn(0, MAX_OPACITY_PERCENT)

    /** True when [style] is one [kind] accepts, i.e. the pairing survives [parse]. */
    fun accepts(kind: BackgroundLayerKind, style: String): Boolean = when (kind) {
        BackgroundLayerKind.WASH -> washStyles.any { it.preferenceValue == style }
        BackgroundLayerKind.SHADE -> shadeStyles.any { it.preferenceValue == style }
        BackgroundLayerKind.FLOOR -> floorStyles.any { it.preferenceValue == style }
    }

    /** Every style [kind] accepts, in the order its own enum declares them. */
    fun stylesFor(kind: BackgroundLayerKind): List<String> = when (kind) {
        BackgroundLayerKind.WASH -> washStyles.map { it.preferenceValue }
        BackgroundLayerKind.SHADE -> shadeStyles.map { it.preferenceValue }
        BackgroundLayerKind.FLOOR -> floorStyles.map { it.preferenceValue }
    }

    /** The style a newly added [kind] layer starts on. */
    fun defaultStyleFor(kind: BackgroundLayerKind): String = when (kind) {
        BackgroundLayerKind.WASH -> PlayerBackgroundStyle.POSTER.preferenceValue
        BackgroundLayerKind.SHADE -> PlayerShadingStyle.BOTTOM_FADE.preferenceValue
        BackgroundLayerKind.FLOOR -> AccentFloorStyle.STANDARD.preferenceValue
    }

    /**
     * [layers] with the entry at [index] moved by [delta], or the list unchanged when it cannot be.
     *
     * A move that would fall off either end is a no-op rather than a clamp: the caller is a pair of
     * arrows the user is holding down, and silently rotating the list under them would be a very
     * confusing answer to "up" at the top.
     */
    fun move(layers: List<BackgroundLayer>, index: Int, delta: Int): List<BackgroundLayer> {
        val target = index + delta
        if (index !in layers.indices || target !in layers.indices) return layers
        val reordered = layers.toMutableList()
        reordered.add(target, reordered.removeAt(index))
        return reordered
    }

    /** [layers] with the entry at [index] repeated directly above itself, up to [MAX_LAYERS]. */
    fun duplicate(layers: List<BackgroundLayer>, index: Int): List<BackgroundLayer> {
        if (index !in layers.indices || layers.size >= MAX_LAYERS) return layers
        val copied = layers.toMutableList()
        copied.add(index + 1, layers[index])
        return copied
    }

    /** [layers] without the entry at [index]. */
    fun remove(layers: List<BackgroundLayer>, index: Int): List<BackgroundLayer> {
        if (index !in layers.indices) return layers
        return layers.toMutableList().also { it.removeAt(index) }
    }

    /** [layers] with [layer] on top, if there is room for it. */
    fun add(layers: List<BackgroundLayer>, layer: BackgroundLayer): List<BackgroundLayer> =
            if (layers.size >= MAX_LAYERS) layers else layers + layer

    private fun parseLayer(encoded: String): BackgroundLayer? {
        val fields = encoded.split(FIELD_SEPARATOR)
        if (fields.size !in 2..5) return null
        val kind = BackgroundLayerKind.fromToken(fields[0]) ?: return null
        val style = fields[1]
        if (!accepts(kind, style)) return null

        val opacity = when (val raw = fields.getOrNull(2)) {
            null -> DEFAULT_OPACITY_PERCENT
            // Not toIntOrNull alone: it accepts "+5", " 5" and "060", so three different strings
            // would encode one layer and the settings digest that backs duplicate detection would
            // stop being a function of the composition. Comparing back against the canonical
            // decimal is what actually closes that, since digits-only still admits leading zeros.
            else -> raw.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.toIntOrNull()
                    ?.takeIf { it in 0..MAX_OPACITY_PERCENT && it.toString() == raw }
                    ?: return null
        }

        val color = when (val raw = fields.getOrNull(3)) {
            null -> BackgroundLayerColor.DEFAULT
            else -> BackgroundLayerColor.entries.firstOrNull { it.preferenceValue == raw }
                    ?: return null
        }

        val custom = fields.getOrNull(4).orEmpty()
        if (color == BackgroundLayerColor.CUSTOM) {
            if (!HEX_COLOR.matches(custom)) return null
        } else if (fields.size == 5) {
            // A hex on a layer that does not read one is not harmless: two encodings of the same
            // composition would digest differently, so the duplicate check would stop seeing them
            // as the same theme.
            return null
        }

        return BackgroundLayer(kind, style, opacity, color, custom)
    }

    /**
     * The shortest form that round-trips, so equal compositions encode identically.
     *
     * Trailing defaults are dropped rather than always written for the digest's sake as much as the
     * payload's: `settingsDigest` is computed over the stored string, so a stack that was built by
     * adding a layer and one built by editing another must not differ by a `.100.default` nobody
     * chose.
     */
    private fun encodeLayer(layer: BackgroundLayer): String {
        val fields = mutableListOf(layer.kind.token, layer.style)
        val custom = layer.customColor.takeIf { layer.color == BackgroundLayerColor.CUSTOM }
                ?.uppercase().orEmpty()
        val needsColor = layer.color != BackgroundLayerColor.DEFAULT
        val needsOpacity = layer.opacityPercent != DEFAULT_OPACITY_PERCENT || needsColor
        if (needsOpacity) fields += layer.opacityPercent.coerceIn(0, MAX_OPACITY_PERCENT).toString()
        if (needsColor) fields += layer.color.preferenceValue
        if (layer.color == BackgroundLayerColor.CUSTOM) fields += custom
        return fields.joinToString(FIELD_SEPARATOR.toString())
    }
}

/** Which of the three drawing roles a layer plays. See [BackgroundLayerStack]. */
enum class BackgroundLayerKind(val token: String) {
    /** An authored background treatment - Poster's gradients, Aurora's ribbon, an opaque field. */
    WASH("w"),

    /** A darkening or tinting pass - see [PlayerShadingStyle]. */
    SHADE("s"),

    /** The accent wash pooled along the bottom edge - see [AccentFloorStyle]. */
    FLOOR("f");

    companion object {
        fun fromToken(token: String?): BackgroundLayerKind? =
                entries.firstOrNull { it.token == token }

        fun fromPreference(value: String?): BackgroundLayerKind? =
                entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}

/**
 * Where a layer takes its colour from.
 *
 * [DEFAULT] is not a colour, it is "whatever this kind draws with when nobody chose" - black for a
 * shading, the album's primary for a floor, and the treatment's own authored palette for a wash.
 * Keeping it distinct from naming that answer explicitly is what lets a wash stay untinted at all:
 * every authored background composes several album tones itself, and a single colour is not
 * something it has a slot for.
 */
enum class BackgroundLayerColor(val preferenceValue: String) {
    DEFAULT("default"),
    ALBUM("album"),
    SECONDARY("secondary"),
    TERTIARY("tertiary"),
    DESATURATED("desaturated"),
    BLACK("black"),
    CUSTOM("custom");

    companion object {
        fun fromPreference(value: String?): BackgroundLayerColor =
                entries.firstOrNull { it.preferenceValue == value } ?: DEFAULT

        /** Modes offered for a shading layer, mirroring `wear_shading_color_mode`. */
        val SHADE_MODES: List<BackgroundLayerColor> =
                listOf(BLACK, ALBUM, DESATURATED, CUSTOM)

        /** Modes offered for an accent-floor layer, mirroring `wear_accent_floor_color_mode`. */
        val FLOOR_MODES: List<BackgroundLayerColor> =
                listOf(ALBUM, SECONDARY, TERTIARY, CUSTOM)

        /** How a shading layer's [DEFAULT] resolves, so the two ways of saying it agree. */
        val SHADE_DEFAULT = BLACK

        /** How a floor layer's [DEFAULT] resolves. */
        val FLOOR_DEFAULT = ALBUM
    }
}

/** One entry in a [BackgroundLayerStack]: an enumerated style, a strength and a colour choice. */
data class BackgroundLayer(
        val kind: BackgroundLayerKind,
        val style: String,
        val opacityPercent: Int = BackgroundLayerStack.DEFAULT_OPACITY_PERCENT,
        val color: BackgroundLayerColor = BackgroundLayerColor.DEFAULT,
        /** `#RRGGBB`, read only when [color] is [BackgroundLayerColor.CUSTOM]. */
        val customColor: String = ""
) {
    /**
     * Strength as the multiplier every renderer applies, on the same scale the legacy keys use.
     *
     * Not clamped to 150: see [BackgroundLayerStack.MAX_OPACITY_PERCENT]. A shading beyond its own
     * ceiling is clamped where it is drawn (`PlayerShadingDrawable`, `drawShadingTreatment`) and a
     * floor's peak alpha saturates at 1, so only a wash can actually use the extra range - which
     * is the one that needs it.
     */
    val strength: Float
        get() = opacityPercent.coerceIn(0, BackgroundLayerStack.MAX_OPACITY_PERCENT) / 100f

    /** The colour mode actually drawn, with [BackgroundLayerColor.DEFAULT] already resolved. */
    val effectiveColor: BackgroundLayerColor
        get() = when {
            color != BackgroundLayerColor.DEFAULT -> color
            kind == BackgroundLayerKind.FLOOR -> BackgroundLayerColor.FLOOR_DEFAULT
            else -> BackgroundLayerColor.SHADE_DEFAULT
        }
}

/**
 * A [BackgroundLayer] with its style decoded and its colour already resolved against the album.
 *
 * The split exists because colour resolution needs `PaletteTransforms` and a live palette, which
 * the three renderers each already have and which would drag `androidx` into the pure model. So
 * this file stops at "which style, how strong, what colour", and each host answers the last part
 * with the same helpers it uses for everything else.
 */
sealed interface ResolvedBackgroundLayer {
    val strength: Float

    data class Wash(
            val style: PlayerBackgroundStyle,
            override val strength: Float
    ) : ResolvedBackgroundLayer

    data class Shade(
            val style: PlayerShadingStyle,
            override val strength: Float,
            val color: Int
    ) : ResolvedBackgroundLayer

    data class Floor(
            val style: AccentFloorStyle,
            override val strength: Float,
            val color: Int
    ) : ResolvedBackgroundLayer
}

/**
 * Decodes [this] into drawable layers, asking [shadeColor]/[floorColor] for the tints.
 *
 * A layer whose style this build does not know is dropped rather than refused: [BackgroundLayerStack.parse]
 * has already refused the whole stack in that case, so reaching here means the value came from the
 * implicit path, where a stale legacy key is exactly the thing that should be ignored quietly.
 */
fun List<BackgroundLayer>.resolveLayers(
        shadeColor: (BackgroundLayer) -> Int,
        floorColor: (BackgroundLayer) -> Int
): List<ResolvedBackgroundLayer> = mapNotNull { layer ->
    when (layer.kind) {
        BackgroundLayerKind.WASH ->
            BackgroundLayerStack.washStyles
                    .firstOrNull { it.preferenceValue == layer.style }
                    ?.let { ResolvedBackgroundLayer.Wash(it, layer.strength) }

        BackgroundLayerKind.SHADE ->
            BackgroundLayerStack.shadeStyles
                    .firstOrNull { it.preferenceValue == layer.style }
                    ?.let { ResolvedBackgroundLayer.Shade(it, layer.strength, shadeColor(layer)) }

        BackgroundLayerKind.FLOOR ->
            BackgroundLayerStack.floorStyles
                    .firstOrNull { it.preferenceValue == layer.style }
                    ?.let { ResolvedBackgroundLayer.Floor(it, layer.strength, floorColor(layer)) }
    }
}
