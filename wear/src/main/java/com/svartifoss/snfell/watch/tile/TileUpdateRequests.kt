package com.svartifoss.snfell.watch.tile

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.RemoteException
import android.provider.Settings
import androidx.wear.tiles.TileService
import androidx.wear.tiles.TileUpdateRequestData
import androidx.wear.tiles.TileUpdateRequesterService
import timber.log.Timber

/**
 * Asks the watch's system UI to refresh one of our Tiles, in place of
 * `TileService.getUpdater(context).requestUpdate(...)`, which was crashing the watch app.
 *
 * What the library does (read off androidx.wear.tiles 1.6.1 with javap): every `getUpdater` call
 * builds a new `SysUiTileUpdateRequester` that keeps the context it was handed and owns a new
 * single-thread executor. Each request binds to the system's `BIND_UPDATE_REQUESTER` service and,
 * once connected, unbinds from that executor's thread. Crashlytics showed what that leads to, on
 * 4.0 through 4.4:
 *  - the usual caller was `MusicStateListenerService`, which the system destroys as soon as
 *    `onDataChanged` returns, and destroying it takes its bindings down with it. The library's
 *    later unbind then threw "Service not registered" on the library's own thread, where nothing
 *    could catch it, and the uncaught exception ended the watch process. This was the watch's most
 *    frequent report.
 *  - every request started a thread that nothing shut down, so on a busy evening the process ran
 *    out of them ("OutOfMemoryError: pthread_create", thrown from that same `onServiceConnected`).
 *  - a failed bind left the requester's "bind in progress" flag set for good, which is why simply
 *    sharing one library instance would trade the crash for Tiles that stop updating.
 *
 * This keeps the protocol (the same intent, and the same AIDL call with an empty request at
 * version 1) and changes only the lifecycle. It always uses the application context and does
 * everything on the main thread. One connection at a time carries every Tile asked for meanwhile
 * ([TileUpdateQueue]), and each connection is released exactly once, with a missing registration
 * tolerated. A connection that never arrives is given up after [CONNECT_TIMEOUT_MS], so the next
 * request can try again. The library's second requester, a broadcast to Android Studio's Tiles
 * viewer, is a development tool and is left out. `TileUpdateRequestsContractTest` pins that
 * nothing calls `getUpdater` again.
 */
@SuppressLint("RestrictedApi") // The AIDL contract with system UI; it has to stay stable across apps.
object TileUpdateRequests {
    private const val ACTION_BIND_UPDATE_REQUESTER = "androidx.wear.tiles.action.BIND_UPDATE_REQUESTER"
    private const val SYSUI_SETTINGS_KEY = "clockwork_sysui_package"
    private const val DEFAULT_SYSUI_PACKAGE = "com.google.android.wearable.app"
    private const val CATEGORY_HOME_MAIN = "android.intent.category.HOME_MAIN"
    private const val CONNECT_TIMEOUT_MS = 10_000L
    private val EMPTY_REQUEST = ByteArray(0)

    private val mainHandler = Handler(Looper.getMainLooper())
    private val queue = TileUpdateQueue<ComponentName>()
    private var connection: UpdateConnection? = null

    /** Asks for [service]'s Tile to be refreshed. Safe from any thread; never throws. */
    fun request(context: Context, service: Class<out TileService>) {
        val app = context.applicationContext
        val tile = ComponentName(app, service)
        if (Looper.myLooper() == Looper.getMainLooper()) {
            enqueue(app, tile)
        } else {
            mainHandler.post { enqueue(app, tile) }
        }
    }

    private fun enqueue(app: Context, tile: ComponentName) {
        if (!queue.add(tile)) return

        val intent = bindIntent(app)
        if (intent == null) {
            queue.release()
            return
        }
        val pending = UpdateConnection(app)
        connection = pending
        val bound = try {
            app.bindService(intent, pending, Context.BIND_AUTO_CREATE)
        } catch (e: RuntimeException) {
            // SecurityException on watches that do not let us reach their system UI.
            Timber.w("Could not bind the Tile update service: %s", e.message)
            false
        }
        if (bound) {
            mainHandler.postDelayed(pending.timeout, CONNECT_TIMEOUT_MS)
        } else {
            pending.finish()
        }
    }

    private fun bindIntent(app: Context): Intent? {
        val intent = Intent(ACTION_BIND_UPDATE_REQUESTER).setPackage(sysUiPackage(app))
        val service = try {
            app.packageManager.queryIntentServices(intent, 0).firstOrNull()?.serviceInfo
        } catch (e: RuntimeException) {
            null
        }
        if (service == null) {
            Timber.w("No Tile update service in %s", intent.`package`)
            return null
        }
        return intent.setClassName(service.packageName, service.name)
    }

    /** Where the update service lives, decided as the library decides it. */
    private fun sysUiPackage(app: Context): String {
        if (Build.VERSION.SDK_INT == 34 && app.applicationInfo.targetSdkVersion > 34) {
            return homePackage(app) ?: DEFAULT_SYSUI_PACKAGE
        }
        val configured = try {
            Settings.Global.getString(app.contentResolver, SYSUI_SETTINGS_KEY)
        } catch (e: SecurityException) {
            // The library let this escape, which ended the request; the home screen is the
            // system UI on a watch, so ask it instead.
            return homePackage(app) ?: DEFAULT_SYSUI_PACKAGE
        }
        return configured?.takeIf { it.isNotEmpty() } ?: DEFAULT_SYSUI_PACKAGE
    }

    private fun homePackage(app: Context): String? = try {
        app.packageManager.resolveActivity(
                Intent(Intent.ACTION_MAIN).addCategory(CATEGORY_HOME_MAIN),
                PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    } catch (e: RuntimeException) {
        null
    }

    /** One bind. Every callback and [finish] run on the main thread. */
    private class UpdateConnection(private val app: Context) : ServiceConnection {
        private var released = false

        val timeout = Runnable {
            Timber.w("Tile update service did not connect")
            finish()
        }

        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (released) return
            val service = binder?.let { TileUpdateRequesterService.Stub.asInterface(it) }
            if (service != null) {
                for (tile in queue.takeForSending()) {
                    try {
                        service.requestUpdate(tile, TileUpdateRequestData(
                                EMPTY_REQUEST, TileUpdateRequestData.VERSION_1))
                    } catch (e: RemoteException) {
                        Timber.w("Tile update request failed: %s", e.message)
                    } catch (e: RuntimeException) {
                        Timber.w("Tile update request failed: %s", e.message)
                    }
                }
            }
            finish()
        }

        override fun onServiceDisconnected(name: ComponentName?) = Unit

        override fun onBindingDied(name: ComponentName?) = finish()

        override fun onNullBinding(name: ComponentName?) = finish()

        /** Ends this bind. Idempotent, and tolerant of a binding the system already removed. */
        fun finish() {
            if (released) return
            released = true
            mainHandler.removeCallbacks(timeout)
            if (connection === this) {
                connection = null
                queue.release()
            }
            try {
                app.unbindService(this)
            } catch (e: IllegalArgumentException) {
                // Already unregistered (the bind failed, or the system tore it down first).
            }
        }
    }
}
