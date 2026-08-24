package com.svartifoss.snfell.view.watchface.theme

import android.content.Context
import android.util.AtomicFile
import com.svartifoss.snfell.common.ArchivedFaces
import com.svartifoss.snfell.common.ThemeAppearance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** One entry in the static community-theme catalogue. */
data class OnlineThemeSummary(
        val id: String,
        val name: String,
        val author: String,
        val baseFace: String,
        val revision: Int,
        /** The profile/library schema used by this published theme. */
        val schemaVersion: Int,
        /** The oldest Svartifoss version that can render the profile, for UI compatibility hints. */
        val minimumAppVersion: String,
        /** ISO-8601 publication timestamp supplied by the static catalogue. */
        val publishedAt: String,
        /** Absent until likes ship; retained here so that Phase 3 is a data-only extension. */
        val likes: Int = 0
)

/** A verified catalogue entry and its flat `profileToJson`-shaped JSON object. */
data class OnlineTheme(
        val summary: OnlineThemeSummary,
        val profileJson: JSONObject
)

/**
 * Read-only client for the community-theme files served from this repository's GitHub Pages site.
 *
 * The canonical catalogue is `{"schemaVersion": 1, "themes": [...]}` in `docs/themes/index.json`.
 * Every item points implicitly to `docs/themes/<uuid>.json`; URLs from the catalogue are never
 * trusted. A profile is intentionally returned as JSON so the caller can pass it through
 * [WatchThemeRepository.parsePublishedProfile] before installing it into the local theme library.
 *
 * Both the catalogue and individual profiles have an ETag-backed, best-effort cache in `cacheDir`.
 * A malformed response never replaces a previously valid cached copy. When the network is absent,
 * a valid cached copy remains usable; [IOException] is thrown only when neither source is usable.
 */
class OnlineThemesRepository(context: Context) {

    private val appContext = context.applicationContext
    private val cacheDirectory = File(appContext.cacheDir, CACHE_DIRECTORY)
    private val cachePreferences = appContext.getSharedPreferences(CACHE_PREFERENCES, Context.MODE_PRIVATE)

    /**
     * Returns the published catalogue, using a fresh disk cache when possible.
     *
     * [forceRefresh] bypasses the local freshness interval but still uses `If-None-Match`, so an
     * unchanged GitHub Pages response costs only a conditional request.
     *
     * @throws IOException when the catalogue cannot be fetched and no valid cached catalogue exists.
     */
    suspend fun loadCatalog(forceRefresh: Boolean): List<OnlineThemeSummary> =
            withContext(Dispatchers.IO) {
                val cached = readCachedCatalog()
                if (cached != null && !forceRefresh && isFresh(CATALOG_CACHE_KEY)) {
                    return@withContext cached
                }

                try {
                    var response = requestJson(
                            url = CATALOG_URL,
                            etag = cachedEtag(CATALOG_CACHE_KEY),
                            maxBytes = MAX_CATALOG_BYTES)
                    // A 304 is only useful if the body on disk still parses. A cleared/corrupt
                    // cache paired with a persisted ETag gets one unconditional repair request.
                    if (response === JsonResponse.NotModified && cached == null) {
                        response = requestJson(CATALOG_URL, etag = null, maxBytes = MAX_CATALOG_BYTES)
                    }
                    when (response) {
                        is JsonResponse.Fresh -> {
                            val parsed = parseCatalog(response.body)
                            writeCache(CATALOG_FILE, CATALOG_CACHE_KEY, response.body, response.etag)
                            parsed
                        }
                        JsonResponse.NotModified -> {
                            touch(CATALOG_CACHE_KEY)
                            cached ?: throw IOException("Online theme catalogue was not cached")
                        }
                    }
                } catch (e: IOException) {
                    if (cached != null) {
                        Timber.d(e, "Using cached online theme catalogue after refresh failure")
                        cached
                    } else {
                        throw e
                    }
                }
            }

