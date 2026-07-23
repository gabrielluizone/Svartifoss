package com.svartifoss.snfell.view.watchface

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceManager
import com.svartifoss.snfell.BuildConfig
import com.svartifoss.snfell.R

/**
 * One-time nudge for a user updating from a build older than [FIRST_FACE_SCOPED_VERSION_CODE] -
 * the release that introduced per-face appearance scoping ([com.svartifoss.snfell.common.FaceScopedPreferences]).
 * Before that, every watch face shared one global value per appearance key; after an in-place
 * update that legacy value keeps bleeding into whichever per-face defaults it happens to match,
 * which reads as a wrong/inconsistent look on faces the user never explicitly touched - "Reset all
 * faces to default" (or a full data clear) is the only way to actually land on the new per-face
 * defaults. Never shown on a fresh install (no prior version recorded) and never shown twice.
 */
object FaceResetMigrationPrompt {

    // Mobile versionCode 56 == 3.0.
    private const val FIRST_FACE_SCOPED_VERSION_CODE = 56
    private const val PREF_LAST_SEEN_VERSION_CODE = "face_reset_prompt_last_seen_version_code"

    /** Buttons stay disabled this long after the dialog appears, so the recommendation can't be
     *  tapped through on reflex before it's actually read. */
    private const val READ_DELAY_MS = 3000L

    /** Call once from MainActivity.onCreate. */
    fun maybeShow(activity: Activity) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
        val lastSeenVersionCode = prefs.getLong(PREF_LAST_SEEN_VERSION_CODE, -1L)
        prefs.edit().putLong(PREF_LAST_SEEN_VERSION_CODE, BuildConfig.VERSION_CODE.toLong()).apply()

        if (lastSeenVersionCode !in 0 until FIRST_FACE_SCOPED_VERSION_CODE) {
            return
        }

        showDialog(activity, prefs)
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
