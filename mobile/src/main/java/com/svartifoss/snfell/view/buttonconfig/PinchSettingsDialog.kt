package com.svartifoss.snfell.view.buttonconfig

import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.SeekBar
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.core.view.ViewCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import com.google.android.material.button.MaterialButton
import com.matejdro.wearutils.preferences.definition.Preferences
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.CommPaths
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.common.PinchCalibration
import com.svartifoss.snfell.common.PinchDetectorSettings
import com.svartifoss.snfell.common.PinchPreferences
import com.svartifoss.snfell.databinding.ItemPinchSliderBinding
import com.svartifoss.snfell.databinding.PopupPinchSettingsBinding
import com.svartifoss.snfell.view.LyraAccent
import com.svartifoss.snfell.view.styleAsBetaBadge
import com.svartifoss.snfell.view.settings.lyraRuntimeAccent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/** One input shared by both action assignments; edits remain a draft until Save. */
class PinchSettingsDialog : DialogFragment() {
    private lateinit var prefs: SharedPreferences
    private var viewBinding: PopupPinchSettingsBinding? = null
    private val binding get() = viewBinding!!
    private var experimental = false
    private var advanced = false
    private var settings = PinchDetectorSettings()
    private var openRequest: Job? = null
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == MiscPreferences.WEAR_PINCH_CALIBRATION.key || LyraAccent.affectsResolvedColor(key)) {
            activity?.runOnUiThread {
                if (viewBinding != null) {
                    updateProfileState()
                    updateColors()
                }
            }
        }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setStyle(STYLE_NO_TITLE, R.style.AppTheme_Dialog_Short)
        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        experimental = state?.getBoolean("experimental")
                ?: (Preferences.getString(prefs, MiscPreferences.WEAR_HAND_GESTURE_MODE) == "experimental")
        advanced = state?.getBoolean("advanced") ?: false
        val stored = PinchPreferences.readSettings(prefs)
        settings = PinchDetectorSettings(
                state?.getInt("sensitivity") ?: stored.sensitivityPercent,
                state?.getInt("gap") ?: stored.maxPinchGapMs,
                state?.getInt("cooldown") ?: stored.cooldownMs
        ).normalized()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        viewBinding = PopupPinchSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        super.onViewCreated(view, state)
        // This surface owns complete foreground/background pairs, including its selected modes.
        binding.root.setTag(R.id.tag_handles_accent_locally, true)
        binding.modeGroup.check(if (experimental) R.id.experimental_mode else R.id.native_mode)
        binding.modeGroup.setOnCheckedChangeListener { _, id ->
            experimental = id == R.id.experimental_mode
            renderMode()
        }
        bindSlider(binding.sensitivity, R.string.pinch_sensitivity_label, R.string.pinch_sensitivity_help,
                R.string.pinch_value_percent, 50, 200, settings.sensitivityPercent) {
            settings = settings.copy(sensitivityPercent = it)
        }
        bindSlider(binding.gap, R.string.pinch_gap_label, R.string.pinch_gap_help,
                R.string.pinch_value_ms, 250, 1500, settings.maxPinchGapMs) {
            settings = settings.copy(maxPinchGapMs = it)
        }
        bindSlider(binding.cooldown, R.string.pinch_cooldown_label, R.string.pinch_cooldown_help,
                R.string.pinch_value_ms, 250, 3000, settings.cooldownMs) {
            settings = settings.copy(cooldownMs = it)
        }
        binding.advancedButton.setOnClickListener { advanced = !advanced; renderAdvanced() }
        binding.calibrateButton.setOnClickListener { openCalibration() }
        binding.cancelButton.setOnClickListener { dismiss() }
        binding.saveButton.setOnClickListener { saveSettings() }
        renderMode()
        renderAdvanced()
        updateProfileState()
    }

    private fun bindSlider(row: ItemPinchSliderBinding, label: Int, help: Int, valueFormat: Int,
                           min: Int, max: Int, initial: Int, changed: (Int) -> Unit) {
        row.label.setText(label)
        row.help.setText(help)
        row.value.text = getString(valueFormat, initial)
        row.slider.max = max - min
        row.slider.progress = initial - min
        row.slider.contentDescription = getString(label)
        ViewCompat.setStateDescription(row.slider, row.value.text)
        row.slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) {
                row.value.text = getString(valueFormat, value + min)
                ViewCompat.setStateDescription(bar, row.value.text)
                changed(value + min)
            }
            override fun onStartTrackingTouch(bar: SeekBar) = Unit
            override fun onStopTrackingTouch(bar: SeekBar) = Unit
        })
    }

    private fun renderMode() {
        binding.experimentalContent.isVisible = experimental
        binding.modeDescription.setText(if (experimental) R.string.pinch_experimental_description
                else R.string.pinch_native_description)
        updateColors()
        fitWindowHeight()
    }

    private fun renderAdvanced() {
        binding.advancedContent.isVisible = advanced
        binding.advancedButton.setText(if (advanced) R.string.pinch_advanced_hide else R.string.pinch_advanced_show)
        fitWindowHeight()
    }

    private fun updateProfileState() {
        val calibrated = PinchCalibration.decode(
                Preferences.getString(prefs, MiscPreferences.WEAR_PINCH_CALIBRATION)) != null
        binding.profileState.setText(if (calibrated) R.string.pinch_profile_saved else R.string.pinch_profile_missing)
        binding.calibrateButton.setText(if (calibrated) R.string.pinch_recalibrate_watch else R.string.pinch_calibrate_watch)
    }

    /** All tints come from the current dialog accent, never the static green theme defaults. */
    private fun updateColors() {
        val ctx = requireContext()
        val accent = lyraRuntimeAccent()
        val surface = ContextCompat.getColor(ctx, R.color.lyra_surface)
        val readable = LyraAccent.contrastSafe(accent, surface, minimumContrast = 4.5)
        val secondary = ContextCompat.getColor(ctx, R.color.lyra_text_secondary)
        val divider = ContextCompat.getColor(ctx, R.color.lyra_divider)
        val controls = ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
                intArrayOf(divider, readable))
        fun button(button: MaterialButton, filled: Boolean = false) {
            button.backgroundTintList = ColorStateList.valueOf(if (filled) readable else Color.TRANSPARENT)
            val text = if (filled) LyraAccent.foregroundFor(readable) else readable
            button.setTextColor(ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
                    intArrayOf(secondary, text)))
            button.strokeColor = controls
            button.rippleColor = ColorStateList.valueOf(androidx.core.graphics.ColorUtils.setAlphaComponent(
                    if (filled) text else readable, 32))
        }
        binding.betaBadge.styleAsBetaBadge()
        button(binding.calibrateButton)
        button(binding.advancedButton)
        button(binding.saveButton, filled = true)
        button(binding.cancelButton)
        binding.cancelButton.setTextColor(secondary)
        listOf(binding.nativeMode, binding.experimentalMode).forEach { radio ->
            radio.setUseMaterialThemeColors(false)
            radio.buttonTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(readable, secondary))
            radio.background = GradientDrawable().apply {
                cornerRadius = 12 * resources.displayMetrics.density
                setColor(if (radio.isChecked) androidx.core.graphics.ColorUtils.setAlphaComponent(accent, 20) else Color.TRANSPARENT)
            }
        }
        listOf(binding.sensitivity, binding.gap, binding.cooldown).forEach { row ->
            row.slider.thumbTintList = controls
            row.slider.progressTintList = controls
            row.slider.progressBackgroundTintList = ColorStateList.valueOf(divider)
            row.value.setTextColor(readable)
        }
    }

    override fun onStart() {
        super.onStart()
        prefs.registerOnSharedPreferenceChangeListener(listener)
        updateProfileState()
        updateColors()
        dialog?.window?.setLayout(dialogWidth(), WindowManager.LayoutParams.WRAP_CONTENT)
        fitWindowHeight()
    }

    private fun availableFrame(): Rect =
            Rect().also { requireActivity().window.decorView.getWindowVisibleDisplayFrame(it) }

    private fun dialogWidth(): Int {
        val width = (availableFrame().width().takeIf { it > 0 }
                ?: resources.displayMetrics.widthPixels) - dp(32)
        return width.coerceAtMost(dp(520))
    }

    private fun availableHeight(): Int = ((availableFrame().height().takeIf { it > 0 }
            ?: resources.displayMetrics.heightPixels) - dp(32)).coerceAtLeast(dp(160))

    /**
     * Wraps the content while it fits, and pins the window to the available height once it does
     * not - the one state in which the weighted body is handed a bounded height and scrolls.
     *
     * Measured rather than left to the window: a WRAP_CONTENT window around taller content is
     * clipped by the display instead of being told to shrink, which is how the advanced sliders
     * ended up below the screen with nothing to scroll. Re-run whenever a section opens or closes.
     */
    private fun fitWindowHeight() {
        val root = viewBinding?.root ?: return
        // After a layout (so the width is real), and posted out of it, since resizing the window
        // from inside a layout pass would only schedule another one mid-flight.
        root.doOnLayout { root.post { applyWindowHeight(root) } }
    }

    private fun applyWindowHeight(root: View) {
        // Posted, so the dialog may have been dismissed in between.
        if (!isAdded || viewBinding == null || root.width <= 0) return
        val window = dialog?.window ?: return
        root.measure(
                View.MeasureSpec.makeMeasureSpec(root.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        // The dialog background's own insets sit between the window edge and this root.
        val chrome = (window.decorView.height - root.height).coerceAtLeast(0)
        val limit = availableHeight()
        val height = if (root.measuredHeight + chrome > limit) limit
                else WindowManager.LayoutParams.WRAP_CONTENT
        if (window.attributes.height != height) {
            window.setLayout(dialogWidth(), height)
        }
        // The measure above was taken with an unbounded height; lay out again with the real one.
        root.requestLayout()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    /**
     * Always saves. Experimental without a calibration used to be refused here by scrolling to the
     * profile line - which, with the body unable to scroll, made Save look like it did nothing.
     * Saving it is safe: the watch starts no detector until a valid profile exists, and starts it
     * by itself the moment one is saved there, so there is no second trip back to this dialog.
     */
    private fun saveSettings() {
        prefs.edit()
                .putString(MiscPreferences.WEAR_HAND_GESTURE_MODE.key, if (experimental) "experimental" else "native")
                .also { PinchPreferences.writeSettings(it, settings) }
                .apply()
        dismiss()
    }

    private fun openCalibration() {
        if (openRequest?.isActive == true) return
        val ctx = requireContext()
        binding.calibrateButton.isEnabled = false
        binding.connectionStatus.isVisible = true
        binding.connectionStatus.setText(R.string.pinch_open_sending)
        openRequest = viewLifecycleOwner.lifecycleScope.launch {
            try {
                withTimeout(8_000) {
                    val nodes = Wearable.getCapabilityClient(ctx).getCapability(
                            CommPaths.WATCH_APP_CAPABILITY, CapabilityClient.FILTER_REACHABLE).await().nodes
                    val node = nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull() ?: error("No reachable watch")
                    Wearable.getMessageClient(ctx).sendMessage(node.id,
                            CommPaths.MESSAGE_OPEN_PINCH_CALIBRATION, ByteArray(0)).await()
                }
                binding.connectionStatus.setText(R.string.pinch_open_sent)
            } catch (e: CancellationException) {
                if (e is kotlinx.coroutines.TimeoutCancellationException) binding.connectionStatus.setText(R.string.pinch_open_failed)
                else throw e
            } catch (_: Exception) {
                binding.connectionStatus.setText(R.string.pinch_open_failed)
            } finally { viewBinding?.calibrateButton?.isEnabled = true }
        }
    }

    override fun onStop() {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
        super.onStop()
    }

    override fun onDestroyView() {
        openRequest?.cancel()
        viewBinding = null
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("experimental", experimental)
        outState.putBoolean("advanced", advanced)
        outState.putInt("sensitivity", settings.sensitivityPercent)
        outState.putInt("gap", settings.maxPinchGapMs)
        outState.putInt("cooldown", settings.cooldownMs)
        super.onSaveInstanceState(outState)
    }
}
