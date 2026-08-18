package com.svartifoss.snfell.view.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.svartifoss.snfell.R
import com.svartifoss.snfell.view.LyraAccent

/**
 * Search across both preference screens.
 *
 * A standalone Activity rather than a fragment inside MainActivity, so it can own the keyboard and
 * the back stack without competing with the bottom navigation for what "back" means. It returns the
 * chosen row as an activity result and lets MainActivity do the navigating - the caller already
 * owns the tab switching, and duplicating that here would give two code paths to the same screens.
 */
class SettingsSearchActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TARGET_TAB = "targetTab"
        const val EXTRA_TARGET_SECTION = "targetSection"
        const val EXTRA_TARGET_KEY = "targetKey"

        const val TAB_SETTINGS = "settings"
        const val TAB_WATCH_FACE = "watchFace"

        fun createIntent(context: Context): Intent =
                Intent(context, SettingsSearchActivity::class.java)
    }

    private lateinit var entries: List<SettingsSearchEntry>
    private lateinit var adapter: ResultsAdapter
    private lateinit var message: TextView
    private lateinit var input: EditText
    private lateinit var clearButton: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_search)

        entries = SettingsSearchIndex.build(this)

        val accent = LyraAccent.resolve(this)
        message = findViewById(R.id.search_message)
        input = findViewById(R.id.search_input)
        clearButton = findViewById(R.id.button_clear)

        val recycler = findViewById<RecyclerView>(R.id.recycler)
        recycler.layoutManager = LinearLayoutManager(this)
        adapter = ResultsAdapter(accent) { finishWith(it) }
        recycler.adapter = adapter

        findViewById<ImageButton>(R.id.button_back).setOnClickListener { finish() }
        clearButton.setOnClickListener { input.setText("") }

        input.doAfterTextChanged { text -> applyQuery(text?.toString().orEmpty()) }
        applyQuery("")

        // The screen exists only to be typed into, so open focused with the keyboard up rather
        // than costing an extra tap on arrival.
        input.requestFocus()
        getSystemService(InputMethodManager::class.java)
                ?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun applyQuery(query: String) {
        clearButton.visibility = if (query.isEmpty()) View.GONE else View.VISIBLE
        val results = SettingsSearchIndex.rank(entries, query)
        adapter.submit(results)

        message.visibility = if (results.isEmpty()) View.VISIBLE else View.GONE
        message.setText(
                if (query.isBlank()) R.string.settings_search_prompt
                else R.string.settings_search_no_results)
    }

    private fun finishWith(entry: SettingsSearchEntry) {
        setResult(Activity.RESULT_OK, Intent()
                .putExtra(EXTRA_TARGET_TAB, when (entry.destination) {
                    is SettingsSearchDestination.Settings -> TAB_SETTINGS
                    is SettingsSearchDestination.WatchFace -> TAB_WATCH_FACE
                })
                .putExtra(EXTRA_TARGET_SECTION, entry.destination.section)
                .putExtra(EXTRA_TARGET_KEY, entry.key))
        finish()
    }

    private class ResultsAdapter(
            private val accent: Int,
            private val onClick: (SettingsSearchEntry) -> Unit
    ) : RecyclerView.Adapter<ResultsAdapter.ViewHolder>() {

        private var items: List<SettingsSearchEntry> = emptyList()

        fun submit(newItems: List<SettingsSearchEntry>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
                ViewHolder(LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_settings_search_result, parent, false))

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val entry = items[position]
            val context = holder.itemView.context
            holder.title.text = entry.title

            val tabName = context.getString(when (entry.destination) {
                is SettingsSearchDestination.Settings -> R.string.action_settings
                is SettingsSearchDestination.WatchFace -> R.string.watch_face_header
            })
            holder.path.text = context.getString(
                    R.string.settings_search_breadcrumb, tabName, entry.categoryTitle)
            holder.path.setTextColor(accent)

            holder.summary.text = entry.summary
            holder.summary.visibility =
                    if (entry.summary.isEmpty()) View.GONE else View.VISIBLE

            holder.itemView.setOnClickListener { onClick(entry) }
        }

        override fun getItemCount(): Int = items.size

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.result_title)
            val path: TextView = view.findViewById(R.id.result_path)
            val summary: TextView = view.findViewById(R.id.result_summary)
        }
    }
}
