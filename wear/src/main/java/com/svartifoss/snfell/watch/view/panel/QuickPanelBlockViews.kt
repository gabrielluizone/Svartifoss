package com.svartifoss.snfell.watch.view.panel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
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
import com.svartifoss.snfell.common.QuickPanelTool
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

    /** The album accent, for level bars. */
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
 * row, a seek row, a row of tools, a grid of favourites and a link to the full menu.
 *
 * Each block's root view is created once and kept, so the panel can add it to and remove it from its
 * column freely; `bind*` fills it for the current configuration and may be called any number of
 * times. State that changes while the panel is open (volume, position, speed) arrives through the
 * `on*` methods, which touch only the views that exist.
 *
 * Plain Views rather than Compose, on purpose: they sit in the panel's existing scroll host and
 * inherit its dismiss, rotary-scroll and indicator behaviour, and their surfaces come from the same
 * Drawable factories as every other pill in it.
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

    private var volume = 0f
    private var positionMs = 0L
    private var durationMs = 0L
    private var seekable = false
    private var speed = 1f

    private var volumeRoot: LinearLayout? = null
    private var volumeLabel: TextView? = null
    private var volumeBar: LevelBarView? = null

    private var seekRoot: LinearLayout? = null
    private var seekTime: TextView? = null
    private var seekBar: LevelBarView? = null
    private val seekChips = ArrayList<TextView>()

    private var toolsRoot: LinearLayout? = null
    private var speedChip: LinearLayout? = null

    private var favoritesRoot: LinearLayout? = null
    private var menuLinkRoot: LinearLayout? = null

    // --- State ---

    fun onVolume(level: Float) {
        volume = level.coerceIn(0f, 1f)
        volumeBar?.fraction = volume
        volumeLabel?.text = context.getString(R.string.quick_volume_percent, (volume * 100f).roundToInt())
    }

    fun onPosition(positionMs: Long, durationMs: Long, seekable: Boolean) {
        this.positionMs = positionMs
        this.durationMs = durationMs
        val changed = this.seekable != seekable
        this.seekable = seekable
        seekBar?.fraction = if (durationMs > 0L) positionMs.toFloat() / durationMs else 0f
        seekTime?.text = timeText()
        if (changed) styleSeekChips()
    }

    fun onSpeed(newSpeed: Float) {
        speed = newSpeed
        speedChip?.let(::styleToolChip)
    }

    /** Re-applies colours and surfaces after the style, accent or palette changed under the panel. */
    fun restyle() {
        volumeRoot?.let(::styleVolume)
        styleSeek()
        toolsRoot?.let(::styleTools)
        menuLinkRoot?.let(::styleMenuLink)
    }

    // --- Volume ---

    fun bindVolume(block: QuickPanelBlock): View {
        val root = volumeRoot ?: LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(52f)).apply { topMargin = dp(6f) }
        }.also { volumeRoot = it }
        root.removeAllViews()

        val step = block.volumeStep
        val down = stepperButton(
                com.svartifoss.snfell.common.R.drawable.action_volume_down,
                R.string.action_name_volume_down, -step)
        val up = stepperButton(
                com.svartifoss.snfell.common.R.drawable.action_volume_up,
                R.string.action_name_volume_up, step)

        val middle = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                    .apply {
                        marginStart = dp(10f)
                        marginEnd = dp(10f)
                    }
        }
        val label = TextView(context).apply {
            textSize = 12f
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTypeface(skin.typeface() ?: Typeface.DEFAULT, Typeface.BOLD)
        }
        val bar = LevelBarView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(8f)).apply { topMargin = dp(5f) }
        }
        middle.addView(label)
        middle.addView(bar)

        root.addView(down)
        root.addView(middle)
        root.addView(up)
        volumeLabel = label
        volumeBar = bar
        styleVolume(root)
        onVolume(host.volume())
        return root
    }

    private fun styleVolume(root: LinearLayout) {
        for (child in listOf(root.getChildAt(0), root.getChildAt(2))) {
            (child as? ImageView)?.apply {
                background = skin.roundBackground()
                setColorFilter(skin.tint())
            }
        }
        volumeLabel?.setTextColor(skin.tint())
        volumeBar?.apply {
            fillColor = skin.accent()
            trackColor = ColorUtils.setAlphaComponent(skin.tint(), 0x38)
        }
    }

    /**
     * A − or + button that steps once on press and then keeps stepping while it is held, so
     * crossing most of the range does not take a dozen taps.
     *
     * Marked clickable even though it handles touch itself: the scroll host treats a tap that lands
     * on nothing clickable as a tap on the backdrop and closes the panel.
     */
    private fun stepperButton(iconRes: Int, descriptionRes: Int, delta: Float): ImageView {
        val button = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(56f), ViewGroup.LayoutParams.MATCH_PARENT)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(13f), dp(13f), dp(13f), dp(13f))
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
                    view.alpha = PRESSED_ALPHA
                    stepVolume(delta)
                    handler.postDelayed(repeat, REPEAT_DELAY_MS)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.alpha = 1f
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

    // --- Seek ---

    fun bindSeek(block: QuickPanelBlock): View {
        val root = seekRoot ?: LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6f) }
        }.also { seekRoot = it }
        root.removeAllViews()
        seekChips.clear()
        seekTime = null
        seekBar = null

        if (block.seekShowsBar) {
            val time = TextView(context).apply {
                textSize = 12f
                gravity = Gravity.CENTER
                includeFontPadding = false
                setTypeface(skin.typeface() ?: Typeface.DEFAULT, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            val bar = LevelBarView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(6f)).apply {
                    topMargin = dp(5f)
                    bottomMargin = dp(7f)
                    marginStart = dp(6f)
                    marginEnd = dp(6f)
                }
            }
            root.addView(time)
            root.addView(bar)
            seekTime = time
            seekBar = bar
        }

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(48f))
        }
        val steps = block.seekSteps
        // Largest first on the left, so the row reads as a number line: -30 -10 | +10 +30.
        val offsets = steps.reversed().map { -it } + steps
        // A fixed share of the row - a quarter, the most chips it ever holds - so a single jump
        // size is two chips of normal width in the middle, not two halves of the screen.
        val seekChipWidth = contentWidthPx() / MAX_CHIPS_PER_ROW - dp(6f)
        offsets.forEach { seconds ->
            val chip = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                        seekChipWidth, ViewGroup.LayoutParams.MATCH_PARENT)
                        .apply {
                            marginStart = dp(3f)
                            marginEnd = dp(3f)
                        }
                gravity = Gravity.CENTER
                textSize = 14f
                includeFontPadding = false
                setTypeface(skin.typeface() ?: Typeface.DEFAULT, Typeface.BOLD)
                text = if (seconds < 0) "−${-seconds}" else "+$seconds"
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
        styleSeek()
        onPosition(positionMs, durationMs, seekable)
        return root
    }

    private fun styleSeek() {
        seekTime?.setTextColor(ColorUtils.setAlphaComponent(skin.tint(), 0xCC))
        seekBar?.apply {
            fillColor = skin.accent()
            trackColor = ColorUtils.setAlphaComponent(skin.tint(), 0x38)
        }
        styleSeekChips()
    }

    private fun styleSeekChips() {
        seekChips.forEach { chip ->
            chip.background = skin.roundBackground()
            chip.setTextColor(skin.tint())
            // Not seekable is a state to show, not to hide: the chips stay where they are so the
            // panel does not rearrange itself under a finger when a player toggles the capability.
            chip.alpha = if (seekable) 1f else DISABLED_ALPHA
        }
    }

    private fun timeText(): String =
            if (durationMs > 0L) "${formatTime(positionMs)} / ${formatTime(durationMs)}" else ""

    // --- Tools ---

    fun bindTools(block: QuickPanelBlock): View {
        val root = toolsRoot ?: LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(2f) }
        }.also { toolsRoot = it }
        root.removeAllViews()
        speedChip = null

        // Every chip the same width, sized for the fullest row - at least a third of the screen so
        // two or three tools are comfortably large - and short rows centred under it.
        val chipWidth = contentWidthPx() /
                max(min(block.tools.size, TOOLS_PER_ROW), MIN_TOOL_COLUMNS) - dp(6f)
        block.tools.chunked(TOOLS_PER_ROW).forEach { tools ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(56f)).apply { topMargin = dp(6f) }
            }
            tools.forEach { tool -> row.addView(toolChip(tool, chipWidth)) }
            root.addView(row)
        }
        styleTools(root)
        return root
    }

    private fun toolChip(tool: QuickPanelTool, widthPx: Int): LinearLayout {
        val chip = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(widthPx, ViewGroup.LayoutParams.MATCH_PARENT)
                    .apply {
                        marginStart = dp(3f)
                        marginEnd = dp(3f)
                    }
            tag = tool
            isClickable = true
            isFocusable = true
            setOnClickListener {
                host.buzz()
                if (tool == QuickPanelTool.SPEED) host.cycleSpeed() else host.openTool(tool)
            }
            setOnTouchListener(pressFeedback)
        }
        val icon = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(20f), dp(20f))
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
        return chip
    }

    private fun styleTools(root: LinearLayout) {
        for (r in 0 until root.childCount) {
            val row = root.getChildAt(r) as? LinearLayout ?: continue
            for (c in 0 until row.childCount) {
                val chip = row.getChildAt(c) as? LinearLayout ?: continue
                styleToolChip(chip)
            }
        }
    }

    private fun styleToolChip(chip: LinearLayout) {
        val tool = chip.tag as? QuickPanelTool ?: return
        val active = tool == QuickPanelTool.SPEED && abs(speed - 1f) > SPEED_EPSILON
        val color = if (active) skin.activeTint() else skin.tint()
        chip.background = if (active) skin.activeRoundBackground() else skin.roundBackground()
        (chip.getChildAt(0) as? ImageView)?.setColorFilter(color)
        (chip.getChildAt(1) as? TextView)?.apply {
            setTextColor(color)
            text = toolLabel(tool)
        }
        chip.contentDescription = if (tool == QuickPanelTool.SPEED) {
            context.getString(R.string.quick_speed_description, formatSpeed(speed))
        } else {
            toolLabel(tool)
        }
    }

    private fun toolLabel(tool: QuickPanelTool): String = when (tool) {
        QuickPanelTool.SPEED -> formatSpeed(speed)
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
     * The container the favourites block lives in. In grid mode this class fills it with tiles; in
     * rows mode the host fills it with the same row views the full actions list uses, so the two
     * can never drift apart in how a shortcut row looks.
     */
    fun favoritesContainer(): LinearLayout {
        return favoritesRoot ?: LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(2f) }
        }.also { favoritesRoot = it }
    }

    /** Fills the favourites container with a grid of tiles, three to a line. */
    fun bindFavoritesGrid(entries: List<QuickPanelFavorite>): View {
        val root = favoritesContainer()
        root.removeAllViews()
        entries.chunked(GRID_COLUMNS).forEach { line ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6f) }
            }
            // Every tile has the same width, and a short last line is centred, so a lone favourite
            // is not blown up to the width of the screen.
            line.forEach { row.addView(favoriteTile(it)) }
            root.addView(row)
        }
        return root
    }

    private fun favoriteTile(entry: QuickPanelFavorite): View {
        val action = entry.action
        val size = dp(54f)
        val tile = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                    contentWidthPx() / GRID_COLUMNS, ViewGroup.LayoutParams.WRAP_CONTENT)
            isClickable = true
            isFocusable = true
            contentDescription = action.title ?: ""
            setOnClickListener {
                host.buzz()
                host.runMenuAction(entry.menuIndex)
            }
            setOnTouchListener(pressFeedback)
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
                // A full-colour app icon: kept in its own colours, on the round surface.
                art.scaleType = ImageView.ScaleType.FIT_CENTER
                art.setPadding(dp(11f), dp(11f), dp(11f), dp(11f))
                art.background = skin.roundBackground()
                art.setImageDrawable(icon)
            }
            else -> {
                art.scaleType = ImageView.ScaleType.FIT_CENTER
                art.setPadding(dp(14f), dp(14f), dp(14f), dp(14f))
                art.background = skin.roundBackground()
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
                    .apply { topMargin = dp(3f) }
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

    // --- Link to the full menu ---

    fun bindMenuLink(): View {
        val root = menuLinkRoot ?: LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = dp(6f) }
            setPadding(dp(18f), 0, dp(18f), 0)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                host.buzz()
                host.openTool(QuickPanelTool.MENU)
            }
            setOnTouchListener(pressFeedback)
        }.also { menuLinkRoot = it }
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
        styleMenuLink(root)
        return root
    }

    private fun styleMenuLink(root: LinearLayout) {
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
        const val TOOLS_PER_ROW = 4
        const val MAX_CHIPS_PER_ROW = 4
        const val MIN_TOOL_COLUMNS = 3
        const val GRID_COLUMNS = 3
        const val REPEAT_DELAY_MS = 380L
        const val REPEAT_INTERVAL_MS = 110L
        const val PRESSED_ALPHA = 0.7f
        const val PRESSED_SCALE = 0.95f
        const val DISABLED_ALPHA = 0.4f
        const val SPEED_EPSILON = 0.01f
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
