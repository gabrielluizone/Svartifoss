package com.svartifoss.snfell.watch.communication

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.flowWithLifecycle
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Keeps [WatchMusicService]'s "UI open" flow collected while the bound activity is alive, which
 * keeps the service (and with it the phone connection) running while that screen is in the
 * foreground. Every full-screen activity binds with this: since the queue/menu windows became
 * opaque (for scroll fluidity), MainActivity underneath them is fully covered and stopped, so it
 * can no longer be the one holding the service for everyone.
 */
class UiOpenServiceConnection(private val lifecycle: Lifecycle) : ServiceConnection {
    override fun onServiceConnected(name: ComponentName?, service: IBinder) {
        service as WatchMusicService.Binder

        lifecycle.coroutineScope.launch {
            service.uiOpenFlow.flowWithLifecycle(lifecycle).collect()
        }
    }

    override fun onServiceDisconnected(name: ComponentName?) {
    }
}
