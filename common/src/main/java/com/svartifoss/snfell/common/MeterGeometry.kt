package com.svartifoss.snfell.common

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * A one-dimensional path a level meter runs along: an arc of a circle or a straight segment.
 *
 * The volume bar used to express every composition as "an arc, or one of four linear special
 * cases", and each new style had to be written once per shape. A track is the common denominator:
 * a point and a normal at a fraction of the way along, with 0 at the quiet end. Anything drawn
 * *relative* to a track - a wave that leaves the line, a ruler whose ticks stand off it, a mark
 * sitting on it - then works on every layout without knowing which one it is on.
 *
 * Deliberately free of `android.*`, so the arithmetic is pinned by plain JVM tests while the
 * drawing that consumes it stays in [VolumeFx].
 */
sealed class MeterTrack {

    /** Length along the track, in the same unit as its coordinates. */
    abstract val length: Float

    /** Writes the position at fraction [t] (0 = quiet end, 1 = loud end) into `out[0]`, `out[1]`. */
    abstract fun pointAt(t: Float, out: FloatArray)

    /**
     * Writes the unit normal at fraction [t] into `out[0]`, `out[1]`. For an arc it points away
     * from the centre; for a line it is the direction of travel turned a quarter turn.
     */
    abstract fun normalAt(t: Float, out: FloatArray)

    /** An arc with the Canvas convention: 0 degrees at three o'clock, positive clockwise. */
    class Arc(
            val cx: Float,
            val cy: Float,
            val radius: Float,
            val startDeg: Float,
            val sweepDeg: Float
    ) : MeterTrack() {
        override val length: Float get() = radius * abs(Math.toRadians(sweepDeg.toDouble())).toFloat()

        override fun pointAt(t: Float, out: FloatArray) {
            val angle = Math.toRadians((startDeg + sweepDeg * t).toDouble())
            out[0] = cx + radius * cos(angle).toFloat()
            out[1] = cy + radius * sin(angle).toFloat()
        }

        override fun normalAt(t: Float, out: FloatArray) {
            val angle = Math.toRadians((startDeg + sweepDeg * t).toDouble())
            out[0] = cos(angle).toFloat()
            out[1] = sin(angle).toFloat()
        }
    }

    /** A straight segment from the quiet end to the loud end. */
    class Line(
            val x0: Float,
            val y0: Float,
            val x1: Float,
            val y1: Float
    ) : MeterTrack() {
        override val length: Float get() = hypot(x1 - x0, y1 - y0)

        override fun pointAt(t: Float, out: FloatArray) {
            out[0] = x0 + (x1 - x0) * t
            out[1] = y0 + (y1 - y0) * t
        }

        override fun normalAt(t: Float, out: FloatArray) {
            val len = length
            if (len <= 0f) {
                out[0] = 0f
                out[1] = -1f
                return
            }
            out[0] = (y1 - y0) / len
            out[1] = -(x1 - x0) / len
        }
    }
}

/**
 * The phase of a travelling wave, advanced one drawn frame at a time.
 *
 * The volume bar, the progress ring and the seek timeline all draw the same wave and used to carry
 * three copies of this bookkeeping. The phase is accumulated rather than derived from the clock so
 * that a wave which stops (the music pauses, the panel closes) resumes where it stood instead of
 * snapping to wherever the clock says it would have got to; and a long gap between two frames is
 * clamped, so a view that was off screen does not throw the crests across the track when it
 * returns. Pure (the caller passes the time), so the rules are pinned by a JVM test.
 */
class WavePhaseClock {

    /** How far the crests have travelled, as a fraction of one wavelength (0 up to 1). */
    var phase: Float = 0f
        private set

    private var lastFrameAt = 0L

    /**
     * Advances [phase] by the time since the previous frame when [moving], or holds it still and
     * forgets the previous frame when not, so the next moving frame starts without a jump.
     * Returns the phase to draw with.
     */
    fun advance(moving: Boolean, nowMs: Long): Float {
        if (!moving) {
            lastFrameAt = 0L
            return phase
        }
        if (lastFrameAt != 0L) {
            val elapsed = (nowMs - lastFrameAt).coerceIn(0L, MAX_STEP_MS)
            phase = (phase + elapsed.toFloat() / MeterShape.WAVE_PERIOD_MS) % 1f
        }
        lastFrameAt = nowMs
        return phase
    }

    companion object {
        /** About thirty frames a second: smooth to the eye at half the cost of sixty. */
        const val FRAME_MS = 33L

        /** The longest stretch one frame may advance the wave by. */
        const val MAX_STEP_MS = 100L
    }
}

