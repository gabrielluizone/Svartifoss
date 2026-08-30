package com.svartifoss.snfell.common

/**
 * Decides when track metadata should use the bundled Special Elite replacement font.
 *
 * The match intentionally remains case-insensitive and substring-based, preserving the behaviour
 * of the original keyword override while keeping the trigger list independent from either app
 * module.
 */
object SpecialEliteKeywordPolicy {
    private val keywords = setOf(
            "iwakura",
            "lain",
            "wired",
            "breakcore",
            "serial experiments"
    )

    fun matches(title: String, artist: String): Boolean {
        val titleLower = title.lowercase()
        val artistLower = artist.lowercase()
        return keywords.any { keyword ->
            titleLower.contains(keyword) || artistLower.contains(keyword)
        }
    }
}
