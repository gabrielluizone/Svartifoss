package com.svartifoss.snfell.view.buttonconfig

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.svartifoss.snfell.R
import com.svartifoss.snfell.actions.PhoneAction
import com.svartifoss.snfell.actions.PlayPlaylistShortcutAction
import com.svartifoss.snfell.config.CustomIconStorage
import com.svartifoss.snfell.databinding.PopupActionPickerBinding
import com.svartifoss.snfell.di.InjectableViewModelFactory
import com.svartifoss.snfell.music.PlaylistShortcut
import com.svartifoss.snfell.view.LyraAccent
import com.svartifoss.snfell.view.actionlist.StreamingLinkSheet
import dagger.Provides
import dagger.android.AndroidInjection
import javax.inject.Inject
import javax.inject.Named

/**
 * Pick action, redesigned as one page (see [ActionPickerLayout]).
 *
 * It used to be a stack: a list of categories, each opening a page of its own, some opening a
 * third, and the streaming one opening a whole other window to add a link. Someone choosing an
 * action had to know where it lived before they could see it. Now every section is on one scrolling
 * page, the strip under the search box jumps to a section (and follows the scroll, so you always
 * know where you are), the few sub-lists open in place, and a link is added from a sheet over the
 * same page. There is nothing to navigate and so nothing to get lost in.
 *
 * The keyboard stays down until the search field is tapped: opening this to pick "Next" should not
 * cost half the screen.
 */
class ActionPickerActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_ACTION_BUNDLE = "Action"
        const val VIEW_MODEL_REQUEST_CODE = 7961
        const val EXTRA_DISPLAY_NONE = "DisplayNone"
        const val EXTRA_SURFACE = "Surface"

        private const val EXPANDED_CHEVRON_DEGREES = 90f
        private const val INDENT_DP = 28
    }

    private val viewModel: ActionPickerViewModel by viewModels { viewModelFactory }
    private lateinit var binding: PopupActionPickerBinding
    private lateinit var adapter: PickerAdapter
    private lateinit var layoutManager: LinearLayoutManager

    private var displayNone = false
    private var screen: PickerScreen? = null
    private var chips: List<Chip> = emptyList()
    private var chipsBuiltFor: List<String> = emptyList()
    private var litChip = -1

    /** True from a tap on a shortcut until the next drag: the scroll that tap causes must not hand
     *  the highlight to whichever section happens to end up at the top, which is not the one that
     *  was asked for when the last sections are too short to scroll to the top. */
    private var scrollingToChip = false

    private val accent by lazy { LyraAccent.resolve(this) }

    @Inject
    lateinit var viewModelFactory: InjectableViewModelFactory<ActionPickerViewModel>

    @Inject
    lateinit var customIconStorage: CustomIconStorage

    override fun onCreate(savedInstanceState: Bundle?) {
        displayNone = intent.getBooleanExtra(EXTRA_DISPLAY_NONE, true)

        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)

        binding = PopupActionPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        layoutManager = LinearLayoutManager(this)
        adapter = PickerAdapter()
        binding.recycler.layoutManager = layoutManager
        binding.recycler.adapter = adapter
        binding.recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) scrollingToChip = false
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (!scrollingToChip) lightChipForScroll()
            }
        })

        // Match whatever accent MainActivity is currently showing (dynamic album-art color,
        // custom color, or the default) instead of the static XML green.
        binding.pickerTitle.setTextColor(accent)
        binding.cancelButton.setOnClickListener { finish() }

        binding.actionSearchInput.doAfterTextChanged {
            viewModel.setQuery(it?.toString().orEmpty())
        }
        binding.actionSearchInput.setOnEditorActionListener { view, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                        .hideSoftInputFromWindow(view.windowToken, 0)
                true
            } else {
                false
            }
        }

        viewModel.screen.observe(this, ::render)
        viewModel.selectedAction.observe(this, pickObserver)
        viewModel.activityStarter.observe(this, activityOpenObserver)
        viewModel.addLinkRequested.observe(this) { showAddLinkSheet() }

        // Back clears a search first, then closes: there is no page to go back to.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.actionSearchInput.text.isNullOrEmpty()) {
                    finish()
                } else {
                    binding.actionSearchInput.setText("")
                }
            }
        })

        setFinishOnTouchOutside(true)
    }

    private fun render(newScreen: PickerScreen) {
        screen = newScreen
        val layout = newScreen.layout

        binding.pickerChipScroll.isVisible = !newScreen.searching && layout.sectionIds.isNotEmpty()
        binding.actionSearchEmpty.isVisible = newScreen.searching && layout.rows.isEmpty()
        if (!newScreen.searching) buildChips(newScreen)

        // The list diffs off the main thread, so what is at the top is only known once it has been
        // applied. A group opening or closing moves every later section, and the lit shortcut with it.
        adapter.submit(layout.rows, newScreen.sections) {
            if (!newScreen.searching && !scrollingToChip) lightChipForScroll()
        }
    }

    // region Section shortcuts

    private fun buildChips(newScreen: PickerScreen) {
        val ids = newScreen.layout.sectionIds
        if (ids == chipsBuiltFor) return
        chipsBuiltFor = ids
        litChip = -1

        val byId = newScreen.sections.associateBy { it.id }
        binding.pickerChips.removeAllViews()
        chips = ids.mapIndexed { index, id ->
            val chip = layoutInflater.inflate(
                    R.layout.item_picker_chip, binding.pickerChips, false) as Chip
            chip.text = byId[id]?.chipLabel.orEmpty()
            chip.isCheckable = false
            chip.setOnClickListener { jumpToSection(index) }
            binding.pickerChips.addView(chip)
            chip
        }
        lightChip(0)
    }

    private fun jumpToSection(index: Int) {
        val start = screen?.layout?.sectionStarts?.getOrNull(index) ?: return
        scrollingToChip = true
        lightChip(index)
        layoutManager.scrollToPositionWithOffset(start, 0)
    }

    private fun lightChipForScroll() {
        val current = screen ?: return
        if (current.searching) return
        val first = layoutManager.findFirstVisibleItemPosition()
        if (first == RecyclerView.NO_POSITION) return
        val section = ActionPickerLayout.sectionAt(current.layout.sectionStarts, first)
        if (section >= 0) lightChip(section)
    }

    private fun lightChip(index: Int) {
        if (index == litChip || index !in chips.indices) return
        litChip = index
        chips.forEachIndexed { position, chip -> styleChip(chip, selected = position == index) }

        // Keep the lit shortcut on screen: with six on a phone the strip scrolls sideways.
        val chip = chips[index]
        binding.pickerChipScroll.post {
            val scroll = binding.pickerChipScroll
            val left = chip.left - scroll.paddingLeft
            val right = chip.right + scroll.paddingRight
            if (left < scroll.scrollX || right > scroll.scrollX + scroll.width) {
                scroll.smoothScrollTo(left, 0)
            }
        }
    }

    /** Same neutral-until-selected treatment as the community gallery's filters, so the two
     *  strips read as one kind of control. */
    private fun styleChip(chip: Chip, selected: Boolean) {
        val surface = getColor(R.color.lyra_surface)
        val selectedContainer = ColorUtils.blendARGB(surface, accent, 0.16f)
        val selectedContent = LyraAccent.contrastSafe(accent, selectedContainer, minimumContrast = 4.5)
        val content = if (selected) selectedContent else getColor(R.color.lyra_on_surface)
        chip.chipBackgroundColor = ColorStateList.valueOf(if (selected) selectedContainer else surface)
        chip.chipStrokeColor = ColorStateList.valueOf(
                if (selected) selectedContent else getColor(R.color.lyra_divider))
        chip.setTextColor(content)
    }

    // endregion

    private fun showAddLinkSheet() {
        StreamingLinkSheet(this, object : StreamingLinkSheet.Host {
            override fun useShortcut(shortcut: PlaylistShortcut) {
                viewModel.selectedAction.value = PlayPlaylistShortcutAction(
                        this@ActionPickerActivity, shortcut.name, shortcut.link)
            }
        }, StreamingLinkSheet.Mode.PICK).show()
    }

    private val pickObserver = androidx.lifecycle.Observer<PhoneAction?> {
        val resultIntent = Intent()
        resultIntent.putExtra(EXTRA_ACTION_BUNDLE, it?.serialize())
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    private val activityOpenObserver = androidx.lifecycle.Observer<Intent?> {
        if (it == null) {
            return@Observer
        }

        startActivityForResult(it, VIEW_MODEL_REQUEST_CODE)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == VIEW_MODEL_REQUEST_CODE) {
            viewModel.onActivityResultReceived(requestCode, resultCode, data)
        }
    }

    // region List

    private inner class PickerAdapter :
            ListAdapter<PickerRow<PhoneAction>, RecyclerView.ViewHolder>(DIFF) {
        private var sections: Map<String, PickerSectionInfo> = emptyMap()

        fun submit(
                rows: List<PickerRow<PhoneAction>>,
                info: List<PickerSectionInfo>,
                onCommitted: () -> Unit
        ) {
            sections = info.associateBy { it.id }
            submitList(rows, onCommitted)
        }

        override fun getItemViewType(position: Int): Int =
                if (getItem(position) is PickerRow.Header) TYPE_HEADER else TYPE_ITEM

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
                if (viewType == TYPE_HEADER) {
                    HeaderHolder(layoutInflater.inflate(R.layout.item_picker_section, parent, false))
                } else {
                    ItemHolder(layoutInflater.inflate(R.layout.item_action, parent, false))
                }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val row = getItem(position)) {
                is PickerRow.Header -> (holder as HeaderHolder).bind(sections[row.sectionId])
                is PickerRow.Choice -> (holder as ItemHolder).bindChoice(row)
                is PickerRow.Group -> (holder as ItemHolder).bindGroup(row)
                is PickerRow.AddLink -> (holder as ItemHolder).bindAddLink(row)
            }
        }
    }

    private inner class HeaderHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.section_icon)
        private val title: TextView = itemView.findViewById(R.id.section_title)

        fun bind(info: PickerSectionInfo?) {
            title.text = info?.title.orEmpty()
            title.setTextColor(accent)
            if (info != null) icon.setImageResource(info.iconRes)
        }
    }

    private inner class ItemHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconView: ImageView = itemView.findViewById(R.id.icon)
        private val textView: TextView = itemView.findViewById(android.R.id.text1)
        private val subtitleView: TextView = itemView.findViewById(R.id.subtitle)
        private val chevronView: ImageView = itemView.findViewById(R.id.chevron)
        private val baseStart = itemView.paddingStart
        private var row: PickerRow<PhoneAction>? = null

        init {
            itemView.setOnClickListener {
                if (bindingAdapterPosition == RecyclerView.NO_POSITION) return@setOnClickListener
                row?.let(viewModel::onRowTapped)
            }
        }

        fun bindChoice(choice: PickerRow.Choice<PhoneAction>) {
            row = choice
            bindAction(choice.action, indent = choice.indent)
            val subtitle = choice.breadcrumb
                    ?: getString(R.string.action_opens_more).takeIf { choice.action.opensMoreOptions }
            showSubtitle(subtitle)
            showChevron(choice.action.opensMoreOptions, expanded = false)
            ViewCompat.setAccessibilityDelegate(itemView, null)
        }

        fun bindGroup(group: PickerRow.Group<PhoneAction>) {
            row = group
            bindAction(group.header, indent = false)
            showSubtitle(getString(R.string.action_opens_more))
            showChevron(true, expanded = group.expanded)
            ViewCompat.setAccessibilityDelegate(itemView, expandDelegate(group.expanded))
        }

        fun bindAddLink(add: PickerRow.AddLink) {
            row = add
            setIndent(false)
            iconView.setImageResource(R.drawable.ic_add_link)
            iconView.setColorFilter(accent)
            textView.setText(R.string.picker_add_link)
            showSubtitle(getString(R.string.picker_add_link_desc))
            showChevron(false, expanded = false)
            ViewCompat.setAccessibilityDelegate(itemView, null)
        }

        private fun bindAction(action: PhoneAction, indent: Boolean) {
            setIndent(indent)
            textView.text = action.title
            if (action.iconTintable) {
                iconView.setColorFilter(ContextCompat.getColor(
                        this@ActionPickerActivity, R.color.lyra_on_surface))
            } else {
                iconView.clearColorFilter()
            }
            iconView.setImageDrawable(customIconStorage[action])
        }

        private fun setIndent(indent: Boolean) {
            val extra = if (indent) (INDENT_DP * resources.displayMetrics.density).toInt() else 0
            itemView.setPaddingRelative(
                    baseStart + extra, itemView.paddingTop, itemView.paddingEnd, itemView.paddingBottom)
        }

        private fun showSubtitle(text: String?) {
            subtitleView.text = text
            subtitleView.isVisible = !text.isNullOrEmpty()
        }

        private fun showChevron(visible: Boolean, expanded: Boolean) {
            chevronView.isVisible = visible
            chevronView.rotation = if (expanded) EXPANDED_CHEVRON_DEGREES else 0f
        }

        /** A group is a disclosure: a screen reader should offer Expand / Collapse for it, not just
         *  "double tap to activate". */
        private fun expandDelegate(expanded: Boolean) = object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(
                    host: View,
                    info: AccessibilityNodeInfoCompat
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.addAction(
                        if (expanded) AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_COLLAPSE
                        else AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_EXPAND)
            }

            override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean {
                if (action == AccessibilityNodeInfoCompat.ACTION_EXPAND ||
                        action == AccessibilityNodeInfoCompat.ACTION_COLLAPSE) {
                    return host.performClick()
                }
                return super.performAccessibilityAction(host, action, args)
            }
        }
    }

    // endregion

    @dagger.Module
    class Module {
        @Provides
        @Named(ActionPickerViewModel.ARG_SHOW_NONE)
        fun displayNone(actionPickerActivity: ActionPickerActivity) = actionPickerActivity.displayNone

        @Provides
        @Named(ActionPickerViewModel.ARG_SURFACE)
        fun surface(actionPickerActivity: ActionPickerActivity): ActionPickerSurface =
                ActionPickerSurface.fromExtra(
                        actionPickerActivity.intent.getStringExtra(EXTRA_SURFACE))
    }
}

