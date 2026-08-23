package com.svartifoss.snfell.view.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import com.svartifoss.snfell.databinding.ItemSettingsSectionBinding
import com.svartifoss.snfell.view.mainactivity.MainActivity

/** A visible destination on a Settings overview screen. */
data class SectionNavigationItem(
    val key: String,
    @StringRes val title: Int,
    @StringRes val description: Int,
    @DrawableRes val icon: Int
)

/** Shared card list used by the Settings and Watch appearance overviews. */
class SectionNavigationAdapter(
    private val items: List<SectionNavigationItem>,
    private val onClick: (SectionNavigationItem) -> Unit
) : RecyclerView.Adapter<SectionNavigationAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(
        ItemSettingsSectionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(
        private val binding: ItemSettingsSectionBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: SectionNavigationItem) {
            binding.sectionTitle.setText(item.title)
            binding.sectionDescription.setText(item.description)
            binding.sectionIcon.setImageResource(item.icon)
            binding.root.setOnClickListener { onClick(item) }
            binding.root.contentDescription = buildString {
                append(binding.sectionTitle.text)
                append(". ")
                append(binding.sectionDescription.text)
            }
            (binding.root.context as? MainActivity)?.applyAccentToView(binding.root)
        }
    }
}
