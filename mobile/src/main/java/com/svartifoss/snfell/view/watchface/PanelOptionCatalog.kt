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
        preference.entries = choices.values.toTypedArray()
        preference.entryValues = choices.keys.toTypedArray()
    }
}
