package com.svartifoss.snfell.music

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Extra, public information about a streaming link. It deliberately contains no account-specific
 * fields: it is assembled from the service's link preview (oEmbed) or the same public HTML a
 * browser receives when opening the share URL.
 */
data class PublicLinkMetadata(
        val title: String? = null,
        val thumbnailUrl: String? = null,
        val creator: String? = null,
        val description: String? = null
) {
    fun mergeFallback(fallback: PublicLinkMetadata?): PublicLinkMetadata = copy(
            title = title ?: fallback?.title,
            thumbnailUrl = thumbnailUrl ?: fallback?.thumbnailUrl,
            creator = creator ?: fallback?.creator,
            description = description ?: fallback?.description)

    val hasDetails: Boolean get() = !creator.isNullOrBlank() || !description.isNullOrBlank()
}

/**
 * A deliberately small, dependency-free reader for public page metadata. It is a fallback, not a
 * general HTML parser: Open Graph is made of meta tags and JSON-LD is JSON, so accepting only
 * those two standard formats keeps the network feature predictable and avoids scraping an app's
 * private page structure.
 */
object PublicLinkMetadataParser {
    private const val MAX_TEXT_LENGTH = 160
    private val metaTags = Regex("""<meta\b([^>]*)>""", RegexOption.IGNORE_CASE)
    private val titleTag = Regex("""<title\b[^>]*>(.*?)</title>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val jsonLdTags = Regex("""<script\b([^>]*)>(.*?)</script>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val attributes = Regex(
            """([\w:-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+))""",
            RegexOption.IGNORE_CASE)

    fun parse(html: String): PublicLinkMetadata? {
        if (html.isBlank()) return null
        val meta = linkedMapOf<String, String>()
        metaTags.findAll(html).forEach { tag ->
            val values = attributes.findAll(tag.groupValues[1]).associate { attribute ->
                attribute.groupValues[1].lowercase() to attribute.groupValues.drop(2)
                        .firstOrNull(String::isNotEmpty).orEmpty()
            }
            val key = (values["property"] ?: values["name"] ?: values["itemprop"])
                    ?.lowercase()
                    ?: return@forEach
            values["content"]?.let { meta.putIfAbsent(key, it) }
        }

        val schema = schemaMetadata(html)
        val openGraph = PublicLinkMetadata(
                title = text(meta["og:title"] ?: meta["twitter:title"] ?: titleTag.find(html)
                        ?.groupValues?.getOrNull(1)),
                thumbnailUrl = url(meta["og:image"] ?: meta["twitter:image"]),
                creator = text(meta["author"] ?: meta["article:author"] ?: meta["music:musician"]),
                description = text(meta["og:description"] ?: meta["twitter:description"] ?:
                        meta["description"]))
        return openGraph.mergeFallback(schema).takeIf { metadata ->
            metadata.title != null || metadata.thumbnailUrl != null || metadata.hasDetails
        }
    }

    private fun schemaMetadata(html: String): PublicLinkMetadata? {
        var result: PublicLinkMetadata? = null
        jsonLdTags.findAll(html).forEach { tag ->
            val attributes = attributes.findAll(tag.groupValues[1]).associate { attribute ->
                attribute.groupValues[1].lowercase() to attribute.groupValues.drop(2)
                        .firstOrNull(String::isNotEmpty).orEmpty()
            }
            if (!attributes["type"].orEmpty().contains("ld+json", ignoreCase = true)) return@forEach
            val raw = tag.groupValues[2].trim()
            val value = try {
                when {
                    raw.startsWith("{") -> JSONObject(raw)
                    raw.startsWith("[") -> JSONArray(raw)
                    else -> null
                }
            } catch (_: JSONException) {
                null
            }
            result = schemaFrom(value)?.mergeFallback(result)
        }
        return result
    }

    private fun schemaFrom(value: Any?): PublicLinkMetadata? = when (value) {
        is JSONObject -> {
            val own = PublicLinkMetadata(
                    title = text(value.optString("name")),
                    thumbnailUrl = imageUrl(value.opt("image")),
                    creator = personName(value.opt("byArtist")) ?: personName(value.opt("author")) ?: 
                            personName(value.opt("creator")),
                    description = text(value.optString("description")))
            var nested: PublicLinkMetadata? = null
            value.opt("@graph")?.let { nested = schemaFrom(it) }
            own.mergeFallback(nested).takeIf { it.title != null || it.thumbnailUrl != null || it.hasDetails }
        }
        is JSONArray -> {
            var result: PublicLinkMetadata? = null
            for (index in 0 until value.length()) {
                result = schemaFrom(value.opt(index))?.mergeFallback(result)
            }
            result
        }
        else -> null
    }

    private fun personName(value: Any?): String? = when (value) {
        is JSONObject -> text(value.optString("name"))
        is JSONArray -> (0 until value.length()).asSequence()
                .mapNotNull { personName(value.opt(it)) }.firstOrNull()
        is String -> text(value)
        else -> null
    }

    private fun imageUrl(value: Any?): String? = when (value) {
        is JSONObject -> url(value.optString("url"))
        is JSONArray -> (0 until value.length()).asSequence()
                .mapNotNull { imageUrl(value.opt(it)) }.firstOrNull()
        is String -> url(value)
        else -> null
    }

    private fun url(raw: String?): String? = raw?.trim()?.takeIf {
        it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true)
    }

    private fun text(raw: String?): String? = raw?.replace(Regex("<[^>]+>"), " ")
            ?.let(OembedParser::cleanTitle)
            ?.take(MAX_TEXT_LENGTH)
}
