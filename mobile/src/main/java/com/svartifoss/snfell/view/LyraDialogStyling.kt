package com.svartifoss.snfell.view

import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
import android.widget.CheckedTextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import com.svartifoss.snfell.R

/**
 * Applies the runtime part of the Lyra AlertDialog contract.
 *
 * The dialog shell itself comes from AppTheme's LyraAlertDialogTheme. Choice indicators and
 * button colours cannot be resolved there because the album-derived accent may change while the
 * app is open, so every AppCompat dialog that must match a preference ListPreference calls this
 * after it is shown.
 */
internal fun AlertDialog.applyLyraDialogStyling(
        accent: Int = LyraAccent.resolve(context),
        positiveColor: Int = accent
) {
    val secondary = ContextCompat.getColor(context, R.color.lyra_text_secondary)
    getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(positiveColor)
    getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(secondary)
    getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(secondary)

    // Stock ListPreference rows are CheckedTextViews. Tint both possible mark renderings so the
    // result is identical across the AppCompat versions used by the preference and activity UIs.
    val choices = listView ?: return
    val markTint = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(accent, secondary))
    fun tintRow(row: View) {
        val checkedText = row as? CheckedTextView ?: return
        checkedText.checkMarkTintList = markTint
        TextViewCompat.setCompoundDrawableTintList(checkedText, markTint)
    }
    for (index in 0 until choices.childCount) {
        tintRow(choices.getChildAt(index))
    }
    choices.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
        override fun onChildViewAdded(parent: View, child: View) = tintRow(child)

        override fun onChildViewRemoved(parent: View, child: View) = Unit
    })
}