private const val TYPE_HEADER = 0
private const val TYPE_ITEM = 1

/**
 * Rows are matched by what they are, not where they sit, so a group opening animates its children
 * in instead of redrawing the page. An action has no stable id, but its class plus its title
 * (which carries its parameters: "Set speed to 0.5×" and "…1×" are one class) tells rows apart.
 */
private val DIFF = object : DiffUtil.ItemCallback<PickerRow<PhoneAction>>() {
    override fun areItemsTheSame(
            oldItem: PickerRow<PhoneAction>,
            newItem: PickerRow<PhoneAction>
    ): Boolean = when {
        oldItem is PickerRow.Header && newItem is PickerRow.Header ->
            oldItem.sectionId == newItem.sectionId
        oldItem is PickerRow.Group && newItem is PickerRow.Group -> oldItem.id == newItem.id
        oldItem is PickerRow.AddLink && newItem is PickerRow.AddLink -> true
        oldItem is PickerRow.Choice && newItem is PickerRow.Choice ->
            oldItem.action.javaClass == newItem.action.javaClass &&
                    oldItem.action.title == newItem.action.title
        else -> false
    }

    override fun areContentsTheSame(
            oldItem: PickerRow<PhoneAction>,
            newItem: PickerRow<PhoneAction>
    ): Boolean = oldItem == newItem
}
