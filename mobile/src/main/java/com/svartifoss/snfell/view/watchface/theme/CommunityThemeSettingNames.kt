package com.svartifoss.snfell.view.watchface.theme

import android.content.Context
import androidx.annotation.XmlRes
import com.svartifoss.snfell.R
import com.svartifoss.snfell.view.settings.SettingsSearchIndex
import org.xmlpull.v1.XmlPullParser

/**
 * Names the control and the value behind a refused submission, in the user's own language.
 *
 * A public profile is validated as a whole against the shipped vocabulary, so a single setting
 * holding a value that vocabulary does not accept refuses the entire theme. Said as "this saved
 * theme cannot be submitted", that is unactionable: nothing on screen is wrong, the face renders
 * correctly, and there is no indication which of a hundred and forty-seven settings to look at.
 * Fifty-one missing fonts once presented as several *layouts* being unsubmittable, because a
 * typeface is chosen per layout.
 *
 * Both halves are read from the settings screens rather than from a table kept here, so a control
 * renamed or retranslated cannot start being described by a stale name: the title comes from the
 * same XML parse that backs settings search, and the value's label from the very picker that
 * offered it.
 */
internal object CommunityThemeSettingNames {

    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

    /**
     * Font rows whose entries `WatchFacePrefsFragment` builds in code from the global catalogue.
     *
     * They carry no `entries`/`entryValues` in XML, so the lookup below cannot find them - and
     * these are exactly the keys the missing fonts came in through, which makes them the ones that
     * most need naming.
     */
    private val FONT_FAMILY_KEYS = setOf(
            "wear_font",
            "wear_title_font",
            "wear_artist_font",
            "wear_clock_font",
            "wear_lyrics_font",
            "wear_track_time_font")

    /**
     * Settings the user edits through a surface rather than a row of their own.
     *
     * The background stack is the only one so far: it is a list somebody builds on the Background
     * page, so there is no `<Preference>` in the XML for the search index to have found a title
     * on - and a refusal that cannot name it would land back on the generic message this object
     * exists to replace.
     */
    private val TITLES_WITHOUT_A_ROW = mapOf(
            "wear_background_layers" to R.string.background_editor_layers)

    /** The row's own localized title, e.g. "Title font". */
    fun settingTitle(context: Context, key: String): String? =
            SettingsSearchIndex.build(context).firstOrNull { it.key == key }?.title
                    ?: TITLES_WITHOUT_A_ROW[key]?.let(context::getString)

    /** The label the picker shows for [value], e.g. "Lobster" rather than `lobster`. */
    fun valueLabel(context: Context, key: String, value: WatchThemeValue): String? {
        val stored = (value as? WatchThemeValue.Text)?.value ?: return null
        val arrays = when {
            key in FONT_FAMILY_KEYS -> R.array.wear_font_entries to R.array.wear_font_values
            else -> listOf(R.xml.watch_face_settings, R.xml.settings)
                    .firstNotNullOfOrNull { entryArrays(context, it, key) }
        } ?: return null
        val labels = context.resources.getStringArray(arrays.first)
        val values = context.resources.getStringArray(arrays.second)
        return labels.getOrNull(values.indexOf(stored))
    }

    /** The `entries`/`entryValues` array ids declared on [key]'s row, if it declares any. */
    private fun entryArrays(
            context: Context,
            @XmlRes xmlRes: Int,
            key: String
    ): Pair<Int, Int>? {
        // XmlResourceParser only became AutoCloseable above this module's minSdk, so it is closed
        // by hand rather than with `use` - the same reason SettingsSearchIndex does.
        val parser = context.resources.getXml(xmlRes)
        try {
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG) continue
                if (parser.getAttributeValue(ANDROID_NS, "key") != key) continue
                val entries = parser.getAttributeResourceValue(ANDROID_NS, "entries", 0)
                val values = parser.getAttributeResourceValue(ANDROID_NS, "entryValues", 0)
                return if (entries != 0 && values != 0) entries to values else null
            }
        } catch (_: Exception) {
            return null
        } finally {
            parser.close()
        }
        return null
    }
}
