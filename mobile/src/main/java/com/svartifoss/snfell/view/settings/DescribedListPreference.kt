package com.svartifoss.snfell.view.settings

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
import com.svartifoss.snfell.view.LyraAccent

/**
 * A [ListPreference] whose modal dialog is a plain, single-line radio list (name only, current
 * choice marked with an accent check) - matching every other selector on the Watch tab (a tinted
 * single-choice dialog) instead of the one-off inline card list it replaced. Per-entry
 * [descriptions] still exist, but only surface in the row's own settings-screen summary
 * ("Name · description") and TalkBack's announcement of each dialog row - the dialog itself stays
 * short and scannable instead of forcing each option onto two or three lines.
 *
 * Selecting a row commits immediately, routing through [callChangeListener] before persisting so
 * WatchFacePrefsFragment's candidate-value preview hook still fires - exactly like every other
 * ListPreference on the screen.
 */
class DescribedListPreference @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null
) : ListPreference(context, attrs), PreferenceWithDialog {

    /** Per-entry descriptions, parallel to [getEntries] / [getEntryValues]. May be shorter. */
    val descriptions: List<CharSequence>

    /**
     * Whether each dialog row renders its own label in the typeface its *value* names (see
     * [WatchFontCatalog]). Only meaningful for the font picker, where a list of names set in one
     * uniform UI font tells the user nothing about what they are choosing.
     */
    val previewTypeface: Boolean

    init {
        val typed = context.obtainStyledAttributes(attrs, R.styleable.DescribedListPreference)
        val descRes = typed.getResourceId(R.styleable.DescribedListPreference_descriptions, 0)
        descriptions = if (descRes != 0) context.resources.getTextArray(descRes).toList() else emptyList()
        previewTypeface = typed.getBoolean(
                R.styleable.DescribedListPreference_previewTypeface, false)
        typed.recycle()

        summaryProvider = SummaryProvider<DescribedListPreference> { pref ->
            val index = pref.findIndexOfValue(pref.value)
            val name = pref.entry ?: pref.entries?.firstOrNull() ?: ""
            val description = descriptions.getOrNull(index)
            if (description.isNullOrBlank()) {
                name
            } else {
                context.getString(R.string.described_list_selected_summary, name, description)
            }
        }
    }

    /**
     * Renders the collapsed row's own summary in the selected font too, so the Typography screen
     * shows what is currently in force without opening the dialog at all.
     */
    override fun onBindViewHolder(holder: androidx.preference.PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        if (!previewTypeface) return
        (holder.findViewById(android.R.id.summary) as? TextView)?.typeface =
                WatchFontCatalog.previewTypefaceFor(context, value)
    }

    /** Commits a chosen value through the change listener so the preview hook runs before persist. */
    fun applyChoice(value: String) {
        if (value == this.value) return
        if (callChangeListener(value)) {
            this.value = value
        }
    }

    override fun createDialog(key: String): PreferenceDialogFragmentCompat =
            DescribedListPreferenceDialog.create(key)

    class DescribedListPreferenceDialog : PreferenceDialogFragmentCompat() {
        companion object {
            fun create(key: String) = DescribedListPreferenceDialog().apply {
                arguments = Bundle(1).apply { putString(ARG_KEY, key) }
            }
        }

        override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
            val pref = preference as DescribedListPreference
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
                            .inflate(R.layout.item_described_choice, parent, false)
                    val title = row.findViewById<TextView>(R.id.choice_title)
                    val check = row.findViewById<ImageView>(R.id.choice_check)

                    title.text = entries[position]
                    if (pref.previewTypeface) {
                        // Each row shows its own font. Resolved per row rather than cached in a
                        // list: the catalog is ~20 entries, all either bundled resources (which
                        // ResourcesCompat caches itself) or Typeface.create calls off the platform
                        // family cache, so there is nothing left to memoise here.
                        title.typeface = WatchFontCatalog.previewTypefaceFor(
                                parent.context, values.getOrNull(position)?.toString())
                    }

                    val selected = position == selectedIndex
                    check.visibility = if (selected) View.VISIBLE else View.INVISIBLE
                    check.imageTintList = ColorStateList.valueOf(accent)
                    title.setTextColor(if (selected) accent else onSurface)
                    // The description isn't shown in the dialog (kept short and scannable, like
                    // every other picker) but is still worth announcing to TalkBack here.
                    val desc = pref.descriptions.getOrNull(position)
                    row.contentDescription = if (desc.isNullOrBlank()) {
                        entries[position]
                    } else {
                        "${entries[position]}. $desc"
                    }
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
    }
}
