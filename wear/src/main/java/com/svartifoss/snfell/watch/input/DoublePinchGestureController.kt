package com.svartifoss.snfell.watch.input

import android.content.Context
import android.os.Build
import android.view.View
import com.google.wear.Sdk
import com.google.wear.input.GestureEvent
import com.google.wear.input.GestureInputManager
import com.svartifoss.snfell.common.HandGestureAvailability
import java.util.function.Consumer
import timber.log.Timber

/**
 * Owns the foreground subscription to Wear OS's primary one-handed gesture.
 *
 * The public surface deliberately contains no Wear-SDK types. A watch running API 36 without the
 * 36.1 gesture feature therefore never loads [Api36PointOne], while a compatible watch registers
 * against [hostView]'s window and automatically stops receiving events whenever that window loses
 * focus. On current Pixel hardware the primary action is a double pinch; other OEMs may map the
 * same semantic action to an equivalent supported hand gesture.
 *
 * **Registration waits for the host view's window.** [setEnabled] is driven by the button config,
 * which arrives on a `LiveData` observer bound to the Activity - so it first runs at `onStart`,
 * and an Activity's decor view is only added to the WindowManager after `onResume`. Registering a
 * window-scoped listener against a view that has no window yet is at best a silent no-op, which
 * is indistinguishable from a watch that does not support the gesture at all. This class therefore
 * separates *wanted* (the user assigned an action) from *registered* (the window exists), and
 * re-registers whenever the host is re-attached.
 *
 * **The gesture can be switched on while the player is open**, in the watch's own Settings, and
 * the system emits nothing for an action that is disabled. Nothing here polls, so [availability]
 * is also watched: a change re-runs registration and reports up through [onAvailabilityChanged],
 * which is what lets the phone's Controls screen stop claiming a gesture that will never fire.
 *
 * Everything it can fail at is logged, because every failure here looks the same from the wrist -
 * nothing happens - and the watch forwards its log to the phone on request.
 */
