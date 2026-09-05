package com.svartifoss.snfell.view.buttonconfig

import java.text.Normalizer
import java.util.Locale

/** A search candidate kept free of Android types so ranking stays JVM-testable. */
internal data class ActionSearchCandidate<T>(
        val value: T,
        val title: String,
        val breadcrumb: String,
        val sourceOrder: Int
)

/**
 * Global Pick action search.
 *
 * Every query term must be present. Titles rank ahead of category breadcrumbs, while source order
 * keeps equally good results in the deliberate order of the action catalogue. Accents are folded
 * so e.g. `acao` can find `Ação` and vice versa.
 */
internal object ActionPickerSearch {
    fun <T> rank(
            candidates: List<ActionSearchCandidate<T>>,
            query: String
    ): List<ActionSearchCandidate<T>> {
        val terms = normalize(query).split(' ').filter(String::isNotBlank)
        if (terms.isEmpty()) return candidates.sortedBy { it.sourceOrder }

        return candidates.mapNotNull { candidate ->
            val title = normalize(candidate.title)
            val breadcrumb = normalize(candidate.breadcrumb)
            var score = 0
            for (term in terms) {
                val termScore = scoreTerm(term, title, breadcrumb) ?: return@mapNotNull null
                score += termScore
            }
            Scored(candidate, score)
        }.sortedWith(
                compareByDescending<Scored<T>> { it.score }
                        .thenBy { it.candidate.sourceOrder }
        ).map { it.candidate }
    }

    private fun scoreTerm(term: String, title: String, breadcrumb: String): Int? = when {
        title == term -> 1_000
        title.startsWith(term) -> 850
        title.contains(term) -> 700
        breadcrumb.split(' ').any { it == term } -> 400
        breadcrumb.contains(term) -> 300
        else -> null
    }

    internal fun normalize(value: String): String = Normalizer
            .normalize(value, Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")
            .lowercase(Locale.ROOT)
            .replace(NON_SEARCH_CHARACTERS, " ")
            .trim()
            .replace(MULTIPLE_SPACES, " ")

    private data class Scored<T>(val candidate: ActionSearchCandidate<T>, val score: Int)

    private val COMBINING_MARKS = Regex("\\p{Mn}+")
    private val NON_SEARCH_CHARACTERS = Regex("[^\\p{L}\\p{N}]+")
    private val MULTIPLE_SPACES = Regex("\\s+")
}
