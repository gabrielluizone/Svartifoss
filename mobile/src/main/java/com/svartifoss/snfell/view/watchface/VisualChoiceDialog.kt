package com.svartifoss.snfell.view.watchface

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.doOnLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.svartifoss.snfell.R
import com.svartifoss.snfell.view.applyLyraDialogStyling
import com.svartifoss.snfell.view.watchface.home.WatchLookThumbnailer
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * A style picker drawn as a grid of pictures - see [VisualChoiceCatalog] for which ones.
 *
 * Every cell is the user's own watch with that one value changed, drawn by [WatchLookThumbnailer]
 * as each cell comes into view, so comparing two styles no longer means picking one, closing the
 * list and opening it again. A tap commits and closes, as a list item did. The pictures live as
 * long as the dialog: nothing is cached once it closes.
 *
 * The same `AlertDialog.Builder` + `applyLyraDialogStyling` shell every other picker uses, with the
 * grid as its content.
 *
 * A cell is drawn by giving the preview [key] = `pictureValue(value)` as its candidate, plus the
 * companion values picking it would also write. For a preference the two are the same value; a
 * background layer's style, which is not a preference, is drawn as the whole stack with that one
 * layer's style replaced.
 */
internal object VisualChoiceDialog {

    private const val COLUMNS = 3

    /** One cell's height, for deciding whether the grid has to scroll: picture, gap, two lines. */
    private const val CELL_HEIGHT_DP = 84 + 6 + 30 + 16

    /** The tallest the grid gets before it scrolls, as a share of the screen. */
    private const val MAX_HEIGHT_SHARE = 0.6f

    fun show(
            context: Context,
            title: CharSequence?,
            key: String,
            labels: Array<CharSequence>,
            values: Array<CharSequence>,
            selected: String?,
            pictures: WatchLookThumbnailer,
            companions: (String) -> Map<String, Any?> = { emptyMap() },
            pictureValue: (String) -> String = { it },
            textAccent: Int,
            ringColor: Int,
            onChosen: (String) -> Unit
    ): AlertDialog {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).roundToInt()

        lateinit var dialog: AlertDialog
        val adapter = ChoiceAdapter(
                key = key,
                labels = labels,
                values = values.map(CharSequence::toString),
                selected = selected,
                pictures = pictures,
                companions = companions,
                pictureValue = pictureValue,
                textAccent = textAccent,
                ringColor = ringColor,
                // The dialog is already lyra_surface; the empty watch has to show on it.
                placeholder = ContextCompat.getColor(context, R.color.lyra_divider),
                onChosen = { value ->
                    dialog.dismiss()
                    onChosen(value)
                })

        val rows = ceil(values.size / COLUMNS.toFloat()).toInt()
        val cap = (context.resources.displayMetrics.heightPixels * MAX_HEIGHT_SHARE).roundToInt()
        val grid = RecyclerView(context).apply {
            layoutManager = GridLayoutManager(context, COLUMNS)
            this.adapter = adapter
            clipToPadding = false
            setPadding(dp(12), dp(4), dp(12), dp(4))
            isVerticalScrollBarEnabled = true
            layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    if (rows * dp(CELL_HEIGHT_DP) > cap) cap else ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        // Nothing is drawn while the grid moves: a picture costs more than a frame, so drawing
        // during a fling stuttered. What is on screen when it rests is drawn then.
        grid.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                pictures.hold(newState != RecyclerView.SCROLL_STATE_IDLE)
            }
        })
        // The dialog stretches its custom view to the space it has; a container keeps the grid at
        // the height chosen above, so a long list scrolls instead of measuring every cell.
        val container = FrameLayout(context).apply { addView(grid) }

        dialog = AlertDialog.Builder(context)
                .setTitle(title)
                .setView(container)
                .setNegativeButton(android.R.string.cancel, null)
                .create()
        dialog.setOnShowListener {
            dialog.applyLyraDialogStyling(accent = textAccent)
            // Opens on the current choice, a third of the way down so the ones before it show
            // too. Asked once the grid has its size: a scroll requested before the dialog is
            // shown is lost when the window first lays it out.
            val index = adapter.selectedIndex
            if (index > 0) {
                grid.doOnLayout { laidOut ->
                    (grid.layoutManager as GridLayoutManager)
                            .scrollToPositionWithOffset(index, laidOut.height / 3)
                }
            }
        }
        dialog.setOnDismissListener {
            pictures.cancelChoices()
            // Closed mid-fling: the theme strip shares this renderer and must not stay held.
            pictures.hold(false)
        }
        dialog.show()
        return dialog
    }

    private class ChoiceAdapter(
            private val key: String,
            private val labels: Array<CharSequence>,
            private val values: List<String>,
            private val selected: String?,
            private val pictures: WatchLookThumbnailer,
            private val companions: (String) -> Map<String, Any?>,
            private val pictureValue: (String) -> String,
            private val textAccent: Int,
            private val ringColor: Int,
            private val placeholder: Int,
            private val onChosen: (String) -> Unit
    ) : RecyclerView.Adapter<ChoiceAdapter.Cell>() {

        /** This dialog's pictures, by value; dropped with the dialog. */
        private val drawn = HashMap<String, Bitmap>()

        val selectedIndex: Int = values.indexOf(selected)

        override fun getItemCount() = values.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Cell(
                LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_visual_choice, parent, false))

        override fun onBindViewHolder(holder: Cell, position: Int) = holder.bind(position)

        override fun onViewRecycled(holder: Cell) {
            // A cell scrolled away before its turn: its picture is not needed any more.
            holder.boundValue?.takeIf { it !in drawn }
                    ?.let { pictures.cancelChoice(key, pictureValue(it)) }
        }

        inner class Cell(view: View) : RecyclerView.ViewHolder(view) {
            private val picture: ImageView = view.findViewById(R.id.visual_choice_picture)
            private val ring: View = view.findViewById(R.id.visual_choice_ring)
            private val label: TextView = view.findViewById(R.id.visual_choice_label)
            var boundValue: String? = null
                private set

            fun bind(position: Int) {
                val value = values[position]
                val chosen = value == selected
                boundValue = value
                label.text = labels[position]
                label.setTextColor(if (chosen) textAccent else
                    ContextCompat.getColor(itemView.context, R.color.lyra_on_surface))
                ring.visibility = if (chosen) View.VISIBLE else View.GONE
                if (chosen) {
                    ring.background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setStroke((2 * itemView.resources.displayMetrics.density).roundToInt(), ringColor)
                    }
                }
                val ready = drawn[value]
                if (ready != null) {
                    picture.setImageBitmap(ready)
                } else {
                    picture.setImageDrawable(InsetDrawable(
                            GradientDrawable().apply {
                                shape = GradientDrawable.OVAL
                                setColor(placeholder)
                            },
                            (3 * itemView.resources.displayMetrics.density).roundToInt()))
                    pictures.requestChoice(key, pictureValue(value), companions(value)) { bitmap ->
                        drawn[value] = bitmap
                        val index = values.indexOf(value)
                        if (index >= 0) notifyItemChanged(index)
                    }
                }

                itemView.contentDescription = labels[position]
                itemView.setOnClickListener { onChosen(value) }
                ViewCompat.setAccessibilityDelegate(itemView, object : AccessibilityDelegateCompat() {
                    override fun onInitializeAccessibilityNodeInfo(
                            host: View,
                            info: AccessibilityNodeInfoCompat
                    ) {
                        super.onInitializeAccessibilityNodeInfo(host, info)
                        info.className = RadioButton::class.java.name
                        info.isCheckable = true
                        info.isChecked = chosen
                    }
                })
            }
        }
    }
}
