package com.svartifoss.snfell.view.buttonconfig

import android.app.Activity
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
import com.svartifoss.snfell.common.ScreenButtons
import com.svartifoss.snfell.common.ScreenQuadrant
import com.svartifoss.snfell.common.SwipeGesture
import com.svartifoss.snfell.common.actions.StandardIcons
import com.svartifoss.snfell.common.buttonconfig.ButtonInfo
import com.svartifoss.snfell.common.buttonconfig.GESTURE_LONG_TAP
import com.svartifoss.snfell.common.buttonconfig.GESTURE_SINGLE_TAP
import com.svartifoss.snfell.common.view.FourWayTouchLayout
import com.svartifoss.snfell.config.CustomIconStorage
import com.svartifoss.snfell.config.WatchInfoWithIcons
import com.svartifoss.snfell.config.buttons.ButtonConfig
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
        setupSwipeGestureRows()
        setupScreenButtonRows()
    }

    // Populated once in setupSwipeGestureRows / setupScreenButtonRows, then refreshed
    // reactively by buttonsConfigObserver whenever the assigned action changes - same idea as
    // the four quadrant icons (binding.iconTop etc.), just for the three configurable swipe
    // directions and the three on-screen mini-button slots.
    private val swipeGestureRows = mutableMapOf<Int, IconTile>()
    private val screenButtonRows = mutableMapOf<Int, IconTile>()

    /** Populates the "Swipe gestures" section: 3 fixed tiles in one row (unlike the
     *  physical-buttons list, not driven by any live watch-info data), one per configurable
     *  [SwipeGesture] direction, each opening the same gesture picker a quadrant tap does - just
     *  in single-action mode, since a swipe has no double/long-press equivalent. Each tile is
     *  just an icon (no background/label - see item_swipe_gesture.xml); a directional arrow
     *  stands in for the direction until an action is assigned, so the three stay
     *  distinguishable without needing a text label. */
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
            tileBinding.icon.contentDescription = label
            tileBinding.label.text = shortLabel
            tileBinding.icon.setOnClickListener {
                configureButton(false, code, label, supportsLongPress = false, singleActionOnly = true)
            }
            swipeGestureRows[code] = IconTile(tileBinding, defaultIconRes)
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
            tileBinding.icon.contentDescription = label
            tileBinding.label.text = shortLabel
            tileBinding.icon.setOnClickListener {
                configureButton(false, code, label, supportsLongPress = true, singleActionOnly = true)
            }
            screenButtonRows[code] = IconTile(tileBinding, R.drawable.ic_plus)
        }
    }

    /** [defaultIconRes] is the placeholder shown until an action is assigned - a directional
     *  arrow for swipe tiles (each direction needs its own) or a "+" for mini-button slots. */
    private class IconTile(val binding: ItemSwipeGestureBinding, val defaultIconRes: Int)

    private val watchInfoObserver = Observer<WatchInfoWithIcons?> {
        this.watchInfo = it

        while (binding.watchButtonContainer.childCount > 0) {
            binding.watchButtonContainer.removeViewAt(0)
        }

        val buttonsCount = it?.watchInfo?.buttonsCount ?: 0

        binding.captionPhysicalButtons.visibility = if (buttonsCount > 0) View.VISIBLE else View.GONE

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

            buttonBinding.button.text = buttonTitle
            buttonBinding.button.icon = icon
            // See the matching comment in GesturePickerFragment.updateButton: forces MaterialButton
            // to recompute the icon's centered position against the text that was just set.
            buttonBinding.button.requestLayout()
            buttonBinding.button.setOnClickListener {
                val buttonName = "$buttonTitle button"
                configureButton(true, buttonCode, buttonName, buttonInfo.supportsLongPress)
            }
        }
    }

    private val buttonsConfigObserver = Observer<ButtonConfig?> {
        if (it == null) {
            return@Observer
        }

        val topAction = it.getScreenAction(ButtonInfo(false, ScreenQuadrant.TOP, GESTURE_SINGLE_TAP))
        setIcon(binding.iconTop, topAction)

        val bottomAction = it.getScreenAction(ButtonInfo(false, ScreenQuadrant.BOTTOM, GESTURE_SINGLE_TAP))
        setIcon(binding.iconBottom, bottomAction)

        val rightAction = it.getScreenAction(ButtonInfo(false, ScreenQuadrant.RIGHT, GESTURE_SINGLE_TAP))
        setIcon(binding.iconRight, rightAction)

        val leftAction = it.getScreenAction(ButtonInfo(false, ScreenQuadrant.LEFT, GESTURE_SINGLE_TAP))
        setIcon(binding.iconLeft, leftAction)

        for ((code, tile) in swipeGestureRows) {
            val action = it.getScreenAction(ButtonInfo(false, code, GESTURE_SINGLE_TAP))
            setTileIcon(tile, action)
        }

        for ((code, tile) in screenButtonRows) {
            // A slot configured only with a long press still shows that action's icon, matching
            // what the watch itself renders on the mini button.
            val action = it.getScreenAction(ButtonInfo(false, code, GESTURE_SINGLE_TAP))
                    ?: it.getScreenAction(ButtonInfo(false, code, GESTURE_LONG_TAP))
            setTileIcon(tile, action)
        }
    }

    private fun setIcon(imageView: ImageView, phoneAction: PhoneAction?) {
        if (phoneAction == null) {
            imageView.setImageDrawable(null)
            return
        }

        // The watch face preview is always dark, so default (vector) icons are forced white
        // to stay legible. Custom user-picked bitmaps are shown untouched.
        val icon = customIconStorage[phoneAction]
        if (icon is VectorDrawable) {
            icon.mutate().setTint(Color.WHITE)
        }
        imageView.setImageDrawable(icon)
    }

    /** Unlike the watch-face preview icons, a swipe/mini-button tile sits on the app's own
     *  (light or dark) theme background, so its icon follows the theme's on-surface color
     *  instead of being forced white. Falls back to the tile's placeholder icon when
     *  unconfigured. */
    private fun setTileIcon(tile: IconTile, phoneAction: PhoneAction?) {
        val isNotSet = phoneAction == null
        val primaryColor = ContextCompat.getColor(requireContext(), R.color.lyra_on_surface)
        val mutedColor = ContextCompat.getColor(requireContext(), R.color.lyra_text_secondary)

        val icon = if (isNotSet) {
            ContextCompat.getDrawable(requireContext(), tile.defaultIconRes)
        } else {
            customIconStorage[phoneAction]
        }
        if (icon is VectorDrawable) {
            icon.mutate().setColorFilter(if (isNotSet) mutedColor else primaryColor, android.graphics.PorterDuff.Mode.SRC_ATOP)
        }

        tile.binding.icon.setImageDrawable(icon)
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
