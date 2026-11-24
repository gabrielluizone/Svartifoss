package com.svartifoss.snfell.whatsnew

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.svartifoss.snfell.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Opens [WhatsNewActivity] once, the first time the app opens after an update - for builds that
 * have no updater screen of their own (the Play build; see [WhatsNewPolicy] for why).
 * `MainActivity` calls [attach] only when `UpdateGateway.SUPPORTS_SELF_UPDATE` is false, because the
 * sideloaded build already opens its Updates screen after an update and a second greeting would be
 * noise.
 *
 * The one thing kept is the version code last seen, in a preferences file of its own: not the
 * default file, which is mirrored to the watch, and not something a backup should carry over to a
 * different install. Nothing leaves the phone.
 *
 * ## Writing the notes for a release
 *
 * The notes are [items], one string resource each (`whats_new_item_*`, translated into every
 * locale - `WhatsNewContractTest` fails on a missing one) with the glyph its row wears, and
 * [FIRST_RELEASE_WITH_NOTES], the first version code that carries them. For the next release
 * replace the items and raise that code to the new release's: an update from an older build then
 * shows the new list, and a user already on the previous release sees only the new one. Keep it to
 * a handful of rows; it is a greeting, not a changelog.
 */
object WhatsNewPrompt {

    /**
     * The first build that carries the notes below. An update from anything older shows them; an
     * update from this one onwards, or a fresh install, does not.
     */
    const val FIRST_RELEASE_WITH_NOTES = 95L

    private const val PREFS = "whats_new"
    private const val KEY_LAST_SEEN_VERSION_CODE = "last_seen_version_code"

    /**
     * How long the screen has to stay put before the greeting opens over it, so it does not land on
     * the first frame, mid-tap.
     */
    private const val SETTLE_MS = 1_200L

    /** One row of the screen: what changed, already in the app's language, and its glyph. */
    internal class Item(@DrawableRes val icon: Int, val text: String)

    /**
     * Wires the prompt to [activity] for as long as it lives. Call once, from `onCreate`. Every
     * return to the screen decides again, which is cheap once the version has been recorded.
     */
    fun attach(activity: AppCompatActivity) {
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                consider(activity)
            }
        }
    }

    private suspend fun consider(activity: AppCompatActivity) {
        val info = packageInfo(activity) ?: return
        val currentVersionCode = PackageInfoCompat.getLongVersionCode(info)
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        val action = WhatsNewPolicy.decide(
                lastSeenVersionCode = prefs.getLong(KEY_LAST_SEEN_VERSION_CODE, WhatsNewPolicy.NEVER_RECORDED),
                currentVersionCode = currentVersionCode,
                firstReleaseWithNotes = FIRST_RELEASE_WITH_NOTES,
                isUpdateInstall = info.lastUpdateTime > info.firstInstallTime)

        when (action) {
            WhatsNewPolicy.Action.NOTHING -> return
            WhatsNewPolicy.Action.REMEMBER -> remember(prefs, currentVersionCode)
            WhatsNewPolicy.Action.SHOW -> {
                delay(SETTLE_MS)
                // A dialog or the notification shade takes the window's focus, which makes this
                // one check cover the notification-access prompt and the face-reset
                // recommendation without either knowing about this. Nothing is recorded yet, so
                // the next opening tries again.
                if (!isShowable(activity)) return
                // Recorded before it is shown: once means once, even if the app is killed while
                // the screen is up.
                remember(prefs, currentVersionCode)
                Timber.i("Showing what's new for version code %d", currentVersionCode)
                activity.startActivity(Intent(activity, WhatsNewActivity::class.java))
            }
        }
    }

    private fun remember(prefs: SharedPreferences, versionCode: Long) {
        prefs.edit().putLong(KEY_LAST_SEEN_VERSION_CODE, versionCode).apply()
    }

    private fun isShowable(activity: AppCompatActivity): Boolean =
            activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && activity.hasWindowFocus()

    private fun packageInfo(context: Context): PackageInfo? = try {
        context.packageManager.getPackageInfo(context.packageName, 0)
    } catch (e: PackageManager.NameNotFoundException) {
        Timber.w(e, "Could not read own package info")
        null
    }

    /** The notes the screen lists, in order, already in the app's language. */
    internal fun items(context: Context): List<Item> = listOf(
            Item(R.drawable.ic_watch,
                    context.getString(R.string.whats_new_item_home, context.getString(R.string.nav_watch_face))),
            Item(R.drawable.ic_palette, context.getString(R.string.whats_new_item_pickers)),
            Item(R.drawable.ic_bell,
                    context.getString(
                            R.string.whats_new_item_notification,
                            // Queue since 4.4.1, where it took the Themes button's place.
                            context.getString(R.string.quick_tool_queue))),
            Item(R.drawable.ic_playlist_play, context.getString(R.string.whats_new_item_songs)),
            Item(R.drawable.ic_apps,
                    context.getString(
                            R.string.whats_new_item_icons,
                            context.getString(R.string.app_icon_style_coral),
                            context.getString(R.string.app_icon_style_ocean),
                            context.getString(R.string.app_icon_style_violet))),
            Item(R.drawable.ic_bolt, context.getString(R.string.whats_new_item_quick_panel)),
            Item(R.drawable.ic_build, context.getString(R.string.whats_new_item_fixes)))
}
