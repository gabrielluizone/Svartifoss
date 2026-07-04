package com.svartifoss.snfell.view.buttonconfig

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PorterDuff
import android.graphics.drawable.VectorDrawable
import android.os.Bundle
import android.os.PersistableBundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentContainerView
import com.svartifoss.snfell.R
import com.svartifoss.snfell.actions.NullAction
import com.svartifoss.snfell.actions.PhoneAction
import com.svartifoss.snfell.common.buttonconfig.ButtonGesture
import com.svartifoss.snfell.common.buttonconfig.ButtonInfo
import com.svartifoss.snfell.common.buttonconfig.GESTURE_DOUBLE_TAP
import com.svartifoss.snfell.common.buttonconfig.GESTURE_LONG_TAP
import com.svartifoss.snfell.common.buttonconfig.GESTURE_SINGLE_TAP
import com.svartifoss.snfell.common.buttonconfig.NUM_BUTTON_GESTURES
import com.svartifoss.snfell.config.ActionConfig
import com.svartifoss.snfell.config.CustomIconStorage
import com.svartifoss.snfell.config.buttons.ButtonConfig
import com.svartifoss.snfell.databinding.PopupGesturePickerBinding
import com.svartifoss.snfell.di.LocalActivityConfig
import com.svartifoss.snfell.view.ActivityResultReceiver
import com.google.android.material.button.MaterialButton
import com.svartifoss.snfell.view.actionconfigs.ActionConfigFragment
import com.matejdro.wearutils.miscutils.BitmapUtils
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject

class GesturePickerFragment : DialogFragment() {
    companion object {
        const val REQUEST_CODE_SAVE_NOTIFICATION = 1578

        private const val PARAM_SETS_PLAYBACK_ACTIONS = "SetsPlaybackActions"
        private const val PARAM_BUTTON_INFO = "ButtonInfo"
        private const val PARAM_BUTTON_NAME = "ButtonName"
        private const val PARAM_SUPPORTS_LONG_PRESS = "SupportsLongPress"
        private const val PARAM_SINGLE_ACTION_ONLY = "SingleActionOnly"

        private const val REQUEST_CODE_PICK_ACTION = 5891
        private const val REQUEST_CODE_PICK_ACTION_TO = REQUEST_CODE_PICK_ACTION + NUM_BUTTON_GESTURES

        private const val REQUEST_CODE_PICK_ICON = 5991

        fun newInstance(
            setsPlaybackButtons: Boolean,
            baseButtonInfo: ButtonInfo,
            buttonName: String,
            supportsLongPress: Boolean,
            singleActionOnly: Boolean = false
        ): GesturePickerFragment {
            val fragment = GesturePickerFragment()

            val args = Bundle()
            args.putBoolean(PARAM_SETS_PLAYBACK_ACTIONS, setsPlaybackButtons)
            args.putParcelable(PARAM_BUTTON_INFO, baseButtonInfo.serialize())
            args.putString(PARAM_BUTTON_NAME, buttonName)
            args.putBoolean(PARAM_SUPPORTS_LONG_PRESS, supportsLongPress)
            args.putBoolean(PARAM_SINGLE_ACTION_ONLY, singleActionOnly)

            fragment.arguments = args
            return fragment
        }
    }

    private var setsPlaybackButtons: Boolean = false
    private lateinit var binding: PopupGesturePickerBinding
    private lateinit var baseButtonInfo: ButtonInfo
    private lateinit var buttonName: String
    private var supportsLongPress: Boolean = false

    /** True for swipe directions: there's only one possible gesture (the swipe itself), so the
     *  double-press section (and its caption) is hidden entirely and the single-press section
     *  is relabeled to a generic "Action" instead of "Single press" - a swipe isn't a "press"
     *  the user clicks, it's the gesture that just happened. */
    private var singleActionOnly: Boolean = false

    private lateinit var actions: Array<PhoneAction?>

    @Inject
    @LocalActivityConfig
    lateinit var config: ActionConfig

    @Inject
    lateinit var customIconStorage: CustomIconStorage

    private lateinit var buttonConfig: ButtonConfig
    private lateinit var buttons: Array<ButtonSet>
    private var anyActionChanged = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        baseButtonInfo = ButtonInfo(requireArguments().getParcelable(PARAM_BUTTON_INFO)!!)
        buttonName = requireArguments().getString(PARAM_BUTTON_NAME)!!
        setsPlaybackButtons = requireArguments().getBoolean(PARAM_SETS_PLAYBACK_ACTIONS)
        supportsLongPress = requireArguments().getBoolean(PARAM_SUPPORTS_LONG_PRESS)
        singleActionOnly = requireArguments().getBoolean(PARAM_SINGLE_ACTION_ONLY)

