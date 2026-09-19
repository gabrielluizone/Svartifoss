package com.svartifoss.snfell.watch.input

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.matejdro.wearutils.preferences.definition.Preferences
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.CommPaths
import com.svartifoss.snfell.common.ExperimentalPinchDetector
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.common.PinchCalibration
import com.svartifoss.snfell.common.PinchCalibrationFailure
import com.svartifoss.snfell.common.PinchCalibrationFitter
import com.svartifoss.snfell.common.PinchCalibrationResult
import com.svartifoss.snfell.common.PinchCalibrationTransfer
import com.svartifoss.snfell.common.PinchMotionFeature
import com.svartifoss.snfell.common.PinchPreferences
import com.svartifoss.snfell.watch.communication.PhoneConnection
import com.svartifoss.snfell.watch.config.PreferencesBus
import com.svartifoss.snfell.watch.input.PinchCalibrationTimeline.Kind
import com.svartifoss.snfell.watch.theme.WatchTheme
import com.svartifoss.snfell.watch.util.WatchLanguage
import com.svartifoss.snfell.watch.view.panel.AlbumPaletteCache
import com.svartifoss.snfell.watch.view.panel.PanelAppearanceResolver
import com.svartifoss.snfell.watch.view.panel.PanelTriad
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import timber.log.Timber

/**
 * Guided local samples; raw IMU data stays in memory and is discarded on completion/exit.
 *
 * The sequence itself is [PinchCalibrationTimeline], the verdict [PinchCalibrationFitter.evaluate]
 * and the screen [PinchCalibrationScreen]; this class only moves samples and state between them.
 */
@AndroidEntryPoint
class PinchCalibrationActivity : AppCompatActivity(), MessageClient.OnMessageReceivedListener {
    /** Read, never observed: observing would open the phone connection just to borrow a colour. */
    @Inject
    lateinit var phoneConnection: PhoneConnection

    private val handler = Handler(Looper.getMainLooper())
    private val trace = SignalTrace()
    private var lastSampleMs = 0L
    private var colors by mutableStateOf(CalibrationColors.from(
            WatchTheme.ACCENT_DEFAULT, WatchTheme.ACCENT_DEFAULT))
    private var input: PinchMotionInput? = null
    private var resumed = false
    private var startedMs = 0L
    private var testing = false
    private var detector: ExperimentalPinchDetector? = null
    private var profile: PinchCalibration? = null
    private var pendingTransfer: PinchCalibrationTransfer? = null
    private var pendingNode: String? = null
    private var acknowledgement: CompletableDeferred<Unit>? = null
    private var sending: Job? = null
    private val rest = mutableListOf<PinchMotionFeature>()
    private val trials = List(PinchCalibrationTimeline.ATTEMPTS) { mutableListOf<PinchMotionFeature>() }
    private val movement = mutableListOf<PinchMotionFeature>()
    private var sampleCount = 0

    private var ui by mutableStateOf(CalibrationUiState(
            mode = CalibrationMode.IDLE,
            step = PinchCalibrationTimeline.stepAt(0),
            testProgress = 0f,
            testSecondsLeft = TEST_SECONDS,
            detections = 0,
            message = CalibrationMessage("", MessageTone.NEUTRAL),
            detail = null,
            profileReady = false,
            attempted = false,
            targetLine = null,
            signalLost = false))

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
        profile = PinchCalibration.decode(state?.getString("profile"))
        ui = ui.copy(
                message = CalibrationMessage(
                        getString(if (profile != null) R.string.pinch_cal_ready else R.string.pinch_cal_intro),
                        if (profile != null) MessageTone.SUCCESS else MessageTone.NEUTRAL),
                profileReady = profile != null,
                attempted = profile != null)
        resolvePlayerColors()
        setContent {
            PinchCalibrationScreen(
                    state = ui,
                    colors = colors,
                    trace = trace,
                    onStart = { beginCapture(false) },
                    onTest = { beginCapture(true) },
                    onSave = ::saveProfile,
                    onClose = ::finish)
        }
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
        if (!interactive() || sending?.isActive == true) return
        stopCapture()
        testing = testOnly
        sampleCount = 0
        clearSamples()
        if (testOnly) {
            val calibrated = profile ?: return
            val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            detector = ExperimentalPinchDetector(calibrated, PinchPreferences.readSettings(prefs))
        } else {
            profile = null
            pendingTransfer = null
        }
        startedMs = SystemClock.elapsedRealtime()
        lastSampleMs = startedMs
        trace.clear()
        input = PinchMotionInput.register(this) { feature ->
            if (!interactive()) { interruptCapture(); return@register }
            if (sampleCount++ >= 10_000) { interruptCapture(); return@register }
            lastSampleMs = SystemClock.elapsedRealtime()
            trace.push(feature.acceleration, feature.rotation)
            if (testing) {
                if (detector?.add(feature) == true) {
                    trace.markLatest()
                    ui = ui.copy(detections = ui.detections + 1)
                }
            } else {
                val step = PinchCalibrationTimeline.stepAt(SystemClock.elapsedRealtime() - startedMs)
                when (step.kind) {
                    Kind.REST -> rest.add(feature)
                    Kind.PINCH -> trials[step.attempt - 1].add(feature)
                    Kind.MOVE -> movement.add(feature)
                    else -> Unit
                }
            }
        }
        if (input == null) {
            showIdle(getString(R.string.pinch_cal_unavailable), MessageTone.ERROR)
            return
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        ui = ui.copy(
                mode = if (testOnly) CalibrationMode.TESTING else CalibrationMode.CAPTURING,
                step = PinchCalibrationTimeline.stepAt(0),
                testProgress = 0f,
                testSecondsLeft = TEST_SECONDS,
                detections = 0,
                profileReady = if (testOnly) ui.profileReady else false,
                attempted = true,
                // The test draws the saved detector's own threshold; a capture has none until
                // its resting phase has measured the noise.
                targetLine = detector?.triggerThreshold?.toFloat(),
                signalLost = false)
        handler.post(tick)
    }