/**
 * The numbers behind the volume styles that are not a recolouring of an arc: how a wave bends, where
 * a ruler puts its marks, how tall each basalt column stands and how much of it is lit.
 *
 * Every function is pure. The drawing that turns these into pixels is [VolumeFx]; keeping the
 * decisions here is what lets a test say "a louder level bends the wave more" or "the level can
 * only ever light one column partially" without a canvas.
 */
object MeterShape {

    // ---- Wave ------------------------------------------------------------------------------

    /**
     * Distance between two wave crests, in density-independent pixels.
     *
     * Long enough for each crest to read as a smooth swell, the way the Expressive face's wavy ring
     * does, rather than as a fine zigzag: at 15 dp the same wave looked like a rough edge on a
     * 200 dp watch.
     */
    const val WAVELENGTH_DP = 26f

    /** The smallest amplitude (as a share of the largest) a non-silent level is drawn with. */
    const val WAVE_FLOOR = 0.18f

    /**
     * The wave's amplitude as a share of its maximum at [level].
     *
     * It *grows* with the level - the whole idea of the style - but never reaches zero for an
     * audible level: a perfectly straight line at 5 % would read as a plain bar and the style would
     * look broken exactly where it is hardest to see.
     */
    fun waveAmplitude(level: Float): Float {
        val clamped = level.coerceIn(0f, 1f)
        if (clamped <= 0.005f) return 0f
        return WAVE_FLOOR + (1f - WAVE_FLOOR) * clamped
    }

    /** How many crests fit on a track [length] long, never fewer than one. */
    fun waveCycles(length: Float, wavelength: Float): Float =
            max(1f, length / max(1f, wavelength))

    /**
     * How long the wave takes to travel one full wavelength, in milliseconds.
     *
     * Slow enough to read as the line breathing rather than as a flicker, and a whole number of
     * cycles per turn so the repeat is invisible. The same value drives the volume and progress
     * waves, because two moving waves on one watch at different speeds would look like a mistake.
     */
    const val WAVE_PERIOD_MS = 1700L

    /** The phase (0 up to, never including, 1 - a fraction of a wavelength) after [elapsedMs]. */
    fun wavePhase(elapsedMs: Long): Float =
            ((elapsedMs % WAVE_PERIOD_MS + WAVE_PERIOD_MS) % WAVE_PERIOD_MS).toFloat() /
                    WAVE_PERIOD_MS

    /**
     * Sideways displacement of the wave at fraction [t], in units of half its peak-to-peak size.
     *
     * Symmetric waves swing about the track, within -1..1, and ease in over the first stretch so
     * they grow out of the baseline instead of starting with a kink. With [inwardOnly] the wave
     * touches the track and only ever moves away from it on one side, within -1..0: that is what an
     * arc on the bezel needs, because the layout reserved room for the arc and not for a crest
     * poking out past it. Both are anchored at the quiet end, so a wave that travels (a [phase]
     * that moves) leaves the start of the track still and swings only from just after it.
     *
     * [phase] is how far the crests have travelled, as a fraction of one wavelength: growing it
     * moves them along the track toward the loud end, which is what makes the line look alive.
     */
    fun waveOffset(t: Float, cycles: Float, inwardOnly: Boolean = false, phase: Float = 0f): Float {
        val angle = 2.0 * PI * (cycles * t - phase)
        val raw = if (inwardOnly) ((cos(angle) - 1.0) / 2.0).toFloat() else sin(angle).toFloat()
        return raw * smoothStep(0f, 0.08f, t)
    }

    private fun smoothStep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    // ---- Ruler -----------------------------------------------------------------------------

    /** Marks on the ruler: one every 5 percent, so there are twenty-one including both ends. */
    const val RULER_MARKS = 21

    /** Every fifth mark is a major one (0, 25, 50, 75, 100 percent). */
    const val RULER_MAJOR_EVERY = 5

    enum class RulerMark { MINOR, MAJOR }

    fun rulerMark(index: Int): RulerMark =
            if (index % RULER_MAJOR_EVERY == 0) RulerMark.MAJOR else RulerMark.MINOR

    /** Fraction along the track of mark [index]. */
    fun rulerPosition(index: Int): Float = index.toFloat() / (RULER_MARKS - 1)

    /** Length of a mark as a share of the ruler's full tick height. */
    fun rulerMarkLength(mark: RulerMark): Float = when (mark) {
        RulerMark.MAJOR -> 1f
        RulerMark.MINOR -> 0.5f
    }

