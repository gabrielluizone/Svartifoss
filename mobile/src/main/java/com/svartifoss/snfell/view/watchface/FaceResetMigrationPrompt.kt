package com.svartifoss.snfell.view.watchface

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceManager
import com.svartifoss.snfell.R
import com.svartifoss.snfell.view.LyraAccent
import com.svartifoss.snfell.view.applyLyraDialogStyling
import timber.log.Timber

/**
 * One-time nudge for a user updating from a pre-3.0 build, the release that introduced per-face
 * appearance scoping ([com.svartifoss.snfell.common.FaceScopedPreferences]). Before that, every
 * watch face shared one global value per appearance key; after an in-place update that legacy
 * value keeps bleeding into whichever per-face defaults it happens to match, which reads as a
 * wrong/inconsistent look on faces the user never explicitly touched - "Reset all faces to
 * default" (or a full data clear) is the only way to actually land on the new per-face defaults.
 *
 * This preference key is itself new in the build that introduces it, so it can never have been
 * written by an older version - reading it can't distinguish "fresh 3.0 install" from "upgraded
 * from 2.x" on the very first run that matters. Instead this uses the OS-tracked
 * PackageInfo.firstInstallTime vs lastUpdateTime: identical on a fresh install, lastUpdateTime
 * later than firstInstallTime on any device that has installed at least one update before this
 * one - which is true for every pre-3.0 upgrader and false for every fresh install, regardless of
 * this feature's own history. [PREF_ALREADY_HANDLED] then makes the whole check (and the dialog)
 * fire at most once ever per device, on the first run of whichever build first ships this code.
 */
object FaceResetMigrationPrompt {

    private const val PREF_ALREADY_HANDLED = "face_reset_prompt_handled"

    /** Buttons stay disabled this long after the dialog appears, so the recommendation can't be
     *  tapped through on reflex before it's actually read. */
    private const val READ_DELAY_MS = 3000L

    /** Call once from MainActivity.onCreate. */
    fun maybeShow(activity: Activity) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
        if (prefs.getBoolean(PREF_ALREADY_HANDLED, false)) {
            return
        }
        prefs.edit().putBoolean(PREF_ALREADY_HANDLED, true).apply()

        if (!isUpgradeFromOlderInstall(activity)) {
            return
        }

        showDialog(activity, prefs)
    }

    private fun isUpgradeFromOlderInstall(activity: Activity): Boolean {
        val info = try {
            activity.packageManager.getPackageInfo(activity.packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            Timber.w(e, "Could not read own package info")
            return false
        }
        return info.lastUpdateTime > info.firstInstallTime
    }

    private fun showDialog(activity: Activity, prefs: SharedPreferences) {
        val dialog = AlertDialog.Builder(activity)
                .setTitle(R.string.face_reset_migration_title)
                .setMessage(R.string.face_reset_migration_message)
                .setNegativeButton(R.string.face_reset_migration_clear_data_button) { _, _ ->
                    activity.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                    .setData(Uri.parse("package:${activity.packageName}"))
                    )
                }
                .setPositiveButton(R.string.face_reset_migration_reset_button) { _, _ ->
                    WatchFacePrefsFragment.resetAllFaces(activity, prefs)
                    Toast.makeText(activity, R.string.reset_all_faces_done, Toast.LENGTH_SHORT).show()
                }
                .show()

        dialog.applyLyraDialogStyling(accent = LyraAccent.resolve(activity))
        armReadDelay(dialog)
    }

    /** Disables both action buttons, showing a countdown in their label, until [READ_DELAY_MS] has
     *  passed - guards against the dialog being dismissed by reflex before it's read. */
    private fun armReadDelay(dialog: AlertDialog) {
        val positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        val negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
        val positiveLabel = positive.text
        val negativeLabel = negative.text
        val handler = Handler(Looper.getMainLooper())

        positive.isEnabled = false
        negative.isEnabled = false

        var remainingSeconds = (READ_DELAY_MS / 1000L).toInt()
        val tick = object : Runnable {
            override fun run() {
                remainingSeconds--
                if (remainingSeconds <= 0) {
                    positive.text = positiveLabel
                    negative.text = negativeLabel
                    positive.isEnabled = true
                    negative.isEnabled = true
                } else {
                    positive.text = "$positiveLabel ($remainingSeconds)"
                    negative.text = "$negativeLabel ($remainingSeconds)"
                    handler.postDelayed(this, 1000L)
                }
            }
        }
        positive.text = "$positiveLabel ($remainingSeconds)"
        negative.text = "$negativeLabel ($remainingSeconds)"
        handler.postDelayed(tick, 1000L)

        dialog.setOnDismissListener { handler.removeCallbacks(tick) }
    }
}
