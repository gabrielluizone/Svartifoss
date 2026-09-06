package com.svartifoss.snfell.view.settings

import android.content.Context
import android.content.res.XmlResourceParser
import androidx.annotation.XmlRes
import com.svartifoss.snfell.R
import com.svartifoss.snfell.update.UpdateGateway
import com.svartifoss.snfell.view.watchface.WatchFacePrefsFragment
import java.text.Normalizer
import org.xmlpull.v1.XmlPullParser

/** Where a search hit lives, and therefore what tapping it has to open. */
sealed class SettingsSearchDestination {
    abstract val section: String

    /** The Settings tab; [section] is a `MiscSettingsFragment.SECTION_*`. */
    data class Settings(override val section: String) : SettingsSearchDestination()

    /** The Watch face tab; [section] is a `WatchFacePrefsFragment.SECTION_*`. */
    data class WatchFace(override val section: String) : SettingsSearchDestination()
}

/** One searchable row. [key] is the preference key, used to scroll to it once the page is open. */
data class SettingsSearchEntry(
        val key: String,
        val title: String,
        val summary: String,
        val sectionTitle: String,
        val categoryTitle: String,
        val destination: SettingsSearchDestination
)

/**
 * Makes the ~190 preferences across the Settings and Watch face tabs findable by name.
 *
 * The index is **built from the preference XML itself** rather than from a hand-written list, so a
 * preference added to either screen is searchable with no second registration to forget. Titles and
 * summaries are resolved through the resource system, which means the index is in the user's chosen
 * language for free - and matches it, which a hardcoded English list could not.
 *
 * Splitting matching ([rank]) from building ([build]) is deliberate: the ranking is where the
 * judgement calls are, so it is a pure function over plain data with no Android types, pinned by
 * `SettingsSearchRankingTest`. That is the same shape as `RotaryAction.resolve` and friends.
 */
object SettingsSearchIndex {

    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

    /** Elements that group other rows rather than being settings themselves. */
    private const val CATEGORY_TAG_SUFFIX = "PreferenceCategory"

    /**
     * Categories whose rows this build hides, so search must not offer them - a result would land
     * on an invisible row and read as broken. The Play flavor drops the in-app self-updater
     * entirely (Play delivers updates), so its Updates category never appears.
     */
    private val EXCLUDED_CATEGORIES: Set<String> =
            if (UpdateGateway.SUPPORTS_SELF_UPDATE) emptySet() else setOf("cat_updates")

    private val FORMAT_PLACEHOLDER =
            Regex("%(?:\\d+\\$)?[-#+ 0,(<]*\\d*(?:\\.\\d+)?[a-zA-Z%]")

    fun build(context: Context): List<SettingsSearchEntry> =
            parse(context, R.xml.settings, SettingsCatalog.SETTINGS_SECTIONS) {
                SettingsSearchDestination.Settings(it)
            } + parse(context, R.xml.watch_face_settings, SettingsCatalog.WATCH_SECTIONS) {
                SettingsSearchDestination.WatchFace(it)
            }

