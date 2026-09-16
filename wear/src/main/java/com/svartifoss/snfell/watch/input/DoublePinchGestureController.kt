package com.svartifoss.snfell.watch.input

import android.content.Context
import android.content.SharedPreferences
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.View
import androidx.preference.PreferenceManager
import com.matejdro.wearutils.preferences.definition.Preferences
import com.google.wear.Sdk
import com.google.wear.input.GestureEvent
import com.google.wear.input.GestureInputManager
import com.svartifoss.snfell.common.HandGestureAvailability
import com.svartifoss.snfell.common.ExperimentalPinchDetector
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.common.PinchCalibration
import com.svartifoss.snfell.common.PinchDetectorSettings
import com.svartifoss.snfell.common.PinchPreferences
import java.util.function.Consumer
import timber.log.Timber

/**
 * Owns the foreground subscription to the watch's primary one-handed gesture.
 *
 * The public surface deliberately contains no Wear-SDK types. A watch running API 36 without the
 * 36.1 gesture feature therefore never loads [Api36PointOne], while a compatible watch registers
 * against [hostView]'s window and automatically stops receiving events whenever that window loses
 * focus. On current Pixel hardware the primary action is a double pinch; other OEMs may map the
 * same semantic action to an equivalent supported hand gesture. Mobvoi watches with the vendor
 * pinch sensor use [MobvoiPinchInput] when the public API is unavailable. This experimental path
 * receives firmware detections, with no accelerometer polling or app-controlled sensitivity.
 * Both subscriptions require an interactive, resumed player with window focus.
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
    private var options = readOptions(PreferenceManager.getDefaultSharedPreferences(context))
    private val displays = context.getSystemService(DisplayManager::class.java)
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (canListen()) registerIfPossible() else unregister()
        }
    }

    /** Whether the active Controls state has an assignment for this input. */
    private var wanted = false
    private var interactive = false
    private var disposed = false

    private val attachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) = registerIfPossible()

        override fun onViewDetachedFromWindow(view: View) = unregister()
    }

    init {
        hostView.addOnAttachStateChangeListener(attachListener)
        displays?.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
    }

    fun configure(preferences: SharedPreferences) {
        val updated = readOptions(preferences)
        if (options == updated) return
        options = updated
        unregister()
        registerIfPossible()
        onAvailabilityChanged()
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

    /** The Activity calls this on resume, pause, focus changes and ambient transitions. */
    fun setInteractive(interactive: Boolean) {
        this.interactive = interactive
        if (canListen()) registerIfPossible() else unregister()
    }

    private fun canListen(): Boolean =
            !disposed && wanted && interactive && hostView.isAttachedToWindow &&
                    hostView.hasWindowFocus() && hostView.display?.state == Display.STATE_ON

    private fun dispatchPrimaryGesture() {
        if (!canListen()) {
            Timber.d("Double pinch: ignoring event outside the active player")
            return
        }
        onPrimaryGesture()
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
        displays?.unregisterDisplayListener(displayListener)
        unregister()
        try {
            availabilityWatch?.unregister()
        } catch (e: Throwable) {
            Timber.w(e, "Double pinch: could not stop watching the gesture settings")
        }
        availabilityWatch = null
    }

    private fun registerIfPossible() {
        if (!canListen() || registration != null) return
        val capability = capability(context, options)
        if (capability.backend == null) {
            Timber.i("Double pinch: no available gesture input (%s, sdk %d)",
                    capability.availability, Build.VERSION.SDK_INT)
            return
        }
        registration = try {
            when (capability.backend) {
                Backend.EXPERIMENTAL -> {
                    val detector = ExperimentalPinchDetector(options.calibration!!, options.settings)
                    PinchMotionInput.register(context) { feature ->
                        if (detector.add(feature)) {
                            Timber.i("Double pinch: experimental detection")
                            dispatchPrimaryGesture()
                        }
                    }?.let { input ->
                        object : Registration {
                            override fun unregister() = input.unregister()
                        }
                    }
                }
                Backend.WEAR -> Api36PointOne.register(context, hostView, ::dispatchPrimaryGesture)
                Backend.MOBVOI -> MobvoiPinchInput.register(
                        context, mobvoiEventFilter, ::dispatchPrimaryGesture)?.let { input ->
                    object : Registration {
                        override fun unregister() = input.unregister()
                    }
                }
            }
        } catch (e: Throwable) {
            Timber.w(e, "Double pinch: could not subscribe to the primary gesture")
            null
        }
        // "listening but disabled" is a real and common state - the subscription is accepted and
        // the system simply emits nothing until the user turns the gesture on - so it is logged
        // apart from an outright failure rather than folded into one "unavailable".
        Timber.i("Double pinch: %s", when {
            registration == null -> "unavailable"
            capability.availability == HandGestureAvailability.DISABLED ->
                "listening, but the gesture is off in the watch's settings"
            else -> "listening"
        })
    }

    private fun unregister() {
        val previous = registration ?: return
        registration = null
        try {
            previous.unregister()
            Timber.d("Double pinch: player subscription stopped")
        } catch (e: Throwable) {
            Timber.w(e, "Double pinch: unregister failed")
        }
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
        if (capability(context).backend != Backend.WEAR) return
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
                Sdk.hasApiFeature(Sdk.FEATURE_WEAR_GESTURE_DETECTION)
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

            var active = true
            val listener = Consumer<GestureEvent> { event ->
                if (active && event.action == GestureEvent.ACTION_PRIMARY) {
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
                    active = false
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

        // Sensor callbacks run on main. Keep the watermark when the Activity is recreated or
        // reopened too; persisting it to disk would incorrectly carry timestamps across reboots.
        private val mobvoiEventFilter = MobvoiPinchEventFilter()

        private enum class Backend { WEAR, MOBVOI, EXPERIMENTAL }

        private data class InputOptions(
                val experimental: Boolean,
                val calibration: PinchCalibration?,
                val settings: PinchDetectorSettings
        )

        private fun readOptions(prefs: SharedPreferences): InputOptions = InputOptions(
                Preferences.getString(prefs, MiscPreferences.WEAR_HAND_GESTURE_MODE) == "experimental",
                PinchCalibration.decode(Preferences.getString(prefs, MiscPreferences.WEAR_PINCH_CALIBRATION)),
                PinchPreferences.readSettings(prefs))

        private data class Capability(
            val backend: Backend?,
            val availability: HandGestureAvailability
        )

        /**
         * What this watch can do with the primary hand gesture, for the phone to render.
         *
         * Public Wear support takes priority; older Mobvoi watches can expose a vendor sensor.
         * A failed probe is UNKNOWN. READY reports a supported input, not recognition accuracy.
         */
        fun availability(context: Context): HandGestureAvailability = capability(context).availability

        private fun capability(context: Context, options: InputOptions = readOptions(
                PreferenceManager.getDefaultSharedPreferences(context))): Capability {
            if (options.experimental) {
                return try {
                    when {
                        !PinchMotionInput.isAvailable(context) ->
                            Capability(null, HandGestureAvailability.UNSUPPORTED)
                        options.calibration == null -> Capability(null, HandGestureAvailability.UNKNOWN)
                        else -> Capability(Backend.EXPERIMENTAL, HandGestureAvailability.READY)
                    }
                } catch (e: RuntimeException) {
                    Timber.w(e, "Double pinch: could not inspect experimental input")
                    Capability(null, HandGestureAvailability.UNKNOWN)
                }
            }
            var probeFailed = false
            // Keep Wear SDK types behind the API/feature guard on Wear OS 4 (API 33).
            try {
                if (Build.VERSION.SDK_INT >= 36 && Api36.hasGestureDetectionFeature()) {
                    val state = Api36PointOne.availability(context)
                    if (state != HandGestureAvailability.UNSUPPORTED) {
                        return Capability(Backend.WEAR, state)
                    }
                }
            } catch (e: Throwable) {
                probeFailed = true
                Timber.w(e, "Double pinch: could not read the Wear gesture capability")
            }
            try {
                if (MobvoiPinchInput.findSensor(context) != null) {
                    return Capability(Backend.MOBVOI, HandGestureAvailability.READY)
                }
            } catch (e: RuntimeException) {
                probeFailed = true
                Timber.w(e, "Double pinch: could not read the Mobvoi gesture capability")
            }
            return Capability(null, if (probeFailed) HandGestureAvailability.UNKNOWN
                    else HandGestureAvailability.UNSUPPORTED)
        }
    }
}
