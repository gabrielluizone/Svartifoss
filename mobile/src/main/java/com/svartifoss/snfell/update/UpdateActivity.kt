package com.svartifoss.snfell.update

import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.google.android.material.button.MaterialButton
import com.google.android.gms.wearable.Wearable
import com.svartifoss.snfell.BuildConfig
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.CommPaths
import com.svartifoss.snfell.proto.WatchInfo
import com.svartifoss.snfell.util.SimpleMarkdown
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

    private lateinit var stateHeadline: TextView
    private lateinit var stateSub: TextView
    private lateinit var statusIcon: ImageView
    private lateinit var statusBadge: View
    private lateinit var phoneVersionText: TextView
    private lateinit var watchVersionText: TextView
    private lateinit var whatsNewCard: View
    private lateinit var releaseNotesText: TextView
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var openReleaseButton: MaterialButton
    private lateinit var updateWatchButton: MaterialButton
    private lateinit var updatePhoneButton: MaterialButton

    private var latestRelease: UpdateChecker.ReleaseInfo? = null
    private var watchVersion: String? = null
    private var pushing = false
    private var installing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_update)

        stateHeadline = findViewById(R.id.text_state_headline)
        stateSub = findViewById(R.id.text_state_sub)
        statusIcon = findViewById(R.id.status_icon)
        statusBadge = findViewById(R.id.status_badge)
        phoneVersionText = findViewById(R.id.text_phone_version)
        watchVersionText = findViewById(R.id.text_watch_version)
        whatsNewCard = findViewById(R.id.card_whats_new)
        releaseNotesText = findViewById(R.id.text_release_notes)
        statusText = findViewById(R.id.text_status)
        progressBar = findViewById(R.id.progress)
        openReleaseButton = findViewById(R.id.button_open_release)
        updateWatchButton = findViewById(R.id.button_update_watch)
        updatePhoneButton = findViewById(R.id.button_update_phone)

        val accent = LyraAccent.resolve(this)

        // Hero badge: a soft wash of the (runtime) accent behind an accent-tinted glyph.
        statusBadge.backgroundTintList =
                ColorStateList.valueOf((accent and 0x00FFFFFF) or (0x24 shl 24))
        statusIcon.imageTintList = ColorStateList.valueOf(accent)

        // "Open release page" is the tertiary action - outlined, accent-tinted content.
        openReleaseButton.setTextColor(accent)
        openReleaseButton.iconTint = ColorStateList.valueOf(accent)

        // The two "Update ..." buttons are primary actions - filled with the accent, grey when
        // disabled (shared state list so a runtime accent applies to both).
        val disabledFill = ContextCompat.getColor(this, R.color.lyra_divider)
        val filledTint = ColorStateList(
                arrayOf(
                        intArrayOf(android.R.attr.state_enabled),
                        intArrayOf(-android.R.attr.state_enabled)
                ),
                intArrayOf(accent, disabledFill)
        )
        updateWatchButton.backgroundTintList = filledTint
        updatePhoneButton.backgroundTintList = filledTint

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
        updatePhoneButton.setOnClickListener { installPhone() }

        phoneVersionText.text = BuildConfig.VERSION_NAME
        watchVersionText.text = getString(R.string.update_watch_version_unknown)
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

            watchVersionText.text =
                    watchVersion ?: getString(R.string.update_watch_version_unknown)
            refreshButtons()
        }
    }

    private fun loadLatestRelease() {
        showState(R.drawable.ic_autorenew, getString(R.string.update_state_checking), null)
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
                showState(
                        R.drawable.ic_autorenew,
                        getString(R.string.update_state_failed),
                        getString(R.string.update_state_failed_sub)
                )
                return@launch
            }

            latestRelease = release
            showReleaseNotes(release)

            if (UpdateChecker.isNewerThanInstalled(release.tag)) {
                UpdateChecker.rememberKnownRelease(this@UpdateActivity, release.tag)
                var sub = getString(R.string.update_state_available_sub, release.title, release.tag)
                if (release.isPrerelease) {
                    sub += " " + getString(R.string.update_prerelease_marker)
                }
                showState(R.drawable.ic_download, getString(R.string.update_state_available), sub)
                setStatus(
                        if (release.wearApkUrl == null) getString(R.string.update_no_wear_asset)
                        else getString(R.string.update_watch_hint)
                )
            } else {
                showState(
                        R.drawable.ic_check_circle,
                        getString(R.string.update_state_current),
                        getString(R.string.update_state_current_sub, release.tag)
                )
                setStatus(
                        if (watchNeedsUpdate(release)) getString(R.string.update_watch_behind, watchVersion)
                        else null
                )
            }
            refreshButtons()
        }
    }

    private fun showState(@DrawableRes iconRes: Int, headline: String, sub: String?) {
        statusIcon.setImageResource(iconRes)
        stateHeadline.text = headline
        if (sub.isNullOrEmpty()) {
            stateSub.visibility = View.GONE
        } else {
            stateSub.text = sub
            stateSub.visibility = View.VISIBLE
        }
    }

    private fun showReleaseNotes(release: UpdateChecker.ReleaseInfo) {
        if (release.body.isBlank()) {
            whatsNewCard.visibility = View.GONE
        } else {
            releaseNotesText.movementMethod = LinkMovementMethod.getInstance()
            releaseNotesText.text = SimpleMarkdown.render(release.body, releaseNotesText.currentTextColor)
            whatsNewCard.visibility = View.VISIBLE
        }
    }

    private fun setStatus(text: CharSequence?) {
        if (text.isNullOrEmpty()) {
            statusText.text = ""
            statusText.visibility = View.GONE
        } else {
            statusText.text = text
            statusText.visibility = View.VISIBLE
        }
    }

    /** The phone can self-update when the release is newer than the installed build and ships a
     *  phone APK asset to download. */
    private fun phoneNeedsUpdate(release: UpdateChecker.ReleaseInfo): Boolean =
            release.mobileApkUrl != null && UpdateChecker.isNewerThanInstalled(release.tag)

    private fun installPhone() {
        val release = latestRelease ?: return
        if (installing || pushing) {
            return
        }
        installing = true
        refreshButtons()
        progressBar.visibility = View.VISIBLE
        progressBar.isIndeterminate = true

        lifecycleScope.launch {
            try {
                PhoneApkInstaller(applicationContext).installLatest(release) { progress ->
                    runOnUiThread { showPhoneProgress(progress) }
                }
            } catch (e: Exception) {
                Timber.w(e, "Phone update failed")
                setStatus(getString(R.string.update_phone_failed))
            } finally {
                installing = false
                progressBar.visibility = View.INVISIBLE
                refreshButtons()
            }
        }
    }

    private fun showPhoneProgress(progress: PhoneApkInstaller.Progress) {
        when (progress) {
            is PhoneApkInstaller.Progress.Downloading -> {
                progressBar.isIndeterminate = false
                progressBar.progress = progress.percent
                setStatus(getString(R.string.update_downloading, progress.percent))
            }
            PhoneApkInstaller.Progress.Installing -> {
                progressBar.isIndeterminate = true
                setStatus(getString(R.string.update_installing))
            }
            PhoneApkInstaller.Progress.NeedsPermission -> {
                progressBar.visibility = View.INVISIBLE
                setStatus(getString(R.string.update_install_permission_needed))
            }
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
        if (pushing || installing) {
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
                setStatus(getString(R.string.update_no_watch))
                progressBar.visibility = View.INVISIBLE
            } catch (e: TimeoutCancellationException) {
                setStatus(getString(R.string.update_push_failed))
                progressBar.visibility = View.INVISIBLE
            } catch (e: Exception) {
                Timber.w(e, "Watch update push failed")
                setStatus(getString(R.string.update_push_failed))
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
                setStatus(getString(R.string.update_downloading, progress.percent))
            }
            is WatchApkPusher.Progress.Connecting -> {
                progressBar.isIndeterminate = true
                setStatus(getString(R.string.update_connecting_watch))
            }
            is WatchApkPusher.Progress.Transferring -> {
                progressBar.isIndeterminate = false
                progressBar.progress = progress.percent
                setStatus(getString(R.string.update_transferring, progress.percent))
            }
            is WatchApkPusher.Progress.AwaitingWatchConfirmation -> {
                progressBar.visibility = View.INVISIBLE
                setStatus(getString(R.string.update_confirm_on_watch))
            }
        }
    }

    private fun refreshButtons() {
        val busy = pushing || installing
        openReleaseButton.isEnabled = !busy && latestRelease != null

        val phoneUpdatable = latestRelease?.let { phoneNeedsUpdate(it) } == true
        updatePhoneButton.visibility = if (phoneUpdatable) View.VISIBLE else View.GONE
        updatePhoneButton.isEnabled = !busy && phoneUpdatable

        updateWatchButton.isEnabled = !busy &&
                latestRelease?.let { watchNeedsUpdate(it) } == true
    }
}
