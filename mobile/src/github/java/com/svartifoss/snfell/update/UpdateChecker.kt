package com.svartifoss.snfell.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.preference.PreferenceManager
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
    private const val PREF_LAST_SEEN_VERSION_CODE = "update_last_seen_version_code"

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
            val mobileApkSize: Long,
            val wearApkUrl: String?,
            val wearApkSize: Long,
            /** The release description (GitHub Markdown), shown as the "What's new" notes. */
            val body: String
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
        var mobileApkSize = 0L
        var wearApkUrl: String? = null
        var wearApkSize = 0L

        val assets = release.optJSONArray("assets") ?: JSONArray()
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            when (asset.optString("name")) {
                ASSET_NAME_MOBILE -> {
                    mobileApkUrl = asset.optString("browser_download_url")
                    mobileApkSize = asset.optLong("size")
                }
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
                mobileApkSize = mobileApkSize,
                wearApkUrl = wearApkUrl,
                wearApkSize = wearApkSize,
                body = release.optString("body").trim()
        )
    }

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

        if (!AppVersionComparison.isNewerThanInstalled(release.tag)) {
            return
        }
        val alreadyNotified = preferences.getString(PREF_LAST_NOTIFIED_TAG, null) == release.tag
        rememberKnownRelease(appContext, release.tag)
        if (alreadyNotified) {
            return
        }

        UpdateNotifier.notifyUpdateAvailable(appContext, release)
    }

    /**
     * Records that [tag] is the newest release the user has seen - either via the background
     * check above, or [UpdateActivity]'s manual "check now" (which never posts a notification but
     * should still count as "seen" and back [hasPendingUpdate]). Reusing the "notified" pref for
     * both means a manual check that already surfaced a release also suppresses a redundant
     * notification for that same tag later.
     */
    fun rememberKnownRelease(context: Context, tag: String) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit().putString(PREF_LAST_NOTIFIED_TAG, tag).apply()
    }

    /**
     * Whether the newest release we know about is newer than what's installed now - a free,
     * no-network read of the cached result of the last check (background or manual), for surfaces
     * (the toolbar help-icon badge) that want to flag a pending update the user may have dismissed
     * or missed. Naturally clears itself once the phone is actually updated, since the remembered
     * tag stops being newer than the (now bumped) installed version.
     */
    fun hasPendingUpdate(context: Context): Boolean {
        val tag = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(PREF_LAST_NOTIFIED_TAG, null) ?: return false
        return AppVersionComparison.isNewerThanInstalled(tag)
    }

    /**
     * True exactly once per version bump: when the installed versionCode is higher than the one
     * recorded on the *previous* call. Also records the current versionCode as a side effect, so
     * a second call on the same build (another Activity recreation, a later session) returns
     * false. A fresh install has no prior recording - [PREF_LAST_SEEN_VERSION_CODE] defaults to
     * -1, which is never less than a real versionCode's floor of 1 in the ">= 0" check below -
     * that first call still records the baseline, it just never returns true for it, so a new
     * user never gets a welcome screen with nothing to compare against.
     */
    fun consumePostUpdateWelcome(context: Context): Boolean {
        val appContext = context.applicationContext
        val preferences = PreferenceManager.getDefaultSharedPreferences(appContext)
        if (!preferences.getBoolean(PREF_CHECK_ENABLED, true)) {
            return false
        }

        val currentVersionCode = try {
            val info = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
        } catch (e: PackageManager.NameNotFoundException) {
            return false
        }

        val lastSeenVersionCode = preferences.getLong(PREF_LAST_SEEN_VERSION_CODE, -1L)
        if (lastSeenVersionCode != currentVersionCode) {
            preferences.edit().putLong(PREF_LAST_SEEN_VERSION_CODE, currentVersionCode).apply()
        }
        return lastSeenVersionCode in 0 until currentVersionCode
    }
}
