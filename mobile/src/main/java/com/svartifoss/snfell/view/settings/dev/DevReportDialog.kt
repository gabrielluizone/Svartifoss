package com.svartifoss.snfell.view.settings.dev

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import com.svartifoss.snfell.R
import com.svartifoss.snfell.view.applyLyraDialogStyling

/**
 * Shows one Developer-section diagnostic report: a scrollable, selectable monospace dump with a
 * one-tap copy, instead of every inspector inventing its own dialog. All five reports (snapshot,
 * Data Layer, media sessions, phone log, theme preflight) are plain text built from data the app
 * already computes - this is only the shared presentation for it.
 */
internal fun showDevReportDialog(context: Context, title: String, body: String) {
    val textView = TextView(context).apply {
        text = body
        textSize = 12f
        setTextIsSelectable(true)
        typeface = Typeface.MONOSPACE
        setTextColor(ContextCompat.getColor(context, R.color.lyra_on_surface))
    }
    val view = NestedScrollView(context).apply {
        val padding = (16 * resources.displayMetrics.density).toInt()
        setPadding(padding, padding, padding, padding)
        addView(textView)
    }

    val dialog = AlertDialog.Builder(context)
            .setTitle(title)
            .setView(view)
            .setPositiveButton(R.string.easter_egg_ok, null)
            .setNeutralButton(R.string.dev_report_copy) { _, _ ->
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText(title, body))
                Toast.makeText(context, R.string.dev_report_copied, Toast.LENGTH_SHORT).show()
            }
            .show()
    dialog.applyLyraDialogStyling()
}
