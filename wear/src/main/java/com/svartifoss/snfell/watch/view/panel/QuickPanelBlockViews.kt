package com.svartifoss.snfell.watch.view.panel

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.QuickPanelBlock
import com.svartifoss.snfell.common.QuickPanelGeometry
import com.svartifoss.snfell.common.QuickPanelTool
import com.svartifoss.snfell.common.SeekGlyphs
import com.svartifoss.snfell.common.SleepTimerPolicy
import com.svartifoss.snfell.watch.config.ButtonAction
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * What the quick panel's block views need from the screen that hosts them in order to look like the
 * rest of the panel.
 *
 * The panel has some thirty styles and they are all drawable factories that live on `MainActivity`
 * (they read its album palette). Rather than copy that table, the new blocks ask for the three
 * surfaces they need - a full-width row, an idle round control and an active one - and the
 * colours that read on them, so a block can never disagree with the buttons beside it about what
 * the chosen style looks like.
 *
 * Every drawable is a **new instance** per call: a Drawable carries state and bounds, so one handed
 * to two views draws wrong in at least one of them.
 */
interface QuickPanelSkin {
    /** Surface of a full-width row, per the panel's style. */
    fun rowBackground(): Drawable

    /** Surface of an idle round control. */
    fun roundBackground(): Drawable

    /** Surface of a round control that is switched on. */
    fun activeRoundBackground(): Drawable

    /** Glyph and text colour on [roundBackground] and [rowBackground]. */
    fun tint(): Int

    /** Glyph and text colour on [activeRoundBackground]. */
    fun activeTint(): Int

    /** The album accent. */
    fun accent(): Int

    /** The panel's chosen typeface, or null for the default. */
    fun typeface(): Typeface?

    /** Height of a full-width pill, in px. */
    fun rowHeightPx(): Int
}

/** What the block views can ask the screen to do. */
interface QuickPanelHost {
    /** The phone's current volume, 0..1. */
    fun volume(): Float

    fun setVolume(volume: Float)

    /** Moves playback by [deltaMs] (negative goes back). */
    fun skipBy(deltaMs: Long)

    /** Steps the playback speed to the next preset. */
    fun cycleSpeed()

    /** Steps the phone's sleep timer to its next preset. The panel stays open. */
    fun cycleSleepTimer()

    /** Runs [tool]. The panel closes first for the ones that open another screen. */
    fun openTool(tool: QuickPanelTool)

    /** Runs the actions-menu entry at [index], as the panel's rows do. */
    fun runMenuAction(index: Int)

    /** A short confirming vibration. */
    fun buzz()
}

/** One entry of the favourites block: the action, and where it sits in the actions menu. */
data class QuickPanelFavorite(val menuIndex: Int, val action: ButtonAction)

/**
 * Builds and updates the blocks of the quick panel that the original panel did not have: a volume
 * row, a skip-and-position block, a row of tools, a grid of favourites and the button that opens
 * the full menu.
 *
 * ## One pill per block
 *
 * Each of these blocks is a single surface - the panel's own row surface, the one Up Next and the
 * action rows are drawn on - with its controls sitting flat on it, and their glyphs and text in the
 * colour that surface was picked for. They used to be bare controls (a round button here, a line of
 * text there) laid directly on the backdrop, and the text was set in the panel's tint, which is
 * chosen to read on a pill: a dark ink for a light tonal surface. On a dark album backdrop that is
 * dark text on a dark ground, and on a light one with a light style it is the reverse. Putting the
 * block on its own surface makes its legibility a property of the panel's style and not of
 * whatever happens to be playing.
 *
 * ## Mechanics
 *
 * Each block's root view is created once and kept, so the panel can add it to and remove it from
 * its column freely; `bind*` fills it for the current configuration and may be called any number of
 * times. State that changes while the panel is open (volume, position, speed) arrives through the
 * `on*` methods, which touch only the views that exist. Colours are applied at bind time: a new
 * track's palette re-runs the panel's layout (`MainActivity.applyAccentColor`), which rebinds.
 *
 * Plain Views rather than Compose, on purpose: they sit in the panel's existing scroll host and
 * inherit its dismiss, rotary-scroll and indicator behaviour, and their surfaces come from the same
 * Drawable factories as every other pill in it. That host treats a tap that lands on nothing
 * clickable as a tap on the backdrop and closes the panel, so every pill is itself marked
 * clickable - a tap on its surface between two controls does nothing, instead of dismissing.
 */