    /**
     * Loads the full profile for [summary]. Cached content is reused only when its immutable id,
     * revision and display metadata match the current catalogue entry.
     *
     * @throws IOException when the profile cannot be fetched, is malformed, or has no valid cache.
     */
    suspend fun loadTheme(summary: OnlineThemeSummary): OnlineTheme = withContext(Dispatchers.IO) {
        val canonicalSummary = validateSummary(summary)
        val cacheKey = themeCacheKey(canonicalSummary.id)
        val cacheFile = themeCacheFile(canonicalSummary.id)
        val cached = readCachedTheme(cacheFile, canonicalSummary)
        if (cached != null && isFresh(cacheKey)) {
            return@withContext cached
        }

        try {
            var response = requestJson(
                    url = themeUrl(canonicalSummary.id),
                    etag = cachedEtag(cacheKey),
                    maxBytes = MAX_PROFILE_BYTES)
            if (response === JsonResponse.NotModified && cached == null) {
                response = requestJson(
                        url = themeUrl(canonicalSummary.id),
                        etag = null,
                        maxBytes = MAX_PROFILE_BYTES)
            }
            when (response) {
                is JsonResponse.Fresh -> {
                    val parsed = parseTheme(canonicalSummary, response.body)
                    writeCache(cacheFile.name, cacheKey, response.body, response.etag)
                    parsed
                }
                JsonResponse.NotModified -> {
                    touch(cacheKey)
                    cached ?: throw IOException("Online theme ${canonicalSummary.id} was not cached")
                }
            }
        } catch (e: IOException) {
            if (cached != null) {
                Timber.d(e, "Using cached online theme %s after refresh failure", canonicalSummary.id)
                cached
            } else {
                throw e
            }
        }
    }

    private fun readCachedCatalog(): List<OnlineThemeSummary>? =
            readCache(CATALOG_FILE, MAX_CATALOG_BYTES)?.let { body ->
                try {
                    parseCatalog(body)
                } catch (e: IOException) {
                    Timber.d(e, "Discarding malformed cached online theme catalogue")
                    null
                }
            }

    private fun readCachedTheme(file: File, summary: OnlineThemeSummary): OnlineTheme? =
            readCache(file.name, MAX_PROFILE_BYTES)?.let { body ->
                try {
                    parseTheme(summary, body)
                } catch (e: IOException) {
                    Timber.d(e, "Discarding malformed cached online theme %s", summary.id)
                    null
                }
            }

    private fun parseCatalog(body: String): List<OnlineThemeSummary> = try {
        val root = JSONTokener(body).nextValue()
        val entries = when (root) {
            is JSONObject -> {
                // `schemaVersion` is canonical; `catalogSchemaVersion` keeps early hand-authored
                // catalogues readable if one existed before the format name was settled.
                val version = root.requiredPositiveInt("schemaVersion", "catalogSchemaVersion")
                        ?: throw IOException("Online theme catalogue has no schema version")
                if (version != CATALOG_SCHEMA_VERSION) {
                    throw IOException("Unsupported online theme catalogue schema $version")
                }
                root.optJSONArray("themes") ?: root.optJSONArray("entries")
                ?: throw IOException("Online theme catalogue has no themes array")
            }
            // Tolerate a bare array for a small hand-authored Phase-1 catalogue. It has no root
            // schema to reject, but every entry remains fully validated below.
            is JSONArray -> root
            else -> throw IOException("Online theme catalogue is not JSON")
        }
        if (entries.length() > MAX_CATALOG_ENTRIES) {
            throw IOException("Online theme catalogue has too many entries")
        }

        val seenIds = HashSet<String>()
        buildList(entries.length()) {
            for (index in 0 until entries.length()) {
                val entry = entries.optJSONObject(index)
                        ?: throw IOException("Invalid online theme catalogue entry $index")
                val summary = parseSummary(entry)
                        ?: throw IOException("Invalid online theme catalogue entry $index")
                if (!seenIds.add(summary.id)) {
                    throw IOException("Duplicate online theme id ${summary.id}")
                }
                add(summary)
            }
        }
    } catch (e: IOException) {
        throw e
    } catch (e: Exception) {
        throw IOException("Invalid online theme catalogue", e)
    }

