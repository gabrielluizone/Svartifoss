package com.svartifoss.snfell.view.settings

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
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
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.common.TransportGlyphs
import com.svartifoss.snfell.common.TransportMark
import com.svartifoss.snfell.view.LyraAccent

/**
 * The picker for the play mark and for the pause mark (`wear_play_icon`, `wear_pause_icon`): a
 * [ListPreference] whose dialog draws each choice's actual glyph beside its name, so the five
 * drawings are seen rather than described.
 *
 * The same class serves both keys - which mark a row shows is read off the key - because the two
 * pickers differ only in the drawings they list. "Follow face" is the one row that cannot be a
 * fixed picture: it stands for whatever the current face was designed with, so it shows that
 * glyph, and the owning fragment hands the face in through [face] right before the dialog opens
 * (the same hand-off [ColorTreatmentPreference] uses for the live album accent). A stale face
 * only means that one row's picture lags; the value a row commits never depends on it.
 *
 * Selecting a row commits and closes, routed through [callChangeListener] first like every other
 * picker on the Watch tab, so the contextual preview hook still fires before the value persists.
 */
class TransportGlyphPreference @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null
) : ListPreference(context, attrs), PreferenceWithDialog {

    /** The structural face being edited; set by the owning fragment before the dialog opens. */
    var face: String = "material"

    private val mark: TransportMark
        get() = if (key == MiscPreferences.WEAR_PAUSE_ICON.key) TransportMark.PAUSE
        else TransportMark.PLAY

    /** The drawable shown for the row whose value is [choice]. */
    internal fun glyphFor(choice: String): Int =
            TransportGlyphs.drawableFor(mark, choice, face)

    fun applyChoice(value: String) {
        if (value == this.value) return
        if (callChangeListener(value)) {
            this.value = value
        }
    }

    override fun createDialog(key: String): PreferenceDialogFragmentCompat =
            TransportGlyphPreferenceDialog.create(key)

    class TransportGlyphPreferenceDialog : PreferenceDialogFragmentCompat() {
        companion object {
            fun create(key: String) = TransportGlyphPreferenceDialog().apply {
                arguments = Bundle(1).apply { putString(ARG_KEY, key) }
            }
        }

        override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
            val pref = preference as TransportGlyphPreference
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
                            .inflate(R.layout.item_transport_glyph_choice, parent, false)
                    val title = row.findViewById<TextView>(R.id.choice_title)
                    val check = row.findViewById<ImageView>(R.id.choice_check)
                    val glyph = row.findViewById<ImageView>(R.id.choice_glyph)

                    title.text = entries[position]
                    glyph.setImageResource(pref.glyphFor(values[position].toString()))

                    val selected = position == selectedIndex
                    check.visibility = if (selected) View.VISIBLE else View.INVISIBLE
                    check.imageTintList = ColorStateList.valueOf(accent)
                    glyph.imageTintList = ColorStateList.valueOf(if (selected) accent else onSurface)
                    title.setTextColor(if (selected) accent else onSurface)

                    // The drawing is decorative: its name is the whole of what TalkBack needs.
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

        override fun onDialogClosed(positiveResult: Boolean) {
            // Rows commit on click via applyChoice; nothing to do when the dialog is dismissed.
        }

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val dialog = super.onCreateDialog(savedInstanceState)
            // Six short rows always fit; the ListView's scrollbar would only flash on first layout.
            (dialog as? AlertDialog)?.listView?.isVerticalScrollBarEnabled = false
            return dialog
        }
    }
}
