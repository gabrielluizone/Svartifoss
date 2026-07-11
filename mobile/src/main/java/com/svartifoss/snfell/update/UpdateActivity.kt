package com.svartifoss.snfell.update

import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.google.android.material.button.MaterialButton
import com.google.android.gms.wearable.Wearable
import com.svartifoss.snfell.BuildConfig
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.CommPaths
import com.svartifoss.snfell.proto.WatchInfo
import com.svartifoss.snfell.view.LyraAccent
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * The single update surface: shows installed phone/watch versions against the latest GitHub
 * release, opens the release page in the browser, and pushes the wear APK to the watch over
 * Bluetooth ([WatchApkPusher]) so the watch can be updated without ADB/Wear Installer.
 * Reached from the update notification and from Settings.
 *
 * The "Update watch" button is gated on the *watch's* version (reported in its WatchInfo
 * DataItem), not the phone's: the normal flow is phone first, then watch - by then the release
 * tag equals the phone version and only the watch is still behind. An unknown watch version
 * (older watch build / nothing paired yet) keeps the button available as long as the release
 * itself is new to the phone.
 */
class UpdateActivity : AppCompatActivity() {

    private lateinit var latestText: TextView
    private lateinit var installedText: TextView
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var openReleaseButton: MaterialButton
    private lateinit var updateWatchButton: MaterialButton

