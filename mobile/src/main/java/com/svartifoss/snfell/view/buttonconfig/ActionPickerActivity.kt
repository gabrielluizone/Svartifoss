package com.svartifoss.snfell.view.buttonconfig

import android.app.Activity
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.svartifoss.snfell.view.LyraAccent
import com.svartifoss.snfell.R
import com.svartifoss.snfell.actions.PhoneAction
import com.svartifoss.snfell.config.CustomIconStorage
import com.svartifoss.snfell.databinding.PopupActionPickerBinding
import com.svartifoss.snfell.di.InjectableViewModelFactory
import dagger.Provides
import dagger.android.AndroidInjection
import javax.inject.Inject
import javax.inject.Named

class ActionPickerActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_ACTION_BUNDLE = "Action"
        const val VIEW_MODEL_REQUEST_CODE = 7961
        const val EXTRA_DISPLAY_NONE = "DisplayNone"
        const val EXTRA_SURFACE = "Surface"
    }

    private val viewModel : ActionPickerViewModel by viewModels { viewModelFactory }
    private lateinit var recycler : RecyclerView
    private lateinit var adapter : ActionsAdapter
    private lateinit var binding: PopupActionPickerBinding

    private var displayNone = false

    @Inject
    lateinit var viewModelFactory: InjectableViewModelFactory<ActionPickerViewModel>

    @Inject
    lateinit var customIconStorage: CustomIconStorage

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        displayNone = intent.getBooleanExtra(EXTRA_DISPLAY_NONE, true)

        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)

        binding = PopupActionPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)


        viewModel.displayedActions.observe(this, listObserver)
        viewModel.pageTitle.observe(this) { title ->
            binding.pickerTitle.text = title ?: getString(R.string.pick_action)
            applyFilter(binding.actionSearchInput.text?.toString().orEmpty())
        }
        viewModel.selectedAction.observe(this, pickObserver)
        viewModel.activityStarter.observe(this, activityOpenObserver)

        recycler = binding.recycler
        adapter = ActionsAdapter()
        recycler.adapter = adapter
        recycler.layoutManager =
            LinearLayoutManager(
                this,
                LinearLayoutManager.VERTICAL,
                false
            )

        // Match whatever accent MainActivity is currently showing (dynamic album-art color,
        // custom color, or the default) instead of the static XML green.
        binding.pickerTitle.setTextColor(LyraAccent.resolve(this))

        binding.cancelButton.setOnClickListener { finish() }
        binding.pickerBack.setOnClickListener { navigateBackOrFinish() }
        binding.actionSearchInput.doAfterTextChanged {
            applyFilter(it?.toString().orEmpty())
        }

        setFinishOnTouchOutside(true)
    }

    private val listObserver = Observer<List<PhoneAction>?> {
        if (it == null) {
            return@Observer
        }

        applyFilter(binding.actionSearchInput.text?.toString().orEmpty())
    }

    private fun applyFilter(query: String) {
        adapter.submit(viewModel.rowsFor(query))
        val isEmpty = adapter.itemCount == 0
        if (isEmpty) {
            binding.actionSearchEmpty.setText(
                    if (query.isBlank()) R.string.error_library_empty
                    else R.string.action_search_empty
            )
        }
        binding.actionSearchEmpty.isVisible = isEmpty
    }

    private val pickObserver = Observer<PhoneAction?> {
        val resultIntent = Intent()
        resultIntent.putExtra(EXTRA_ACTION_BUNDLE, it?.serialize())
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    private val activityOpenObserver = Observer<Intent?> {
        if (it == null) {
            return@Observer
        }

        startActivityForResult(it, VIEW_MODEL_REQUEST_CODE)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (clearSearch()) return
        if (!viewModel.tryGoBack()) super.onBackPressed()
    }

    private fun navigateBackOrFinish() {
        if (clearSearch()) return
        if (!viewModel.tryGoBack()) finish()
    }

    private fun clearSearch(): Boolean {
        if (binding.actionSearchInput.text.isNullOrEmpty()) return false
        binding.actionSearchInput.setText("")
        return true
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == VIEW_MODEL_REQUEST_CODE) {
            viewModel.onActivityResultReceived(requestCode, resultCode, data)
        }
    }

    private inner class ActionsAdapter : RecyclerView.Adapter<ActionsHolder>() {
        private var items: List<ActionPickerRow> = emptyList()

        fun submit(rows: List<ActionPickerRow>) {
            items = rows
            notifyDataSetChanged()
        }

        fun rowAt(position: Int): ActionPickerRow? = items.getOrNull(position)

        override fun getItemCount(): Int {
            return items.size
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActionsHolder {
            val view = layoutInflater.inflate(R.layout.item_action, parent, false)
            return ActionsHolder(view)
        }

        override fun onBindViewHolder(holder: ActionsHolder, position: Int) {
            val row = items.getOrNull(position) ?: return
            val action = row.action

            val icon = customIconStorage[action]
            if (action.iconTintable) {
                val iconColor = ContextCompat.getColor(this@ActionPickerActivity, R.color.lyra_on_surface)
                holder.iconView.setColorFilter(iconColor)
            } else {
                holder.iconView.clearColorFilter()
            }

            holder.textView.text = action.title
            holder.iconView.setImageDrawable(icon)

            // Entries that open another chooser get a chevron + hint so they don't read as a
            // single action.
            holder.subtitleView.text = row.breadcrumb ?: getString(R.string.action_opens_more)
            holder.subtitleView.isVisible = row.breadcrumb != null || action.opensMoreOptions
            holder.chevronView.isVisible = action.opensMoreOptions
        }

    }

    private inner class ActionsHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textView: TextView = itemView.findViewById(android.R.id.text1)
        val iconView: ImageView = itemView.findViewById(R.id.icon)
        val subtitleView: TextView = itemView.findViewById(R.id.subtitle)
        val chevronView: ImageView = itemView.findViewById(R.id.chevron)

        init {
            itemView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION) {
                    return@setOnClickListener
                }

                val action = adapter.rowAt(position)?.action ?: return@setOnClickListener
                viewModel.onActionTapped(action)
            }
        }
    }

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
