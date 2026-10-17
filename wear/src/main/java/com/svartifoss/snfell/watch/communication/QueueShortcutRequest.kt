package com.svartifoss.snfell.watch.communication

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.svartifoss.snfell.common.QueueShortcutImport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import timber.log.Timber

/** Opens the phone's shortcut editor with a queue row already filled in. */
object QueueShortcutRequest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * The queue screen can finish immediately after this call, so the remote launch is process
     * scoped rather than tied to QueueActivity's lifecycle.
     */
    fun openOnPhone(
            context: Context,
            name: String,
            subtitle: String?,
            link: String,
            onOpened: () -> Unit = {}
    ) {
        val appContext = context.applicationContext
        val intent = Intent(QueueShortcutImport.ACTION).apply {
            component = ComponentName(appContext.packageName, QueueShortcutImport.PHONE_ACTIVITY)
            putExtra(QueueShortcutImport.EXTRA_NAME, name)
            putExtra(QueueShortcutImport.EXTRA_SUBTITLE, subtitle)
            putExtra(QueueShortcutImport.EXTRA_LINK, link)
        }
        scope.launch {
            try {
                RemoteActivityHelper(
                        appContext,
                        ContextCompat.getMainExecutor(appContext)
                ).startRemoteActivity(intent).await()
                onOpened()
            } catch (e: Exception) {
                Timber.w(e, "Could not open the phone shortcut editor")
            }
        }
    }
}
