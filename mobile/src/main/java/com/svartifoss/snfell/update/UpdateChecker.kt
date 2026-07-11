package com.svartifoss.snfell.update

import android.content.Context
import androidx.preference.PreferenceManager
import com.svartifoss.snfell.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks the GitHub releases of this repo for a version newer than the installed one. The app
 * is sideload-only, so this is the only way users learn about updates - checks run silently in
 * the background (throttled, see [maybeCheckInBackground]) from the places the app naturally
 * wakes up (music starts playing, app opened), and post a notification pointing at
 * [UpdateActivity] when something newer is found.
 *
 * Pre-releases are opt-in via [PREF_INCLUDE_PRERELEASES]; drafts are always ignored.
 */
object UpdateChecker {
    const val PREF_CHECK_ENABLED = "update_check_enabled"
    const val PREF_INCLUDE_PRERELEASES = "update_include_prereleases"
    private const val PREF_LAST_CHECK_MS = "update_last_check_ms"
    private const val PREF_LAST_NOTIFIED_TAG = "update_last_notified_tag"

    private const val RELEASES_URL =
            "https://api.github.com/repos/gabrielluizone/Svartifoss/releases?per_page=15"
    private const val BACKGROUND_CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000

    private const val ASSET_NAME_MOBILE = "mobile-release.apk"
    private const val ASSET_NAME_WEAR = "wear-release.apk"

    class ReleaseInfo(
            val tag: String,
            val title: String,
            val htmlUrl: String,
            val isPrerelease: Boolean,
            val mobileApkUrl: String?,
            val wearApkUrl: String?,
            val wearApkSize: Long
    )

    /**
     * Fetches the newest non-draft release (optionally including pre-releases). Returns null
     * when the repo has no matching release; throws on network/HTTP errors.
     */
    suspend fun fetchLatestRelease(includePrereleases: Boolean): ReleaseInfo? =
            withContext(Dispatchers.IO) {
                val connection = URL(RELEASES_URL).openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 15_000
                    connection.readTimeout = 15_000
                    connection.setRequestProperty("Accept", "application/vnd.github+json")
                    // GitHub rejects requests without a User-Agent
                    connection.setRequestProperty("User-Agent", "Svartifoss-update-check")

                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val releases = JSONArray(body)
                    for (i in 0 until releases.length()) {
                        val release = releases.getJSONObject(i)
                        if (release.optBoolean("draft")) continue
                        if (release.optBoolean("prerelease") && !includePrereleases) continue
                        // The API returns releases newest-first, so the first match is the latest
                        return@withContext parseRelease(release)
                    }
                    null
                } finally {
                    connection.disconnect()
                }
            }

    private fun parseRelease(release: JSONObject): ReleaseInfo {
        var mobileApkUrl: String? = null
        var wearApkUrl: String? = null
        var wearApkSize = 0L

        val assets = release.optJSONArray("assets") ?: JSONArray()
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            when (asset.optString("name")) {
                ASSET_NAME_MOBILE -> mobileApkUrl = asset.optString("browser_download_url")
                ASSET_NAME_WEAR -> {
                    wearApkUrl = asset.optString("browser_download_url")
                    wearApkSize = asset.optLong("size")
                }
            }
        }

        val tag = release.getString("tag_name")
        return ReleaseInfo(
                tag = tag,
                title = release.optString("name").ifBlank { tag },
                htmlUrl = release.getString("html_url"),
                isPrerelease = release.optBoolean("prerelease"),
                mobileApkUrl = mobileApkUrl,
                wearApkUrl = wearApkUrl,
                wearApkSize = wearApkSize
        )
    }

    fun isNewerThanInstalled(tag: String): Boolean = isNewer(tag, BuildConfig.VERSION_NAME)

    /**
     * Numeric segment-wise version comparison ("2.1.1" < "2.2"). Tags may carry a leading "v"
     * and non-numeric suffixes ("v2.2-rc1" reads as 2.2) - suffixes are ignored, so a
     * same-numbered rc/final pair compares equal.
     */
    fun isNewer(tag: String, installed: String): Boolean {
        val tagSegments = parseVersionSegments(tag)
        val installedSegments = parseVersionSegments(installed)
        if (tagSegments.isEmpty()) {
            return false
        }
        for (i in 0 until maxOf(tagSegments.size, installedSegments.size)) {
            val a = tagSegments.getOrElse(i) { 0 }
            val b = installedSegments.getOrElse(i) { 0 }
            if (a != b) {
                return a > b
            }
        }
        return false
    }

    private fun parseVersionSegments(version: String): List<Int> = version
            .trim()
            .removePrefix("v")
            .removePrefix("V")
            .split('.', '-', ' ')
            .mapNotNull { segment -> segment.takeWhile { it.isDigit() }.toIntOrNull() }

    /**
     * Background check: throttled to once a day, silent on any failure, posts (at most once per
     * release) a notification when a newer version exists. Cheap no-op when the user disabled
     * update checks.
     */
    suspend fun maybeCheckInBackground(context: Context) {
        val appContext = context.applicationContext
        val preferences = PreferenceManager.getDefaultSharedPreferences(appContext)
        if (!preferences.getBoolean(PREF_CHECK_ENABLED, true)) {
            return
        }

        val now = System.currentTimeMillis()
        val lastCheck = preferences.getLong(PREF_LAST_CHECK_MS, 0)
        if (now in lastCheck until (lastCheck + BACKGROUND_CHECK_INTERVAL_MS)) {
            return
        }

        val release = try {
            fetchLatestRelease(preferences.getBoolean(PREF_INCLUDE_PRERELEASES, false))
        } catch (e: Exception) {
            Timber.w(e, "Background update check failed")
            return
        } ?: return

        preferences.edit().putLong(PREF_LAST_CHECK_MS, now).apply()

        if (!isNewerThanInstalled(release.tag)) {
            return
        }
        if (preferences.getString(PREF_LAST_NOTIFIED_TAG, null) == release.tag) {
            return
        }

        UpdateNotifier.notifyUpdateAvailable(appContext, release)
        preferences.edit().putString(PREF_LAST_NOTIFIED_TAG, release.tag).apply()
    }
}
