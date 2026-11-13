package com.svartifoss.snfell.watch.communication

import android.content.Context
import android.os.PowerManager
import com.svartifoss.snfell.watch.util.FrontActivity
import com.svartifoss.snfell.watch.view.shortcut.showContinueOnPhone
import timber.log.Timber

/**
 * Says on the wrist that a shortcut has moved to the phone's screen.
 *
 * The phone answers a pick with a link to open only when it could not start the music silently,
 * and what follows needs the phone in hand: it has to be unlocked for the link to open at all, and
 * an app that only shows the content (Spotify on a free account shows the playlist and plays
 * nothing) has to be pressed play there. Until now the watch opened the link and said nothing, so
 * a shortcut that ended on a locked phone's screen looked exactly like one that did nothing.
 *
 * It is the system's own "continue on your phone" confirmation, the one "Open on phone" already
 * shows, with the same words - so it costs no new translation.
 */
internal object OpenOnPhoneNotice {

    /**
     * Whether the confirmation is shown for a link the phone asked the watch to open.
     *
     * Not for "Open on phone" ([openOnly]): the screen that sent it has already shown this very
     * confirmation, and its link arriving is not news. Not without one of this app's screens in
     * front ([hasFrontScreen]): a pick from the Tile leaves nothing of the app on screen, and a
     * confirmation started from there would bring the player forward over the Tile. And not on a
     * dimmed watch ([interactive]), where it would light the screen for a wrist already lowered.
     */
    fun shouldShow(openOnly: Boolean, hasFrontScreen: Boolean, interactive: Boolean): Boolean =
            !openOnly && hasFrontScreen && interactive

    fun showIfWanted(context: Context, openOnly: Boolean) {
        val front = FrontActivity.current()
        val interactive = context.getSystemService(PowerManager::class.java)?.isInteractive ?: true
        if (!shouldShow(openOnly, hasFrontScreen = front != null, interactive = interactive)) return
        try {
            showContinueOnPhone(front ?: return)
        } catch (e: RuntimeException) {
            // The screen went away between the check and the start. The link is still opened.
            Timber.w(e, "Could not show the continue-on-phone confirmation")
        }
    }
}
