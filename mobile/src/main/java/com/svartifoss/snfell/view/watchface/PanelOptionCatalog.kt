package com.svartifoss.snfell.view.watchface

import android.content.res.Resources
import androidx.annotation.ArrayRes
import androidx.preference.ListPreference
import com.svartifoss.snfell.R

/**
 * Appends the expanded Panel catalog without copying every already-translated legacy array.
 *
 * Android replaces a localized string-array as a whole; it cannot extend the base array. Keeping
 * the additions in small companion arrays lets every locale retain its current translations and
 * inherit English only for a newly introduced label it has not translated yet. Stable values are
 * declared once in `values/` and are deduplicated here so rebuilding the preference screen is safe.
 */
internal object PanelOptionCatalog {

    private data class Extension(
            val key: String,
            @ArrayRes val entries: Int,
            @ArrayRes val values: Int)

    private val extensions = listOf(
            Extension(
                    "wear_overlay_backdrop_style",
                    R.array.wear_overlay_backdrop_extra_entries,
                    R.array.wear_overlay_backdrop_extra_values),
            Extension(
                    "wear_volume_style",
                    R.array.wear_volume_style_extra_entries,
                    R.array.wear_volume_style_extra_values),
            Extension(
                    "wear_volume_layout",
                    R.array.wear_volume_layout_extra_entries,
                    R.array.wear_volume_layout_extra_values),
            Extension(
                    "wear_progress_style",
                    R.array.wear_progress_style_extra_entries,
                    R.array.wear_progress_style_extra_values),
            Extension(
                    "wear_seek_style",
                    R.array.wear_seek_style_extra_entries,
                    R.array.wear_seek_style_extra_values),
            Extension(
                    "wear_seek_layout",
                    R.array.wear_seek_layout_extra_entries,
                    R.array.wear_seek_layout_extra_values),
            Extension(
                    "wear_quick_panel_style",
                    R.array.wear_quick_panel_style_extra_entries,
                    R.array.wear_quick_panel_style_extra_values),
            Extension(
                    "wear_quick_panel_layout",
                    R.array.wear_quick_panel_layout_extra_entries,
                    R.array.wear_quick_panel_layout_extra_values),
            Extension(
                    "wear_queue_style",
                    R.array.wear_queue_style_extra_entries,
                    R.array.wear_queue_style_extra_values))

    fun apply(
            resources: Resources,
            preferenceFor: (String) -> ListPreference?
    ) {
        extensions.forEach { extension ->
            val preference = preferenceFor(extension.key) ?: return@forEach
            append(
                    preference,
                    resources.getStringArray(extension.entries),
                    resources.getStringArray(extension.values))
        }
    }

    private fun append(
            preference: ListPreference,
            extraEntries: Array<String>,
            extraValues: Array<String>
    ) {
        check(extraEntries.size == extraValues.size) {
            "Panel option entries and values must stay aligned for ${preference.key}"
        }

        val choices = linkedMapOf<String, CharSequence>()
        preference.entryValues.orEmpty().forEachIndexed { index, value ->
            preference.entries?.getOrNull(index)?.let { entry -> choices[value.toString()] = entry }
        }
        extraValues.forEachIndexed { index, value ->
            choices.putIfAbsent(value, extraEntries[index])
        }
        val order = ordered(choices.keys.toList(), featured[preference.key].orEmpty())
        preference.entries = order.map { choices.getValue(it) }.toTypedArray()
        preference.entryValues = order.toTypedArray()
    }

    /**
     * The options that are worth seeing without scrolling, by preference.
     *
     * These lists have grown to thirty entries and everything added is appended to the end, which
     * is the one place nobody looks: a new style was there and effectively invisible. The distinctive
     * ones are put straight after the first entry instead, so they are on the first screen of the
     * dialog, and everything else keeps the order it always had.
     */
    private val featured = mapOf(
            "wear_volume_style" to listOf("wave", "ruler"),
            "wear_progress_style" to listOf("wave", "minutes", "hex_cells"),
            "wear_seek_layout" to listOf("wave_timeline"),
            "wear_queue_style" to listOf("route", "basalt", "strata"))

    /**
     * [values] with the [featured] ones that are present moved to just after the first, in the order
     * given. Pure so the placement can be pinned without an Android `ListPreference`.
     */
    internal fun ordered(values: List<String>, featured: List<String>): List<String> {
        val present = featured.filter { it in values }
        if (present.isEmpty() || values.size < 2) return values
        return listOf(values.first()) + present + values.drop(1).filter { it !in present }
    }
}
