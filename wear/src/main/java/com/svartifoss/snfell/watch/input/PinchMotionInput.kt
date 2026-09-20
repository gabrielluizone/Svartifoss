package com.svartifoss.snfell.watch.input

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import com.svartifoss.snfell.common.PinchMotionFeature
import com.svartifoss.snfell.common.PinchMotionFilter
import timber.log.Timber

/** Foreground-only IMU reader shared by calibration and the experimental player input. */
internal class PinchMotionInput private constructor(
        private val manager: SensorManager,
        private val onFeature: (PinchMotionFeature) -> Unit
) : SensorEventListener {
    private var active = true
    private val filter = PinchMotionFilter()
    private var gyroTimestamp = 0L
    private var gx = 0.0
    private var gy = 0.0
    private var gz = 0.0

    override fun onSensorChanged(event: SensorEvent) {
        if (!active || event.values.size < 3) return
        if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            gyroTimestamp = event.timestamp
            gx = event.values[0].toDouble()
            gy = event.values[1].toDouble()
            gz = event.values[2].toDouble()
        } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            // Never interpret missing/stale gyro data as a still wrist.
            if (gyroTimestamp == 0L || kotlin.math.abs(event.timestamp - gyroTimestamp) > 100_000_000L) {
                filter.reset()
                return
            }
            filter.add(event.timestamp, event.values[0].toDouble(), event.values[1].toDouble(),
                    event.values[2].toDouble(), gx, gy, gz)?.let(onFeature)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    fun unregister() {
        active = false
        manager.unregisterListener(this)
        filter.reset()
    }

    companion object {
        fun isAvailable(context: Context): Boolean {
            val manager = context.getSystemService(SensorManager::class.java) ?: return false
            return manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null &&
                    manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null
        }

        fun register(context: Context, onFeature: (PinchMotionFeature) -> Unit): PinchMotionInput? {
            val manager = context.getSystemService(SensorManager::class.java) ?: return null
            val accel = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return null
            val gyro = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) ?: return null
            val input = PinchMotionInput(manager, onFeature)
            val handler = Handler(Looper.getMainLooper())
            try {
                // Around 50 Hz on Pro 5; no high-rate permission and no background service.
                if (manager.registerListener(input, gyro, 20_000, handler) &&
                        manager.registerListener(input, accel, 20_000, handler)) return input
            } catch (e: RuntimeException) {
                Timber.w(e, "Double pinch: IMU registration failed")
            }
            input.unregister()
            return null
        }
    }
}