    private val tick = object : Runnable {
        override fun run() {
            if (input == null) return
            if (!interactive()) { interruptCapture(); return }
            val now = SystemClock.elapsedRealtime()
            val elapsed = now - startedMs
            val lost = now - lastSampleMs > SIGNAL_LOST_MS
            if (lost != ui.signalLost) ui = ui.copy(signalLost = lost)
            if (testing) {
                val total = TEST_SECONDS * 1000L
                ui = ui.copy(
                        testProgress = (elapsed.toFloat() / total).coerceIn(0f, 1f),
                        testSecondsLeft = ((total - elapsed + 999) / 1000).toInt().coerceAtLeast(0))
                if (elapsed >= total) {
                    stopCapture()
                    showIdle(getString(R.string.pinch_cal_test_done, ui.detections), MessageTone.NEUTRAL)
                    return
                }
            } else {
                val step = PinchCalibrationTimeline.stepAt(elapsed)
                if (step.kind == Kind.DONE) {
                    finishCapture()
                    return
                }
                // Once rest is recorded, draw the level a pinch has to clear to count as strong -
                // the same minimum the fit applies, so the attempts can be judged as they happen.
                val target = if (step.kind != Kind.COUNTDOWN && step.kind != Kind.REST) {
                    ui.targetLine ?: PinchCalibrationFitter.minimumPeakFor(rest)?.toFloat()
                } else {
                    null
                }
                ui = ui.copy(step = step, targetLine = target)
            }
            handler.postDelayed(this, 100)
        }
    }

    /** Fits what was recorded and reports either a profile to test or why there is none. */
    private fun finishCapture() {
        stopCapture()
        val result = PinchCalibrationFitter.evaluate(rest, trials, movement)
        clearSamples()
        // The watch forwards its log to the phone on request - the only way a refusal on somebody
        // else's wrist can be looked at.
        Timber.i("Pinch calibration %s: %s", result.failure ?: "fitted", result.diagnostics)
        profile = result.calibration
        if (profile != null) {
            showIdle(getString(R.string.pinch_cal_ready), MessageTone.SUCCESS, detailFor(result))
        } else {
            showIdle(failureText(result.failure), MessageTone.ERROR, detailFor(result))
        }
    }

    /** "Recognised 2 of 6 · taps per attempt: 1 3 0 2 4 1" - null when the fit got nowhere. */
    private fun detailFor(result: PinchCalibrationResult): String? {
        if (result.attemptPulses.isEmpty()) return null
        return getString(R.string.pinch_cal_detail, result.attemptsRecognized,
                result.attemptPulses.size, result.attemptPulses.joinToString(" "))
    }

