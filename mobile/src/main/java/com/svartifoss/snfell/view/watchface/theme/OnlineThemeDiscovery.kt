package com.svartifoss.snfell.view.watchface.theme

import java.text.Normalizer
import java.time.Instant
import java.util.Locale

/** The gallery's base-face chip. [All] deliberately has no sentinel string value. */
sealed class OnlineThemeBaseFaceFilter {
    object All : OnlineThemeBaseFaceFilter()

    data class BaseFace(val value: String) : OnlineThemeBaseFaceFilter()
}

/** Orders that can be applied entirely from the public static catalogue. */
enum class OnlineThemeSort {
    NEWEST,
    MOST_LIKED,

    /**
     * By the published install count.
     *
     * Kept beside [MOST_LIKED] rather than replacing it because the two answer different
     * questions: a like is an opinion somebody chose to record, an install is what people actually
     * put on their watch. A theme can be widely installed and rarely hearted, and the gallery is
     * more useful when it can be asked either way round.
     */
    MOST_DOWNLOADED
}

/** Pure, saved-state-friendly input for Community-theme discovery. */
data class OnlineThemeDiscoveryRequest(
        val query: String = "",
        val baseFace: OnlineThemeBaseFaceFilter = OnlineThemeBaseFaceFilter.All,
        val sort: OnlineThemeSort = OnlineThemeSort.NEWEST,
        /** Set only after an explicit authenticated “Liked” filter request. */
        val likedOnly: Boolean = false,
        /**
         * One author's published themes, matched exactly on the normalized pseudonym.
         *
         * Deliberately its own field rather than a search term. "Everything this person made" is
         * an exact question, and pushing the name through [query] would answer a different one:
         * the searchable text also carries every theme *name*, so an author called Verse would
         * come back with the whole Verse catalogue beside their own work.
         */
        val author: String? = null,
        /**
         * Hides themes this phone has already installed. **On by default**: the gallery exists to
         * find something new, and a theme already in My themes is the one result that cannot be
         * that. It reads a local set, never the network, so it costs nothing to leave on.
         *
         * Deliberately not folded into [likedOnly]-style opt-in: this is a default the user turns
         * *off* to see everything, which is why [hasActiveDefaults] treats it as unremarkable.
         */
        val hideInstalled: Boolean = true
)

/**
 * Search, filtering, and ordering for the public Community-theme catalogue.
 *
 * This deliberately receives an already downloaded [OnlineThemeSummary] list. The gallery can
 * therefore stay a single ETag-cached Pages request: changing a search query, face chip, or sort
 * order never creates a Firestore or per-card network read. The optional private reaction set is
 * supplied separately because it is never public catalogue data and is loaded only when its
 * explicit filter is enabled.
 */
object OnlineThemeDiscovery {

    /**
     * Returns a new, deterministic display list. Every normalized query term must occur in the
     * theme name, author pseudonym, or base-face key; terms may be spread across those fields.
     */
    fun discover(
            themes: List<OnlineThemeSummary>,
            request: OnlineThemeDiscoveryRequest,
            likedThemeIds: Set<String> = emptySet(),
            installedThemeIds: Set<String> = emptySet()
    ): List<OnlineThemeSummary> {
        val terms = normalize(request.query).split(' ').filter(String::isNotEmpty)
        val selectedFace = when (val filter = request.baseFace) {
            OnlineThemeBaseFaceFilter.All -> null
            is OnlineThemeBaseFaceFilter.BaseFace -> normalize(filter.value)
        }
        // A blank author is no filter at all rather than a filter nothing can satisfy: the name
        // can arrive from saved state written by an older build, or from a catalogue entry whose
        // author string is empty.
        val selectedAuthor = request.author?.let(::normalize)?.takeIf(String::isNotEmpty)

        return themes.asSequence()
                .filter { summary -> !request.likedOnly || summary.id in likedThemeIds }
                // Liked wins over hidden-when-installed: asking to see what you liked and being
                // shown nothing because you also installed it would read as a broken filter.
                .filter { summary ->
                    !request.hideInstalled ||
                            request.likedOnly ||
                            summary.id !in installedThemeIds
                }
                .filter { summary ->
                    selectedFace == null || normalize(summary.baseFace) == selectedFace
                }
                .filter { summary ->
                    selectedAuthor == null || normalize(summary.author) == selectedAuthor
                }
                .filter { summary ->
                    val searchable = normalize(
                            "${summary.name} ${summary.author} ${summary.baseFace}")
                    terms.all(searchable::contains)
                }
                .sortedWith(comparatorFor(request.sort))
                .toList()
    }

    private fun comparatorFor(sort: OnlineThemeSort): Comparator<OnlineThemeSummary> = when (sort) {
        OnlineThemeSort.NEWEST -> compareByDescending<OnlineThemeSummary> { publicationTime(it) }
                .thenBy { it.id }
        OnlineThemeSort.MOST_LIKED -> compareByDescending<OnlineThemeSummary> { it.likes }
                .thenByDescending { publicationTime(it) }
                .thenBy { it.id }
        OnlineThemeSort.MOST_DOWNLOADED -> compareByDescending<OnlineThemeSummary> { it.installs }
                .thenByDescending { it.likes }
                .thenByDescending { publicationTime(it) }
                .thenBy { it.id }
    }

    /** An invalid date is ordered after every valid public timestamp, without crashing the UI. */
    private fun publicationTime(summary: OnlineThemeSummary): Long = try {
        Instant.parse(summary.publishedAt).toEpochMilli()
    } catch (_: Exception) {
        Long.MIN_VALUE
    }

    /** Lowercases, strips diacritics, and treats punctuation as a word separator. */
    internal fun normalize(value: String): String =
            Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
                    .replace(COMBINING_MARKS, "")
                    .replace(NON_WORD, " ")
                    .trim()
                    .replace(WHITESPACE, " ")

    private val COMBINING_MARKS = Regex("\\p{Mn}+")
    private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")
    private val WHITESPACE = Regex("\\s+")
}
