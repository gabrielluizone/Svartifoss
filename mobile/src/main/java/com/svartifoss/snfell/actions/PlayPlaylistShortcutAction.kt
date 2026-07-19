package com.svartifoss.snfell.actions

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.PersistableBundle
import androidx.appcompat.content.res.AppCompatResources
import com.svartifoss.snfell.music.MusicService
import com.svartifoss.snfell.music.StreamingShortcutLinks
import javax.inject.Inject

/**
 * Opens one specific saved playlist shortcut (see
 * [com.svartifoss.snfell.music.PlaylistShortcutStorage]) directly - unlike
 * [OpenPlaylistShortcutsAction], which shows the whole list on the watch for the user to pick
 * from. Because this is a regular parameterized [PhoneAction] (the chosen playlist's name and
 * link are baked into the action bundle, Tasker-task style), it can be assigned to anything:
 * a quadrant, a swipe, a stem button, an on-screen mini button, or the actions menu.
 *
 * Created by [PlaylistShortcutPickerAction]; never appears in the picker list itself.
 */
class PlayPlaylistShortcutAction : SelectableAction {
    companion object {
        const val KEY_PLAYLIST_NAME = "PLAYLIST_NAME"
        const val KEY_PLAYLIST_LINK = "PLAYLIST_LINK"
    }

    val playlistName: String
    val link: String

    constructor(context: Context, playlistName: String, link: String) : super(context) {
        this.playlistName = playlistName
        this.link = link
    }

    constructor(context: Context, bundle: PersistableBundle) : super(context, bundle) {
        this.playlistName = bundle.getString(KEY_PLAYLIST_NAME)!!
        this.link = bundle.getString(KEY_PLAYLIST_LINK)!!
    }

    override fun writeToBundle(bundle: PersistableBundle) {
        super.writeToBundle(bundle)

        bundle.putString(KEY_PLAYLIST_NAME, playlistName)
        bundle.putString(KEY_PLAYLIST_LINK, link)
    }

    override fun retrieveTitle(): String = playlistName

    /**
     * A saved shortcut represents a concrete destination, so its icon should identify that
     * destination in Pick action (and on the watch after it is assigned), rather than making
     * every playlist/track look like the same generic playlist command.
     *
     * Supported services use their installed launcher icon. For a custom scheme/provider, use
     * Android's default handler when one exists. The monochrome playlist glyph remains the safe
     * fallback for an app that is not installed or a link without a default handler.
     */
    private val destinationAppIcon: Drawable? by lazy {
        val service = StreamingShortcutLinks.detect(link)
        val knownPackage = service.packageName
        if (knownPackage != null) {
            applicationIcon(knownPackage)
        } else {
            resolveCustomLinkHandlerIcon()
        }
    }

    override val defaultIconTintable: Boolean
        get() = destinationAppIcon == null

    override val defaultIcon: Drawable
        get() = destinationAppIcon ?: AppCompatResources.getDrawable(
                context,
                com.svartifoss.snfell.common.R.drawable.action_open_playlist
        )!!

    override val remoteUri: String
        get() {
            val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
            val openMode = prefs.getString(StreamingShortcutLinks.OPEN_MODE_KEY, null)
                    ?: if (prefs.getBoolean(
                                    StreamingShortcutLinks.PREFER_INSTALLED_APP_KEY,
                                    true)) {
                        StreamingShortcutLinks.OPEN_MODE_APP
                    } else {
                        StreamingShortcutLinks.OPEN_MODE_DEFAULT
                    }

            val service = StreamingShortcutLinks.detect(link)
            val targetPackage = service.packageName?.takeIf { packageName ->
                openMode == StreamingShortcutLinks.OPEN_MODE_APP && isPackageInstalled(packageName)
            }

            val primaryLink = if (targetPackage != null) {
                StreamingShortcutLinks.forInstalledApp(link)
            } else {
                StreamingShortcutLinks.forBrowser(link)
            }

            return if (targetPackage != null) {
                "$targetPackage|$primaryLink"
            } else {
                primaryLink
            }
        }

    private fun isPackageInstalled(packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun applicationIcon(packageName: String): Drawable? = try {
        context.packageManager.getApplicationIcon(packageName)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    } catch (_: SecurityException) {
        null
    }

    private fun resolveCustomLinkHandlerIcon(): Drawable? {
        val canonicalLink = StreamingShortcutLinks.canonicalize(link)
        if (!StreamingShortcutLinks.isSafeLink(canonicalLink)) return null

        val resolvedPackage = try {
            context.packageManager.resolveActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(canonicalLink)),
                    PackageManager.MATCH_DEFAULT_ONLY
            )?.activityInfo?.packageName
        } catch (_: RuntimeException) {
            null
        }
        return resolvedPackage?.let(::applicationIcon)
    }

    override fun isEqualToAction(other: PhoneAction): Boolean {
        other as PlayPlaylistShortcutAction
        return super.isEqualToAction(other) &&
                this.playlistName == other.playlistName &&
                this.link == other.link
    }

    class Handler @Inject constructor(private val service: MusicService) : ActionHandler<PlayPlaylistShortcutAction> {
        override suspend fun handleAction(action: PlayPlaylistShortcutAction) {
            service.playDeepLink(action.link)
        }
    }
}