    private fun parse(
            context: Context,
            @XmlRes xmlRes: Int,
            sections: Map<String, Set<String>>,
            destination: (String) -> SettingsSearchDestination
    ): List<SettingsSearchEntry> {
        val entries = mutableListOf<SettingsSearchEntry>()
        // A category's rows are its children, so the enclosing category is simply the last one
        // opened at a shallower depth. Tracking the depth it opened at is what lets us clear it on
        // the matching close tag rather than on the first close tag of any kind.
        var categoryTitle = ""
        var categorySection: String? = null
        var categoryDepth = -1

        // XmlResourceParser only became AutoCloseable well above this module's minSdk, so it is
        // closed by hand rather than with `use`.
        val parser = context.resources.getXml(xmlRes)
        try {
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> {
                        val key = parser.attr(context, "key")
                        val title = parser.attr(context, "title")
                        if (parser.name.endsWith(CATEGORY_TAG_SUFFIX)) {
                            categoryTitle = title
                            categorySection = key
                                    .takeIf { it.isNotEmpty() && it !in EXCLUDED_CATEGORIES }
                                    ?.let { SettingsCatalog.sectionForCategory(sections, it) }
                            categoryDepth = parser.depth
                        } else if (key.isNotEmpty() && title.isNotEmpty()) {
                            // A row outside any category, or in one no section shows, cannot be
                            // navigated to - offering it as a result would dead-end the user.
                            categorySection?.let { section ->
                                val rowDestination = destination(section)
                                entries += SettingsSearchEntry(
                                        key = key,
                                        title = title,
                                        summary = cleanSummary(parser.attr(context, "summary")),
                                        sectionTitle = sectionTitle(context, rowDestination),
                                        categoryTitle = categoryTitle,
                                        destination = rowDestination)
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> if (parser.depth == categoryDepth) {
                        categoryTitle = ""
                        categorySection = null
                        categoryDepth = -1
                    }
                }
            }
        } finally {
            parser.close()
        }
        return entries
    }

    /** Reads an `android:` attribute, resolving a `@string/...` reference to its localized text. */
    private fun XmlResourceParser.attr(context: Context, name: String): String {
        val resId = getAttributeResourceValue(ANDROID_NS, name, 0)
        return if (resId != 0) context.getString(resId)
        else getAttributeValue(ANDROID_NS, name).orEmpty()
    }

    /** Resolves the existing section label in the active locale for result paths and matching. */
    private fun sectionTitle(context: Context, destination: SettingsSearchDestination): String {
        val titleRes = when (destination) {
            is SettingsSearchDestination.Settings -> when (destination.section) {
                MiscSettingsFragment.SECTION_GENERAL -> R.string.settings_section_general
                MiscSettingsFragment.SECTION_WATCH -> R.string.settings_section_watch
                MiscSettingsFragment.SECTION_AUTOMATION -> R.string.settings_section_automation
                MiscSettingsFragment.SECTION_APPS -> R.string.settings_section_apps
                MiscSettingsFragment.SECTION_DATA -> R.string.settings_section_data
                else -> error("Unknown Settings section: ${destination.section}")
            }
            is SettingsSearchDestination.WatchFace -> when (destination.section) {
                WatchFacePrefsFragment.SECTION_STYLE ->
                    R.string.watch_section_style
                WatchFacePrefsFragment.SECTION_BACKGROUND ->
                    R.string.watch_section_background
                WatchFacePrefsFragment.SECTION_COLORS ->
                    R.string.watch_section_colors
                WatchFacePrefsFragment.SECTION_TYPOGRAPHY ->
                    R.string.watch_section_typography
                WatchFacePrefsFragment.SECTION_AOD ->
                    R.string.watch_section_aod
                WatchFacePrefsFragment.SECTION_PANELS ->
                    R.string.watch_section_panels
                WatchFacePrefsFragment.SECTION_MINI_BUTTONS ->
                    R.string.watch_section_mini_buttons
                else -> error("Unknown Watch appearance section: ${destination.section}")
            }
        }
        return context.getString(titleRes)
    }

    /**
     * Orders [entries] by how well they match [query]; an empty query returns nothing (the screen
     * shows its prompt instead of dumping all 190 rows).
     *
     * Every whitespace-separated term must match somewhere in a row, so extra words narrow rather
     * than widen - typing "clock colour" should not return every row mentioning colour. Where a
     * term matches decides the score: a title hit outranks a category hit, which outranks the
     * broader section, which outranks a summary hit. Matching the start of the title outranks
     * matching its middle.
     *
     * Comparison is accent- and case-insensitive, which is not cosmetic here: the app ships 13
     * languages, and requiring "reprodução" to be typed with the diacritic would make search close
     * to unusable in most of them.
     */
    fun rank(entries: List<SettingsSearchEntry>, query: String): List<SettingsSearchEntry> {
        val terms = normalize(query).split(' ').filter { it.isNotEmpty() }
        if (terms.isEmpty()) return emptyList()

        return entries.mapNotNull { entry ->
            val title = normalize(entry.title)
            val category = normalize(entry.categoryTitle)
            val section = normalize(entry.sectionTitle)
            val summary = normalize(cleanSummary(entry.summary))

            var total = 0
            for (term in terms) {
                val score = when {
                    title.startsWith(term) -> 100
                    title.contains(term) -> 60
                    category.contains(term) -> 30
                    section.contains(term) -> 20
                    summary.contains(term) -> 10
                    else -> return@mapNotNull null
                }
                total += score
            }
            entry to total
        }
                // Ties broken by title so the order is stable rather than dependent on XML order,
                // which would shuffle results whenever a preference is moved.
                .sortedWith(compareByDescending<Pair<SettingsSearchEntry, Int>> { it.second }
                        .thenBy { it.first.title })
                .map { it.first }
    }

    /** Lowercases and strips diacritics, so "Reproducao" finds "Reprodução". */
    internal fun normalize(text: String): String =
            Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
                    .replace(Regex("\\p{Mn}+"), "")
                    .trim()

    /**
     * List preferences often use a bare printf token as a hook for their selected value. The
     * Preference framework replaces it on the actual page, but the XML index cannot; showing or
     * searching raw text such as `%s` is noise. Keep real prose even when it includes a token.
     */
    internal fun cleanSummary(text: String): String {
        val summary = text.trim()
        if (summary.isEmpty()) return ""
        if (!FORMAT_PLACEHOLDER.containsMatchIn(summary)) return summary

        val withoutPlaceholders = summary.replace(FORMAT_PLACEHOLDER, "")
        return if (withoutPlaceholders.any(Char::isLetterOrDigit)) summary else ""
    }
}
