package com.svartifoss.snfell.view.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.InputFilter
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.svartifoss.snfell.R
import com.svartifoss.snfell.view.LyraAccent
import com.svartifoss.snfell.view.applyLyraDialogStyling
import com.svartifoss.snfell.view.mainactivity.MainActivity
import com.matejdro.wearutils.preferences.compat.PreferenceFragmentCompatEx
import com.svartifoss.snfell.view.NEUTRAL_WATCH_ACCENT

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
    try { Color.parseColor(hex) } catch (ignored: Exception) { NEUTRAL_WATCH_ACCENT }
} else NEUTRAL_WATCH_ACCENT

/** A colour as the `#RRGGBB` every preference behind this picker stores. */
private fun hexOf(color: Int): String = String.format("#%06X", 0xFFFFFF and color)

/**
 * Shared HSV color picker dialog (accent color, mini buttons color, ...): live preview swatch and
 * the value as editable text on top, the wheel below it, and the colours most recently applied
 * anywhere in the app under that. Reset/Cancel/Apply are tinted with the runtime accent.
 *
 * The hex field and the recent row are two answers to the same request, and both are needed. The
 * field is what makes a colour leave the app or arrive from one - it can be selected, copied and
 * pasted like any other text, which the wheel alone could not offer at all. The recent row is what
 * makes the ordinary case one tap: carrying the colour you just used on the title over to the
 * artist never involved another app, and going through the clipboard for it would be a chore
 * invented by the picker rather than by the person using it.
 *
 * The whole thing scrolls, because it has grown past the height of a dialog on a short screen or a
 * large font scale. See HSVColorPickerView.onTouchEvent for the half of that which is not free.
 */
