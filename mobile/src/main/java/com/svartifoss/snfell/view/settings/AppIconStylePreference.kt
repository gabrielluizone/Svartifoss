package com.svartifoss.snfell.view.settings

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.preference.ListPreference
import androidx.preference.PreferenceDialogFragmentCompat
import com.matejdro.wearutils.preferences.compat.PreferenceWithDialog
import com.svartifoss.snfell.LauncherIconController
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.AppIconStyle
import com.svartifoss.snfell.view.LyraAccent

/**
 * The Play build's "Launcher icon" picker: a [ListPreference] whose dialog shows each choice as the
 * two icons it produces - the phone's and the watch's - beside its name, instead of the name alone.
 *
 * The choice is one value synced to both devices, and what it means differs by device (the cream
 * style is a charcoal icon with a cream mark on the phone and the reverse on the watch), so a
 * single picture would only half answer "what will I get". The pictures come from [AppIconPreviews],
 * which has a real implementation in the Play build and an inert one in the GitHub build; this
 * class has no Play-only reference, so the shared settings XML can keep declaring it for both.
 *
 * Selecting a row commits immediately and closes, like every other picker on this screen. The
 * value goes through [callChangeListener] first and is then persisted; this picker applies the
 * alias on the phone itself, and `WatchPreferenceSyncCoordinator` pushes the value to the watch,
 * which applies its own.
 */
class AppIconStylePreference @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null
) : ListPreference(context, attrs), PreferenceWithDialog {

    /** Commits a chosen value through the change listener, as a stock ListPreference would. */
    fun applyChoice(value: String) {
        if (value == this.value) return
        if (callChangeListener(value)) {
            this.value = value
            // WatchPreferenceSyncCoordinator applies the alias on every change of this key, but it
            // stops itself on a phone with no Wearable API, and whoever picks an icon should see
            // it change either way. Applying twice is harmless: the second call finds every alias
            // already in its state and changes nothing.
            LauncherIconController.apply(context, value)
        }
    }

    override fun createDialog(key: String): PreferenceDialogFragmentCompat =
            AppIconStylePreferenceDialog.create(key)

    class AppIconStylePreferenceDialog : PreferenceDialogFragmentCompat() {
        companion object {
            fun create(key: String) = AppIconStylePreferenceDialog().apply {
                arguments = Bundle(1).apply { putString(ARG_KEY, key) }
            }
        }

        override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
            val pref = preference as AppIconStylePreference
            val entries = pref.entries ?: emptyArray()
            val values = pref.entryValues ?: emptyArray()
            val selectedIndex = pref.findIndexOfValue(pref.value)
            val accent = LyraAccent.contrastSafe(requireContext(), LyraAccent.resolve(requireContext()))
            val onSurface = ContextCompat.getColor(requireContext(), R.color.lyra_on_surface)

            val adapter = object : BaseAdapter() {
                override fun getCount() = entries.size
                override fun getItem(position: Int): Any = entries[position]
                override fun getItemId(position: Int) = position.toLong()

                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val row = convertView ?: LayoutInflater.from(parent.context)
                            .inflate(R.layout.item_icon_style_choice, parent, false)
                    val title = row.findViewById<TextView>(R.id.choice_title)
                    val check = row.findViewById<ImageView>(R.id.choice_check)
                    val phone = row.findViewById<ImageView>(R.id.preview_phone)
                    val watch = row.findViewById<ImageView>(R.id.preview_watch)

                    title.text = entries[position]

                    // Resolved by value, not by position: the entries array is translated and the
                    // values are the protocol identifiers the enum already owns.
                    val style = AppIconStyle.fromPreference(values.getOrNull(position)?.toString())
                    bindPreview(phone, AppIconPreviews.phone(parent.context, style))
                    bindPreview(watch, AppIconPreviews.watch(parent.context, style))

                    val selected = position == selectedIndex
                    check.visibility = if (selected) View.VISIBLE else View.INVISIBLE
                    check.imageTintList = ColorStateList.valueOf(accent)
                    title.setTextColor(if (selected) accent else onSurface)

                    // The pictures are decorative: the name says everything TalkBack needs.
                    row.contentDescription = entries[position]
                    return row
                }
            }

            builder.setSingleChoiceItems(adapter, selectedIndex) { dialog, which ->
                pref.applyChoice(values[which].toString())
                dialog.dismiss()
            }
            // Selecting a row commits and closes; only Cancel remains, like a stock ListPreference.
            builder.setPositiveButton(null, null)
        }

        /** A build with nothing to preview gets no empty boxes, just the name. */
        private fun bindPreview(view: ImageView, preview: Drawable?) {
            view.setImageDrawable(preview)
            view.visibility = if (preview == null) View.GONE else View.VISIBLE
        }

        override fun onDialogClosed(positiveResult: Boolean) {
            // Rows commit on click via applyChoice; nothing to do when the dialog is dismissed.
        }

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val dialog = super.onCreateDialog(savedInstanceState)
            // setSingleChoiceItems backs onto a plain ListView, which flashes its fading vertical
            // scrollbar on first layout even though the few options always fit without scrolling.
            (dialog as? AlertDialog)?.listView?.isVerticalScrollBarEnabled = false
            return dialog
        }
    }
}