    /**
     * The player's colours: the album triad the player already extracted, or the same extraction
     * run on the same cover when it has not. The theme accent when nothing is playing.
     */
    private fun resolvePlayerColors() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val appearance = PanelAppearanceResolver.appearanceContext(prefs)
        val source = PanelAppearanceResolver.accentSource(prefs, appearance)
        val themeAccent = getColor(R.color.theme_accent)
        val art = phoneConnection.albumArt.value
        fun apply(triad: PanelTriad) {
            colors = CalibrationColors.from(triad.primary, triad.secondary)
        }
        val cached = AlbumPaletteCache.get(art, source)
        if (cached != null) {
            apply(cached)
        } else {
            PanelAppearanceResolver.albumTriad(art, source, themeAccent) { triad ->
                if (!isFinishing && !isDestroyed) apply(triad)
            }
        }
    }

    private fun failureText(failure: PinchCalibrationFailure?): String {
        val reason = getString(when (failure) {
            PinchCalibrationFailure.PINCHES_TOO_WEAK -> R.string.pinch_cal_failed_weak
            PinchCalibrationFailure.PINCHES_NOT_RECOGNIZED -> R.string.pinch_cal_failed_unrecognized
            PinchCalibrationFailure.MOVEMENT_TRIGGERS -> R.string.pinch_cal_failed_movement
            PinchCalibrationFailure.RECORDING_INCOMPLETE, null -> R.string.pinch_cal_failed_incomplete
        })
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val saved = PinchCalibration.decode(
                Preferences.getString(prefs, MiscPreferences.WEAR_PINCH_CALIBRATION)) != null
        return if (saved) "$reason ${getString(R.string.pinch_cal_failed_kept)}" else reason
    }

    private fun showIdle(message: String, tone: MessageTone, detail: String? = ui.detail) {
        ui = ui.copy(
                mode = CalibrationMode.IDLE,
                message = CalibrationMessage(message, tone),
                detail = detail,
                profileReady = profile != null,
                targetLine = null,
                signalLost = false)
    }

    private fun clearSamples() {
        rest.clear()
        trials.forEach { it.clear() }
        movement.clear()
    }

    private fun stopCapture() {
        input?.unregister()
        input = null
        detector = null
        handler.removeCallbacks(tick)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    /**
     * The screen turned off or lost focus mid-capture.
     *
     * Once the movement step has a couple of seconds on record, the recording is complete in
     * every part the fitter needs, so it is fitted rather than thrown away. That step asks the
     * user to swing and turn the wrist - precisely the motion that makes Wear OS decide the watch
     * was lowered and turn the screen off - so it was also the step most likely to lose the whole
     * forty seconds before it.
     */
    private fun interruptCapture() {
        if (input == null) return
        if (!testing && movement.size >= PARTIAL_MOVEMENT_SAMPLES &&
                PinchCalibrationTimeline.stepAt(SystemClock.elapsedRealtime() - startedMs).kind == Kind.MOVE) {
            finishCapture()
            return
        }
        stopCapture()
        clearSamples()
        showIdle(getString(R.string.pinch_cal_interrupted), MessageTone.ERROR)
    }

    private fun saveProfile() {
        val calibrated = profile ?: return
        if (sending?.isActive == true) return
        val transfer = pendingTransfer?.takeIf { it.profile == calibrated }
                ?: PinchCalibrationTransfer(UUID.randomUUID().toString(), calibrated).also { pendingTransfer = it }
        val client = Wearable.getMessageClient(this)
        // Set synchronously too: an undispatched click cannot queue two sends.
        ui = ui.copy(mode = CalibrationMode.SENDING,
                message = CalibrationMessage(getString(R.string.pinch_cal_sending), MessageTone.NEUTRAL))
        sending = lifecycleScope.launch {
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
                // The mode is phone-owned and synced here, so the watch can say which of the two
                // next steps applies instead of always sending the user back to the phone.
                val active = Preferences.getString(prefs, MiscPreferences.WEAR_HAND_GESTURE_MODE) == "experimental"
                showIdle(getString(if (active) R.string.pinch_cal_saved_active else R.string.pinch_cal_saved),
                        MessageTone.SUCCESS)
            } catch (e: CancellationException) {
                showIdle(getString(R.string.pinch_cal_send_failed), MessageTone.ERROR)
                if (e !is kotlinx.coroutines.TimeoutCancellationException) throw e
            } catch (_: Exception) {
                showIdle(getString(R.string.pinch_cal_send_failed), MessageTone.ERROR)
            } finally {
                client.removeListener(this@PinchCalibrationActivity)
                pendingNode = null
                acknowledgement = null
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

    private companion object {
        const val TEST_SECONDS = 20

        /** About two seconds at the IMU's ~50 Hz: enough arm movement to judge false triggers. */
        const val PARTIAL_MOVEMENT_SAMPLES = 100

        /** Five missed samples at ~50 Hz: long enough not to flicker, short enough to notice. */
        const val SIGNAL_LOST_MS = 1_000L
    }
}
