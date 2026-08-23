package com.svartifoss.snfell.view.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import com.svartifoss.snfell.NotificationService
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.databinding.FragmentSettingsHomeBinding
import com.svartifoss.snfell.view.TitledActivity
import com.svartifoss.snfell.view.mainactivity.MainActivity

/** Settings grouped into focused, horizontally swipeable sections. */
class SettingsHomeFragment : Fragment() {
    companion object {
        private const val STATE_SELECTED_SECTION = "selectedSettingsSection"
        private const val ARG_SECTION = "initialSettingsSection"
        private const val ARG_HIGHLIGHT_KEY = "initialSettingsHighlightKey"
        private var lastSelectedSection = 0

        /** Opens straight at [section], scrolled to [highlightKey]. Used by Settings Search. */
        fun newInstance(section: String?, highlightKey: String?) = SettingsHomeFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_SECTION, section)
                putString(ARG_HIGHLIGHT_KEY, highlightKey)
            }
        }
    }

    private val sections = listOf(
        Section(MiscSettingsFragment.SECTION_GENERAL, R.string.settings_section_general,
            R.string.settings_section_general_description),
        Section(MiscSettingsFragment.SECTION_WATCH, R.string.settings_section_watch,
            R.string.settings_section_watch_description),
        Section(MiscSettingsFragment.SECTION_AUTOMATION, R.string.settings_section_automation,
            R.string.settings_section_automation_description),
        Section(MiscSettingsFragment.SECTION_APPS, R.string.settings_section_apps,
            R.string.settings_section_apps_description),
        Section(MiscSettingsFragment.SECTION_DATA, R.string.settings_section_data,
            R.string.settings_section_data_description)
    )

    private var _binding: FragmentSettingsHomeBinding? = null
    private val binding get() = _binding!!
    private var selectedSection = 0
    private var pendingHighlightKey: String? = null
    private var requestedSection: String? = null
    private var tabsMediator: TabLayoutMediator? = null
    private var pageChangeCallback: ViewPager2.OnPageChangeCallback? = null

    private val preferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == MiscPreferences.WEAR_QUICK_PANEL_SOURCE.key) {
                context?.applicationContext?.let { appContext ->
                    NotificationService.updateQuickActionsBinding(appContext)
                }
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requestedSection = arguments?.getString(ARG_SECTION)
        val rawHighlightKey = arguments?.getString(ARG_HIGHLIGHT_KEY)
        val searchTarget = if (savedInstanceState == null && rawHighlightKey != null) {
            resolveSearchTarget(rawHighlightKey)
        } else {
            null
        }
        pendingHighlightKey = searchTarget?.key ?: rawHighlightKey

        binding.settingsPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = sections.size

            override fun createFragment(position: Int): Fragment {
                val key = sections[position].key
                val highlight = pendingHighlightKey?.takeIf { key == requestedSection }
                if (highlight != null) pendingHighlightKey = null
                return MiscSettingsFragment.newInstance(key, highlight)
            }
        }

        tabsMediator = TabLayoutMediator(binding.settingsTabs, binding.settingsPager) { tab, position ->
            tab.setText(sections[position].title)
        }.also { it.attach() }

        selectedSection = (savedInstanceState?.getInt(STATE_SELECTED_SECTION)
            ?: requestedSection?.let { key -> sections.indexOfFirst { it.key == key } }
                ?.takeIf { it >= 0 }
            ?: lastSelectedSection).coerceIn(sections.indices)

        pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) = showSection(position)
        }.also { binding.settingsPager.registerOnPageChangeCallback(it) }

        binding.settingsPager.setCurrentItem(selectedSection, false)
        showSection(selectedSection)
        tintTabs()

        if (searchTarget?.redirected == true) {
            Toast.makeText(requireContext(), R.string.settings_search_prerequisite, Toast.LENGTH_LONG)
                .show()
        }
    }

    private fun resolveSearchTarget(key: String): SettingsSearchTargetResolver.Target {
        val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        return SettingsSearchTargetResolver.resolve(
            key,
            readString = { preferenceKey, default ->
                preferences.getString(preferenceKey, default) ?: default
            },
            readBoolean = { preferenceKey, default ->
                preferences.getBoolean(preferenceKey, default)
            }
        )
    }

    private fun showSection(index: Int) {
        selectedSection = index
        lastSelectedSection = index
        binding.settingsSectionDescription.setText(sections[index].description)
    }

    private fun tintTabs() {
        (activity as? MainActivity)?.applyAccentToView(binding.settingsTabs)
    }

    override fun onStart() {
        super.onStart()
        (activity as? TitledActivity)?.updateActivityTitle(getString(R.string.action_settings))
        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .registerOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    override fun onStop() {
        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_SELECTED_SECTION, selectedSection)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        pageChangeCallback?.let(binding.settingsPager::unregisterOnPageChangeCallback)
        pageChangeCallback = null
        tabsMediator?.detach()
        tabsMediator = null
        binding.settingsPager.adapter = null
        _binding = null
        super.onDestroyView()
    }

    private data class Section(val key: String, val title: Int, val description: Int)
}