        AndroidSupportInjection.inject(this)

        setStyle(STYLE_NORMAL, R.style.AppTheme_Dialog_Short)

        buttonConfig = if (setsPlaybackButtons)
            config.getPlayingConfig()
        else
            config.getStoppedConfig()

        actions = Array(NUM_BUTTON_GESTURES) {
            buttonConfig.getScreenAction(baseButtonInfo.copy(gesture = it))
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = PopupGesturePickerBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dialog!!.setCanceledOnTouchOutside(true)
        dialog!!.setTitle(buttonName)

        (activity as? com.svartifoss.snfell.view.mainactivity.MainActivity)
            ?.applyAccentToView(view)

        buttons = arrayOf(
                ButtonSet(binding.singlePressButton, binding.singlePressConfigFragment),
                ButtonSet(binding.doublePressButton, binding.doublePressConfigFragment),
                ButtonSet(binding.longPressButton, binding.longPressConfigFragment)
        )

        updateButton(buttons.elementAt(0), GESTURE_SINGLE_TAP)
        updateButton(buttons.elementAt(1), GESTURE_DOUBLE_TAP)
        updateButton(buttons.elementAt(2), GESTURE_LONG_TAP)

        binding.customizeIcon.isVisible = !baseButtonInfo.physicalButton

        binding.longPressDescription.isVisible = supportsLongPress
        binding.longPressButton.isVisible = supportsLongPress

        if (singleActionOnly) {
            binding.singlePressDescription.setText(R.string.gesture_swipe_action)
            binding.doublePressDescription.isVisible = false
            binding.doublePressButton.isVisible = false
        }

        binding.customizeIcon.setOnClickListener { startIconSelection() }
        binding.singlePressButton.setOnClickListener { changeAction(GESTURE_SINGLE_TAP) }
        binding.doublePressButton.setOnClickListener { changeAction(GESTURE_DOUBLE_TAP) }
        binding.longPressButton.setOnClickListener { changeAction(GESTURE_LONG_TAP) }
        binding.okButton.setOnClickListener { save() }
        binding.cancelButton.setOnClickListener { dismiss() }
    }

    private fun changeAction(gesture: Int) {
        val requestCode = REQUEST_CODE_PICK_ACTION + gesture
        startActivityForResult(Intent(activity, ActionPickerActivity::class.java), requestCode)

    }

    private fun updateButton(buttonSet: ButtonSet, @ButtonGesture gesture: Int) {
        val phoneAction = actions[gesture]


        updateButton(buttonSet, phoneAction)
    }

    private fun updateButton(buttonSet: ButtonSet, phoneAction: PhoneAction?) {
        val isNotSet = phoneAction == null || phoneAction is NullAction
        val mutableAction = phoneAction ?: NullAction(requireActivity())

        // "Not set" gets a subtle muted look with a + affordance; real actions use their own icon.
        val primaryColor = ContextCompat.getColor(requireContext(), R.color.lyra_on_surface)
        val mutedColor = ContextCompat.getColor(requireContext(), R.color.lyra_text_secondary)

        var icon = if (isNotSet) {
            ContextCompat.getDrawable(requireContext(), R.drawable.ic_plus)!!
        } else {
            customIconStorage[mutableAction]
        }
        if (icon is VectorDrawable) {
            icon = icon.mutate()
            icon.setColorFilter(if (isNotSet) mutedColor else primaryColor, PorterDuff.Mode.SRC_ATOP)
        }

        buttonSet.button.text = mutableAction.title
        buttonSet.button.setTextColor(if (isNotSet) mutedColor else primaryColor)
        // MaterialButton icon API (not a compound drawable) so the style's iconSize/iconPadding
        // apply. The style start-aligns icon+text, which pins the icon at paddingStart - there
        // is no centered-offset computation left to go stale on a text-only rebind.
        buttonSet.button.icon = icon

        val configFragmentClass = mutableAction.configFragment
        if (configFragmentClass != null) {
            @Suppress("UNCHECKED_CAST")
            val configFragment: ActionConfigFragment<PhoneAction> = configFragmentClass.newInstance() as ActionConfigFragment<PhoneAction>
            childFragmentManager.beginTransaction()
                    .replace(buttonSet.configFragmentContainer.id, configFragment)
                    .commit()

            configFragment.load(mutableAction)

        } else {
            val existingFragment = childFragmentManager.findFragmentById(buttonSet.configFragmentContainer.id)
            existingFragment?.let {
                childFragmentManager.beginTransaction()
                        .remove(it)
                        .commit()
            }
        }
    }

