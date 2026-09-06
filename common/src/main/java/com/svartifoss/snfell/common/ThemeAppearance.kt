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
    val KEYS: Set<String> = setOf(
            "vinyl", "halo", "aurora", "eclipse", "spectrum", "depth",
            // The tribute to the original WearMusicCenter. Archived not because anything is wrong
            // with it but because it is a period piece: it reproduces a 2017 screen, so it belongs
            // behind the same switch as the other faces kept for the people already on them
            // rather than in the picker everyone scrolls.
            "matejdro")

    /**
     * Archived faces additionally excluded from the community gallery - unsubmittable, and never
     * installable from someone else's submission.
     *
     * Archival on its own is only ever a *picker* decision (see [KEYS]'s own doc): a face here
     * still resolves normally for anyone using it, so nothing stops the rest of [KEYS] from being
     * shared like any current face. "depth" is the deliberate exception - its own rendering is
     * known to be problematic, so it stays out of the public catalogue on top of being archived,
     * until that is fixed.
     */
    val COMMUNITY_GALLERY_EXCLUDED: Set<String> = setOf("depth")
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
            "metadata",
            "artist",
            "ribbon",
            "frame",
            "matejdro"
    )

    /**
     * Faces that draw the playback queue's own covers as part of their composition, at a size the
     * 30dp list thumbnail cannot serve - Carousel's neighbouring cards, Ribbon's rails.
     *
     * Named here rather than in either module because both sides act on it and neither can see the
     * other: the phone sizes the queue thumbnails it transmits (`OpenPlaylistAction`), and the
     * watch warms the queue the moment such a face becomes visible (`MainActivity`). A face added
     * to one list and not the other renders a soft cover, or an empty rail, with nothing to say
     * why.
     */
    val QUEUE_ART_FACES: Set<String> = setOf("carousel", "ribbon")

    /**
     * Faces that draw a picture of the *performer* rather than the record sleeve.
     *
     * The one registry the artist-artwork lookup is gated on, and the reason that lookup is
     * acceptable: it is the app's only network call made on behalf of a purely visual choice, so
     * nothing may run for the great majority of users who have never selected such a face. The
     * phone reads it to decide whether to look one up at all (`MusicService.artistFaceSelected`),
     * and the phone's preview reads it to decide whether to draw a stand-in picture; a face added
     * to the enum and not to this set simply never receives an artist picture, with nothing on
     * either device to say why.
     */
    val ARTIST_ART_FACES: Set<String> = setOf("artist")

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
