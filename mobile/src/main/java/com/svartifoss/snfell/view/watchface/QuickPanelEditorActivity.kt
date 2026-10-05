package com.svartifoss.snfell.view.watchface

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.NinePatchDrawable
import android.os.Bundle
import android.os.PersistableBundle
import android.os.Vibrator
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.h6ah4i.android.widget.advrecyclerview.animator.DraggableItemAnimator
import com.h6ah4i.android.widget.advrecyclerview.draggable.DraggableItemAdapter
import com.h6ah4i.android.widget.advrecyclerview.draggable.ItemDraggableRange
import com.h6ah4i.android.widget.advrecyclerview.draggable.RecyclerViewDragDropManager
import com.h6ah4i.android.widget.advrecyclerview.utils.AbstractDraggableItemViewHolder
import com.matejdro.wearutils.miscutils.VibratorCompat
import com.matejdro.wearutils.preferences.definition.Preferences
import com.svartifoss.snfell.R
import com.svartifoss.snfell.actions.NullAction
import com.svartifoss.snfell.actions.PhoneAction
import com.svartifoss.snfell.common.ActionsMode
import com.svartifoss.snfell.common.FavoritesMode
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.common.QuickPanelBlock
import com.svartifoss.snfell.common.QuickPanelBlockType
import com.svartifoss.snfell.common.QuickPanelButtons
import com.svartifoss.snfell.common.QuickPanelSource
import com.svartifoss.snfell.common.QuickPanelStack
import com.svartifoss.snfell.common.QuickPanelTool
import com.svartifoss.snfell.common.QuickPanelVisibility
import com.svartifoss.snfell.common.buttonconfig.ButtonInfo
import com.svartifoss.snfell.common.buttonconfig.GESTURE_SINGLE_TAP
import com.svartifoss.snfell.config.ActionConfig
import com.svartifoss.snfell.config.CustomIconStorage
import com.svartifoss.snfell.di.GlobalConfig
import com.svartifoss.snfell.view.LyraAccent
import com.svartifoss.snfell.view.applyLyraDialogStyling
import com.svartifoss.snfell.view.buttonconfig.ActionPickerActivity
import com.svartifoss.snfell.view.buttonconfig.ActionPickerSurface
import dagger.android.AndroidInjection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * The quick actions panel's one editing surface.
 *
 * The panel is a list of blocks (see [QuickPanelStack]); this screen shows it the way the watch
 * draws it, docked above, with the blocks beneath in order. A block is moved by holding and
 * dragging, adjusted by tapping, added from the button below and removed from its own sheet. The
 * looks - layout, style, background - stay where they were, on the Watch tab's Panels page, because
 * they belong to the face being worn; what the panel *holds* belongs to the person, and used to be
 * spread across the Actions tab (hidden outright while the buttons came from the playing app), the
 * Controls tab and that page.
 *
 * Nothing is written until something is changed: opening the editor on a panel nobody has composed
 * shows the original arrangement, and the first edit starts from exactly that, so adopting blocks
 * is not itself a visible change.
 */