    private var latestRelease: UpdateChecker.ReleaseInfo? = null
    private var watchVersion: String? = null
    private var pushing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_update)

        latestText = findViewById(R.id.text_latest)
        installedText = findViewById(R.id.text_installed)
        statusText = findViewById(R.id.text_status)
        progressBar = findViewById(R.id.progress)
        openReleaseButton = findViewById(R.id.button_open_release)
        updateWatchButton = findViewById(R.id.button_update_watch)

        val accent = LyraAccent.resolve(this)
        for (button in listOf(openReleaseButton, updateWatchButton)) {
            button.setTextColor(accent)
            button.iconTint = ColorStateList.valueOf(accent)
        }
        progressBar.progressTintList = ColorStateList.valueOf(accent)
        progressBar.indeterminateTintList = ColorStateList.valueOf(accent)

        openReleaseButton.setOnClickListener {
            val url = latestRelease?.htmlUrl ?: return@setOnClickListener
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (e: Exception) {
                Timber.w(e, "Could not open release page")
            }
        }
        updateWatchButton.setOnClickListener { pushToWatch() }

        installedText.text = getString(R.string.update_installed_phone, BuildConfig.VERSION_NAME)
        loadWatchVersion()
        loadLatestRelease()
    }

    /** The watch reports its version inside its WatchInfo DataItem (absent on older builds). */
    private fun loadWatchVersion() {
        lifecycleScope.launch {
            watchVersion = try {
                val dataItems = Wearable.getDataClient(this@UpdateActivity).dataItems.await()
                try {
                    dataItems
                            .firstOrNull { it.uri.path == CommPaths.DATA_WATCH_INFO }
                            ?.let { WatchInfo.parseFrom(it.data) }
                            ?.takeIf { it.hasAppVersionName() }
                            ?.appVersionName
                } finally {
                    dataItems.release()
                }
            } catch (e: Exception) {
                Timber.w(e, "Could not read watch version")
                null
            }

            installedText.text = getString(
                    R.string.update_installed_versions,
                    BuildConfig.VERSION_NAME,
                    watchVersion ?: getString(R.string.update_watch_version_unknown)
            )
            refreshButtons()
        }
    }

    private fun loadLatestRelease() {
        latestText.text = getString(R.string.update_checking)
        progressBar.visibility = View.VISIBLE
        progressBar.isIndeterminate = true
        refreshButtons()

        lifecycleScope.launch {
            val includePrereleases = PreferenceManager
                    .getDefaultSharedPreferences(this@UpdateActivity)
                    .getBoolean(UpdateChecker.PREF_INCLUDE_PRERELEASES, false)

            val release = try {
                UpdateChecker.fetchLatestRelease(includePrereleases)
            } catch (e: Exception) {
                Timber.w(e, "Update check failed")
                null
            }

            progressBar.visibility = View.INVISIBLE
            if (release == null) {
                latestText.text = getString(R.string.update_check_failed)
                return@launch
            }

            latestRelease = release

            if (UpdateChecker.isNewerThanInstalled(release.tag)) {
                latestText.text = getString(
                        R.string.update_available_line,
                        release.title,
                        release.tag
                ) + if (release.isPrerelease) " " + getString(R.string.update_prerelease_marker) else ""
                statusText.text = if (release.wearApkUrl == null) {
                    getString(R.string.update_no_wear_asset)
                } else {
                    getString(R.string.update_watch_hint)
                }
            } else {
                latestText.text = getString(R.string.update_up_to_date, release.tag)
                statusText.text = if (watchNeedsUpdate(release)) {
                    getString(R.string.update_watch_behind, watchVersion)
                } else {
                    ""
                }
            }
            refreshButtons()
        }
    }

    /**
     * The watch needs the release when its known version is older - or when its version is
     * unknown while the phone itself would take the update (an unknown-version watch running a
     * pre-2.2 build can't self-update anyway, but the attempt fails gracefully with a timeout).
     */
    private fun watchNeedsUpdate(release: UpdateChecker.ReleaseInfo): Boolean {
        if (release.wearApkUrl == null) {
            return false
        }
        val watch = watchVersion
        return if (watch != null) {
            UpdateChecker.isNewer(release.tag, watch)
        } else {
            UpdateChecker.isNewerThanInstalled(release.tag)
        }
    }

    private fun pushToWatch() {
        val release = latestRelease ?: return
        if (pushing) {
            return
        }
        pushing = true
        refreshButtons()
        progressBar.visibility = View.VISIBLE
        progressBar.isIndeterminate = true

        lifecycleScope.launch {
            try {
                withTimeout(TimeUnit.MINUTES.toMillis(15)) {
                    WatchApkPusher(applicationContext).pushToWatch(release) { progress ->
                        runOnUiThread { showPushProgress(progress) }
                    }
                }
            } catch (e: WatchApkPusher.NoWatchException) {
                statusText.text = getString(R.string.update_no_watch)
                progressBar.visibility = View.INVISIBLE
            } catch (e: TimeoutCancellationException) {
                statusText.text = getString(R.string.update_push_failed)
                progressBar.visibility = View.INVISIBLE
            } catch (e: Exception) {
                Timber.w(e, "Watch update push failed")
                statusText.text = getString(R.string.update_push_failed)
                progressBar.visibility = View.INVISIBLE
            } finally {
                pushing = false
                refreshButtons()
            }
        }
    }

    private fun showPushProgress(progress: WatchApkPusher.Progress) {
        when (progress) {
            is WatchApkPusher.Progress.Downloading -> {
                progressBar.isIndeterminate = false
                progressBar.progress = progress.percent
                statusText.text = getString(R.string.update_downloading, progress.percent)
            }
            is WatchApkPusher.Progress.Connecting -> {
                progressBar.isIndeterminate = true
                statusText.text = getString(R.string.update_connecting_watch)
            }
            is WatchApkPusher.Progress.Transferring -> {
                progressBar.isIndeterminate = false
                progressBar.progress = progress.percent
                statusText.text = getString(R.string.update_transferring, progress.percent)
            }
            is WatchApkPusher.Progress.AwaitingWatchConfirmation -> {
                progressBar.visibility = View.INVISIBLE
                statusText.text = getString(R.string.update_confirm_on_watch)
            }
        }
    }

    private fun refreshButtons() {
        openReleaseButton.isEnabled = !pushing && latestRelease != null
        updateWatchButton.isEnabled = !pushing &&
                latestRelease?.let { watchNeedsUpdate(it) } == true
    }
}