    fun save() {
        val anyActionHasConfigFragment = buttons
                .mapNotNull { childFragmentManager.findFragmentById(it.configFragmentContainer.id) }
                .isNotEmpty()

        if (anyActionChanged || anyActionHasConfigFragment) {
            for ((gesture, action) in actions.withIndex()) {
                val button = buttons[gesture]
                if (action != null) {
                    @Suppress("UNCHECKED_CAST")
                    (childFragmentManager.findFragmentById(button.configFragmentContainer.id) as? ActionConfigFragment<PhoneAction>)
                            ?.save(action)
                }

                val buttonInfo = baseButtonInfo.copy(gesture = gesture)

                buttonConfig.saveButtonAction(buttonInfo, action)
            }

            (activity as ActivityResultReceiver)
                    .onActivityResult(REQUEST_CODE_SAVE_NOTIFICATION, Activity.RESULT_OK, null)
        }

        dismiss()
    }

    private fun startIconSelection() {
        val accent = (activity as? com.svartifoss.snfell.view.mainactivity.MainActivity)
                ?.currentAccentColor()
                ?: com.svartifoss.snfell.view.LyraAccent.resolve(requireContext())

        com.svartifoss.snfell.view.BuiltInIconPicker.show(
                requireActivity(),
                accent,
                onIconPicked = { uri, bitmap -> applyPickedIcon(uri, bitmap) },
                onGalleryRequested = { startGalleryIconSelection() }
        )
    }

    /** Same handling as a gallery pick result, minus the uri decoding (built-in icons hand us
     *  the bitmap directly - their vector resources can't be decoded through a content
     *  stream). */
    private fun applyPickedIcon(iconUri: android.net.Uri, bitmap: android.graphics.Bitmap) {
        val action = actions[GESTURE_SINGLE_TAP] ?: NullAction(requireContext()).also {
            actions[GESTURE_SINGLE_TAP] = it
        }

        action.customIconUri = iconUri
        customIconStorage.setIcon(iconUri, bitmap)

        anyActionChanged = true
        updateButton(buttons[GESTURE_SINGLE_TAP], action)
    }

    private fun startGalleryIconSelection() {
        if (ContextCompat.checkSelfPermission(requireActivity(), Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestStoragePermission()
            return
        }

        try {
            var intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"

            intent = Intent.createChooser(intent, getString(R.string.icon_selection_title))

            startActivityForResult(intent, REQUEST_CODE_PICK_ICON)
        } catch (ignored: ActivityNotFoundException) {
            AlertDialog.Builder(requireContext())
                    .setTitle(R.string.icon_selection_title)
                    .setMessage(R.string.icon_selection_no_icon_pack)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
        }
    }

    private fun requestStoragePermission() {
        AlertDialog.Builder(requireContext())
                .setTitle(R.string.icon_selection_title)
                .setMessage(R.string.icon_selection_no_storage_permission)
                .setPositiveButton(android.R.string.ok, null)
                .setOnDismissListener {
                    requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                            REQUEST_CODE_PICK_ACTION)
                }
                .show()

    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK || data == null) {
            return
        }

        if (requestCode in REQUEST_CODE_PICK_ACTION until REQUEST_CODE_PICK_ACTION_TO) {
            val gesture = requestCode - REQUEST_CODE_PICK_ACTION
            val actionBundle = data.getParcelableExtra<PersistableBundle>(ActionPickerActivity.EXTRA_ACTION_BUNDLE)
            val action = PhoneAction.deserialize<PhoneAction>(requireActivity(), actionBundle)

            anyActionChanged = anyActionChanged || action != actions[gesture]

            actions[gesture] = action
            updateButton(buttons[gesture], action)
        } else if (requestCode == REQUEST_CODE_PICK_ICON) {
            val iconUri = data.data!!

            val action = actions[GESTURE_SINGLE_TAP].let {
                if (it == null) {
                    val newAction = NullAction(requireContext())
                    actions[GESTURE_SINGLE_TAP] = newAction
                    newAction
                } else {
                    it
                }

            }

            val bitmap = BitmapUtils.getBitmap(BitmapUtils.getDrawableFromUri(activity, iconUri)) ?: return

            action.customIconUri = iconUri
            customIconStorage.setIcon(iconUri, bitmap)

            anyActionChanged = true
            updateButton(buttons[GESTURE_SINGLE_TAP], action)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (permissions.isNotEmpty() &&
                permissions[0] == Manifest.permission.READ_EXTERNAL_STORAGE &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startGalleryIconSelection()
        }
    }

    private class ButtonSet(val button: MaterialButton, val configFragmentContainer: FragmentContainerView)
}