internal fun Fragment.showLyraColorPickerDialog(
        initialColor: Int,
        onReset: () -> Unit,
        onApply: (String) -> Unit,
        onPreviewColor: ((String) -> Unit)? = null,
        onPreviewCancelled: (() -> Unit)? = null
) {
    val ctx = requireContext()
    val dp = ctx.resources.displayMetrics.density
    val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
    val secondary = ContextCompat.getColor(ctx, R.color.lyra_text_secondary)
    val onSurface = ContextCompat.getColor(ctx, R.color.lyra_on_surface)

    val previewSwatch = View(ctx).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp * 8f
            setColor(initialColor)
        }
        layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (dp * 44f).toInt()
        ).also { it.bottomMargin = (dp * 8f).toInt() }
    }

    // The hint is the value's own shape rather than a word, so it needs no translation and says
    // more than a label would about what may be typed here.
    val hexField = EditText(ctx).apply {
        setSingleLine()
        hint = "#RRGGBB"
        filters = arrayOf(InputFilter.LengthFilter(9))
        setTextColor(onSurface)
        setHintTextColor(secondary)
        setText(hexOf(initialColor))
        layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
    }

    val borderlessRipple = TypedValue().also {
        ctx.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, it, true)
    }.resourceId

    val copyButton = ImageButton(ctx).apply {
        setImageResource(R.drawable.ic_file_copy)
        setColorFilter(secondary)
        if (borderlessRipple != 0) setBackgroundResource(borderlessRipple)
        // The platform's own string, so it is already translated everywhere this app is.
        contentDescription = ctx.getString(android.R.string.copy)
        val pad = (dp * 8f).toInt()
        setPadding(pad, pad, pad, pad)
        layoutParams = LinearLayout.LayoutParams(
                (dp * 40f).toInt(), (dp * 40f).toInt())
    }

    val hexRow = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(hexField)
        addView(copyButton)
        layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).also { it.bottomMargin = (dp * 4f).toInt() }
    }

    val picker = HSVColorPickerView(ctx).apply {
        setColor(initialColor)
        layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (dp * 260f).toInt()
        )
    }

    var selectedColor = initialColor
    // setText fires the watcher, and a round trip through HSV does not always return the hue it
    // started from - a colour at zero saturation has no hue to preserve. Without this the field
    // updating itself from the wheel would feed a colour back and jump the selector.
    var syncingHexField = false

    fun select(color: Int, updateField: Boolean, updatePicker: Boolean) {
        selectedColor = color
        (previewSwatch.background as? GradientDrawable)?.setColor(color)
        if (updatePicker) picker.setColor(color)
        if (updateField) {
            syncingHexField = true
            val text = hexOf(color)
            if (hexField.text.toString() != text) {
                hexField.setText(text)
                hexField.setSelection(text.length)
            }
            syncingHexField = false
        }
        onPreviewColor?.invoke(hexOf(color))
    }

    picker.onColorChanged = { color -> select(color, updateField = true, updatePicker = false) }

    // Attached after the initial setText, so opening the dialog is not itself an edit.
    hexField.doAfterTextChanged { editable ->
        if (syncingHexField) return@doAfterTextChanged
        // A half-typed value is not an error, it is a value that is not finished. Nothing moves
        // until the field holds a colour, and Apply falls back to the last one that did.
        val normalized = ColorHistory.normalize(editable?.toString()) ?: return@doAfterTextChanged
        val color = Color.parseColor(normalized)
        if ((0xFFFFFF and color) == (0xFFFFFF and selectedColor)) return@doAfterTextChanged
        select(color, updateField = false, updatePicker = true)
    }

    copyButton.setOnClickListener {
        val text = hexOf(selectedColor)
        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
                ?.setPrimaryClip(ClipData.newPlainText(text, text))
        // Android 13 shows its own confirmation for every copy, and adding a second one on top of
        // it is the platform's example of what not to do.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(ctx, R.string.color_picker_copied, Toast.LENGTH_SHORT).show()
        }
    }

    val root = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        val pad = (dp * 20f).toInt()
        setPadding(pad, (dp * 12f).toInt(), pad, (dp * 8f).toInt())
        addView(previewSwatch)
        addView(hexRow)
        addView(picker)
    }

    val recent = ColorHistory.parse(prefs.getString(ColorHistory.PREFERENCE_KEY, null))
    if (recent.isNotEmpty()) {
        root.addView(TextView(ctx).apply {
            setText(R.string.color_picker_recent)
            setTextColor(secondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = (dp * 12f).toInt() }
        })
        val swatches = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        recent.forEach { hex ->
            val color = Color.parseColor(hex)
            swatches.addView(View(ctx).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                    setStroke((dp * 1f).toInt(), secondary)
                }
                // The value itself, which is also the only thing there is to say about it.
                contentDescription = hex
                if (borderlessRipple != 0) foreground =
                        ContextCompat.getDrawable(ctx, borderlessRipple)
                setOnClickListener { select(color, updateField = true, updatePicker = true) }
                layoutParams = LinearLayout.LayoutParams(
                        (dp * 32f).toInt(), (dp * 32f).toInt()
                ).also { it.marginEnd = (dp * 10f).toInt() }
            })
        }
        // The row is capped at a handful of entries, but a narrow dialog at a large font scale
        // still cannot always show all of them.
        root.addView(HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            addView(swatches)
            layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = (dp * 8f).toInt() }
        })
    }

    val dialog = AlertDialog.Builder(ctx)
            .setTitle(R.string.color_picker_title)
            .setView(ScrollView(ctx).apply { addView(root) })
            .setNeutralButton(R.string.color_picker_reset) { _, _ -> onReset() }
            .setNegativeButton(android.R.string.cancel) { _, _ -> onPreviewCancelled?.invoke() }
            .setPositiveButton(R.string.color_picker_apply) { _, _ ->
                val hex = hexOf(selectedColor)
                // Recorded on Apply alone: a colour dragged past on the way somewhere else was
                // never chosen, and Reset is the removal of a choice rather than the making of one.
                prefs.edit()
                        .putString(
                                ColorHistory.PREFERENCE_KEY,
                                ColorHistory.remember(
                                        prefs.getString(ColorHistory.PREFERENCE_KEY, null), hex))
                        .apply()
                onApply(hex)
            }
            .show()

    // Back/outside cancellation does not invoke the negative button listener.
    dialog.setOnCancelListener { onPreviewCancelled?.invoke() }

    // An EditText in a dialog otherwise takes focus on open and brings the keyboard up with it,
    // over the wheel - which is the control almost everybody came here for. Typing a value stays
    // one tap away; it just stops being what opening the picker does.
    dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)

    // Same runtime-accent treatment the preference dialogs get in
    // tintOpenLyraPreferenceDialog - this dialog is built directly, so tint it here.
    val accent = lyraRuntimeAccent()
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

    dialog.applyLyraDialogStyling(accent = lyraRuntimeAccent())
}
