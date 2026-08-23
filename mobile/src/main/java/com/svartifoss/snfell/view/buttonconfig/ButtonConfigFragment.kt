package com.svartifoss.snfell.view.buttonconfig

import android.app.Activity
import android.content.ContentResolver
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.VectorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import com.svartifoss.snfell.R
import com.svartifoss.snfell.actions.PhoneAction
import com.svartifoss.snfell.common.CenterButton
import com.svartifoss.snfell.common.ScreenButtons
import com.svartifoss.snfell.common.ScreenQuadrant
import com.svartifoss.snfell.common.SwipeGesture
import com.svartifoss.snfell.common.actions.StandardIcons
import com.svartifoss.snfell.common.buttonconfig.ButtonInfo
import com.svartifoss.snfell.common.buttonconfig.GESTURE_DOUBLE_TAP
import com.svartifoss.snfell.common.buttonconfig.GESTURE_LONG_TAP
import com.svartifoss.snfell.common.buttonconfig.GESTURE_SINGLE_TAP
import com.svartifoss.snfell.common.view.FourWayTouchLayout
import com.svartifoss.snfell.config.CustomIconStorage
import com.svartifoss.snfell.config.WatchInfoWithIcons
import com.svartifoss.snfell.config.buttons.ButtonConfig
import com.svartifoss.snfell.view.mainactivity.MainActivity
import com.svartifoss.snfell.databinding.FragmentButtonConfigBinding
import com.svartifoss.snfell.databinding.ItemSwipeGestureBinding
import com.svartifoss.snfell.databinding.ItemWatchButtonBinding
import com.svartifoss.snfell.di.InjectableViewModelFactory
import com.svartifoss.snfell.view.TitledActivity
import dagger.Provides
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject
import javax.inject.Named

class ButtonConfigFragment : Fragment(), FourWayTouchLayout.UserActionListener {
    companion object {
        private const val ARGUMENT_SETS_PLAYBACK_ACTIONS = "SetsPlaybackActions"

        fun newInstance(setsPlaybackActions: Boolean): ButtonConfigFragment {
            val arguments = Bundle()
            arguments.putBoolean(ARGUMENT_SETS_PLAYBACK_ACTIONS, setsPlaybackActions)

            val fragment = ButtonConfigFragment()
            fragment.arguments = arguments
            return fragment
        }
    }

    private var setsPlaybackActions: Boolean = false
    private var watchInfo: WatchInfoWithIcons? = null

    /** Lets the Controls pager route its legacy save notification to the owning page only. */
    internal fun configuresPlaybackActions(): Boolean =
        arguments?.getBoolean(ARGUMENT_SETS_PLAYBACK_ACTIONS, setsPlaybackActions)
            ?: setsPlaybackActions

    private lateinit var binding: FragmentButtonConfigBinding
    private val viewModel: ButtonConfigViewModel by viewModels { viewModelFactory }

    @Inject
    lateinit var viewModelFactory: InjectableViewModelFactory<ButtonConfigViewModel>

    @Inject
    lateinit var customIconStorage: CustomIconStorage


    override fun onCreate(savedInstanceState: Bundle?) {
        setsPlaybackActions = requireArguments().getBoolean(ARGUMENT_SETS_PLAYBACK_ACTIONS)
        AndroidSupportInjection.inject(this)

        super.onCreate(savedInstanceState)
    }

