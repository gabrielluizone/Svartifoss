package com.svartifoss.snfell.view.actionlist

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.drawable.NinePatchDrawable
import android.graphics.drawable.VectorDrawable
import android.os.Bundle
import android.os.PersistableBundle
import android.os.Vibrator
import android.provider.Settings
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.h6ah4i.android.widget.advrecyclerview.animator.DraggableItemAnimator
import com.h6ah4i.android.widget.advrecyclerview.draggable.DraggableItemAdapter
import com.h6ah4i.android.widget.advrecyclerview.draggable.ItemDraggableRange
import com.h6ah4i.android.widget.advrecyclerview.draggable.RecyclerViewDragDropManager
import com.h6ah4i.android.widget.advrecyclerview.utils.AbstractDraggableItemViewHolder
import com.svartifoss.snfell.R
import com.svartifoss.snfell.actions.NullAction
import com.svartifoss.snfell.actions.PhoneAction
import com.svartifoss.snfell.actions.appplay.AppPlayAction
import com.svartifoss.snfell.common.QuickPanelButtons
import com.svartifoss.snfell.common.buttonconfig.ButtonInfo
import com.svartifoss.snfell.common.buttonconfig.GESTURE_SINGLE_TAP
import com.svartifoss.snfell.config.ActionConfig
import com.svartifoss.snfell.config.CustomIconStorage
import com.svartifoss.snfell.di.LocalActivityConfig
import com.svartifoss.snfell.view.buttonconfig.ActionPickerActivity
import com.svartifoss.snfell.databinding.FragmentActionListBinding
import com.svartifoss.snfell.di.InjectableViewModelFactory
import com.svartifoss.snfell.util.IdentifiedItem
import com.svartifoss.snfell.view.FabFragment
import com.svartifoss.snfell.view.TitledActivity
import com.matejdro.wearutils.miscutils.VibratorCompat
import com.matejdro.wearutils.preferences.definition.Preferences
import com.matejdro.wearutils.preferencesync.PreferencePusher
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject

@SuppressLint("NotifyDataSetChanged")
class ActionListFragment : Fragment(), FabFragment, RecyclerViewDragDropManager.OnItemDragEventListener {
    companion object {
        const val REQUEST_CODE_EDIT_WINDOW = 1031

        // One request code per quick-panel slot (BASE + slot index 0..2).
        private const val REQUEST_CODE_QUICK_SLOT_BASE = 1040

        private const val STATE_LAST_EDITED_ACTION_POSITION = "LastEditedActionPosition"
    }

    private val viewModel: ActionListViewModel by viewModels { viewModelFactory }
    private lateinit var binding: FragmentActionListBinding
    private lateinit var listItemAdapter: ListItemAdapter
    private lateinit var adapter: RecyclerView.Adapter<ListItemHolder>
    private lateinit var dragDropManager: RecyclerViewDragDropManager
    private lateinit var vibrator: Vibrator

    private var actions: List<IdentifiedItem<PhoneAction>> = emptyList()
    private var ignoreNextUpdate: Boolean = false
    private var lastEditedActionPosition = -1

    @Inject
    lateinit var viewModelFactory: InjectableViewModelFactory<ActionListViewModel>

    @Inject
    lateinit var customIconStorage: CustomIconStorage

    @Inject
    @LocalActivityConfig
    lateinit var actionConfig: ActionConfig

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidSupportInjection.inject(this)
        super.onCreate(savedInstanceState)

        if (savedInstanceState != null) {
            lastEditedActionPosition = savedInstanceState.getInt(STATE_LAST_EDITED_ACTION_POSITION)
        }

        vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_LAST_EDITED_ACTION_POSITION, lastEditedActionPosition)

        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart()
        if (parentFragmentManager.findFragmentById(R.id.fragment_container) !== this) return

        val activity = activity
        if (activity is TitledActivity) {
            activity.updateActivityTitle(getString(R.string.actions_menu))
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentActionListBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recycler = binding.recycler

        dragDropManager = RecyclerViewDragDropManager()
        dragDropManager.setInitiateOnLongPress(true)
        dragDropManager.setInitiateOnMove(false)
        dragDropManager.setInitiateOnTouch(false)
        dragDropManager.onItemDragEventListener = this
        dragDropManager.setDraggingItemShadowDrawable(ResourcesCompat.getDrawable(
                resources,
                R.drawable.material_shadow_z3,
                null) as NinePatchDrawable)

        listItemAdapter = ListItemAdapter()
        @Suppress("UNCHECKED_CAST")
        adapter = dragDropManager.createWrappedAdapter(listItemAdapter) as RecyclerView.Adapter<ListItemHolder>
        recycler.adapter = adapter
        recycler.itemAnimator = DraggableItemAnimator()
        recycler.layoutManager =
                LinearLayoutManager(
                        context,
                        LinearLayoutManager.VERTICAL,
                        false
                )

        recycler.addItemDecoration(
                DividerItemDecoration(
                        context,
                        DividerItemDecoration.VERTICAL
                )
        )
        dragDropManager.attachRecyclerView(recycler)

        setupQuickPanelToggles()
    }

    /** The quick-actions-panel entry row above the list: opens a dialog with the four panel
     *  slots (three buttons + the long row), mirroring the watch panel opened by
     *  double-tapping the center of the now-playing screen. Tapping a slot row opens the
     *  action picker; long-press restores the slot's classic default. Slot assignments are
     *  written into BOTH the playing and stopped button configs so the panel behaves the same
     *  regardless of playback state. */
    private fun setupQuickPanelToggles() {
        binding.quickPanelRow.setOnClickListener { showQuickPanelDialog() }
        refreshQuickPanelSummary()
    }

    override fun onResume() {
        super.onResume()
        refreshQuickPanelSummary()
    }

    private val quickSlotCodes = intArrayOf(
            QuickPanelButtons.SLOT_1,
            QuickPanelButtons.SLOT_2,
            QuickPanelButtons.SLOT_3,
            QuickPanelButtons.SLOT_LONG
    )

    private fun assignedQuickAction(index: Int): PhoneAction? =
            actionConfig.getPlayingConfig().getScreenAction(
                    ButtonInfo(false, quickSlotCodes[index], GESTURE_SINGLE_TAP))

    private fun quickSlotDefaultName(index: Int): String = getString(when (index) {
        0 -> R.string.quick_panel_default_like
        1 -> R.string.quick_panel_default_shuffle
        2 -> R.string.quick_panel_default_repeat
        else -> R.string.quick_panel_default_up_next
    })

    private fun quickSlotSummary(index: Int): String {
        val assigned = assignedQuickAction(index)
        return when {
            assigned == null -> getString(R.string.quick_panel_slot_default, quickSlotDefaultName(index))
            assigned is NullAction -> getString(R.string.quick_panel_slot_hidden)
            else -> assigned.title
        }
    }

    private fun refreshQuickPanelSummary() {
        if (!::binding.isInitialized) {
            return
        }
        binding.quickPanelRowSummary.text = (0 until quickSlotCodes.size)
                .joinToString(" · ") { index ->
                    val assigned = assignedQuickAction(index)
                    when {
                        assigned == null -> quickSlotDefaultName(index)
                        assigned is NullAction -> getString(R.string.quick_panel_slot_hidden)
                        else -> assigned.title
                    }
                }
    }

    private fun saveQuickPanelSlot(index: Int, action: PhoneAction?) {
        val buttonInfo = ButtonInfo(false, quickSlotCodes[index], GESTURE_SINGLE_TAP)
        for (config in listOf(actionConfig.getPlayingConfig(), actionConfig.getStoppedConfig())) {
            config.saveButtonAction(buttonInfo, action)
            config.commit()
        }
        refreshQuickPanelSummary()
    }

    private fun showQuickPanelDialog() {
        val inflater = LayoutInflater.from(requireContext())
        val container = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
        }

        val hint = TextView(requireContext()).apply {
            text = getString(R.string.quick_panel_config_hint)
            textSize = 13f
            setTextColor(ContextCompat.getColor(requireContext(), R.color.lyra_pref_summary))
            typeface = ResourcesCompat.getFont(requireContext(), R.font.google_sans)
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, (8 * resources.displayMetrics.density).toInt())
        }
        container.addView(hint)

        val slotTitles = intArrayOf(
                R.string.quick_panel_slot_1,
                R.string.quick_panel_slot_2,
                R.string.quick_panel_slot_3,
                R.string.quick_panel_slot_long
        )
        val defaultIcons = intArrayOf(
                com.svartifoss.snfell.common.R.drawable.action_like,
                com.svartifoss.snfell.common.R.drawable.action_shuffle,
                com.svartifoss.snfell.common.R.drawable.action_repeat,
                R.drawable.ic_playlist_play
        )

        lateinit var dialog: androidx.appcompat.app.AlertDialog

        for (index in quickSlotCodes.indices) {
            val row = inflater.inflate(R.layout.item_quick_panel_slot, container, false)
            val iconView = row.findViewById<ImageView>(R.id.slot_icon)
            val titleView = row.findViewById<TextView>(R.id.slot_title)
            val summaryView = row.findViewById<TextView>(R.id.slot_summary)

            fun bindRow() {
                titleView.setText(slotTitles[index])
                summaryView.text = quickSlotSummary(index)

                val assigned = assignedQuickAction(index)
                val icon = if (assigned != null && assigned !is NullAction) {
                    customIconStorage[assigned]
                } else {
                    ContextCompat.getDrawable(requireContext(), defaultIcons[index])
                }
                if (icon is VectorDrawable) {
                    iconView.setColorFilter(
                            ContextCompat.getColor(requireContext(), R.color.lyra_on_surface))
                } else {
                    iconView.clearColorFilter()
                }
                iconView.setImageDrawable(icon)
                iconView.alpha = if (assigned is NullAction) 0.4f else 1f
            }
            bindRow()

            row.setOnClickListener {
                dialog.dismiss()
                startActivityForResult(
                        Intent(context, ActionPickerActivity::class.java),
                        REQUEST_CODE_QUICK_SLOT_BASE + index
                )
            }
            row.setOnLongClickListener {
                saveQuickPanelSlot(index, null)
                bindRow()
                true
            }

            container.addView(row)
        }

        dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.quick_panel_row_title)
                .setView(android.widget.ScrollView(requireContext()).apply { addView(container) })
                .setPositiveButton(android.R.string.ok, null)
                .show()

        val accent = (activity as? com.svartifoss.snfell.view.mainactivity.MainActivity)
                ?.currentAccentColor()
                ?: com.svartifoss.snfell.view.LyraAccent.resolve(requireContext())
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)?.setTextColor(accent)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        viewModel.actions.observe(viewLifecycleOwner, actionListListener)
        viewModel.openActionEditor.observe(viewLifecycleOwner, openEditDialogListener)
    }

    override fun onDestroy() {
        super.onDestroy()

        dragDropManager.release()
    }

    override fun onFabClicked() {
        val intent = Intent(context, ActionEditorActivity::class.java)
        startActivityForResult(intent, REQUEST_CODE_EDIT_WINDOW)
    }

    override fun prepareFab(fab: FloatingActionButton) {
        fab.contentDescription = getString(R.string.actions_add)
    }

    private val actionListListener = Observer<List<IdentifiedItem<PhoneAction>>?> {
        if (it == null) {
            return@Observer
        }

        this.actions = it

        binding.actionListEmpty.visibility = if (it.isEmpty()) View.VISIBLE else View.GONE
        binding.recycler.visibility = if (it.isEmpty()) View.GONE else View.VISIBLE
        binding.actionListSummary.text = resources.getQuantityString(
                R.plurals.actions_list_summary,
                it.size,
                it.size
        )

        if (!ignoreNextUpdate) {
            adapter.notifyDataSetChanged()
        }
        ignoreNextUpdate = false
    }

    private val openEditDialogListener = Observer<Int?> {
        if (it == null || it < 0) {
            return@Observer
        }

        lastEditedActionPosition = it

        val intent = Intent(context, ActionEditorActivity::class.java)
        intent.putExtra(ActionEditorActivity.EXTRA_ACTION, actions[it].item.serialize())
        startActivityForResult(intent, REQUEST_CODE_EDIT_WINDOW)
    }

    private fun buzz() {
        if (isHapticEnabled()) {
            VibratorCompat.vibrate(vibrator, 25)
        }
    }

    private fun isHapticEnabled(): Boolean {
        val contentResolver = requireActivity().contentResolver

        val setting = Settings.System.getInt(contentResolver,
                Settings.System.HAPTIC_FEEDBACK_ENABLED, 0)
        return setting != 0
    }

    override fun onItemDragStarted(position: Int) {
        buzz()
    }

    override fun onItemDragPositionChanged(fromPosition: Int, toPosition: Int) = Unit

    override fun onItemDragFinished(fromPosition: Int, toPosition: Int, result: Boolean) {
        buzz()
    }

    override fun onItemDragMoveDistanceUpdated(offsetX: Int, offsetY: Int) = Unit

    /** Moves an action without requiring a drag gesture. TalkBack exposes this through the
     *  custom "Move up/down" actions below; hardware keyboards use Ctrl+Up/Down. */
    private fun moveItemAccessibly(holder: ListItemHolder, offset: Int): Boolean {
        val fromPosition = holder.bindingAdapterPosition
        val toPosition = fromPosition + offset
        if (fromPosition == RecyclerView.NO_POSITION || toPosition !in actions.indices) {
            return false
        }

        val actionTitle = actions[fromPosition].item.title
        ignoreNextUpdate = true
        viewModel.moveItem(fromPosition, toPosition)
        listItemAdapter.notifyItemMoved(fromPosition, toPosition)

        holder.itemView.post {
            holder.itemView.requestFocus()
            holder.itemView.announceForAccessibility(
                    getString(
                            R.string.actions_moved_to_position,
                            actionTitle,
                            toPosition + 1,
                            actions.size
                    )
            )
        }
        return true
    }


    private inner class ListItemAdapter : RecyclerView.Adapter<ListItemHolder>(),
            DraggableItemAdapter<ListItemHolder> {
        init {
            setHasStableIds(true)
        }

        override fun onBindViewHolder(holder: ListItemHolder, position: Int) {
            val phoneAction = actions[position].item

            holder.text.text = phoneAction.title


            val icon = customIconStorage[phoneAction]
            if (icon is VectorDrawable) {
                // Theme-aware, not Color.BLACK: a hardcoded black icon disappears against
                // the dark theme's circle background.
                holder.icon.setColorFilter(
                        ContextCompat.getColor(requireContext(), R.color.lyra_on_surface))
            } else {
                holder.icon.clearColorFilter()
            }
            holder.icon.setImageDrawable(icon)

            // App launcher icons already carry their own shape - the circle backdrop makes
            // them read as an icon-in-a-dish, so it stays reserved for glyph-style icons.
            val density = resources.displayMetrics.density
            if (phoneAction is AppPlayAction && phoneAction.customIconUri == null) {
                holder.icon.background = null
                val pad = (3 * density).toInt()
                holder.icon.setPadding(pad, pad, pad, pad)
            } else {
                holder.icon.setBackgroundResource(R.drawable.circle_icon_bg)
                val pad = (10 * density).toInt()
                holder.icon.setPadding(pad, pad, pad, pad)
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ListItemHolder {
            val view = layoutInflater.inflate(R.layout.item_action_list, parent, false)
            return ListItemHolder(view)
        }

        override fun getItemCount(): Int = actions.size

        override fun getItemId(position: Int): Long = actions[position].id.toLong()

        override fun onGetItemDraggableRange(holder: ListItemHolder, position: Int): ItemDraggableRange? = null

        override fun onCheckCanDrop(draggingPosition: Int, dropPosition: Int): Boolean = true

        override fun onCheckCanStartDrag(holder: ListItemHolder, position: Int, x: Int, y: Int): Boolean {
            return true
        }

        override fun onMoveItem(fromPosition: Int, toPosition: Int) {
            ignoreNextUpdate = true
            viewModel.moveItem(fromPosition, toPosition)
        }

        override fun onItemDragStarted(position: Int) = Unit

        override fun onItemDragFinished(fromPosition: Int, toPosition: Int, result: Boolean) = Unit
    }

    private inner class ListItemHolder(itemView: View) : AbstractDraggableItemViewHolder(itemView) {
        val icon: ImageView = itemView.findViewById(R.id.icon)
        val text: TextView = itemView.findViewById(R.id.text)

        init {
            itemView.setOnClickListener {
                viewModel.editAction(adapterPosition)
            }

            ViewCompat.setAccessibilityDelegate(itemView, object : AccessibilityDelegateCompat() {
                override fun onInitializeAccessibilityNodeInfo(
                    host: View,
                    info: AccessibilityNodeInfoCompat
                ) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    val position = bindingAdapterPosition
                    if (position == RecyclerView.NO_POSITION) return

                    if (position > 0) {
                        info.addAction(
                                AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                                        R.id.accessibility_action_move_up,
                                        getString(R.string.actions_move_up)
                                )
                        )
                    }
                    if (position < actions.lastIndex) {
                        info.addAction(
                                AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                                        R.id.accessibility_action_move_down,
                                        getString(R.string.actions_move_down)
                                )
                        )
                    }
                }

                override fun performAccessibilityAction(
                    host: View,
                    action: Int,
                    args: Bundle?
                ): Boolean = when (action) {
                    R.id.accessibility_action_move_up -> moveItemAccessibly(this@ListItemHolder, -1)
                    R.id.accessibility_action_move_down -> moveItemAccessibly(this@ListItemHolder, 1)
                    else -> super.performAccessibilityAction(host, action, args)
                }
            })

            itemView.setOnKeyListener { _, keyCode, event ->
                val reorderKey = keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                        keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                if (!event.isCtrlPressed || !reorderKey) {
                    false
                } else {
                    if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                        moveItemAccessibly(
                                this@ListItemHolder,
                                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) -1 else 1
                        )
                    }
                    true
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode in REQUEST_CODE_QUICK_SLOT_BASE until REQUEST_CODE_QUICK_SLOT_BASE + quickSlotCodes.size) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                val actionBundle = data.getParcelableExtra<PersistableBundle>(
                        ActionPickerActivity.EXTRA_ACTION_BUNDLE)
                val action = PhoneAction.deserialize<PhoneAction>(requireContext(), actionBundle)
                if (action != null) {
                    saveQuickPanelSlot(requestCode - REQUEST_CODE_QUICK_SLOT_BASE, action)
                }
            }
            return
        }

        if (requestCode == REQUEST_CODE_EDIT_WINDOW &&
                resultCode == Activity.RESULT_OK &&
                data != null) {
            if (data.getBooleanExtra(ActionEditorActivity.EXTRA_DELETING, false) && lastEditedActionPosition >= 0) {
                viewModel.deleteAction(lastEditedActionPosition)
                lastEditedActionPosition = -1
                return
            }

            val actionBundle = data.getParcelableExtra<PersistableBundle>(
                    ActionEditorActivity.EXTRA_ACTION) ?: return

            val newAction = PhoneAction.deserialize<PhoneAction>(requireContext(), actionBundle) ?: return

            if (lastEditedActionPosition < 0) {
                viewModel.addAction(newAction)
            } else {
                viewModel.actionEditFinished(newAction, lastEditedActionPosition)
            }

            lastEditedActionPosition = -1
        }

        super.onActivityResult(requestCode, resultCode, data)
    }
}