    private fun parseSummary(json: JSONObject): OnlineThemeSummary? {
        val id = canonicalThemeId(json.requiredText("id")) ?: return null
        val name = json.requiredText("name") ?: return null
        val author = json.requiredText("author") ?: return null
        val baseFace = json.requiredText("baseFace") ?: return null
        val revision = json.requiredPositiveInt("revision") ?: return null
        // `profileSchemaVersion` and `minAppVersionCode` are accepted aliases from the original
        // design note; the checked-in GitHub Pages catalogue uses the first field in each pair.
        val schemaVersion = json.requiredPositiveInt("schemaVersion", "profileSchemaVersion")
                ?: return null
        val minimumAppVersion = json.requiredText("minimumAppVersion", "minAppVersion")
                ?: return null
        val publishedAt = json.requiredText("publishedAt") ?: return null
        val likes = if (json.has("likes")) {
            json.nonNegativeInt("likes") ?: return null
        } else {
            0
        }
        return OnlineThemeSummary(
                id = id,
                name = name,
                author = author,
                baseFace = baseFace,
                revision = revision,
                schemaVersion = schemaVersion,
                minimumAppVersion = minimumAppVersion,
                publishedAt = publishedAt,
                likes = likes)
    }

    private fun validateSummary(summary: OnlineThemeSummary): OnlineThemeSummary {
        val id = canonicalThemeId(summary.id)
                ?: throw IOException("Invalid online theme id")
        val name = cleanText(summary.name)
                ?: throw IOException("Invalid online theme name")
        val author = cleanText(summary.author)
                ?: throw IOException("Invalid online theme author")
        val baseFace = cleanText(summary.baseFace)
                ?: throw IOException("Invalid online theme base face")
        val minimumAppVersion = cleanText(summary.minimumAppVersion)
                ?: throw IOException("Invalid online theme minimum app version")
        val publishedAt = cleanText(summary.publishedAt)
                ?: throw IOException("Invalid online theme publication date")
        if (summary.revision < 1 || summary.schemaVersion < 1 || summary.likes < 0) {
            throw IOException("Invalid online theme metadata")
        }
        // The activity shows these items with an upgrade requirement rather than requesting
        // them. Keep the same boundary at the data layer so a future caller cannot turn a
        // catalogue entry for an unknown or retired face into an install candidate.
        if (summary.schemaVersion != WatchThemeRepository.LIBRARY_SCHEMA) {
            throw IOException("Unsupported online theme profile schema ${summary.schemaVersion}")
        }
        if (baseFace !in ThemeAppearance.ALLOWED_BASE_FACES || baseFace in ArchivedFaces.KEYS) {
            throw IOException("Unsupported online theme base face")
        }
        return summary.copy(
                id = id,
                name = name,
                author = author,
                baseFace = baseFace,
                minimumAppVersion = minimumAppVersion,
                publishedAt = publishedAt)
    }

    private fun parseTheme(summary: OnlineThemeSummary, body: String): OnlineTheme = try {
        val profile = JSONObject(body)
        val id = canonicalThemeId(profile.requiredText("id"))
                ?: throw IOException("Online theme profile has an invalid id")
        if (id != summary.id) throw IOException("Online theme profile id does not match its catalogue entry")

        val name = profile.requiredText("name")
                ?: throw IOException("Online theme profile has no name")
        if (name != summary.name) throw IOException("Online theme profile name does not match its catalogue entry")

        val baseFace = profile.requiredText("baseFace")
                ?: throw IOException("Online theme profile has no base face")
        if (baseFace != summary.baseFace) {
            throw IOException("Online theme profile base face does not match its catalogue entry")
        }

        val revision = profile.requiredPositiveInt("revision")
                ?: throw IOException("Online theme profile has an invalid revision")
        if (revision != summary.revision) {
            throw IOException("Online theme profile revision does not match its catalogue entry")
        }
        if (profile.optJSONObject("settings") == null) {
            throw IOException("Online theme profile has no settings object")
        }

        // Store metadata is optional on a raw `profileToJson` object, but the canonical Pages files
        // carry it. When present, it must agree with the index rather than silently changing a
        // card's title, compatibility requirement or provenance at install time.
        profile.optionalPositiveInt("schemaVersion")?.let {
            if (it != summary.schemaVersion) {
                throw IOException("Online theme profile schema does not match its catalogue entry")
            }
        }
        profile.optionalText("author")?.let {
            if (it != summary.author) {
                throw IOException("Online theme profile author does not match its catalogue entry")
            }
        }
        profile.optionalText("minimumAppVersion", "minAppVersion")?.let {
            if (it != summary.minimumAppVersion) {
                throw IOException("Online theme profile minimum app version does not match its catalogue entry")
            }
        }
        profile.optionalText("publishedAt")?.let {
            if (it != summary.publishedAt) {
                throw IOException("Online theme profile publication date does not match its catalogue entry")
            }
        }
        OnlineTheme(summary, profile)
    } catch (e: IOException) {
        throw e
    } catch (e: Exception) {
        throw IOException("Invalid online theme profile ${summary.id}", e)
    }

