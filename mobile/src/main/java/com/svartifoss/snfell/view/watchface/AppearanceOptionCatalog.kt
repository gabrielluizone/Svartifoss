package com.svartifoss.snfell.view.watchface

import android.content.res.Resources
import androidx.annotation.ArrayRes
import androidx.preference.ListPreference
import com.svartifoss.snfell.R

/** Additive non-panel appearance choices; avoids replacing localized legacy arrays as a whole. */
internal object AppearanceOptionCatalog {
    private data class Extension(
            val key: String,
            @ArrayRes val entries: Int,
            @ArrayRes val values: Int)

    private val extensions = listOf(
            Extension("album_art_style", R.array.album_art_style_extra_entries,
                    R.array.album_art_style_extra_values),
            Extension("album_art_filter", R.array.album_art_filter_extra_entries,
                    R.array.album_art_filter_extra_values),
            Extension("wear_player_shading_style", R.array.player_shading_style_extra_entries,
                    R.array.player_shading_style_extra_values),
            Extension("screen_buttons_shape", R.array.screen_buttons_shape_extra_entries,
                    R.array.screen_buttons_shape_extra_values),
            Extension("wear_accent_floor", R.array.wear_accent_floor_extra_entries,
                    R.array.wear_accent_floor_extra_values),
            Extension("wear_up_next_pill_style", R.array.wear_up_next_pill_extra_entries,
                    R.array.wear_up_next_pill_extra_values))

    fun apply(resources: Resources, preferenceFor: (String) -> ListPreference?) {
        extensions.forEach { extension ->
            val preference = preferenceFor(extension.key) ?: return@forEach
            val entries = resources.getStringArray(extension.entries)
            val values = resources.getStringArray(extension.values)
            check(entries.size == values.size) { "Appearance entries/values differ for ${extension.key}" }
            val choices = linkedMapOf<String, CharSequence>()
            preference.entryValues.orEmpty().forEachIndexed { index, value ->
                preference.entries?.getOrNull(index)?.let { choices[value.toString()] = it }
            }
            values.forEachIndexed { index, value -> choices.putIfAbsent(value, entries[index]) }
            preference.entries = choices.values.toTypedArray()
            preference.entryValues = choices.keys.toTypedArray()
        }
    }
}