class QuickPanelEditorActivity : AppCompatActivity(),
        RecyclerViewDragDropManager.OnItemDragEventListener {

    @Inject
    @GlobalConfig
    lateinit var actionConfig: ActionConfig

    @Inject
    lateinit var customIconStorage: CustomIconStorage

    private lateinit var prefs: SharedPreferences
    private lateinit var preview: WatchPreviewView
    private lateinit var recycler: RecyclerView
    private lateinit var dragDropManager: RecyclerViewDragDropManager
    private lateinit var blockAdapter: BlockAdapter
    private lateinit var wrappedAdapter: RecyclerView.Adapter<BlockHolder>
    private lateinit var vibrator: Vibrator

    private var blocks: MutableList<QuickPanelBlock> = ArrayList()
    private var accent = 0
    private var counts = QuickPanelFold.Counts(starredFavorites = 0, menuActions = 0)

    /** The sheet showing one block's options, so a change made elsewhere can refresh it. */
    private var openSheet: BottomSheetDialog? = null
    private var rebuildSheet: (() -> Unit)? = null

    private var pendingSlot = -1
    private val slotPicker = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()) { result ->
        val slot = pendingSlot
        pendingSlot = -1
        if (slot < 0 || result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val bundle = result.data?.getParcelableExtra<PersistableBundle>(
                ActionPickerActivity.EXTRA_ACTION_BUNDLE)
        val action = PhoneAction.deserialize<PhoneAction>(this, bundle) ?: return@registerForActivityResult
        saveSlot(slot, action)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quick_panel_editor)

        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        accent = LyraAccent.resolve(this)
        blocks = QuickPanelStack.editable(
                Preferences.getString(prefs, MiscPreferences.WEAR_QUICK_PANEL_BLOCKS)).toMutableList()

        findViewById<View>(R.id.button_back).setOnClickListener { finish() }
        findViewById<View>(R.id.button_more).setOnClickListener(::showMoreMenu)
        findViewById<MaterialButton>(R.id.add_block).apply {
            setOnClickListener { showAddSheet() }
            iconTint = ColorStateList.valueOf(accent)
        }

        preview = findViewById(R.id.quick_preview)
        preview.showPreviewSurface(WatchPreviewView.PreviewSurface.QUICK_PANEL)

        setUpRecycler()
    }

    override fun onResume() {
        super.onResume()
        reloadPreviewData()
    }

    override fun onDestroy() {
        openSheet?.dismiss()
        dragDropManager.release()
        super.onDestroy()
    }

    // --- The list ---------------------------------------------------------------------------

    private fun setUpRecycler() {
        recycler = findViewById(R.id.recycler)
        dragDropManager = RecyclerViewDragDropManager().apply {
            setInitiateOnLongPress(true)
            setInitiateOnMove(false)
            setInitiateOnTouch(false)
            onItemDragEventListener = this@QuickPanelEditorActivity
            setDraggingItemShadowDrawable(ResourcesCompat.getDrawable(
                    resources, R.drawable.material_shadow_z3, null) as NinePatchDrawable)
        }
        blockAdapter = BlockAdapter()
        @Suppress("UNCHECKED_CAST")
        wrappedAdapter = dragDropManager.createWrappedAdapter(blockAdapter)
                as RecyclerView.Adapter<BlockHolder>
        recycler.adapter = wrappedAdapter
        recycler.itemAnimator = DraggableItemAnimator()
        recycler.layoutManager = LinearLayoutManager(this)
        dragDropManager.attachRecyclerView(recycler)
    }

    /** What the preview and the fold marker need from the user's own lists - read off disk, off the
     *  main thread, every time the screen comes back (stars are set on another tab). */
    private fun reloadPreviewData() {
        lifecycleScope.launch {
            val (icons, menu) = withContext(Dispatchers.IO) {
                MiniButtonIconLoader.loadConfiguredIcons(
                        this@QuickPanelEditorActivity, playing = true) to
                        MiniButtonIconLoader.loadMenuEntries(this@QuickPanelEditorActivity)
            }
            preview.setButtonIcons(icons)
            preview.setMenuEntries(menu)
            counts = QuickPanelFold.Counts(
                    starredFavorites = menu.count { it.inQuickPanel },
                    menuActions = menu.size)
            preview.refresh()
            blockAdapter.notifyDataSetChanged()
            rebuildSheet?.invoke()
        }
    }

    /** Writes the stack and redraws everything that shows it. */
    private fun persist() {
        prefs.edit()
                .putString(MiscPreferences.WEAR_QUICK_PANEL_BLOCKS.key, QuickPanelStack.encode(blocks))
                .apply()
        preview.refresh(MiscPreferences.WEAR_QUICK_PANEL_BLOCKS.key)
        blockAdapter.notifyDataSetChanged()
    }

    private fun update(block: QuickPanelBlock) {
        val index = blocks.indexOfFirst { it.type == block.type }
        if (index < 0) return
        blocks[index] = block
        persist()
    }

    /**
     * The index of the block after which the watch's first screen ends, over the blocks that are
     * on screen while music plays - the ones for idle only are not what the panel opens to.
     */
    private fun foldIndex(): Int {
        val shown = blocks.withIndex().filter { it.value.visibility != QuickPanelVisibility.NOT_PLAYING }
        val last = QuickPanelFold.lastOnFirstScreen(shown.map { it.value }, counts)
        // No marker when everything fits: there is no fold to speak of.
        return if (last in 0 until shown.lastIndex) shown[last].index else -1
    }

    private inner class BlockAdapter : RecyclerView.Adapter<BlockHolder>(),
            DraggableItemAdapter<BlockHolder> {
        init {
            setHasStableIds(true)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BlockHolder =
                BlockHolder(layoutInflater.inflate(R.layout.item_quick_panel_block, parent, false))

        override fun getItemCount(): Int = blocks.size

        override fun getItemId(position: Int): Long = blocks[position].type.ordinal.toLong()

        override fun onBindViewHolder(holder: BlockHolder, position: Int) {
            val block = blocks[position]
            holder.icon.setImageResource(iconOf(block.type))
            holder.icon.setColorFilter(ContextCompat.getColor(this@QuickPanelEditorActivity, R.color.lyra_on_surface))
            holder.title.setText(titleOf(block.type))
            holder.summary.text = summaryOf(block)
            holder.fold.visibility = if (position == foldIndex()) View.VISIBLE else View.GONE
            holder.fold.setTextColor(LyraAccent.contrastSafe(
                    this@QuickPanelEditorActivity, accent))
        }

        override fun onGetItemDraggableRange(holder: BlockHolder, position: Int): ItemDraggableRange? = null

        override fun onCheckCanDrop(draggingPosition: Int, dropPosition: Int): Boolean = true

        override fun onCheckCanStartDrag(holder: BlockHolder, position: Int, x: Int, y: Int): Boolean = true

        override fun onMoveItem(fromPosition: Int, toPosition: Int) {
            // The saved order is written once the drag ends, not on every row it passes over.
            blocks.add(toPosition, blocks.removeAt(fromPosition))
        }

        override fun onItemDragStarted(position: Int) = Unit

        override fun onItemDragFinished(fromPosition: Int, toPosition: Int, result: Boolean) = Unit
    }

    private inner class BlockHolder(itemView: View) : AbstractDraggableItemViewHolder(itemView) {
        val icon: ImageView = itemView.findViewById(R.id.block_icon)
        val title: TextView = itemView.findViewById(R.id.block_title)
        val summary: TextView = itemView.findViewById(R.id.block_summary)
        val fold: TextView = itemView.findViewById(R.id.block_fold)

        init {
            itemView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) showBlockSheet(blocks[position].type)
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
                        info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                                R.id.accessibility_action_move_up,
                                getString(R.string.quick_panel_editor_move_up)))
                    }
                    if (position < blocks.lastIndex) {
                        info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                                R.id.accessibility_action_move_down,
                                getString(R.string.quick_panel_editor_move_down)))
                    }
                }

                override fun performAccessibilityAction(
                        host: View,
                        action: Int,
                        args: Bundle?
                ): Boolean = when (action) {
                    R.id.accessibility_action_move_up -> moveBy(this@BlockHolder, -1)
                    R.id.accessibility_action_move_down -> moveBy(this@BlockHolder, 1)
                    else -> super.performAccessibilityAction(host, action, args)
                }
            })
        }
    }

    /** Moves a block one place without a drag - what TalkBack's custom actions call. */
    private fun moveBy(holder: BlockHolder, offset: Int): Boolean {
        val from = holder.bindingAdapterPosition
        val to = from + offset
        if (from == RecyclerView.NO_POSITION || to !in blocks.indices) return false
        blocks = QuickPanelStack.moveTo(blocks, from, to).toMutableList()
        persist()
        return true
    }

    override fun onItemDragStarted(position: Int) = buzz()

    override fun onItemDragPositionChanged(fromPosition: Int, toPosition: Int) = Unit

    override fun onItemDragFinished(fromPosition: Int, toPosition: Int, result: Boolean) {
        buzz()
        if (result && fromPosition != toPosition) persist() else blockAdapter.notifyDataSetChanged()
    }

    override fun onItemDragMoveDistanceUpdated(offsetX: Int, offsetY: Int) = Unit

    private fun buzz() {
        val enabled = Settings.System.getInt(
                contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 0) != 0
        if (enabled) VibratorCompat.vibrate(vibrator, 25)
    }

    // --- What each block is called and looks like in the list -----------------------------------

    private fun titleOf(type: QuickPanelBlockType): Int = QuickPanelBlockText.title(type)

    private fun descriptionOf(type: QuickPanelBlockType): Int = QuickPanelBlockText.description(type)

    private fun iconOf(type: QuickPanelBlockType): Int = QuickPanelBlockText.icon(type)

    private fun toolName(tool: QuickPanelTool): String = getString(when (tool) {
        QuickPanelTool.SPEED -> R.string.quick_tool_speed
        QuickPanelTool.SLEEP -> R.string.quick_tool_sleep
        QuickPanelTool.LYRICS -> R.string.quick_tool_lyrics
        QuickPanelTool.QUEUE -> R.string.quick_tool_queue
        QuickPanelTool.VOLUME -> R.string.quick_tool_volume
        QuickPanelTool.PROGRESS -> R.string.quick_tool_progress
        QuickPanelTool.FACES -> R.string.quick_tool_faces
        QuickPanelTool.SEARCH -> R.string.quick_tool_search
        QuickPanelTool.MENU -> R.string.quick_tool_menu
    })

    private fun summaryOf(block: QuickPanelBlock): String {
        val base = when (block.type) {
            QuickPanelBlockType.HEADER -> getString(R.string.quick_summary_header)
            QuickPanelBlockType.BUTTONS -> buttonsSummary()
            QuickPanelBlockType.UP_NEXT -> getString(R.string.quick_summary_upnext)
            QuickPanelBlockType.VOLUME ->
                getString(R.string.quick_summary_volume, (block.volumeStep * 100f).roundToInt())
            QuickPanelBlockType.SEEK -> getString(
                    if (block.seekShowsBar) R.string.quick_summary_seek_bar
                    else R.string.quick_summary_seek,
                    block.seekSteps.joinToString(" · ") { "±$it" })
            QuickPanelBlockType.TOOLS -> block.tools.joinToString(" · ", transform = ::toolName)
            QuickPanelBlockType.FAVORITES -> favoritesSummary(block)
            QuickPanelBlockType.ACTIONS -> when {
                block.actionsMode == ActionsMode.BUTTON ->
                    getString(R.string.quick_summary_actions_button)
                block.maxEntries > 0 ->
                    getString(R.string.quick_summary_actions_first, block.maxEntries)
                else -> getString(R.string.quick_summary_actions_all, counts.menuActions)
            }
        }
        val when_ = when (block.visibility) {
            QuickPanelVisibility.ALWAYS -> null
            QuickPanelVisibility.PLAYING -> getString(R.string.quick_summary_only_playing)
            QuickPanelVisibility.NOT_PLAYING -> getString(R.string.quick_summary_only_not_playing)
        }
        return listOfNotNull(base, when_).joinToString(" · ")
    }

    private fun favoritesSummary(block: QuickPanelBlock): String {
        if (counts.starredFavorites == 0) return getString(R.string.quick_summary_favorites_none)
        val layout = getString(
                if (block.favoritesMode == FavoritesMode.GRID) R.string.quick_mode_grid
                else R.string.quick_mode_rows)
        return resources.getQuantityString(
                R.plurals.quick_summary_favorites,
                counts.starredFavorites, counts.starredFavorites, layout)
    }

    private fun buttonsSummary(): String {
        if (!QuickPanelSource.usesConfiguredButtons(
                        Preferences.getString(prefs, MiscPreferences.WEAR_QUICK_PANEL_SOURCE))) {
            return getString(R.string.quick_summary_buttons_session)
        }
        val names = QuickPanelButtons.ALL_SLOTS.indices.mapNotNull { index ->
            when (val assigned = assignedSlotAction(index)) {
                null -> slotDefaultName(index)
                is NullAction -> null
                else -> assigned.title
            }
        }
        return if (names.isEmpty()) getString(R.string.quick_summary_buttons_empty)
        else names.joinToString(" · ")
    }

    // --- The options of one block -----------------------------------------------------------

    private fun showBlockSheet(type: QuickPanelBlockType) {
        val view = layoutInflater.inflate(R.layout.sheet_quick_panel_block, null)
        val sheet = BottomSheetDialog(this)
        sheet.setContentView(view)
        sheet.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        sheet.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let {
            BottomSheetBehavior.from(it).apply {
                skipCollapsed = true
                state = BottomSheetBehavior.STATE_EXPANDED
            }
        }

        view.findViewById<TextView>(R.id.sheet_title).setText(titleOf(type))
        view.findViewById<TextView>(R.id.sheet_subtitle).setText(descriptionOf(type))
        val content = view.findViewById<LinearLayout>(R.id.sheet_content)
        view.findViewById<MaterialButton>(R.id.sheet_remove).setOnClickListener {
            sheet.dismiss()
            removeBlock(type)
        }

        val rebuild = { buildSheetContent(content, type) }
        rebuildSheet = rebuild
        rebuild()
        sheet.setOnDismissListener {
            if (openSheet === sheet) {
                openSheet = null
                rebuildSheet = null
            }
        }
        openSheet = sheet
        sheet.show()
    }

    private fun currentBlock(type: QuickPanelBlockType): QuickPanelBlock? =
            blocks.firstOrNull { it.type == type }

    private fun buildSheetContent(content: LinearLayout, type: QuickPanelBlockType) {
        content.removeAllViews()
        val block = currentBlock(type) ?: return

        // When it is shown: the same three choices for every block.
        addChoiceRow(content, R.string.quick_sheet_show,
                listOf(
                        getString(R.string.quick_sheet_always),
                        getString(R.string.quick_sheet_while_playing),
                        getString(R.string.quick_sheet_while_not_playing)),
                selected = QuickPanelVisibility.entries.indexOf(block.visibility)) { index ->
            update(block.withVisibility(QuickPanelVisibility.entries[index]))
            buildSheetContent(content, type)
        }

        when (type) {
            QuickPanelBlockType.VOLUME -> addChoiceRow(content, R.string.quick_sheet_step,
                    QuickPanelStack.VOLUME_STEPS.map { "$it%" },
                    selected = QuickPanelStack.VOLUME_STEPS.indexOf((block.volumeStep * 100f).roundToInt())
            ) { index ->
                update(block.withOption(QuickPanelStack.OPTION_STEP, QuickPanelStack.VOLUME_STEPS[index].toString()))
                buildSheetContent(content, type)
            }

            QuickPanelBlockType.SEEK -> {
                val presets = SEEK_PRESETS
                addChoiceRow(content, R.string.quick_sheet_jump,
                        presets.map { preset -> preset.joinToString(" + ") { "$it s" } },
                        selected = presets.indexOf(block.seekSteps)
                ) { index ->
                    update(block.withSeekSteps(presets[index]))
                    buildSheetContent(content, type)
                }
                addSwitchRow(content, R.string.quick_sheet_seek_bar, block.seekShowsBar) { checked ->
                    update(block.withOption(QuickPanelStack.OPTION_BAR, if (checked) "1" else "0"))
                }
            }

            QuickPanelBlockType.TOOLS -> addToolsRow(content, block)

            QuickPanelBlockType.FAVORITES -> {
                addChoiceRow(content, R.string.quick_sheet_layout,
                        listOf(getString(R.string.quick_mode_grid), getString(R.string.quick_mode_rows)),
                        selected = FavoritesMode.entries.indexOf(block.favoritesMode)
                ) { index ->
                    update(block.withOption(QuickPanelStack.OPTION_MODE, FavoritesMode.entries[index].token))
                    buildSheetContent(content, type)
                }
                addLimitRow(content, block, QuickPanelStack.FAVORITES_LIMITS, type)
                addHint(content, R.string.quick_sheet_favorites_hint)
            }

            QuickPanelBlockType.ACTIONS -> {
                // Whether the menu is on the panel as rows or behind one button. Removing the
                // block is the third answer, and it is on the sheet below.
                addChoiceRow(content, R.string.quick_sheet_actions_mode,
                        listOf(
                                getString(R.string.quick_mode_actions_list),
                                getString(R.string.quick_mode_actions_button)),
                        selected = ActionsMode.entries.indexOf(block.actionsMode)
                ) { index ->
                    update(block.withActionsMode(ActionsMode.entries[index]))
                    buildSheetContent(content, type)
                }
                // How many rows only means something while there are rows.
                if (block.actionsMode == ActionsMode.LIST) {
                    addLimitRow(content, block, QuickPanelStack.ACTIONS_LIMITS, type)
                }
                addHint(content, R.string.quick_sheet_actions_hint)
            }

            QuickPanelBlockType.BUTTONS -> addButtonsSection(content, type)

            QuickPanelBlockType.HEADER,
            QuickPanelBlockType.UP_NEXT -> Unit
        }
    }

    private fun addLimitRow(
            content: LinearLayout,
            block: QuickPanelBlock,
            limits: List<Int>,
            type: QuickPanelBlockType
    ) {
        addChoiceRow(content, R.string.quick_sheet_how_many,
                limits.map { if (it == 0) getString(R.string.quick_sheet_all) else it.toString() },
                selected = limits.indexOf(block.maxEntries)
        ) { index ->
            update(block.withOption(QuickPanelStack.OPTION_MAX, limits[index].toString()))
            buildSheetContent(content, type)
        }
    }

    /** The tools, each switched on or off; the panel keeps them in this order. */
    private fun addToolsRow(content: LinearLayout, block: QuickPanelBlock) {
        val on = block.tools.toSet()
        addSectionLabel(content, R.string.quick_sheet_tools)
        val group = chipGroup(content)
        QuickPanelTool.entries.forEach { tool ->
            val selected = tool in on
            val chip = newChip(toolName(tool), selected)
            chip.setOnClickListener {
                val next = QuickPanelTool.entries.filter { (it in on) != (it == tool) }
                // A tools block with nothing in it would draw nothing, so the last one stays.
                if (next.isEmpty()) return@setOnClickListener
                update(block.withTools(next))
                buildSheetContent(content, QuickPanelBlockType.TOOLS)
            }
            group.addView(chip)
        }
    }

    // --- The buttons block: where its three buttons come from, and what they do ----------------

    private fun addButtonsSection(content: LinearLayout, type: QuickPanelBlockType) {
        val fromConfigured = QuickPanelSource.usesConfiguredButtons(
                Preferences.getString(prefs, MiscPreferences.WEAR_QUICK_PANEL_SOURCE))
        addChoiceRow(content, R.string.quick_sheet_buttons_source,
                listOf(
                        getString(R.string.quick_sheet_source_configured),
                        getString(R.string.quick_sheet_source_session)),
                selected = if (fromConfigured) 0 else 1) { index ->
            prefs.edit().putString(
                    MiscPreferences.WEAR_QUICK_PANEL_SOURCE.key,
                    if (index == 0) QuickPanelSource.MANUAL else QuickPanelSource.SESSION).apply()
            preview.refresh(MiscPreferences.WEAR_QUICK_PANEL_SOURCE.key)
            blockAdapter.notifyDataSetChanged()
            buildSheetContent(content, type)
        }
        if (!fromConfigured) return

        addHint(content, R.string.quick_sheet_buttons_hint)
        val defaultIcons = intArrayOf(
                com.svartifoss.snfell.common.R.drawable.action_like,
                com.svartifoss.snfell.common.R.drawable.action_shuffle,
                com.svartifoss.snfell.common.R.drawable.action_repeat)
        val titles = intArrayOf(
                R.string.quick_panel_slot_1, R.string.quick_panel_slot_2, R.string.quick_panel_slot_3)
        for (index in QuickPanelButtons.ALL_SLOTS.indices) {
            val row = layoutInflater.inflate(R.layout.item_quick_panel_slot, content, false)
            row.findViewById<TextView>(R.id.slot_title).setText(titles[index])
            row.findViewById<TextView>(R.id.slot_summary).text = slotSummary(index)
            val iconView = row.findViewById<ImageView>(R.id.slot_icon)
            val assigned = assignedSlotAction(index)
            val icon = if (assigned != null && assigned !is NullAction) {
                customIconStorage[assigned]
            } else {
                ContextCompat.getDrawable(this, defaultIcons[index])
            }
            if (assigned?.iconTintable != false) {
                iconView.setColorFilter(ContextCompat.getColor(this, R.color.lyra_on_surface))
            } else {
                iconView.clearColorFilter()
            }
            iconView.setImageDrawable(icon)
            iconView.alpha = if (assigned is NullAction) 0.4f else 1f
            row.setOnClickListener {
                pendingSlot = index
                slotPicker.launch(Intent(this, ActionPickerActivity::class.java).putExtra(
                        ActionPickerActivity.EXTRA_SURFACE, ActionPickerSurface.QUICK_PANEL.name))
            }
            row.setOnLongClickListener {
                saveSlot(index, null)
                true
            }
            content.addView(row)
        }
    }

    private fun assignedSlotAction(index: Int): PhoneAction? =
            actionConfig.getPlayingConfig().getScreenAction(
                    ButtonInfo(false, QuickPanelButtons.ALL_SLOTS[index], GESTURE_SINGLE_TAP))

    private fun slotDefaultName(index: Int): String = getString(when (index) {
        0 -> R.string.quick_panel_default_like
        1 -> R.string.quick_panel_default_shuffle
        else -> R.string.quick_panel_default_repeat
    })

    private fun slotSummary(index: Int): String = when (val assigned = assignedSlotAction(index)) {
        null -> getString(R.string.quick_panel_slot_default, slotDefaultName(index))
        is NullAction -> getString(R.string.quick_panel_slot_hidden)
        else -> assigned.title
    }

    /** The slot's action goes into both configs, so the panel is the same whether or not music
     *  is playing - the same rule the Actions tab always followed. */
    private fun saveSlot(index: Int, action: PhoneAction?) {
        val info = ButtonInfo(false, QuickPanelButtons.ALL_SLOTS[index], GESTURE_SINGLE_TAP)
        for (config in listOf(actionConfig.getPlayingConfig(), actionConfig.getStoppedConfig())) {
            config.saveButtonAction(info, action)
            config.commit()
        }
        reloadPreviewData()
    }

    // --- Adding, removing and restoring ---------------------------------------------------------

    private fun showAddSheet() {
        val missing = QuickPanelStack.missingTypes(blocks)
        if (missing.isEmpty()) {
            Toast.makeText(this, R.string.quick_panel_editor_nothing_to_add, Toast.LENGTH_SHORT).show()
            return
        }
        val view = layoutInflater.inflate(R.layout.sheet_quick_panel_block, null)
        val sheet = BottomSheetDialog(this)
        sheet.setContentView(view)
        sheet.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        sheet.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let {
            BottomSheetBehavior.from(it).apply {
                skipCollapsed = true
                state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
        view.findViewById<TextView>(R.id.sheet_title).setText(R.string.quick_panel_editor_add_block)
        view.findViewById<TextView>(R.id.sheet_subtitle).visibility = View.GONE
        view.findViewById<View>(R.id.sheet_remove).visibility = View.GONE
        val content = view.findViewById<LinearLayout>(R.id.sheet_content)

        missing.forEach { type ->
            val row = layoutInflater.inflate(R.layout.item_quick_panel_block, content, false)
            row.findViewById<ImageView>(R.id.block_icon).apply {
                setImageResource(iconOf(type))
                setColorFilter(ContextCompat.getColor(this@QuickPanelEditorActivity, R.color.lyra_on_surface))
            }
            row.findViewById<TextView>(R.id.block_title).setText(titleOf(type))
            row.findViewById<TextView>(R.id.block_summary).setText(descriptionOf(type))
            // A new block is something to place, not to drag; the grip would only suggest otherwise.
            (row as ViewGroup).getChildAt(row.childCount - 1).visibility = View.GONE
            row.setOnClickListener {
                sheet.dismiss()
                blocks = QuickPanelStack.add(blocks, QuickPanelStack.newBlock(type)).toMutableList()
                persist()
                recycler.smoothScrollToPosition(blocks.lastIndex)
            }
            content.addView(row)
        }
        sheet.show()
    }

    private fun removeBlock(type: QuickPanelBlockType) {
        val index = blocks.indexOfFirst { it.type == type }
        if (index < 0) return
        if (blocks.size <= 1) {
            Toast.makeText(this, R.string.quick_panel_editor_last_block, Toast.LENGTH_SHORT).show()
            return
        }
        val removed = blocks[index]
        blocks = QuickPanelStack.remove(blocks, index).toMutableList()
        persist()
        Snackbar.make(
                recycler,
                getString(R.string.quick_panel_editor_removed, getString(titleOf(type))),
                Snackbar.LENGTH_LONG)
                .setAction(R.string.quick_panel_editor_undo) {
                    blocks = ArrayList(blocks).also { it.add(index.coerceAtMost(it.size), removed) }
                    persist()
                }
                .setActionTextColor(LyraAccent.contrastSafe(this, accent))
                .show()
    }

    private fun showMoreMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(0, MENU_RESTORE, 0, R.string.quick_panel_editor_restore)
            setOnMenuItemClickListener { item ->
                if (item.itemId == MENU_RESTORE) confirmRestore()
                true
            }
        }.show()
    }

    private fun confirmRestore() {
        AlertDialog.Builder(this)
                .setTitle(R.string.quick_panel_editor_restore_title)
                .setMessage(R.string.quick_panel_editor_restore_message)
                .setPositiveButton(R.string.quick_panel_editor_restore_confirm) { _, _ ->
                    prefs.edit().remove(MiscPreferences.WEAR_QUICK_PANEL_BLOCKS.key).apply()
                    blocks = QuickPanelStack.implicitStack().toMutableList()
                    preview.refresh(MiscPreferences.WEAR_QUICK_PANEL_BLOCKS.key)
                    blockAdapter.notifyDataSetChanged()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
                .applyLyraDialogStyling(accent = accent)
    }

    // --- Sheet building blocks --------------------------------------------------------------

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun addSectionLabel(content: LinearLayout, label: Int) {
        content.addView(TextView(this).apply {
            setText(label)
            typeface = ResourcesCompat.getFont(this@QuickPanelEditorActivity, R.font.google_sans)
            textSize = 12f
            includeFontPadding = false
            setTextColor(LyraAccent.contrastSafe(this@QuickPanelEditorActivity, accent))
            setPadding(0, dp(18f), 0, dp(8f))
        })
    }

    private fun chipGroup(content: LinearLayout): ChipGroup {
        val group = ChipGroup(this).apply {
            isSingleLine = false
            chipSpacingHorizontal = dp(8f)
            chipSpacingVertical = dp(4f)
        }
        content.addView(group, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return group
    }

    /** A labelled row of exclusive choices; the one at [selected] is lit. */
    private fun addChoiceRow(
            content: LinearLayout,
            label: Int,
            options: List<String>,
            selected: Int,
            onSelect: (Int) -> Unit
    ) {
        addSectionLabel(content, label)
        val group = chipGroup(content)
        options.forEachIndexed { index, text ->
            val chip = newChip(text, index == selected)
            chip.setOnClickListener {
                if (index != selected) {
                    buzz()
                    onSelect(index)
                }
            }
            group.addView(chip)
        }
    }

    private fun addSwitchRow(
            content: LinearLayout,
            label: Int,
            checked: Boolean,
            onChange: (Boolean) -> Unit
    ) {
        content.addView(SwitchMaterial(this).apply {
            setText(label)
            typeface = ResourcesCompat.getFont(this@QuickPanelEditorActivity, R.font.google_sans)
            textSize = 14f
            includeFontPadding = false
            setTextColor(ContextCompat.getColor(this@QuickPanelEditorActivity, R.color.lyra_on_surface))
            minHeight = dp(48f)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8f), 0, 0)
            isChecked = checked
            setOnCheckedChangeListener { _, isChecked -> onChange(isChecked) }
        })
    }

    private fun addHint(content: LinearLayout, text: Int) {
        content.addView(TextView(this).apply {
            setText(text)
            typeface = ResourcesCompat.getFont(this@QuickPanelEditorActivity, R.font.google_sans)
            textSize = 12f
            includeFontPadding = false
            setLineSpacing(0f, 1.15f)
            setTextColor(ContextCompat.getColor(this@QuickPanelEditorActivity, R.color.lyra_text_secondary))
            setPadding(0, dp(14f), 0, 0)
        })
    }

    /** Neutral until selected, like the community gallery's filters and the action picker's strip. */
    private fun newChip(text: String, selected: Boolean): Chip {
        val chip = layoutInflater.inflate(R.layout.item_picker_chip, null, false) as Chip
        val surface = getColor(R.color.lyra_surface)
        val selectedContainer = ColorUtils.blendARGB(surface, accent, 0.16f)
        val selectedContent = LyraAccent.contrastSafe(accent, selectedContainer, minimumContrast = 4.5)
        chip.text = text
        chip.isCheckable = false
        chip.chipBackgroundColor = ColorStateList.valueOf(if (selected) selectedContainer else surface)
        chip.chipStrokeColor = ColorStateList.valueOf(
                if (selected) selectedContent else getColor(R.color.lyra_divider))
        chip.setTextColor(if (selected) selectedContent else getColor(R.color.lyra_on_surface))
        return chip
    }

    companion object {
        private const val MENU_RESTORE = 1

        /** The jump sets the seek block offers. Each is a list the model accepts, and between them
         *  they cover every way of choosing one or two of the three jumps that have a glyph. */
        private val SEEK_PRESETS: List<List<Int>> = listOf(
                listOf(5), listOf(10), listOf(30),
                listOf(5, 10), listOf(5, 30), listOf(10, 30))

        fun createIntent(context: Context): Intent =
                Intent(context, QuickPanelEditorActivity::class.java)
    }
}