    override fun onStart() {
        super.onStart()
        if (parentFragmentManager.findFragmentById(R.id.fragment_container) !== this) return

        val activity = activity
        if (activity is TitledActivity) {
            activity.updateActivityTitle(getString(if (setsPlaybackActions) R.string.playing_controls else R.string.stopped_controls))
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        viewModel.watchInfoProvider.observe(viewLifecycleOwner, watchInfoObserver)
        viewModel.buttonConfig.observe(viewLifecycleOwner, buttonsConfigObserver)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentButtonConfigBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.fourWayTouch.listener = this
        setupTouchZone(binding.iconTop, ScreenQuadrant.TOP, R.string.touch_zone_top)
        setupTouchZone(binding.iconBottom, ScreenQuadrant.BOTTOM, R.string.touch_zone_bottom)
        setupTouchZone(binding.iconLeft, ScreenQuadrant.LEFT, R.string.touch_zone_left)
        setupTouchZone(binding.iconRight, ScreenQuadrant.RIGHT, R.string.touch_zone_right)
        setupCenterButton()
        setupSwipeGestureRows()
        setupScreenButtonRows()
        binding.quickPanelLink.setOnClickListener {
            (activity as? MainActivity)?.openActionsMenu()
        }
    }

    /** The center-tap zone: unlike the four quadrants, it always does *something* even when
     *  unconfigured (toggles play/pause), and only single tap is reassignable here - double tap
     *  and long press keep their fixed quick-panel/queue behavior, so the picker is opened in
     *  single-action mode (same flag the mini-button slots use). */
    private fun setupCenterButton() {
        val imageView = binding.iconCenter ?: return
        imageView.isClickable = true
        imageView.isFocusable = true
        imageView.setOnClickListener {
            configureButton(
                false,
                CenterButton.TAP,
                getString(R.string.touch_zone_center),
                supportsLongPress = false,
                singleActionOnly = true
            )
        }
    }

    private fun setupTouchZone(imageView: ImageView, quadrant: Int, label: Int) {
        imageView.isClickable = true
        imageView.isFocusable = true
        imageView.setOnClickListener { onSingleTap(quadrant) }
        imageView.contentDescription = getString(
            R.string.touch_zone_action,
            getString(label),
            getString(R.string.no_action)
        )
    }

    // Populated once in setupSwipeGestureRows / setupScreenButtonRows, then refreshed
    // reactively by buttonsConfigObserver whenever the assigned action changes - same idea as
    // the four quadrant icons (binding.iconTop etc.), just for the three configurable swipe
    // directions and the three on-screen mini-button slots.
    private val swipeGestureRows = mutableMapOf<Int, IconTile>()
    private val screenButtonRows = mutableMapOf<Int, IconTile>()
    private val physicalButtonRows = mutableMapOf<Int, PhysicalButtonRow>()
    private var currentButtonConfig: ButtonConfig? = null

    /** Populates the "Swipe gestures" section: 3 fixed tiles in one row (unlike the
     *  physical-buttons list, not driven by any live watch-info data), one per configurable
     *  [SwipeGesture] direction, each opening the same gesture picker a quadrant tap does - just
     *  in single-action mode, since a swipe has no double/long-press equivalent. Each tile is
     *  just an icon plus a concise direction caption (see item_swipe_gesture.xml). The caption
     *  remains after a user action replaces the arrow, preserving orientation without verbose
     *  helper text. */
    private fun setupSwipeGestureRows() {
        val container = binding.swipeGestureContainer ?: return
        val directions = listOf(
                Triple(SwipeGesture.UP, getString(R.string.swipe_gesture_up), R.drawable.ic_arrow_up)
                        to getString(R.string.swipe_gesture_label_up),
                Triple(SwipeGesture.DOWN, getString(R.string.swipe_gesture_down), R.drawable.ic_arrow_down)
                        to getString(R.string.swipe_gesture_label_down),
                Triple(SwipeGesture.LEFT, getString(R.string.swipe_gesture_left), R.drawable.ic_arrow_left)
                        to getString(R.string.swipe_gesture_label_left)
        )

        val inflater = LayoutInflater.from(requireContext())
        for ((info, shortLabel) in directions) {
            val (code, label, defaultIconRes) = info
            val tileBinding = ItemSwipeGestureBinding.inflate(inflater, container, true)
            tileBinding.label.text = shortLabel
            tileBinding.root.setOnClickListener {
                configureButton(false, code, label, supportsLongPress = false, singleActionOnly = true)
            }
            swipeGestureRows[code] = IconTile(tileBinding, defaultIconRes, shortLabel, label)
        }
    }

    /** Populates the "Mini buttons" section: 3 fixed tiles, one per [ScreenButtons] slot, in
     *  the same left-to-right order the watch renders them under the track time. Unlike swipes
     *  a slot is a real visible button, so it supports a long press as a second action -
     *  double tap stays off because a visible button that waits out the double-tap timeout
     *  before reacting would feel broken. */
    private fun setupScreenButtonRows() {
        val container = binding.screenButtonContainer ?: return
        val slots = listOf(
                ScreenButtons.SLOT_1 to getString(R.string.screen_button_1) to getString(R.string.screen_button_label_1),
                ScreenButtons.SLOT_2 to getString(R.string.screen_button_2) to getString(R.string.screen_button_label_2),
                ScreenButtons.SLOT_3 to getString(R.string.screen_button_3) to getString(R.string.screen_button_label_3)
        )

        val inflater = LayoutInflater.from(requireContext())
        for ((codeAndLabel, shortLabel) in slots) {
            val (code, label) = codeAndLabel
            val tileBinding = ItemSwipeGestureBinding.inflate(inflater, container, true)
            tileBinding.label.text = shortLabel
            tileBinding.root.setOnClickListener {
                configureButton(false, code, label, supportsLongPress = true, singleActionOnly = true)
            }
            screenButtonRows[code] = IconTile(tileBinding, R.drawable.ic_plus, shortLabel, label)
        }
    }

    /** [defaultIconRes] is the placeholder shown until an action is assigned - a directional
     *  arrow for swipe tiles (each direction needs its own) or a "+" for mini-button slots. */
    private class IconTile(
        val binding: ItemSwipeGestureBinding,
        val defaultIconRes: Int,
        val defaultLabel: String,
        val accessibilityLabel: String
    )

    private class PhysicalButtonRow(
        val binding: ItemWatchButtonBinding,
        val title: String,
        val supportsLongPress: Boolean
    )

    private val watchInfoObserver = Observer<WatchInfoWithIcons?> {
        this.watchInfo = it

        while (binding.watchButtonContainer.childCount > 0) {
            binding.watchButtonContainer.removeViewAt(0)
        }
        physicalButtonRows.clear()

        val buttonsCount = it?.watchInfo?.buttonsCount ?: 0

        binding.captionPhysicalButtons.visibility = View.VISIBLE
        binding.physicalButtonsHint.visibility = if (buttonsCount > 0) View.GONE else View.VISIBLE
        binding.physicalButtonsHint.setText(
            if (it == null) R.string.physical_buttons_disconnected
            else R.string.physical_buttons_unavailable
        )

        if (it == null) {
            return@Observer
        }

        if (it.watchInfo.roundWatch) {
            binding.watchDisplayBackground.setImageResource(R.drawable.watch_round_background)
            binding.watchDisplayBorder.setImageResource(R.drawable.watch_round_border)
        } else {
            binding.watchDisplayBackground.setImageResource(R.drawable.watch_square_background)
            binding.watchDisplayBorder.setImageResource(R.drawable.watch_square_border)
        }

        val inflater = LayoutInflater.from(activity)
        for (buttonIndex in 0 until buttonsCount) {
            val buttonInfo = watchInfo!!.watchInfo.buttonsList[buttonIndex]
            val buttonTitle = buttonInfo.label
            val buttonCode = if (buttonInfo.hasCode()) buttonInfo.code else buttonIndex

            val icon = if (StandardIcons.hasIcon(buttonCode)) {
                ContextCompat.getDrawable(requireContext(), StandardIcons.getIcon(buttonCode))
            } else {
                watchInfo!!.icons[buttonCode]
            }

            // Tint to the theme's on-surface color so icons stay visible in both light and dark mode.
            icon?.setTint(ContextCompat.getColor(requireContext(), R.color.lyra_on_surface))

            val buttonBinding = ItemWatchButtonBinding.inflate(inflater, binding.watchButtonContainer, true)

            buttonBinding.buttonTitle.text = buttonTitle
            buttonBinding.buttonIcon.setImageDrawable(icon)
            buttonBinding.root.setOnClickListener {
                val buttonName = "$buttonTitle button"
                configureButton(true, buttonCode, buttonName, buttonInfo.supportsLongPress)
            }
            physicalButtonRows[buttonCode] = PhysicalButtonRow(
                    buttonBinding, buttonTitle, buttonInfo.supportsLongPress)
        }
        currentButtonConfig?.let(::updatePhysicalButtonRows)
    }

    private val buttonsConfigObserver = Observer<ButtonConfig?> {
        if (it == null) {
            return@Observer
        }
        currentButtonConfig = it

        val topAction = it.getScreenAction(ButtonInfo(false, ScreenQuadrant.TOP, GESTURE_SINGLE_TAP))
        setTouchZoneIcon(binding.iconTop, R.string.touch_zone_top, topAction)

        val bottomAction = it.getScreenAction(ButtonInfo(false, ScreenQuadrant.BOTTOM, GESTURE_SINGLE_TAP))
        setTouchZoneIcon(binding.iconBottom, R.string.touch_zone_bottom, bottomAction)

        val rightAction = it.getScreenAction(ButtonInfo(false, ScreenQuadrant.RIGHT, GESTURE_SINGLE_TAP))
        setTouchZoneIcon(binding.iconRight, R.string.touch_zone_right, rightAction)

        val leftAction = it.getScreenAction(ButtonInfo(false, ScreenQuadrant.LEFT, GESTURE_SINGLE_TAP))
        setTouchZoneIcon(binding.iconLeft, R.string.touch_zone_left, leftAction)

        val centerAction = it.getScreenAction(ButtonInfo(false, CenterButton.TAP, GESTURE_SINGLE_TAP))
        setCenterButtonIcon(binding.iconCenter, centerAction)

        for ((code, tile) in swipeGestureRows) {
            val action = it.getScreenAction(ButtonInfo(false, code, GESTURE_SINGLE_TAP))
            setTileIcon(tile, action)
        }

        for ((code, tile) in screenButtonRows) {
            val tap = it.getScreenAction(ButtonInfo(false, code, GESTURE_SINGLE_TAP))
            val hold = it.getScreenAction(ButtonInfo(false, code, GESTURE_LONG_TAP))
            // The watch renders the primary action's icon, but the phone overview must expose
            // both assignments so a configured long press does not disappear behind the tap.
            val summary = listOfNotNull(
                    tap?.let { action -> getString(R.string.control_assignment_tap, action.title) },
                    hold?.let { action -> getString(R.string.control_assignment_hold, action.title) }
            ).ifEmpty { listOf(getString(R.string.no_action)) }
                    .joinToString("\n")
            setTileIcon(tile, tap ?: hold, summary)
        }
        updatePhysicalButtonRows(it)
    }

    private fun updatePhysicalButtonRows(config: ButtonConfig) {
        for ((code, row) in physicalButtonRows) {
            val single = config.getScreenAction(ButtonInfo(true, code, GESTURE_SINGLE_TAP))
            val double = config.getScreenAction(ButtonInfo(true, code, GESTURE_DOUBLE_TAP))
            val hold = if (row.supportsLongPress) {
                config.getScreenAction(ButtonInfo(true, code, GESTURE_LONG_TAP))
            } else {
                null
            }

            val assignments = mutableListOf(
                    formatAssignment(R.string.gesture_single_press, single),
                    formatAssignment(R.string.gesture_double_press, double)
            ).apply {
                if (row.supportsLongPress) {
                    add(formatAssignment(R.string.gesture_long_press, hold))
                }
            }
            row.binding.buttonActions.text = assignments.joinToString("\n")
            row.binding.root.contentDescription = buildString {
                append(row.title)
                append(". ")
                append(row.binding.buttonActions.text)
            }
        }
    }

    private fun formatAssignment(label: Int, action: PhoneAction?): String =
            "${getString(label)}: ${action?.title ?: getString(R.string.no_action)}"

    private fun setIcon(imageView: ImageView, phoneAction: PhoneAction?) {
        if (phoneAction == null) {
            imageView.setImageResource(R.drawable.ic_plus)
            imageView.setColorFilter(Color.WHITE)
            imageView.alpha = 0.55f
            return
        }

        // The watch face preview is always dark, so default (vector) icons are forced white
        // to stay legible. Custom user-picked bitmaps are shown untouched.
        val icon = customIconStorage[phoneAction]
        if (icon is VectorDrawable) {
            icon.mutate().setTint(Color.WHITE)
        }
        imageView.clearColorFilter()
        imageView.setImageDrawable(icon)
        imageView.alpha = 1f
    }

    private fun setTouchZoneIcon(imageView: ImageView, label: Int, phoneAction: PhoneAction?) {
        setIcon(imageView, phoneAction)
        imageView.contentDescription = getString(
            R.string.touch_zone_action,
            getString(label),
            phoneAction?.title ?: getString(R.string.no_action)
        )
    }

    /** Unlike the four quadrants, an unconfigured center button isn't a no-op - it toggles
     *  play/pause - so its placeholder is that real default icon/label instead of the muted "+"
     *  [setTouchZoneIcon] shows for a quadrant that truly does nothing yet. */
    private fun setCenterButtonIcon(imageView: ImageView, phoneAction: PhoneAction?) {
        if (phoneAction == null) {
            val icon = ContextCompat.getDrawable(requireContext(), com.svartifoss.snfell.common.R.drawable.action_play_pause)
                ?.mutate()
            icon?.setTint(Color.WHITE)
            imageView.clearColorFilter()
            imageView.setImageDrawable(icon)
            imageView.alpha = 1f
        } else {
            setIcon(imageView, phoneAction)
        }

        imageView.contentDescription = getString(
            R.string.touch_zone_action,
            getString(R.string.touch_zone_center),
            phoneAction?.title ?: getString(R.string.action_play_pause)
        )
    }

    /** Unlike the watch-face preview icons, a swipe/mini-button tile sits on the app's own
     *  (light or dark) theme background, so its icon follows the theme's on-surface color
     *  instead of being forced white. Falls back to the tile's placeholder icon when
     *  unconfigured. */
    private fun setTileIcon(
        tile: IconTile,
        phoneAction: PhoneAction?,
        actionSummary: String = phoneAction?.title ?: getString(R.string.no_action)
    ) {
        val isNotSet = phoneAction == null
        val primaryColor = ContextCompat.getColor(requireContext(), R.color.lyra_on_surface)
        val mutedColor = ContextCompat.getColor(requireContext(), R.color.lyra_text_secondary)

        val icon = if (isNotSet) {
            ContextCompat.getDrawable(requireContext(), tile.defaultIconRes)
        } else {
            customIconStorage[phoneAction]
        }
        tile.binding.icon.setImageDrawable(icon)
        tile.binding.icon.clearColorFilter()
        val isTemplate = isNotSet || icon is VectorDrawable ||
                phoneAction?.customIconUri?.scheme == ContentResolver.SCHEME_ANDROID_RESOURCE
        if (isTemplate) {
            tile.binding.icon.setColorFilter(if (isNotSet) mutedColor else primaryColor)
        }
        tile.binding.label.text = tile.defaultLabel
        tile.binding.actionLabel.text = actionSummary
        tile.binding.root.contentDescription = getString(
                R.string.touch_zone_action,
                tile.accessibilityLabel,
                actionSummary
        )
    }

    override fun onSingleTap(quadrant: Int) {
        val quadrantName = ScreenQuadrant.QUADRANT_NAMES[quadrant]
        val buttonName = "$quadrantName touch"

        configureButton(false, quadrant, buttonName, true)
    }

    private fun configureButton(
        physicalButton: Boolean,
        buttonCode: Int,
        buttonName: String,
        supportsLongPress: Boolean,
        singleActionOnly: Boolean = false
    ) {
        val buttonInfo = ButtonInfo(physicalButton, buttonCode, GESTURE_SINGLE_TAP)

        val gesturePicker = GesturePickerFragment.newInstance(setsPlaybackActions,
                buttonInfo,
                buttonName,
                supportsLongPress,
                singleActionOnly)
        gesturePicker.show(requireFragmentManager(), "GesturePickerFragment")
    }

    private fun onButtonConfigurationFinished() {
        viewModel.commitConfig()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == GesturePickerFragment.REQUEST_CODE_SAVE_NOTIFICATION &&
                resultCode == Activity.RESULT_OK) {
            onButtonConfigurationFinished()
        }
    }

    override fun onUpwardsSwipe() = Unit

    override fun onDownwardsSwipe() = Unit

    override fun onSwipeLeft() = Unit

    override fun onDoubleTap(quadrant: Int) = Unit

    override fun onLongTap(quadrant: Int) = Unit

    @dagger.Module
    class Module {
        @Provides
        @Named(ButtonConfigViewModel.ARG_DISPLAY_PLAYBACK_ACTIONS)
        fun displayPlaybackActions(configFragment: ButtonConfigFragment) = configFragment.setsPlaybackActions
    }
}
