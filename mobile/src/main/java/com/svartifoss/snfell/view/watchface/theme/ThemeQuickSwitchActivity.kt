package com.svartifoss.snfell.view.watchface.theme

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.preference.PreferenceManager
import com.svartifoss.snfell.R
import com.svartifoss.snfell.view.LyraAccent
import com.svartifoss.snfell.view.applyLyraDialogStyling

/**
 * The small "pick a watch theme" window the persistent notification opens.
 *
 * Meant to be reachable from anywhere in two taps - from the shade while another app is in front,
 * or from the lock screen's unlocked shade - without the app's own screens coming forward. So the
 * activity itself is nothing: a transparent window in a task of its own (`taskAffinity=""`, no
 * history, out of Recents; see the manifest), with one dialog on it. Dismissing the dialog finishes
 * the activity and the user is back exactly where they were.
 *
 * Choosing a row applies that theme at once and closes. Applying goes through [QuickThemeSwitch],
 * which uses the same repository calls as the Watch themes screen, so the result is the same; the
 * preference change reaches the watch through `WatchPreferenceSyncCoordinator`, which lives in the
 * application and does not depend on this window staying open. The neutral button is the way on
 * to that full screen for anyone who wants to create, rename or delete themes.
 *
 * Not exported: only the notification's own PendingIntent starts it.
 */
class ThemeQuickSwitchActivity : AppCompatActivity() {

    private var dialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showThemes()
    }

    override fun onDestroy() {
        // A dialog still on screen when the activity goes (a rotation recreates it) would leak its
        // window; the recreated activity shows a fresh one.
        dialog?.setOnDismissListener(null)
        dialog?.dismiss()
        dialog = null
        super.onDestroy()
    }

    private fun showThemes() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        val rows = QuickThemeSwitch.rows(this, preferences)
        val accent = LyraAccent.contrastSafe(this, LyraAccent.resolve(this))
        val adapter = QuickThemeAdapter(this, rows, accent)

        val shown = AlertDialog.Builder(this)
                .setTitle(R.string.watch_themes_title)
                .setSingleChoiceItems(adapter, rows.indexOfFirst { it is QuickThemeRow.Choice && it.active }) { chooser, which ->
                    (rows.getOrNull(which) as? QuickThemeRow.Choice)?.let { choose(preferences, it) }
                    chooser.dismiss()
                }
                .setNeutralButton(R.string.watch_theme_manage) { _, _ -> openThemeManager() }
                .create()
        shown.setOnDismissListener { finish() }
        dialog = shown
        shown.show()
        shown.applyLyraDialogStyling()
    }

    private fun choose(preferences: android.content.SharedPreferences, row: QuickThemeRow.Choice) {
        if (row.active) return
        val name = QuickThemeSwitch.apply(this, preferences, row.key)
        val message = if (name != null) {
            getString(R.string.watch_theme_apply_success, name)
        } else {
            getString(R.string.watch_theme_apply_error)
        }
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
    }

    private fun openThemeManager() {
        startActivity(Intent(this, WatchThemesActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Headings are text; themes are rows with a check on the one in use. */
    private class QuickThemeAdapter(
            context: Context,
            private val rows: List<QuickThemeRow>,
            private val accent: Int
    ) : BaseAdapter() {
        private val inflater = LayoutInflater.from(context)
        private val onSurface = ContextCompat.getColor(context, R.color.lyra_on_surface)
        private val applied = context.getString(R.string.watch_theme_applied)

        override fun getCount() = rows.size
        override fun getItem(position: Int): Any = rows[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getViewTypeCount() = 2
        override fun getItemViewType(position: Int) =
                if (rows[position] is QuickThemeRow.Header) TYPE_HEADER else TYPE_CHOICE

        // A heading cannot be picked, and the list must not draw it as if it could.
        override fun areAllItemsEnabled() = false
        override fun isEnabled(position: Int) = rows[position] is QuickThemeRow.Choice

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                when (val row = rows[position]) {
                    is QuickThemeRow.Header -> bindHeader(row, convertView, parent)
                    is QuickThemeRow.Choice -> bindChoice(row, convertView, parent)
                }

        private fun bindHeader(row: QuickThemeRow.Header, convertView: View?, parent: ViewGroup): View {
            val view = (convertView as? TextView) ?: inflater.inflate(
                    R.layout.item_theme_quick_header, parent, false) as TextView
            view.text = row.title
            view.setTextColor(accent)
            return view
        }

        private fun bindChoice(row: QuickThemeRow.Choice, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: inflater.inflate(R.layout.item_theme_quick_choice, parent, false)
            val title = view.findViewById<TextView>(R.id.choice_title)
            val subtitle = view.findViewById<TextView>(R.id.choice_subtitle)
            val check = view.findViewById<ImageView>(R.id.choice_check)

            title.text = row.title
            title.setTextColor(if (row.active) accent else onSurface)
            subtitle.text = row.subtitle
            subtitle.visibility = if (row.subtitle.isNullOrEmpty()) View.GONE else View.VISIBLE
            check.visibility = if (row.active) View.VISIBLE else View.INVISIBLE
            check.imageTintList = ColorStateList.valueOf(accent)

            view.contentDescription = listOfNotNull(row.title, row.subtitle).joinToString(". ")
            ViewCompat.setStateDescription(view, if (row.active) applied else null)
            return view
        }

        private companion object {
            const val TYPE_HEADER = 0
            const val TYPE_CHOICE = 1
        }
    }
}