class QuickPanelBlockViews(
        private val context: Context,
        private val skin: QuickPanelSkin,
        private val host: QuickPanelHost
) {
    private val density = context.resources.displayMetrics.density
    private val handler = Handler(Looper.getMainLooper())

    private fun dp(value: Float): Int = (value * density).roundToInt()

    /** The width the panel's content has to work with: the screen less its side padding. */
    private fun contentWidthPx(): Int = context.resources.displayMetrics.widthPixels - dp(16f)

    /** The width inside a pill that holds a row of chips. */
    private fun chipRowWidthPx(): Int = contentWidthPx() - dp(QuickPanelGeometry.PILL_PADDING_H_DP) * 2

    private var volume = 0f
    private var positionMs = 0L
    private var durationMs = 0L
    private var seekable = false
    private var speed = 1f

    private var volumeRoot: LinearLayout? = null
    private var volumeBar: LevelBarView? = null

    private var seekRoot: LinearLayout? = null
    private var seekTime: TextView? = null
    private var seekDuration: TextView? = null
    private var seekBar: LevelBarView? = null
    private val seekChips = ArrayList<ImageView>()

    private var toolsRoot: LinearLayout? = null
    private var speedChip: LinearLayout? = null
    private var sleepChip: LinearLayout? = null

    /** When the sleep timer ends, on this device's monotonic clock; 0 when none is running. */
    private var sleepEndsAtRealtimeMs = 0L

    private var favoritesRoot: LinearLayout? = null
    private var actionsButtonRoot: LinearLayout? = null

    // --- State ---

    fun onVolume(level: Float) {
        volume = level.coerceIn(0f, 1f)
        volumeBar?.apply {
            fraction = volume
            // The number is no longer drawn, so the bar is what says it to a screen reader.
            contentDescription = context.getString(
                    R.string.quick_volume_description, (volume * 100f).roundToInt())
        }
    }

    fun onPosition(positionMs: Long, durationMs: Long, seekable: Boolean) {
        this.positionMs = positionMs
        this.durationMs = durationMs
        val changed = this.seekable != seekable
        this.seekable = seekable
        val known = durationMs > 0L
        seekBar?.fraction = if (known) positionMs.toFloat() / durationMs else 0f
        // Nothing to say about a track whose length is not known (a live stream, or no track):
        // both times go and the bar takes the row.
        seekTime?.apply {
            text = if (known) formatTime(positionMs) else ""
            visibility = if (known) View.VISIBLE else View.GONE
        }
        seekDuration?.apply {
            text = if (known) formatTime(durationMs) else ""
            visibility = if (known) View.VISIBLE else View.GONE
        }
        if (changed) styleSeekChips()
    }

    fun onSpeed(newSpeed: Float) {
        speed = newSpeed
        speedChip?.let(::styleToolChip)
    }

    /** [endsAtRealtimeMs] is when the phone's sleep timer ends on this device's clock; 0 for none. */
    fun onSleepTimer(endsAtRealtimeMs: Long) {
        sleepEndsAtRealtimeMs = endsAtRealtimeMs
        sleepChip?.let(::styleToolChip)
    }

    private fun sleepMinutesLeft(): Int = if (sleepEndsAtRealtimeMs == 0L) 0 else {
        SleepTimerPolicy.minutesLeft(sleepEndsAtRealtimeMs - SystemClock.elapsedRealtime())
    }

    /**
     * Keeps the sleep chip's minutes honest while the panel is open. The phone says how long is
     * left once; counting down is this side's job, and a label that only changed when a message
     * arrived would say "30 min" for the whole half hour.
     */
    private val sleepTick = object : Runnable {
        override fun run() {
            val chip = sleepChip ?: return
            if (!chip.isAttachedToWindow) return
            styleToolChip(chip)
            handler.postDelayed(this, SLEEP_TICK_MS)
        }
    }

    // --- Surfaces ---

    /**
     * Makes [root] the panel's row surface. Clickable without a listener on purpose: see the class
     * comment - the scroll host reads "nothing clickable under the finger" as "dismiss".
     */
    private fun surface(root: View) {
        root.background = skin.rowBackground()
        root.isClickable = true
        root.isSoundEffectsEnabled = false
    }

    /** A soft disc or rounded square that lights under a finger, in the colour the glyph is drawn in. */
    private fun pressHighlight(circle: Boolean): Drawable {
        val mask = GradientDrawable().apply {
            shape = if (circle) GradientDrawable.OVAL else GradientDrawable.RECTANGLE
            cornerRadius = dp(CHIP_CORNER_DP).toFloat()
            setColor(Color.WHITE)
        }
        return RippleDrawable(
                ColorStateList.valueOf(ColorUtils.setAlphaComponent(skin.tint(), PRESS_HIGHLIGHT_ALPHA)),
                null,
                mask)
    }

    // --- Volume ---

    fun bindVolume(block: QuickPanelBlock): View {
        val root = volumeRoot ?: LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(QuickPanelGeometry.VOLUME_HEIGHT_DP))
                    .apply { topMargin = dp(QuickPanelGeometry.GAP_DP) }
            setPaddingRelative(
                    dp(QuickPanelGeometry.VOLUME_PADDING_H_DP), 0,
                    dp(QuickPanelGeometry.VOLUME_PADDING_H_DP), 0)
        }.also { volumeRoot = it }
        root.removeAllViews()

        val step = block.volumeStep
        val down = stepperButton(
                com.svartifoss.snfell.common.R.drawable.action_volume_down,
                R.string.action_name_volume_down, -step)
        val up = stepperButton(
                com.svartifoss.snfell.common.R.drawable.action_volume_up,
                R.string.action_name_volume_up, step)

        // Just the bar between the two buttons. It used to carry the percentage over it, which
        // took half the height and put a number where the eye wants a length; the bar says the
        // level well enough, and the number is still there for a screen reader.
        val bar = LevelBarView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                    0, dp(QuickPanelGeometry.VOLUME_BAR_DP), 1f).apply {
                marginStart = dp(6f)
                marginEnd = dp(6f)
            }
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }

        root.addView(down)
        root.addView(bar)
        root.addView(up)
        volumeBar = bar
        styleVolume(root)
        onVolume(host.volume())
        return root
    }

    private fun styleVolume(root: LinearLayout) {
        surface(root)
        for (child in listOf(root.getChildAt(0), root.getChildAt(2))) {
            (child as? ImageView)?.apply {
                setColorFilter(skin.tint())
                foreground = pressHighlight(circle = true)
            }
        }
        volumeBar?.apply {
            fillColor = skin.tint()
            trackColor = ColorUtils.setAlphaComponent(skin.tint(), TRACK_ALPHA)
        }
    }

    /**
     * A − or + button that steps once on press and then keeps stepping while it is held, so
     * crossing most of the range does not take a dozen taps.
     *
     * Marked clickable even though it handles touch itself, for the reason in the class comment.
     * It drives its own pressed state because the listener below consumes every touch, which
     * otherwise leaves the highlight with nothing to follow.
     */
    private fun stepperButton(iconRes: Int, descriptionRes: Int, delta: Float): ImageView {
        val size = dp(QuickPanelGeometry.STEPPER_DP)
        val glyphInset = (size - dp(QuickPanelGeometry.STEPPER_GLYPH_DP)) / 2
        val button = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(size, size)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(glyphInset, glyphInset, glyphInset, glyphInset)
            setImageResource(iconRes)
            contentDescription = context.getString(descriptionRes)
            isClickable = true
            isFocusable = true
        }
        val repeat = object : Runnable {
            override fun run() {
                stepVolume(delta)
                handler.postDelayed(this, REPEAT_INTERVAL_MS)
            }
        }
        // Reached only through an accessibility service: the touch listener below consumes every
        // real touch, so it never forwards one to the click listener.
        button.setOnClickListener { stepVolume(delta) }
        button.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.isPressed = true
                    stepVolume(delta)
                    handler.postDelayed(repeat, REPEAT_DELAY_MS)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.isPressed = false
                    handler.removeCallbacks(repeat)
                }
            }
            true
        }
        return button
    }

    private fun stepVolume(delta: Float) {
        val next = (host.volume() + delta).coerceIn(0f, 1f)
        host.setVolume(next)
        onVolume(next)
    }

    // --- Skip and position ---

    fun bindSeek(block: QuickPanelBlock): View {
        val root = seekRoot ?: LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = dp(QuickPanelGeometry.GAP_DP) }
        }.also { seekRoot = it }
        root.removeAllViews()
        seekChips.clear()
        seekTime = null
        seekDuration = null
        seekBar = null

        val sidePadding = dp(QuickPanelGeometry.PILL_PADDING_H_DP)
        // The readout - the time played, the bar and the length of the track, in one row - is
        // QuickPanelGeometry.SEEK_READOUT_DP from the top of the pill to the top of the chips.
        // Without it the chips sit under the plain padding.
        root.setPaddingRelative(
                sidePadding,
                dp(if (block.seekShowsBar) QuickPanelGeometry.SEEK_READOUT_TOP_DP
                else QuickPanelGeometry.SEEK_PADDING_TOP_DP),
                sidePadding,
                dp(QuickPanelGeometry.SEEK_PADDING_BOTTOM_DP))

        if (block.seekShowsBar) {
            // 1:23 ━━━━━━━━━ 3:33: a time at each end of the bar, where the eye expects to find
            // "how far" and "how long", rather than both over the bar as one line of text.
            val readout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(QuickPanelGeometry.SEEK_READOUT_ROW_DP)).apply {
                    marginStart = dp(QuickPanelGeometry.SEEK_READOUT_INSET_DP)
                    marginEnd = dp(QuickPanelGeometry.SEEK_READOUT_INSET_DP)
                    bottomMargin = dp(QuickPanelGeometry.SEEK_READOUT_GAP_DP)
                }
            }
            val elapsed = readoutTime()
            val bar = LevelBarView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                        0, dp(QuickPanelGeometry.SEEK_BAR_DP), 1f).apply {
                    marginStart = dp(QuickPanelGeometry.SEEK_BAR_MARGIN_DP)
                    marginEnd = dp(QuickPanelGeometry.SEEK_BAR_MARGIN_DP)
                }
            }
            val total = readoutTime()
            readout.addView(elapsed)
            readout.addView(bar)
            readout.addView(total)
            root.addView(readout)
            seekTime = elapsed
            seekDuration = total
            seekBar = bar
        }

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(QuickPanelGeometry.SEEK_CHIP_DP))
        }
        val steps = block.seekSteps
        // Largest first on the left, so the row reads as a number line: back 30, back 10 | 10, 30.
        val offsets = steps.reversed().map { -it } + steps
        // A fixed share of the row - a quarter, the most chips it ever holds - so a single jump
        // size is two chips of normal width in the middle, not two halves of the screen.
        val chipWidth = chipRowWidthPx() / QuickPanelGeometry.SEEK_CHIPS_ACROSS
        val chipHeight = dp(QuickPanelGeometry.SEEK_CHIP_DP)
        val glyph = dp(QuickPanelGeometry.SEEK_GLYPH_DP)
        offsets.forEach { seconds ->
            val chip = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(chipWidth, chipHeight)
                scaleType = ImageView.ScaleType.FIT_CENTER
                // The glyph is a square drawn in the middle of a chip that is wider than it is tall.
                setPadding((chipWidth - glyph) / 2, (chipHeight - glyph) / 2,
                        (chipWidth - glyph) / 2, (chipHeight - glyph) / 2)
                setImageResource(
                        SeekGlyphs.forOffset(seconds)
                                ?: if (seconds < 0) com.svartifoss.snfell.common.R.drawable.action_rewind
                                else com.svartifoss.snfell.common.R.drawable.action_fast_forward)
                contentDescription = if (seconds < 0) {
                    context.getString(R.string.quick_seek_back_description, -seconds)
                } else {
                    context.getString(R.string.quick_seek_forward_description, seconds)
                }
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    if (!seekable) return@setOnClickListener
                    host.buzz()
                    host.skipBy(seconds * 1000L)
                }
                setOnTouchListener(pressFeedback)
            }
            seekChips += chip
            row.addView(chip)
        }
        root.addView(row)
        styleSeek(root)
        onPosition(positionMs, durationMs, seekable)
        return root
    }

    /** One of the two times beside the bar. */
    private fun readoutTime(): TextView = TextView(context).apply {
        textSize = 11f
        includeFontPadding = false
        gravity = Gravity.CENTER
        setTypeface(skin.typeface() ?: Typeface.DEFAULT, Typeface.BOLD)
        // Digits of one width, so the bar between the two times does not creep as the seconds
        // tick over (a 1 is narrower than a 0 in most typefaces).
        fontFeatureSettings = "tnum"
        layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun styleSeek(root: LinearLayout) {
        surface(root)
        seekTime?.setTextColor(skin.tint())
        seekDuration?.setTextColor(skin.tint())
        seekBar?.apply {
            fillColor = skin.tint()
            trackColor = ColorUtils.setAlphaComponent(skin.tint(), TRACK_ALPHA)
        }
        styleSeekChips()
    }

    private fun styleSeekChips() {
        seekChips.forEach { chip ->
            chip.setColorFilter(skin.tint())
            chip.foreground = pressHighlight(circle = false)
            // Not seekable is a state to show, not to hide: the chips stay where they are so the
            // panel does not rearrange itself under a finger when a player toggles the capability.
            chip.alpha = if (seekable) 1f else DISABLED_ALPHA
        }
    }

    // --- Tools ---

    fun bindTools(block: QuickPanelBlock): View {
        val root = toolsRoot ?: LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = dp(QuickPanelGeometry.GAP_DP) }
        }.also { toolsRoot = it }
        root.removeAllViews()
        speedChip = null
        sleepChip = null
        handler.removeCallbacks(sleepTick)

        root.setPaddingRelative(
                dp(QuickPanelGeometry.PILL_PADDING_H_DP), dp(QuickPanelGeometry.TOOLS_PADDING_V_DP),
                dp(QuickPanelGeometry.PILL_PADDING_H_DP), dp(QuickPanelGeometry.TOOLS_PADDING_V_DP))

        // Every chip the same width, sized for the fullest row - at least a third of the pill so
        // two or three tools are comfortably large - and short rows centred under it.
        val chipWidth = chipRowWidthPx() / QuickPanelGeometry.toolColumns(block.tools.size)
        block.tools.chunked(QuickPanelGeometry.TOOLS_PER_ROW).forEach { tools ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(QuickPanelGeometry.TOOL_CHIP_DP))
            }
            tools.forEach { tool -> row.addView(toolChip(tool, chipWidth)) }
            root.addView(row)
        }
        styleTools(root)
        if (sleepChip != null) handler.postDelayed(sleepTick, SLEEP_TICK_MS)
        return root
    }

    private fun toolChip(tool: QuickPanelTool, widthPx: Int): LinearLayout {
        val chip = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                    widthPx, ViewGroup.LayoutParams.MATCH_PARENT)
                    .apply {
                        topMargin = dp(2f)
                        bottomMargin = dp(2f)
                    }
            tag = tool
            isClickable = true
            isFocusable = true
            setOnClickListener {
                host.buzz()
                when (tool) {
                    QuickPanelTool.SPEED -> host.cycleSpeed()
                    QuickPanelTool.SLEEP -> host.cycleSleepTimer()
                    else -> host.openTool(tool)
                }
            }
            setOnTouchListener(pressFeedback)
        }
        val icon = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                    dp(QuickPanelGeometry.TOOL_GLYPH_DP), dp(QuickPanelGeometry.TOOL_GLYPH_DP))
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageResource(toolIcon(tool))
        }
        val label = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = dp(2f) }
            textSize = 10f
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            includeFontPadding = false
            setTypeface(skin.typeface() ?: Typeface.DEFAULT, Typeface.BOLD)
            setPadding(dp(2f), 0, dp(2f), 0)
        }
        chip.addView(icon)
        chip.addView(label)
        if (tool == QuickPanelTool.SPEED) speedChip = chip
        if (tool == QuickPanelTool.SLEEP) sleepChip = chip
        return chip
    }

    private fun styleTools(root: LinearLayout) {
        surface(root)
        for (r in 0 until root.childCount) {
            val row = root.getChildAt(r) as? LinearLayout ?: continue
            for (c in 0 until row.childCount) {
                val chip = row.getChildAt(c) as? LinearLayout ?: continue
                styleToolChip(chip)
            }
        }
    }

    /**
     * A tool sits flat on the block's pill. The one that is switched on (a playback speed other
     * than normal) is the exception: it gets the panel's *active* round surface, so the pill still
     * says at a glance that something is changed.
     */
    private fun styleToolChip(chip: LinearLayout) {
        val tool = chip.tag as? QuickPanelTool ?: return
        val active = when (tool) {
            QuickPanelTool.SPEED -> abs(speed - 1f) > SPEED_EPSILON
            QuickPanelTool.SLEEP -> sleepMinutesLeft() > 0
            else -> false
        }
        val color = if (active) skin.activeTint() else skin.tint()
        chip.background = if (active) skin.activeRoundBackground() else null
        chip.foreground = if (active) null else pressHighlight(circle = false)
        (chip.getChildAt(0) as? ImageView)?.setColorFilter(color)
        (chip.getChildAt(1) as? TextView)?.apply {
            setTextColor(color)
            text = toolLabel(tool)
        }
        chip.contentDescription = when (tool) {
            QuickPanelTool.SPEED ->
                context.getString(R.string.quick_speed_description, formatSpeed(speed))
            QuickPanelTool.SLEEP -> sleepMinutesLeft().let { left ->
                if (left > 0) context.getString(R.string.quick_sleep_description, left)
                else context.getString(R.string.quick_sleep_off_description)
            }
            else -> toolLabel(tool)
        }
    }

    private fun toolLabel(tool: QuickPanelTool): String = when (tool) {
        QuickPanelTool.SPEED -> formatSpeed(speed)
        QuickPanelTool.SLEEP -> sleepMinutesLeft().let { left ->
            if (left > 0) context.getString(R.string.quick_sleep_minutes, left)
            else context.getString(R.string.quick_tool_sleep)
        }
        QuickPanelTool.LYRICS -> context.getString(R.string.quick_tool_lyrics)
        QuickPanelTool.QUEUE -> context.getString(R.string.quick_tool_queue)
        QuickPanelTool.VOLUME -> context.getString(R.string.quick_tool_volume)
        QuickPanelTool.PROGRESS -> context.getString(R.string.quick_tool_progress)
        QuickPanelTool.FACES -> context.getString(R.string.quick_tool_faces)
        QuickPanelTool.SEARCH -> context.getString(R.string.quick_tool_search)
        QuickPanelTool.MENU -> context.getString(R.string.quick_tool_menu)
    }

    private fun toolIcon(tool: QuickPanelTool): Int = when (tool) {
        QuickPanelTool.SPEED -> com.svartifoss.snfell.common.R.drawable.action_speed
        QuickPanelTool.SLEEP -> com.svartifoss.snfell.common.R.drawable.action_sleep_timer
        QuickPanelTool.LYRICS -> com.svartifoss.snfell.common.R.drawable.action_lyrics
        QuickPanelTool.QUEUE -> R.drawable.ic_queue_music
        QuickPanelTool.VOLUME -> com.svartifoss.snfell.common.R.drawable.action_volume_up
        QuickPanelTool.PROGRESS -> com.svartifoss.snfell.common.R.drawable.action_progress
        QuickPanelTool.FACES -> com.svartifoss.snfell.common.R.drawable.action_face_picker
        QuickPanelTool.SEARCH -> com.svartifoss.snfell.common.R.drawable.action_search
        QuickPanelTool.MENU -> com.svartifoss.snfell.common.R.drawable.action_open_menu
    }

    // --- Favourites ---

    /**
     * The container the favourites block lives in. As a grid it is one pill and this class fills it
     * with tiles; as rows it is bare and the host fills it with the same row views the full actions
     * list uses - each of those is a pill already - so the two can never drift apart in how a
     * shortcut row looks.
     */
    fun favoritesContainer(asGrid: Boolean): LinearLayout {
        val root = favoritesRoot ?: LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }.also { favoritesRoot = it }
        val params = root.layoutParams as LinearLayout.LayoutParams
        if (asGrid) {
            // The grid is the pill; the rows each bring their own 6dp of space above them.
            params.topMargin = dp(QuickPanelGeometry.GAP_DP)
            root.setPaddingRelative(
                    dp(QuickPanelGeometry.PILL_PADDING_H_DP), dp(QuickPanelGeometry.FAVORITES_PADDING_V_DP),
                    dp(QuickPanelGeometry.PILL_PADDING_H_DP), dp(QuickPanelGeometry.FAVORITES_PADDING_V_DP))
            surface(root)
        } else {
            params.topMargin = 0
            root.setPadding(0, 0, 0, 0)
            root.background = null
            root.isClickable = false
        }
        root.layoutParams = params
        return root
    }

    /** Fills the favourites container with a grid of tiles, three to a line. */
    fun bindFavoritesGrid(entries: List<QuickPanelFavorite>): View {
        val root = favoritesContainer(asGrid = true)
        root.removeAllViews()
        entries.chunked(QuickPanelGeometry.FAVORITES_COLUMNS).forEachIndexed { index, line ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    if (index > 0) topMargin = dp(QuickPanelGeometry.FAVORITES_ROW_GAP_DP)
                }
            }
            // Every tile has the same width, and a short last line is centred, so a lone favourite
            // is not blown up to the width of the pill.
            line.forEach { row.addView(favoriteTile(it)) }
            root.addView(row)
        }
        return root
    }

    private fun favoriteTile(entry: QuickPanelFavorite): View {
        val action = entry.action
        val size = dp(QuickPanelGeometry.FAVORITES_ART_DP)
        val tile = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                    chipRowWidthPx() / QuickPanelGeometry.FAVORITES_COLUMNS,
                    ViewGroup.LayoutParams.WRAP_CONTENT)
            isClickable = true
            isFocusable = true
            contentDescription = action.title ?: ""
            setOnClickListener {
                host.buzz()
                host.runMenuAction(entry.menuIndex)
            }
            setOnTouchListener(pressFeedback)
        }
        // The disc a glyph or an app icon sits on. Drawn from the pill's own ink rather than taken
        // from the round-button surface, which in most styles is the very colour the pill already
        // is - a tile that is there but cannot be seen.
        val disc = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(ColorUtils.setAlphaComponent(skin.tint(), DISC_ALPHA))
        }
        val art = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(size, size)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val icon = action.icon
        when {
            icon != null && action.isCoverArt -> {
                art.scaleType = ImageView.ScaleType.CENTER_CROP
                art.setImageDrawable(icon)
                art.outlineProvider = CircleOutline
                art.clipToOutline = true
            }
            icon != null && !action.iconTintable -> {
                // A full-colour app icon: kept in its own colours, on the disc.
                art.scaleType = ImageView.ScaleType.FIT_CENTER
                art.setPadding(dp(11f), dp(11f), dp(11f), dp(11f))
                art.background = disc
                art.setImageDrawable(icon)
            }
            else -> {
                art.scaleType = ImageView.ScaleType.FIT_CENTER
                art.setPadding(dp(14f), dp(14f), dp(14f), dp(14f))
                art.background = disc
                if (icon != null) {
                    art.setImageDrawable(icon)
                } else {
                    art.setImageResource(com.svartifoss.snfell.common.R.drawable.action_custom)
                }
                art.setColorFilter(skin.tint())
            }
        }
        val label = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = dp(QuickPanelGeometry.FAVORITES_LABEL_GAP_DP) }
            text = action.title.orEmpty()
            textSize = 10f
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            includeFontPadding = false
            setTypeface(skin.typeface() ?: Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(skin.tint())
            setPadding(dp(2f), 0, dp(2f), 0)
        }
        tile.addView(art)
        tile.addView(label)
        return tile
    }

    // --- The actions block as a button ---

    /** The pill that opens the full actions menu - the actions block in its button mode. */
    fun bindActionsButton(): View {
        val root = actionsButtonRoot ?: LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = dp(QuickPanelGeometry.GAP_DP) }
            setPadding(dp(18f), 0, dp(18f), 0)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                host.buzz()
                host.openTool(QuickPanelTool.MENU)
            }
            setOnTouchListener(pressFeedback)
        }.also { actionsButtonRoot = it }
        root.removeAllViews()
        root.contentDescription = context.getString(R.string.quick_block_all_actions)

        val icon = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(24f), dp(24f)).apply { marginEnd = dp(12f) }
            setImageResource(com.svartifoss.snfell.common.R.drawable.action_open_menu)
        }
        val label = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            text = context.getString(R.string.quick_block_all_actions)
            textSize = 15f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            includeFontPadding = false
            setTypeface(skin.typeface() ?: Typeface.DEFAULT, Typeface.BOLD)
        }
        root.addView(icon)
        root.addView(label)
        styleActionsButton(root)
        return root
    }

    private fun styleActionsButton(root: LinearLayout) {
        root.minimumHeight = skin.rowHeightPx()
        root.background = skin.rowBackground()
        (root.getChildAt(0) as? ImageView)?.setColorFilter(skin.tint())
        (root.getChildAt(1) as? TextView)?.setTextColor(skin.tint())
    }

    // --- Shared ---

    /** A light press response for views that are not ImageViews with their own state drawable. */
    private val pressFeedback = View.OnTouchListener { view, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                view.animate().cancel()
                view.animate().scaleX(PRESSED_SCALE).scaleY(PRESSED_SCALE).setDuration(70L).start()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                view.animate().cancel()
                view.animate().scaleX(1f).scaleY(1f).setDuration(110L).start()
            }
        }
        // Never consume: the click listener and the scroll host both still need the stream.
        false
    }

    private fun formatTime(timeMs: Long): String {
        val totalSeconds = (timeMs / 1000L).coerceAtLeast(0L)
        return String.format(Locale.getDefault(), "%d:%02d", totalSeconds / 60, totalSeconds % 60)
    }

    private companion object {
        const val CHIP_CORNER_DP = 18f
        const val REPEAT_DELAY_MS = 380L
        const val REPEAT_INTERVAL_MS = 110L
        const val PRESSED_SCALE = 0.95f
        const val DISABLED_ALPHA = 0.4f
        const val SPEED_EPSILON = 0.01f

        /** How often the sleep chip re-reads the minutes left: often enough that the label never
         *  lags a minute boundary by more than this, rarely enough to cost nothing. */
        const val SLEEP_TICK_MS = 10_000L

        /** How much of the ink a bar's empty part, a pressed chip and a tile's disc are drawn in. */
        const val TRACK_ALPHA = 0x40
        const val PRESS_HIGHLIGHT_ALPHA = 0x38
        const val DISC_ALPHA = 0x26
    }
}

