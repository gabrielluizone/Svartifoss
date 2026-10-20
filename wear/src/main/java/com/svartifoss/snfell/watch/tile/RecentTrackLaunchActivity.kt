package com.svartifoss.snfell.watch.tile

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.google.android.gms.wearable.Wearable
import com.matejdro.wearutils.messages.sendMessageToNearestClient
import com.svartifoss.snfell.common.CommPaths
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Invisible bridge for a [RecentTracksTileService] row: a Tile click can only start an activity, so
 * this is where it becomes the phone playing that track again - by searching for its artist and
 * title, the way the queue's history fallback replays one (a history entry carries nothing else).
 *
 * Shows nothing and finishes at once; the send outlives it on a process scope.
 */
class RecentTrackLaunchActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val title = intent?.getStringExtra(EXTRA_TITLE).orEmpty()
        val artist = intent?.getStringExtra(EXTRA_ARTIST).orEmpty()
        val query = "$artist $title".trim()
        if (savedInstanceState == null && query.isNotEmpty()) {
            val app = applicationContext
            SCOPE.launch {
                try {
                    Wearable.getMessageClient(app).sendMessageToNearestClient(
                            Wearable.getNodeClient(app),
                            CommPaths.MESSAGE_PLAY_FROM_SEARCH,
                            query.toByteArray(Charsets.UTF_8))
                } catch (e: Exception) {
                    Timber.w(e, "Could not ask the phone to replay a recent track")
                }
            }
        }
        finish()
    }

    companion object {
        const val EXTRA_TITLE = "com.svartifoss.snfell.watch.tile.EXTRA_RECENT_TITLE"
        const val EXTRA_ARTIST = "com.svartifoss.snfell.watch.tile.EXTRA_RECENT_ARTIST"

        private val SCOPE = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
