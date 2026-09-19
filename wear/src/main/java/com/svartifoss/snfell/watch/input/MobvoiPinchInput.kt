package com.svartifoss.snfell.watch.input

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import timber.log.Timber

/**
 * Foreground-only subscription to Mobvoi's existing pinch detector, validated on the Pro 5.
 * This uses the vendor's discrete events, not an app-owned accelerometer/gyroscope classifier.
 * The caller owns the player lifecycle and must unregister when the player is no longer active.
 */
internal class MobvoiPinchInput private constructor(
    private val manager: SensorManager,
    private val sensor: Sensor,
    private val eventFilter: MobvoiPinchEventFilter,
    private val onDoublePinch: () -> Unit
) {
    private var active = true
    /** Set just before registering, so the activation replay can be told apart - see
     *  [MobvoiPinchEventFilter.acceptLive]. */
    private var registeredAtMs = SystemClock.elapsedRealtime()

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (!active) {
                Timber.d("Double pinch: ignoring queued Mobvoi event after unregister")
                return
            }
            val sinceRegistration = SystemClock.elapsedRealtime() - registeredAtMs
            if (!eventFilter.acceptLive(event.timestamp, event.values, sinceRegistration)) {
                Timber.d("Double pinch: ignoring Mobvoi value, activation replay or repeated/" +
                        "out-of-order event: %s at %d, %d ms after registering",
                        event.values.contentToString(), event.timestamp, sinceRegistration)
                return
            }
            Timber.i("Double pinch: Mobvoi double pinch received at %d", event.timestamp)
            onDoublePinch()
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun unregister() {
        if (!active) return
        // Already queued callbacks must lose permission to execute before the framework detaches.
        active = false
        try {
            manager.unregisterListener(listener, sensor)
            Timber.d("Double pinch: Mobvoi listener unregistered")
        } catch (e: RuntimeException) {
            Timber.w(e, "Double pinch: could not unregister Mobvoi listener")
        }
    }

    companion object {
        private const val SENSOR_TYPE_MOBVOI_PINCH = 33171134
        private const val SENSOR_STRING_TYPE_MOBVOI_PINCH = "mobvoi_pinch"

        fun findSensor(context: Context): Sensor? {
            val manager = context.getSystemService(SensorManager::class.java) ?: return null
            return findSensor(manager)
        }

        private fun findSensor(manager: SensorManager): Sensor? {
            val sensors = manager.getSensorList(Sensor.TYPE_ALL).filter { sensor ->
                sensor.vendor.equals("mobvoi", ignoreCase = true) &&
                        (sensor.type == SENSOR_TYPE_MOBVOI_PINCH ||
                                sensor.stringType == SENSOR_STRING_TYPE_MOBVOI_PINCH)
            }
            // Both variants reported the same physical gestures on the test watch. Subscribing
            // to both would duplicate commands; prefer the one that cannot wake the device.
            return sensors.firstOrNull { !it.isWakeUpSensor } ?: sensors.firstOrNull()
        }

        fun register(
            context: Context,
            eventFilter: MobvoiPinchEventFilter,
            onDoublePinch: () -> Unit
        ): MobvoiPinchInput? {
            val manager = context.getSystemService(SensorManager::class.java) ?: return null
            val sensor = findSensor(manager) ?: return null
            val input = MobvoiPinchInput(manager, sensor, eventFilter, onDoublePinch)
            val registered = try {
                input.registeredAtMs = SystemClock.elapsedRealtime()
                // Match the tested delivery rate; this does not change firmware sensitivity.
                manager.registerListener(input.listener, sensor, SensorManager.SENSOR_DELAY_FASTEST,
                        Handler(Looper.getMainLooper()))
            } catch (e: RuntimeException) {
                input.unregister()
                Timber.w(e, "Double pinch: could not register Mobvoi listener")
                return null
            }
            if (!registered) {
                input.unregister()
                Timber.w("Double pinch: Mobvoi listener registration rejected for %s", sensor.name)
                return null
            }
            Timber.i("Double pinch: listening to Mobvoi sensor %s (type %d, wakeup %s)",
                    sensor.name, sensor.type, sensor.isWakeUpSensor)
            return input
        }
    }
}
