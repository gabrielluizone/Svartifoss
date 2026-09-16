package com.svartifoss.snfell.watch.input

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.CommPaths
import com.svartifoss.snfell.common.ExperimentalPinchDetector
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.common.PinchCalibration
import com.svartifoss.snfell.common.PinchCalibrationFitter
import com.svartifoss.snfell.common.PinchCalibrationTransfer
import com.svartifoss.snfell.common.PinchPreferences
import com.svartifoss.snfell.common.PinchMotionFeature
import com.svartifoss.snfell.watch.config.PreferencesBus
import com.svartifoss.snfell.watch.util.WatchLanguage
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/** Guided local samples; raw IMU data stays in memory and is discarded on completion/exit. */
class PinchCalibrationActivity : AppCompatActivity(), MessageClient.OnMessageReceivedListener {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var title: TextView
    private lateinit var detail: TextView
    private lateinit var start: Button
    private lateinit var test: Button
    private lateinit var save: Button
    private lateinit var scroll: ScrollView
    private var input: PinchMotionInput? = null
    private var resumed = false
    private var startedMs = 0L
    private var testing = false
    private var detector: ExperimentalPinchDetector? = null
    private var detections = 0
    private var profile: PinchCalibration? = null
    private var pendingTransfer: PinchCalibrationTransfer? = null
    private var pendingNode: String? = null
    private var acknowledgement: CompletableDeferred<Unit>? = null
    private var sending: Job? = null
    private val rest = mutableListOf<PinchMotionFeature>()
    private val trials = List(6) { mutableListOf<PinchMotionFeature>() }
    private val movement = mutableListOf<PinchMotionFeature>()
    private var phase = -2
    private var sampleCount = 0
    private val displays by lazy { getSystemService(DisplayManager::class.java) }
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (!interactive()) interruptCapture()
        }
    }

    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(WatchLanguage.attach(newBase))

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val horizontal = (28 * resources.displayMetrics.density).toInt()
            setPadding(horizontal, horizontal, horizontal, horizontal)
        }
        fun text(size: Float) = TextView(this).also {
            it.gravity = Gravity.CENTER
            it.textSize = size
            it.setTextColor(android.graphics.Color.WHITE)
            it.setPadding(0, 8, 0, 8)
            column.addView(it)
        }
        title = text(18f)
        detail = text(14f)
        fun button(label: Int, click: () -> Unit) = Button(this).also {
            it.setText(label)
            it.isAllCaps = false
            column.addView(it, LinearLayout.LayoutParams(-1, -2))
            it.setOnClickListener { click() }
        }
        start = button(R.string.pinch_cal_start) { beginCapture(false) }
        test = button(R.string.pinch_cal_test) { beginCapture(true) }
        save = button(R.string.pinch_cal_save) { saveProfile() }
        button(R.string.pinch_cal_close) { finish() }
        scroll = ScrollView(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            addView(column)
        }
        setContentView(scroll)
        profile = PinchCalibration.decode(state?.getString("profile"))
        title.setText(R.string.pinch_cal_title)
        detail.setText(R.string.pinch_cal_intro)
        refreshButtons()
        displays?.registerDisplayListener(displayListener, handler)
    }

    override fun onResume() {
        super.onResume()
        resumed = true
    }

    override fun onPause() {
        resumed = false
        interruptCapture()
        sending?.cancel()
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) interruptCapture()
    }

    override fun onDestroy() {
        stopCapture()
        sending?.cancel()
        displays?.unregisterDisplayListener(displayListener)
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        profile?.let { outState.putString("profile", it.encode()) }
        super.onSaveInstanceState(outState)
    }

    private fun interactive() = resumed && window.decorView.hasWindowFocus() &&
            window.decorView.display?.state == Display.STATE_ON

    private fun beginCapture(testOnly: Boolean) {
        if (!interactive()) return
        stopCapture()
        testing = testOnly
        detections = 0
        sampleCount = 0
        phase = -2
        rest.clear()
        movement.clear()
        trials.forEach { it.clear() }
        if (testOnly) {
            val calibrated = profile ?: return
            val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            detector = ExperimentalPinchDetector(calibrated, PinchPreferences.readSettings(prefs))
        } else {
            profile = null
            pendingTransfer = null
        }
        startedMs = SystemClock.elapsedRealtime()
        scroll.post { scroll.smoothScrollTo(0, 0) }
        input = PinchMotionInput.register(this) { feature ->
            if (!interactive()) { interruptCapture(); return@register }
            if (sampleCount++ >= 10_000) { interruptCapture(); return@register }
            if (testing) {
                if (detector?.add(feature) == true) detections++
            } else {
                val elapsed = SystemClock.elapsedRealtime() - startedMs
                when {
                    elapsed in 3000 until 7000 -> rest.add(feature)
                    elapsed in 7000 until 37000 -> {
                        val offset = elapsed - 7000
                        if (offset % 5000 < 3000) trials[(offset / 5000).toInt()].add(feature)
                    }
                    elapsed in 39000 until 45000 -> movement.add(feature)
                }
            }
        }
        if (input == null) {
            detail.setText(R.string.pinch_cal_unavailable)
            refreshButtons()
            return
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        refreshButtons()
        handler.post(tick)
    }

    private val tick = object : Runnable {
        override fun run() {
            if (input == null) return
            if (!interactive()) { interruptCapture(); return }
            val elapsed = SystemClock.elapsedRealtime() - startedMs
            if (testing) {
                title.setText(R.string.pinch_cal_testing)
                detail.text = getString(R.string.pinch_cal_test_count, detections,
                        ((20_000 - elapsed).coerceAtLeast(0) / 1000).toInt())
                if (elapsed >= 20_000) {
                    stopCapture()
                    detail.text = getString(R.string.pinch_cal_test_done, detections)
                    refreshButtons()
                    return
                }
            } else {
                val newPhase = when {
                    elapsed < 3000 -> -2
                    elapsed < 7000 -> -1
                    elapsed < 37000 -> ((elapsed - 7000) / 1000 / 5).toInt() * 2 +
                            if ((elapsed - 7000) % 5000 < 3000) 0 else 1
                    elapsed < 39000 -> 12
                    elapsed < 45000 -> 13
                    else -> 14
                }
                if (newPhase != phase || elapsed < 3000) {
                    phase = newPhase
                    title.setText(R.string.pinch_cal_title)
                    detail.text = when {
                        phase == -2 -> getString(R.string.pinch_cal_countdown, (3 - elapsed / 1000).toInt())
                        phase == -1 -> getString(R.string.pinch_cal_rest)
                        phase in 0..11 && phase % 2 == 0 -> getString(R.string.pinch_cal_pinch, phase / 2 + 1)
                        phase in 0..11 -> getString(R.string.pinch_cal_wait)
                        phase == 12 -> getString(R.string.pinch_cal_prepare_move)
                        phase == 13 -> getString(R.string.pinch_cal_move)
                        else -> ""
                    }
                }
                if (phase == 14) {
                    stopCapture()
                    profile = PinchCalibrationFitter.fit(rest, trials, movement)
                    rest.clear(); trials.forEach { it.clear() }; movement.clear()
                    detail.setText(if (profile != null) R.string.pinch_cal_ready else R.string.pinch_cal_failed)
                    refreshButtons()
                    return
                }
            }
            handler.postDelayed(this, 100)
        }
    }

    private fun stopCapture() {
        input?.unregister()
        input = null
        detector = null
        handler.removeCallbacks(tick)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun interruptCapture() {
        if (input == null) return
        stopCapture()
        rest.clear(); trials.forEach { it.clear() }; movement.clear()
        detail.setText(R.string.pinch_cal_interrupted)
        refreshButtons()
    }

    private fun refreshButtons() {
        val idle = input == null && sending?.isActive != true
        start.isEnabled = idle
        test.visibility = if (profile != null) View.VISIBLE else View.GONE
        save.visibility = test.visibility
        test.isEnabled = idle
        save.isEnabled = idle
    }

    private fun saveProfile() {
        val calibrated = profile ?: return
        if (sending?.isActive == true) return
        val transfer = pendingTransfer?.takeIf { it.profile == calibrated }
                ?: PinchCalibrationTransfer(UUID.randomUUID().toString(), calibrated).also { pendingTransfer = it }
        val client = Wearable.getMessageClient(this)
        sending = lifecycleScope.launch {
            // Disable synchronously too: an undispatched click cannot queue two sends.
            start.isEnabled = false; test.isEnabled = false; save.isEnabled = false
            detail.setText(R.string.pinch_cal_sending)
            try {
                withTimeout(8_000) {
                    val nodes = Wearable.getCapabilityClient(this@PinchCalibrationActivity)
                            .getCapability(CommPaths.PHONE_APP_CAPABILITY, CapabilityClient.FILTER_REACHABLE).await().nodes
                    val node = nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull() ?: error("No phone")
                    pendingNode = node.id
                    acknowledgement = CompletableDeferred()
                    client.addListener(this@PinchCalibrationActivity).await()
                    client.sendMessage(node.id, CommPaths.MESSAGE_PINCH_CALIBRATION_RESULT, transfer.encode()).await()
                    acknowledgement!!.await()
                }
                // Phone is the source of truth. Its ordinary sync may already have landed.
                val prefs = PreferenceManager.getDefaultSharedPreferences(this@PinchCalibrationActivity)
                prefs.edit().putString(MiscPreferences.WEAR_PINCH_CALIBRATION.key, calibrated.encode()).apply()
                PreferencesBus.postValue(prefs)
                detail.setText(R.string.pinch_cal_saved)
            } catch (e: CancellationException) {
                detail.setText(R.string.pinch_cal_send_failed)
                if (e !is kotlinx.coroutines.TimeoutCancellationException) throw e
            } catch (_: Exception) {
                detail.setText(R.string.pinch_cal_send_failed)
            } finally {
                client.removeListener(this@PinchCalibrationActivity)
                pendingNode = null
                acknowledgement = null
                start.isEnabled = true; test.isEnabled = true; save.isEnabled = true
            }
        }
    }

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != CommPaths.MESSAGE_PINCH_CALIBRATION_ACK) return
        val reply = PinchCalibrationTransfer.decode(event.data) ?: return
        handler.post {
            if (event.sourceNodeId == pendingNode && reply == pendingTransfer) acknowledgement?.complete(Unit)
        }
    }
}
