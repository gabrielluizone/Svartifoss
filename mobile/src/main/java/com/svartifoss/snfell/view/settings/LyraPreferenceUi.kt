package com.svartifoss.snfell.view.settings

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.CheckedTextView
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceFragmentCompat
import com.svartifoss.snfell.R
import com.svartifoss.snfell.view.LyraAccent
import com.svartifoss.snfell.view.mainactivity.MainActivity
import com.matejdro.wearutils.preferences.compat.PreferenceFragmentCompatEx

/**
 * Lyra-styled preference UI helpers shared by the settings screens ([MiscSettingsFragment],
 * the watch face customization screen) - the HSV color picker dialog and the runtime-accent
 * tinting of preference dialogs.
 */

/** The accent currently on screen (dynamic album-art color included) - LyraAccent.resolve
 *  reads a persisted snapshot that can lag behind what's actually displayed. */
internal fun Fragment.lyraRuntimeAccent(): Int =
        (activity as? MainActivity)?.currentAccentColor() ?: LyraAccent.resolve(requireContext())

internal fun parseHexOrDefault(hex: String?): Int = if (hex != null) {
    try { Color.parseColor(hex) } catch (ignored: Exception) { 0xFF86A69D.toInt() }
} else 0xFF86A69D.toInt()

/** Shared HSV color picker dialog (accent color, mini buttons color, ...): live preview
 *  swatch on top, Reset/Cancel/Apply buttons tinted with the runtime accent. */
internal fun Fragment.showLyraColorPickerDialog(
        initialColor: Int,
        onReset: () -> Unit,
        onApply: (String) -> Unit
) {
    val ctx = requireContext()
    val dp = ctx.resources.displayMetrics.density

    val previewSwatch = View(ctx).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp * 8f
            setColor(initialColor)
        }
        layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (dp * 44f).toInt()
        ).also { it.bottomMargin = (dp * 12f).toInt() }
    }

    val picker = HSVColorPickerView(ctx).apply {
        setColor(initialColor)
        layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (dp * 260f).toInt()
        )
    }

    var selectedColor = initialColor
    picker.onColorChanged = { color ->
        selectedColor = color
        (previewSwatch.background as? GradientDrawable)?.setColor(color)
    }

    val root = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        val pad = (dp * 20f).toInt()
        setPadding(pad, (dp * 12f).toInt(), pad, (dp * 8f).toInt())
        addView(previewSwatch)
        addView(picker)
    }

    val dialog = AlertDialog.Builder(ctx)
            .setTitle(R.string.color_picker_title)
            .setView(root)
            .setNeutralButton(R.string.color_picker_reset) { _, _ -> onReset() }
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.color_picker_apply) { _, _ ->
                onApply(String.format("#%06X", 0xFFFFFF and selectedColor))
            }
            .show()

    // Same runtime-accent treatment the preference dialogs get in
    // tintOpenLyraPreferenceDialog - this dialog is built directly, so tint it here.
    val accent = lyraRuntimeAccent()
    val secondary = ContextCompat.getColor(ctx, R.color.lyra_text_secondary)
    dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(accent)
    dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(secondary)
    dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(secondary)
}

/**
 * Re-tints the currently opening preference dialog (Theme, Album art style, Screen theme, ...)
 * with the accent currently on screen - dialogs inflate with the static theme colors. Call from
 * [PreferenceFragmentCompat.onDisplayPreferenceDialog] via `view?.post { ... }`.
 */
internal fun PreferenceFragmentCompat.tintOpenLyraPreferenceDialog(attempt: Int = 0) {
    // The dialog fragment show() is an async commit - force it through so the dialog
    // exists on the very first attempt instead of racing it.
    if (attempt == 0) {
        try {
            parentFragmentManager.executePendingTransactions()
        } catch (ignored: IllegalStateException) {
        }
    }

    // Standard preferences use androidx's tag, PreferenceFragmentCompatEx's custom
    // dialogs use its own legacy tag - check both.
    val dialogFragment = (parentFragmentManager.findFragmentByTag(
            "androidx.preference.PreferenceFragment.DIALOG")
            ?: parentFragmentManager.findFragmentByTag(PreferenceFragmentCompatEx.DIALOG_FRAGMENT_TAG))
            as? DialogFragment
    val dialog = dialogFragment?.dialog as? AlertDialog

    // Even forced, the dialog can lag a frame behind (its window shows after onStart) -
    // retry over the next frames rather than silently leaving the whole dialog, radio
    // marks included, in the static theme color.
    if (dialog == null || !dialog.isShowing) {
        if (attempt < 20) {
            view?.postDelayed({ tintOpenLyraPreferenceDialog(attempt + 1) }, 50L)
        }
        return
    }

    val accent = lyraRuntimeAccent()
    val secondary = ContextCompat.getColor(requireContext(), R.color.lyra_text_secondary)

    dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(accent)
    dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(secondary)
    dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(secondary)

    // Single/multi-choice rows are CheckedTextViews whose radio/check mark comes tinted
    // with the static colorControlActivated; re-tint them, including rows (re)bound
    // later while scrolling.
    val listView = dialog.listView ?: return
    val markTint = android.content.res.ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(accent, secondary)
    )
    fun tintRow(row: View) {
        val checkedText = row as? CheckedTextView ?: return
        checkedText.checkMarkTintList = markTint
        // Some AppCompat versions render the radio as a compound drawable instead of the
        // check mark - tint both so the accent applies regardless.
        TextViewCompat.setCompoundDrawableTintList(checkedText, markTint)
    }
    for (i in 0 until listView.childCount) {
        tintRow(listView.getChildAt(i))
    }
    listView.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
        override fun onChildViewAdded(parent: View, child: View) = tintRow(child)

        override fun onChildViewRemoved(parent: View, child: View) = Unit
    })
}