/** "1×", "1.25×", "0.75×": no trailing zeros, and locale-independent digits for the glyph's width. */
internal fun formatSpeed(speed: Float): String {
    val text = String.format(Locale.US, "%.2f", speed).trimEnd('0').trimEnd('.')
    return "$text×"
}

/** A pill-shaped level indicator: a track, and a rounded fill that is never narrower than a dot. */
internal class LevelBarView(context: Context) : View(context) {
    var fraction: Float = 0f
        set(value) {
            val clamped = value.coerceIn(0f, 1f)
            if (clamped != field) {
                field = clamped
                invalidate()
            }
        }
    var trackColor: Int = Color.WHITE
        set(value) { field = value; invalidate() }
    var fillColor: Int = Color.WHITE
        set(value) { field = value; invalidate() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val radius = h / 2f
        paint.color = trackColor
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, radius, radius, paint)
        if (fraction > 0f) {
            paint.color = fillColor
            rect.set(0f, 0f, min(w, max(h, w * fraction)), h)
            canvas.drawRoundRect(rect, radius, radius, paint)
        }
    }
}

/** Clips a view to the circle inscribed in its bounds. */
private object CircleOutline : android.view.ViewOutlineProvider() {
    override fun getOutline(view: View, outline: android.graphics.Outline) {
        outline.setOval(0, 0, view.width, view.height)
    }
}
