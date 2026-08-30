package com.svartifoss.snfell.watch.view.facepicker

import androidx.annotation.StringRes
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.ArchivedFaces
import com.svartifoss.snfell.common.ThemeAppearance

/** One selectable face in the on-watch picker. */
data class WatchFaceOption(
        val key: String,
        @StringRes val labelRes: Int,
        val customName: String? = null,
        /**
         * The structural face this option renders with.
         *
         * For a built-in face that is the key itself; for a custom theme it is the profile's base
         * face, which is the only part of a theme the watch can know without the phone's snapshot.
         * Both the miniature and the picker's instant local apply need it - see
         * `FacePickerActivity.applyFace` for why writing `custom:<id>` into `wear_screen_face`
         * is not an option.
         */
        val baseFace: String
) {
    val isCustomTheme: Boolean get() = key.startsWith(CUSTOM_PREFIX)

    companion object {
        const val CUSTOM_PREFIX = "custom:"
    }
}

/**
 * The faces the on-watch picker offers, in display order.
 *
 * Derived from [ThemeAppearance.ALLOWED_BASE_FACES] rather than hand-listed: that set is the
 * canonical registry both sides already validate against, so a face added there cannot end up
 * missing from this picker, and a key here that is not a real face cannot be produced. Names are
 * looked up through an explicit key→resource map, deliberately *not* through an ordered array
 * paired by index - that pairing is exactly how the phone's face picker has silently mislabelled
 * options before (see the string-array hazard in CLAUDE.md).
 *
 * A face with no label entry is dropped rather than shown with a placeholder: an unnamed row in a
 * picker is worse than one fewer row, and the omission is a build-time mistake to fix, not a state
 * to render.
 */
object WatchFaceCatalog {

    private val LABELS: Map<String, Int> = mapOf(
            "classic" to R.string.face_name_classic,
            "expressive" to R.string.face_name_expressive,
            "vinyl" to R.string.face_name_vinyl,
            "poster" to R.string.face_name_poster,
            "studio" to R.string.face_name_studio,
            "halo" to R.string.face_name_halo,
            "aurora" to R.string.face_name_aurora,
            "eclipse" to R.string.face_name_eclipse,
            "spectrum" to R.string.face_name_spectrum,
            "material" to R.string.face_name_material,
            "immersive" to R.string.face_name_immersive,
            "depth" to R.string.face_name_depth,
            "carousel" to R.string.face_name_carousel,
            "chat" to R.string.face_name_chat,
            "split" to R.string.face_name_split,
            "note" to R.string.face_name_note,
            "verse" to R.string.face_name_verse,
            "metadata" to R.string.face_name_metadata,
            "ribbon" to R.string.face_name_ribbon,
            "frame" to R.string.face_name_frame
    )

    /**
     * The faces to offer, given the one currently in use and the serialized custom themes.
     *
     * Retired faces ([ArchivedFaces]) are dropped: they are hidden from the phone's picker too, and
     * offering a face that is known to misbehave is worse than a shorter list. The exception is the
     * face already in use - if someone is on an archived one, a picker that cannot show what they
     * have reads as a bug. The phone can reveal the rest behind its developer switch; the watch
     * cannot, since that key is phone-local and never synced.
     */
    /** The display name of one face, or null for a key with no label entry. */
    fun labelFor(faceKey: String): Int? = LABELS[faceKey]

    fun builtInOptions(activeFace: String): List<WatchFaceOption> =
            ThemeAppearance.ALLOWED_BASE_FACES.mapNotNull { key ->
                if (key in ArchivedFaces.KEYS && key != activeFace) return@mapNotNull null
                LABELS[key]?.let { WatchFaceOption(key, it, baseFace = key) }
            }

    /**
     * The user's saved themes, decoded from the JSON the phone syncs.
     *
     * A malformed array yields an empty list rather than throwing: this is remote data that
     * arrived over Bluetooth from a possibly-newer phone build, and a picker with no custom
     * section is a far better outcome than one that crashes on open. Individual entries missing an
     * id or name are skipped for the same reason, while an unknown `baseFace` is normalized rather
     * than dropped - the theme is still selectable, it just previews as the default face.
     */
    fun customOptions(customThemesJson: String): List<WatchFaceOption> = try {
        val array = org.json.JSONArray(customThemesJson)
        (0 until array.length()).mapNotNull { i ->
            val obj = array.optJSONObject(i) ?: return@mapNotNull null
            val id = obj.optString("id")
            val name = obj.optString("name")
            if (id.isEmpty() || name.isEmpty()) return@mapNotNull null
            WatchFaceOption(
                    key = WatchFaceOption.CUSTOM_PREFIX + id,
                    labelRes = 0,
                    customName = name,
                    baseFace = ThemeAppearance.normalizeBaseFace(obj.optString("baseFace")))
        }
    } catch (_: org.json.JSONException) {
        emptyList()
    }

    fun optionsFor(activeFace: String, customThemesJson: String): List<WatchFaceOption> =
            builtInOptions(activeFace) + customOptions(customThemesJson)
}
