package com.svartifoss.snfell.view.settings.dev

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.matejdro.wearutils.preferences.definition.PreferenceDefinition
import com.svartifoss.snfell.common.AppearanceContext
import com.svartifoss.snfell.common.FaceScopedPreferences
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.common.ThemeAppearance

private data class ResolvedField(val key: String, val source: String, val value: String)

/**
 * For every scoped appearance key, names which of [FaceScopedPreferences.getString] /
 * [FaceScopedPreferences.getBoolean] / [FaceScopedPreferences.getInt]'s resolution steps actually
 * won - explicit per-face value, a legacy album-art override, a per-face default, the legacy
 * global value, or the bare definition default - alongside the value each already-tested function
 * returns. The four-step order is easy to get partially right (both [getBoolean] and [getInt] have
 * at some point dropped the per-face-default step, quietly, for as long as no key of that type had
 * one), so this mirrors the *current* source exactly rather than restating the intended contract.
 */
private fun resolveField(
        prefs: SharedPreferences,
        definition: PreferenceDefinition<*>,
        context: AppearanceContext
): ResolvedField {
    val key = definition.key
    val (source, value) = when (definition.defaultValue) {
        is String -> {
            @Suppress("UNCHECKED_CAST")
            val stringDef = definition as PreferenceDefinition<String>
            val resolved = FaceScopedPreferences.getString(prefs, stringDef, context)
            stringSource(prefs, key, context) to resolved
        }
        is Boolean -> {
            @Suppress("UNCHECKED_CAST")
            val boolDef = definition as PreferenceDefinition<Boolean>
            val resolved = FaceScopedPreferences.getBoolean(prefs, boolDef, context)
            booleanOrIntSource(prefs, key, context) to resolved.toString()
        }
        is Int -> {
            @Suppress("UNCHECKED_CAST")
            val intDef = definition as PreferenceDefinition<Int>
            val resolved = FaceScopedPreferences.getInt(prefs, intDef, context)
            booleanOrIntSource(prefs, key, context) to resolved.toString()
        }
        else -> "unknown type" to "?"
    }
    return ResolvedField(key, source, value)
}

private fun stringSource(prefs: SharedPreferences, key: String, context: AppearanceContext): String =
        when (context) {
            is AppearanceContext.BuiltIn -> {
                val face = context.baseFace
                val scoped = FaceScopedPreferences.scopedKey(key, face)
                when {
                    prefs.contains(scoped) -> "explicit ($face)"
                    key == MiscPreferences.ALBUM_ART_STYLE.key &&
                            FaceScopedPreferences.hasLegacyAlbumArtOverride(prefs) ->
                        "legacy global (album art override)"
                    FaceScopedPreferences.perFaceDefault(face, key) != null -> "per-face default"
                    prefs.contains(key) -> "legacy global value"
                    else -> "definition default"
                }
            }
            is AppearanceContext.Custom -> {
                val scoped = FaceScopedPreferences.scopedKey(key, ThemeAppearance.CUSTOM_SCOPE)
                when {
                    prefs.contains(scoped) -> "explicit (custom theme)"
                    FaceScopedPreferences.perFaceDefault(context.baseFace, key) != null ->
                        "per-face default (base ${context.baseFace})"
                    else -> "definition default"
                }
            }
        }

private fun booleanOrIntSource(
        prefs: SharedPreferences,
        key: String,
        context: AppearanceContext
): String = when (context) {
    is AppearanceContext.BuiltIn -> {
        val face = context.baseFace
        val scoped = FaceScopedPreferences.scopedKey(key, face)
        when {
            prefs.contains(scoped) -> "explicit ($face)"
            FaceScopedPreferences.perFaceDefault(face, key) != null -> "per-face default"
            prefs.contains(key) -> "legacy global value"
            else -> "definition default"
        }
    }
    is AppearanceContext.Custom -> {
        val scoped = FaceScopedPreferences.scopedKey(key, ThemeAppearance.CUSTOM_SCOPE)
        when {
            prefs.contains(scoped) -> "explicit (custom theme)"
            FaceScopedPreferences.perFaceDefault(context.baseFace, key) != null ->
                "per-face default (base ${context.baseFace})"
            else -> "definition default"
        }
    }
}

internal fun buildAppearanceResolutionReport(context: Context): String {
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    val appearanceContext = ThemeAppearance.resolve(prefs)

    val fields = FaceScopedPreferences.SCOPED_DEFINITIONS
            .map { resolveField(prefs, it, appearanceContext) }
            .sortedBy { it.key }

    val bySource = fields.groupBy { it.source }

    return buildString {
        when (appearanceContext) {
            is AppearanceContext.BuiltIn ->
                appendLine("Active context: built-in face \"${appearanceContext.baseFace}\"")
            is AppearanceContext.Custom ->
                appendLine("Active context: custom theme \"${appearanceContext.themeId}\" " +
                        "(base face \"${appearanceContext.baseFace}\", schema " +
                        "${appearanceContext.schema}, revision ${appearanceContext.revision})")
        }
        appendLine("${fields.size} scoped appearance keys")
        appendLine()

        appendLine("By resolution source:")
        for ((source, group) in bySource.entries.sortedByDescending { it.value.size }) {
            appendLine("  $source: ${group.size}")
        }
        appendLine()

        appendLine("Every key:")
        for (field in fields) {
            appendLine("  ${field.key} = ${field.value}  [${field.source}]")
        }
    }
}