class DoublePinchGestureController(
    private val context: Context,
    private val hostView: View,
    /** Called on the main thread when [availability] may have changed, never with a value. */
    private val onAvailabilityChanged: () -> Unit = {},
    private val onPrimaryGesture: () -> Unit
) {
    private var registration: Registration? = null
    private var availabilityWatch: Registration? = null

    /** Whether the active Controls state has an assignment for this input. */
    private var wanted = false
    private var disposed = false

    private val attachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) = registerIfPossible()

        override fun onViewDetachedFromWindow(view: View) = unregister()
    }

    init {
        hostView.addOnAttachStateChangeListener(attachListener)
    }

    /** Starts listening only while the active Controls state has an assignment for this input. */
    fun setEnabled(enabled: Boolean) {
        wanted = enabled
        if (enabled) {
            watchAvailability()
            registerIfPossible()
        } else {
            unregister()
        }
    }

    /** Lets Wear OS keep its own gesture-discovery cadence in sync with an action we handled. */
    fun notifyGestureConsumed() {
        try {
            registration?.notifyGestureConsumed()
        } catch (e: Throwable) {
            Timber.w(e, "Double pinch: notifyGestureConsumed failed")
        }
    }

    fun dispose() {
        disposed = true
        hostView.removeOnAttachStateChangeListener(attachListener)
        unregister()
        try {
            availabilityWatch?.unregister()
        } catch (e: Throwable) {
            Timber.w(e, "Double pinch: could not stop watching the gesture settings")
        }
        availabilityWatch = null
    }

    private fun registerIfPossible() {
        if (disposed || !wanted || registration != null) return
        if (!hostView.isAttachedToWindow) {
            // Not an error: the attach listener above re-runs this as soon as the window exists.
            Timber.d("Double pinch: assigned, waiting for the player window")
            return
        }
        val availability = availability(context)
        if (availability == HandGestureAvailability.UNSUPPORTED) {
            Timber.i("Double pinch: this watch does not support the primary hand gesture (sdk %d)",
                    Build.VERSION.SDK_INT)
            return
        }
        registration = try {
            Api36PointOne.register(context, hostView, onPrimaryGesture)
        } catch (e: Throwable) {
            Timber.w(e, "Double pinch: could not subscribe to the primary gesture")
            null
        }
        // "listening but disabled" is a real and common state - the subscription is accepted and
        // the system simply emits nothing until the user turns the gesture on - so it is logged
        // apart from an outright failure rather than folded into one "unavailable".
        Timber.i("Double pinch: %s", when {
            registration == null -> "unavailable"
            availability == HandGestureAvailability.DISABLED ->
                "listening, but the gesture is off in the watch's settings"
            else -> "listening"
        })
    }

    private fun unregister() {
        try {
            registration?.unregister()
        } catch (e: Throwable) {
            Timber.w(e, "Double pinch: unregister failed")
        }
        registration = null
    }

    /**
     * Subscribes once to the watch's own enabled-gesture set.
     *
     * Turning the gesture on in Settings is not something this app is told about any other way,
     * and the whole subsystem is silent by nature - so without this, a user who followed the
     * phone's own advice and enabled the gesture had to guess that the player needed reopening.
     */
    private fun watchAvailability() {
        if (disposed || availabilityWatch != null) return
        if (availability(context) == HandGestureAvailability.UNSUPPORTED) return
        availabilityWatch = try {
            Api36PointOne.watchEnabledActions(context) {
                if (disposed) return@watchEnabledActions
                // The subscription itself may already be live; re-making it is documented as
                // overriding the previous one, and costs nothing when it was never made.
                unregister()
                registerIfPossible()
                onAvailabilityChanged()
            }
        } catch (e: Throwable) {
            Timber.w(e, "Double pinch: could not watch the watch's gesture settings")
            null
        }
    }

    private interface Registration {
        fun unregister()
        fun notifyGestureConsumed() = Unit
    }

    /** Safe to load on base API 36, where the feature check returns false. */
    private object Api36 {
        fun hasGestureDetectionFeature(): Boolean =
            try {
                Sdk.hasApiFeature(Sdk.FEATURE_WEAR_GESTURE_DETECTION)
            } catch (e: Throwable) {
                Timber.w(e, "Double pinch: gesture-detection feature check failed")
                false
            }
    }

    /** Loaded only after [Api36] has confirmed the API-36.1 gesture feature. */
    private object Api36PointOne {
        fun availability(context: Context): HandGestureAvailability {
            val manager = Sdk.getWearManager(context, GestureInputManager::class.java)
            if (manager == null) {
                Timber.i("Double pinch: no GestureInputManager on this watch")
                return HandGestureAvailability.UNSUPPORTED
            }
            if (!manager.isActionSupported(GestureEvent.ACTION_PRIMARY)) {
                Timber.i("Double pinch: the primary action is not supported by this hardware")
                return HandGestureAvailability.UNSUPPORTED
            }
            return if (manager.isActionEnabled(GestureEvent.ACTION_PRIMARY)) {
                HandGestureAvailability.READY
            } else {
                // Supported but switched off in the watch's own Settings -> Gestures. Subscribing
                // anyway is correct: the user can turn it on without restarting the player, and
                // the system simply sends nothing until they do.
                HandGestureAvailability.DISABLED
            }
        }

        fun register(
            context: Context,
            hostView: View,
            onPrimaryGesture: () -> Unit
        ): Registration? {
            val manager = Sdk.getWearManager(context, GestureInputManager::class.java)
                    ?: return null
            if (!manager.isActionSupported(GestureEvent.ACTION_PRIMARY)) {
                // addGestureEventListener rejects an unsupported action outright, so this is a
                // precondition rather than a nicety.
                return null
            }
            Timber.d("Double pinch: primary action maps to gesture %d",
                    manager.getGestureForAction(GestureEvent.ACTION_PRIMARY))

            val listener = Consumer<GestureEvent> { event ->
                if (event.action == GestureEvent.ACTION_PRIMARY) {
                    onPrimaryGesture()
                }
            }
            manager.addGestureEventListener(
                intArrayOf(GestureEvent.ACTION_PRIMARY),
                hostView,
                context.mainExecutor,
                listener
            )

            return object : Registration {
                override fun unregister() {
                    manager.removeGestureEventListener(listener)
                }

                override fun notifyGestureConsumed() {
                    manager.notifyGestureConsumed(
                        DOUBLE_PINCH_EXPERIENCE_ID,
                        GestureEvent.ACTION_PRIMARY
                    )
                }
            }
        }

        fun watchEnabledActions(context: Context, onChanged: () -> Unit): Registration? {
            val manager = Sdk.getWearManager(context, GestureInputManager::class.java)
                    ?: return null
            val listener = Consumer<MutableSet<Int>> { onChanged() }
            manager.addEnabledActionsChangeListener(context.mainExecutor, listener)
            return object : Registration {
                override fun unregister() {
                    manager.removeEnabledActionsChangeListener(listener)
                }
            }
        }
    }

    companion object {
        private const val DOUBLE_PINCH_EXPERIENCE_ID = "svartifoss_double_pinch"

        /**
         * What this watch can do with the primary hand gesture, for the phone to render.
         *
         * Never throws: an API level below 36.1's gesture feature is a definite [UNSUPPORTED],
         * while a probe that fails for any other reason is [HandGestureAvailability.UNKNOWN] -
         * claiming "your watch cannot do this" on the strength of an exception would be worse
         * than admitting the app does not know.
         */
        fun availability(context: Context): HandGestureAvailability {
            // `Sdk` itself is present from API 36; the feature distinguishes the 36.1 addition.
            if (Build.VERSION.SDK_INT < 36 || !Api36.hasGestureDetectionFeature()) {
                return HandGestureAvailability.UNSUPPORTED
            }
            return try {
                Api36PointOne.availability(context)
            } catch (e: Throwable) {
                Timber.w(e, "Double pinch: could not read the gesture capability")
                HandGestureAvailability.UNKNOWN
            }
        }
    }
}