    /** True when mark [index] is at or below [level]; the epsilon keeps 50 % lighting the 50 % mark. */
    fun rulerMarkLit(index: Int, level: Float): Boolean =
            rulerPosition(index) <= level + 0.001f

    // ---- Basalt ----------------------------------------------------------------------------

    /** Columns across the meter. Odd, so there is a middle one. */
    const val BASALT_COLUMNS = 7

    /**
     * Relative heights of the columns, left (quiet) to right (loud).
     *
     * Not a smooth ramp: the cliff at Svartifoss is a ragged crown of hexagonal basalt, and a
     * monotonic staircase would just be the signal-strength icon with a different cap. The profile
     * still rises overall, so the loud end is always the tall end and the level stays readable.
     */
    val BASALT_PROFILE: FloatArray = floatArrayOf(0.46f, 0.62f, 0.54f, 0.78f, 0.70f, 0.90f, 1.0f)

    /** Gap between neighbours as a share of one column's width. */
    const val BASALT_GAP = 0.22f

    /** The hairline every column is outlined with, so the unlit ones still stand on any backdrop. */
    const val BASALT_OUTLINE_DP = 1f
    const val BASALT_OUTLINE_COLOR = 0x47FFFFFF

    /** Half the height of the rhombus on top of a column, as a share of the column's width. */
    const val BASALT_CAP = 0.2887f   // tan(30 degrees): the isometric slope of a hexagon's top

    /** Width of each column when [count] of them share [totalWidth]. */
    fun basaltColumnWidth(totalWidth: Float, count: Int = BASALT_COLUMNS): Float =
            totalWidth / (count + (count - 1) * BASALT_GAP)

    /** Left edge of column [index]. */
    fun basaltColumnLeft(totalLeft: Float, totalWidth: Float, index: Int,
                         count: Int = BASALT_COLUMNS): Float {
        val width = basaltColumnWidth(totalWidth, count)
        return totalLeft + index * width * (1f + BASALT_GAP)
    }

    /** Height of column [index] when the tallest one is [maxHeight]. */
    fun basaltColumnHeight(maxHeight: Float, index: Int): Float =
            maxHeight * BASALT_PROFILE[index.coerceIn(0, BASALT_PROFILE.lastIndex)]

    /**
     * How full column [index] of [count] is at [level], 0..1.
     *
     * The level is spread over the columns, so exactly one of them is partly lit at any moment and
     * the rest are all or nothing. That partial column is what gives the control a continuous
     * response; with seven columns a plain on/off row would move in 14 percent jumps.
     */
    fun basaltFill(index: Int, level: Float, count: Int = BASALT_COLUMNS): Float {
        val clamped = level.coerceIn(0f, 1f)
        val span = 1f / count
        return ((clamped - index * span) / span).coerceIn(0f, 1f)
    }

    // ---- Thickness -------------------------------------------------------------------------

    const val THICKNESS_MIN = 50
    const val THICKNESS_MAX = 180
    const val THICKNESS_DEFAULT = 100

    /** The stroke multiplier for a stored percentage, clamped to what the layouts have room for. */
    fun thicknessScale(percent: Int): Float =
            percent.coerceIn(THICKNESS_MIN, THICKNESS_MAX) / 100f

    /**
     * The stroke scale layout margins must reserve room for: never below 1, so thinning a stroke
     * does not also move the arc inwards and make the thin setting look like a different layout.
     */
    fun reservedScale(percent: Int): Float = max(1f, thicknessScale(percent))

    // ---- Layout metrics, in dp -------------------------------------------------------------
    //
    // Shared by the watch and the phone's miniature, which cannot read each other's sources: the
    // numbers that decide where the two new compositions sit live here so they cannot drift.

    /** The pillar is a centred upright capsule, nudged left to leave the readout a place of its own. */
    const val PILLAR_WIDTH_DP = 18f
    const val PILLAR_HEIGHT_DP = 108f
    const val PILLAR_HEIGHT_FRACTION = 0.5f
    const val PILLAR_OFFSET_X_DP = -38f
    const val PILLAR_READOUT_SHIFT_X_DP = 34f

    /** The basalt cluster sits below the middle, with the percentage above it. */
    const val BASALT_WIDTH_DP = 150f
    const val BASALT_WIDTH_FRACTION = 0.7f
    const val BASALT_HEIGHT_DP = 52f
    const val BASALT_BOTTOM_OFFSET_DP = 50f
    const val BASALT_READOUT_SHIFT_Y_DP = -34f

    /** Clamp helper kept here so every caller bounds a column index the same way. */
    fun columnRange(count: Int = BASALT_COLUMNS): IntRange = 0 until min(count, BASALT_PROFILE.size)
}
