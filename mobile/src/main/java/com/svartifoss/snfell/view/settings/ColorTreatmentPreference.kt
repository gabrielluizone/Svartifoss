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
import com.svartifoss.snfell.common.ColorModifier
import com.svartifoss.snfell.common.SurfaceColorTreatment
import com.svartifoss.snfell.common.SurfacePaletteResolver
import com.svartifoss.snfell.view.LyraAccent
import com.svartifoss.snfell.view.NEUTRAL_WATCH_ACCENT

/**
 * A [ListPreference] for a Normal/Desaturated/Expressive-family color treatment (the
 * global "Watch color treatment" and its four per-target overrides) whose modal dialog shows each
 * option's actual resolved colors as three small swatches, instead of a name the user has to pick
 * blind and then check the preview to understand.
 *
 * The swatch data ([albumAccents], [normalPreviewColor], [globalTreatmentValue]) has no route
 * from a bare [Preference] to the live album accent, so the owning fragment must set all three
 * right before the dialog opens (see WatchFacePrefsFragment.onDisplayPreferenceDialog) - stale
 * values just mean a slightly outdated preview, never a wrong persisted choice, since selecting a
 * row always applies that row's own preference value regardless of what its swatch showed.
 */
class ColorTreatmentPreference @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null
) : ListPreference(context, attrs), PreferenceWithDialog {

    /** The preference key holding this target's own custom color, or null for the global picker
     *  (which has no per-target override - see [R.styleable.ColorTreatmentPreference]). */
    val customColorKey: String?

    /** Raw (primary, secondary, tertiary) album accent - what Expressive/Desaturated's
     *  swatches are derived from. Set by the owning fragment; see the class doc. */
    var albumAccents: Triple<Int, Int, Int> = Triple(DEFAULT_COLOR, DEFAULT_COLOR, DEFAULT_COLOR)

    /** The color the "Normal" row's swatch would actually resolve to - [customColorKey]'s value
     *  if this target has one set, else the global Normal color. Set by the owning fragment. */
    var normalPreviewColor: Int = DEFAULT_COLOR

    /** The current global "wear_color_treatment" value, needed to resolve what a "Follow watch
     *  treatment" row would actually show. Set by the owning fragment; irrelevant for the global
     *  picker itself, which has no Follow option. */
    var globalTreatmentValue: String = "expressive"

    /** The active tone modifier ("wear_color_modifier"), applied to every swatch so the rows show
     *  what the surface will really look like rather than the unmodified treatment. Set by the
     *  owning fragment; see the class doc. */
    var colorModifierValue: String = "none"

    /** The active palette hue shift ("wear_color_hue_shift"), applied to every swatch so the rows
     *  show the real colours - without it the swatches would keep promising the unshifted primary
     *  the treatment alone produces. Set by the owning fragment; see the class doc. */
    var colorHueShiftValue: Int = 0

    /** Whether to generate a triad for the normal treatment or just use a single solid color.
     *  Set by the owning fragment. */
    var normalColorMulti: Boolean = true

    init {
        val typed = context.obtainStyledAttributes(attrs, R.styleable.ColorTreatmentPreference)
        customColorKey = typed.getString(R.styleable.ColorTreatmentPreference_customColorKey)
        typed.recycle()
    }

    /** Commits a chosen value through the change listener so the preview hook runs before persist. */
    fun applyChoice(value: String) {
        if (value == this.value) return
        if (callChangeListener(value)) {
            this.value = value
        }
    }

    override fun createDialog(key: String): PreferenceDialogFragmentCompat =
            ColorTreatmentPreferenceDialog.create(key)

    class ColorTreatmentPreferenceDialog : PreferenceDialogFragmentCompat() {
        companion object {
            fun create(key: String) = ColorTreatmentPreferenceDialog().apply {
                arguments = Bundle(1).apply { putString(ARG_KEY, key) }
            }
        }

        override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
            val pref = preference as ColorTreatmentPreference
            val entries = pref.entries ?: emptyArray()
            val values = pref.entryValues ?: emptyArray()
            val selectedIndex = pref.findIndexOfValue(pref.value)
            val accent = LyraAccent.contrastSafe(requireContext(), LyraAccent.resolve(requireContext()))
            val onSurface = ContextCompat.getColor(requireContext(), R.color.lyra_on_surface)
            val (rawPrimary, rawSecondary, rawTertiary) = pref.albumAccents
            val global = SurfaceColorTreatment.fromPreference(
                    pref.globalTreatmentValue, default = SurfaceColorTreatment.EXPRESSIVE)

            val modifier = ColorModifier.fromPreference(pref.colorModifierValue)

            // Derived by the same `common` resolver the watch and the preview use, so a row's
            // swatches cannot promise a palette the watch renders differently - and a treatment
            // added later shows real colours here without this dialog being touched.
            fun swatchesFor(value: String): Triple<Int, Int, Int> {
                val triad = SurfacePaletteResolver.derive(
                        SurfaceColorTreatment.fromPreference(value).resolveAgainst(global),
                        modifier,
                        rawPrimary,
                        rawSecondary,
                        rawTertiary,
                        pref.normalPreviewColor,
                        pref.colorHueShiftValue.toFloat(),
                        pref.normalColorMulti)
                return Triple(triad.primary, triad.secondary, triad.tertiary)
            }

            val adapter = object : BaseAdapter() {
                override fun getCount() = entries.size
                override fun getItem(position: Int): Any = entries[position]
                override fun getItemId(position: Int) = position.toLong()

                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val row = convertView ?: LayoutInflater.from(parent.context)
                            .inflate(R.layout.item_color_treatment_choice, parent, false)
                    val title = row.findViewById<TextView>(R.id.choice_title)
                    val check = row.findViewById<ImageView>(R.id.choice_check)
                    val swatchPrimary = row.findViewById<ImageView>(R.id.swatch_primary)
                    val swatchSecondary = row.findViewById<ImageView>(R.id.swatch_secondary)
                    val swatchTertiary = row.findViewById<ImageView>(R.id.swatch_tertiary)

                    title.text = entries[position]

                    val selected = position == selectedIndex
                    check.visibility = if (selected) View.VISIBLE else View.INVISIBLE
                    check.imageTintList = ColorStateList.valueOf(accent)
                    title.setTextColor(if (selected) accent else onSurface)

                    val (primary, secondary, tertiary) = swatchesFor(values[position].toString())
                    swatchPrimary.imageTintList = ColorStateList.valueOf(primary)
                    swatchSecondary.imageTintList = ColorStateList.valueOf(secondary)
                    swatchTertiary.imageTintList = ColorStateList.valueOf(tertiary)

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
            // setSingleChoiceItems backs onto a plain ListView, which flashes its fading vertical
            // scrollbar on first layout even though every option always fits without scrolling -
            // there both a few rows and only ever 3-5 options.
            (dialog as? AlertDialog)?.listView?.isVerticalScrollBarEnabled = false
            return dialog
        }
    }

    companion object {
        private const val DEFAULT_COLOR = NEUTRAL_WATCH_ACCENT
    }
}
