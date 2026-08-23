package com.svartifoss.snfell.common

import android.content.SharedPreferences

/**
 * Fully validated context used to resolve appearance preferences.
 *
 * [BuiltIn] preserves the historical per-face preference behavior. [Custom] keeps the structural
 * renderer in [baseFace], but reads visual values from one fixed synchronization scope, allowing
 * the phone to keep an arbitrarily large profile library locally while the watch receives only
 * the active, complete snapshot.
 */
sealed class AppearanceContext {
    abstract val baseFace: String

    data class BuiltIn(override val baseFace: String) : AppearanceContext()

    data class Custom(
            val themeId: String,
            override val baseFace: String,
            val schema: Int,
            val revision: Int
    ) : AppearanceContext()
}

/** Shared validation contract for selecting a built-in face or an active custom snapshot. */
/**
 * Faces retired for known problems.
 *
 * Hidden from every face picker so nobody lands on one by accident, while still resolving normally
 * for anyone already using one - retiring a face must never silently change what is on their wrist.
 * Lives in `common` because both pickers need it: the phone's list (which can reveal them again
 * behind the developer "Show archived options" switch) and the on-watch picker, which cannot -
 * `dev_show_archived` is a phone-local key and is not in [MiscPreferences.EXPORTABLE], so the watch
 * has no way to read it and simply always hides them.
 */
object ArchivedFaces {
    val KEYS: Set<String> = setOf("vinyl", "halo", "aurora", "eclipse", "spectrum", "depth")
}

object ThemeAppearance {
    const val CURRENT_SCHEMA = 1
    const val CUSTOM_SCOPE = "custom_active"
    const val DEFAULT_FACE = "classic"

    /** Stable renderer IDs understood by both the phone preview and the Wear renderer. */
    val ALLOWED_BASE_FACES: Set<String> = linkedSetOf(
            "classic",
            "expressive",
            "vinyl",
            "poster",
            "studio",
            "halo",
            "aurora",
            "eclipse",
            "spectrum",
            "material",
            "immersive",
            "depth",
            "carousel",
            "chat",
            "split",
            "note",
            "verse",
            "metadata"
    )

    fun normalizeBaseFace(face: String?): String =
            face?.takeIf { it in ALLOWED_BASE_FACES } ?: DEFAULT_FACE

    /**
     * Pure resolver used by persistence code and unit tests. Invalid/incomplete custom metadata
     * can never activate a partially synchronized snapshot; it falls back to the validated
     * built-in face instead.
     */
    fun resolve(
            baseFace: String?,
            customThemeId: String?,
            customComplete: Boolean,
            customSchema: Int,
            customRevision: Int = 0
    ): AppearanceContext {
        val normalizedFace = normalizeBaseFace(baseFace)
        val normalizedId = customThemeId?.trim().orEmpty()
        return if (
                normalizedId.isNotEmpty() &&
                customComplete &&
                customSchema == CURRENT_SCHEMA &&
                baseFace == normalizedFace
        ) {
            AppearanceContext.Custom(
                    themeId = normalizedId,
                    baseFace = normalizedFace,
                    schema = customSchema,
                    revision = customRevision.coerceAtLeast(0)
            )
        } else {
            AppearanceContext.BuiltIn(normalizedFace)
        }
    }

    /** Type-tolerant SharedPreferences entry point used across an app-version transition. */
    fun resolve(prefs: SharedPreferences): AppearanceContext = resolve(
            baseFace = prefs.getStringSafely(MiscPreferences.WEAR_SCREEN_FACE.key),
            customThemeId = prefs.getStringSafely(MiscPreferences.WEAR_ACTIVE_CUSTOM_THEME_ID.key),
            customComplete = prefs.getBooleanSafely(
                    MiscPreferences.WEAR_CUSTOM_THEME_COMPLETE.key,
                    MiscPreferences.WEAR_CUSTOM_THEME_COMPLETE.defaultValue),
            customSchema = prefs.getStringIntSafely(
                    MiscPreferences.WEAR_CUSTOM_THEME_SCHEMA.key,
                    MiscPreferences.WEAR_CUSTOM_THEME_SCHEMA.defaultValue),
            customRevision = prefs.getStringIntSafely(
                    MiscPreferences.WEAR_CUSTOM_THEME_REVISION.key,
                    MiscPreferences.WEAR_CUSTOM_THEME_REVISION.defaultValue)
    )

    private fun SharedPreferences.getStringSafely(key: String): String? = try {
        getString(key, null)
    } catch (_: ClassCastException) {
        null
    }

    private fun SharedPreferences.getBooleanSafely(key: String, defaultValue: Boolean): Boolean =
            try {
                getBoolean(key, defaultValue)
            } catch (_: ClassCastException) {
                defaultValue
            }

    /** Integers are defined as string preferences, but raw ints from older/debug paths remain
     *  readable. Any other type or malformed number safely resolves to the definition default. */
    private fun SharedPreferences.getStringIntSafely(key: String, defaultValue: Int): Int {
        val stringValue = try {
            getString(key, null)
        } catch (_: ClassCastException) {
            null
        }
        if (stringValue != null) return stringValue.toIntOrNull() ?: defaultValue

        return try {
            if (contains(key)) getInt(key, defaultValue) else defaultValue
        } catch (_: ClassCastException) {
            defaultValue
        }
    }
}