    private fun requestJson(url: String, etag: String?, maxBytes: Int): JsonResponse {
        var connection: HttpURLConnection? = null
        try {
            connection = URL(url).openConnection() as? HttpURLConnection
                    ?: throw IOException("Online theme URL is not HTTP")
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", USER_AGENT)
            // See ApkDownloader: Android can truncate a pooled redirected HttpURLConnection body.
            connection.setRequestProperty("Connection", "close")
            etag?.takeIf { it.isNotBlank() }?.let {
                connection.setRequestProperty("If-None-Match", it)
            }

            return when (val status = connection.responseCode) {
                HttpURLConnection.HTTP_NOT_MODIFIED -> JsonResponse.NotModified
                HttpURLConnection.HTTP_OK -> {
                    if (connection.contentLength > maxBytes) {
                        throw IOException("Online theme response is too large")
                    }
                    JsonResponse.Fresh(
                            body = readUtf8Limited(connection.inputStream, maxBytes),
                            etag = connection.getHeaderField("ETag")?.takeIf { it.isNotBlank() })
                }
                else -> throw IOException("Online themes returned HTTP $status")
            }
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException("Online theme request failed", e)
        } finally {
            connection?.disconnect()
        }
    }

    private fun readUtf8Limited(input: InputStream, maxBytes: Int): String = input.use { stream ->
        val buffer = ByteArray(8 * 1024)
        val bytes = ByteArrayOutputStream()
        var total = 0
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) throw IOException("Online theme response is too large")
            bytes.write(buffer, 0, read)
        }
        String(bytes.toByteArray(), Charsets.UTF_8)
    }

    private fun readCache(fileName: String, maxBytes: Int): String? {
        val file = File(cacheDirectory, fileName)
        if (!file.isFile || file.length() > maxBytes) return null
        return try {
            file.inputStream().use { input -> readUtf8LimitedWithoutClose(input, maxBytes) }
        } catch (e: Exception) {
            Timber.d(e, "Could not read online theme cache")
            null
        }
    }

    /** [readUtf8Limited] owns its input; cache reads already own it in an outer `use` block. */
    private fun readUtf8LimitedWithoutClose(input: InputStream, maxBytes: Int): String {
        val buffer = ByteArray(8 * 1024)
        val bytes = ByteArrayOutputStream()
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) throw IOException("Online theme cache is too large")
            bytes.write(buffer, 0, read)
        }
        return String(bytes.toByteArray(), Charsets.UTF_8)
    }

    /** Writes the body first, atomically; metadata is then only a cache revalidation hint. */
    private fun writeCache(fileName: String, cacheKey: String, body: String, etag: String?) {
        if (!cacheDirectory.exists() && !cacheDirectory.mkdirs()) {
            Timber.d("Could not create online theme cache directory")
            return
        }
        val atomicFile = AtomicFile(File(cacheDirectory, fileName))
        var output: FileOutputStream? = null
        try {
            output = atomicFile.startWrite()
            output.write(body.toByteArray(Charsets.UTF_8))
            output.flush()
            atomicFile.finishWrite(output)
            output = null
            cachePreferences.edit().apply {
                putLong(lastFetchKey(cacheKey), System.currentTimeMillis())
                if (etag == null) remove(etagKey(cacheKey)) else putString(etagKey(cacheKey), etag)
            }.commit()
        } catch (e: Exception) {
            output?.let(atomicFile::failWrite)
            Timber.d(e, "Could not cache online theme response")
        }
    }

    private fun isFresh(cacheKey: String): Boolean {
        val fetchedAt = cachePreferences.getLong(lastFetchKey(cacheKey), 0L)
        val age = System.currentTimeMillis() - fetchedAt
        return age in 0 until CACHE_FRESH_FOR_MS
    }

    private fun touch(cacheKey: String) {
        cachePreferences.edit().putLong(lastFetchKey(cacheKey), System.currentTimeMillis()).apply()
    }

    private fun cachedEtag(cacheKey: String): String? =
            cachePreferences.getString(etagKey(cacheKey), null)

    private fun themeUrl(id: String): String = "$THEMES_URL$id.json"

    private fun themeCacheFile(id: String): File = File(cacheDirectory, "theme-$id.json")

    private fun themeCacheKey(id: String): String = "theme-$id"

    private fun JSONObject.requiredText(vararg names: String): String? =
            cleanText(firstValue(*names))

    private fun JSONObject.optionalText(vararg names: String): String? {
        if (!hasAny(*names)) return null
        return cleanText(firstValue(*names))
                ?: throw IOException("Invalid online theme text metadata")
    }

    private fun JSONObject.requiredPositiveInt(vararg names: String): Int? =
            positiveInt(firstValue(*names))

    private fun JSONObject.optionalPositiveInt(vararg names: String): Int? {
        if (!hasAny(*names)) return null
        return positiveInt(firstValue(*names))
                ?: throw IOException("Invalid online theme numeric metadata")
    }

    private fun JSONObject.nonNegativeInt(vararg names: String): Int? =
            integerValue(firstValue(*names))?.takeIf { it >= 0 }

    private fun JSONObject.firstValue(vararg names: String): Any? {
        for (name in names) {
            if (has(name)) {
                val value = opt(name)
                return value.takeUnless { it == JSONObject.NULL }
            }
        }
        return null
    }

    private fun JSONObject.hasAny(vararg names: String): Boolean = names.any(::has)

    private fun cleanText(value: Any?): String? = (value as? String)
            ?.trim()
            ?.replace(WHITESPACE, " ")
            ?.takeIf { it.isNotBlank() && it.length <= MAX_TEXT_LENGTH }

    private fun canonicalThemeId(value: String?): String? = try {
        val parsed = UUID.fromString(value)
        parsed.toString().takeIf { value.equals(it, ignoreCase = true) }
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun positiveInt(value: Any?): Int? = integerValue(value)?.takeIf { it > 0 }

    private fun integerValue(value: Any?): Int? = when (value) {
        is Int -> value
        is Long -> value.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()
        is Number -> {
            val asLong = value.toLong()
            if (value.toDouble() == asLong.toDouble() && asLong in Int.MIN_VALUE..Int.MAX_VALUE) {
                asLong.toInt()
            } else {
                null
            }
        }
        is String -> value.toIntOrNull()
        else -> null
    }

    private sealed class JsonResponse {
        data class Fresh(val body: String, val etag: String?) : JsonResponse()
        object NotModified : JsonResponse()
    }

    private companion object {
        private const val CATALOG_URL =
                "https://gabrielluizone.github.io/Svartifoss/themes/index.json"
        private const val THEMES_URL = "https://gabrielluizone.github.io/Svartifoss/themes/"

        private const val USER_AGENT = "Svartifoss-online-themes"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 15_000

        private const val CATALOG_SCHEMA_VERSION = 1
        private const val MAX_CATALOG_ENTRIES = 5_000
        private const val MAX_CATALOG_BYTES = 512 * 1024
        private const val MAX_PROFILE_BYTES = 128 * 1024
        private const val MAX_TEXT_LENGTH = 96
        private const val CACHE_FRESH_FOR_MS = 6L * 60L * 60L * 1_000L

        private const val CACHE_DIRECTORY = "online_themes"
        private const val CACHE_PREFERENCES = "online_theme_cache"
        private const val CATALOG_FILE = "catalog.json"
        private const val CATALOG_CACHE_KEY = "catalog"

        private val WHITESPACE = Regex("\\s+")

        private fun etagKey(cacheKey: String): String = "$cacheKey.etag"
        private fun lastFetchKey(cacheKey: String): String = "$cacheKey.last_fetch"
    }
}
