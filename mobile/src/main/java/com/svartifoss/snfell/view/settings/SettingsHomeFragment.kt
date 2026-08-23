package com.svartifoss.snfell.view.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.svartifoss.snfell.NotificationService
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.databinding.FragmentSettingsHomeBinding
import com.svartifoss.snfell.view.TitledActivity
import com.svartifoss.snfell.view.mainactivity.MainActivity

/**
 * Settings starts with a stable overview instead of a horizontally scrolling tab strip. Each
 * destination has a plain-language description, then opens the existing preference hierarchy in
 * place. The preference XML and keys are untouched by this shell, so persistence, dependencies,
 * backup and Phone -> Watch sync retain their existing contracts.
 */
class SettingsHomeFragment : Fragment() {
    companion object {
        private const val STATE_SELECTED_SECTION = "selectedSettingsSection"
        private const val ARG_SECTION = "initialSettingsSection"
        private const val ARG_HIGHLIGHT_KEY = "initialSettingsHighlightKey"

        /** Opens straight at [section], scrolled to [highlightKey]. Used by Settings Search. */
        fun newInstance(section: String?, highlightKey: String?) = SettingsHomeFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_SECTION, section)
                putString(ARG_HIGHLIGHT_KEY, highlightKey)
            }
        }
    }

    private val sections = listOf(
        SectionNavigationItem(
            MiscSettingsFragment.SECTION_GENERAL,
            R.string.settings_section_general,
            R.string.settings_section_general_description,
            R.drawable.ic_settings
        ),
        SectionNavigationItem(
            MiscSettingsFragment.SECTION_WATCH,
            R.string.settings_section_watch,
            R.string.settings_section_watch_description,
            R.drawable.ic_devices_wearables
        ),
        SectionNavigationItem(
            MiscSettingsFragment.SECTION_AUTOMATION,
            R.string.settings_section_automation,
            R.string.settings_section_automation_description,
            R.drawable.ic_autorenew
        ),
        SectionNavigationItem(
            MiscSettingsFragment.SECTION_APPS,
            R.string.settings_section_apps,
            R.string.settings_section_apps_description,
            R.drawable.ic_apps
        ),
        SectionNavigationItem(
            MiscSettingsFragment.SECTION_DATA,
            R.string.settings_section_data,
            R.string.settings_section_data_description,
            R.drawable.ic_backup
        )
    )

    private var _binding: FragmentSettingsHomeBinding? = null
    private val binding get() = _binding!!
    private var selectedSection: String? = null
    private var backCallback: OnBackPressedCallback? = null

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

        binding.settingsSections.layoutManager = LinearLayoutManager(requireContext())
        binding.settingsSections.adapter = SectionNavigationAdapter(sections, ::openSection)
        binding.settingsSectionBack.setOnClickListener { showOverview() }
        ViewCompat.setAccessibilityHeading(binding.settingsSectionTitle, true)

        backCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() = showOverview()
        }.also { requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, it) }

        val requestedSection = arguments?.getString(ARG_SECTION)
            ?.takeIf { requested -> sections.any { it.key == requested } }
        val requestedKey = arguments?.getString(ARG_HIGHLIGHT_KEY)
        val searchTarget = requestedKey?.let(::resolveSearchTarget)
        selectedSection = if (savedInstanceState?.containsKey(STATE_SELECTED_SECTION) == true) {
            savedInstanceState.getString(STATE_SELECTED_SECTION)
        } else {
            requestedSection
        }

        val selected = selectedSection
        if (selected == null) {
            showOverview(removeChild = false)
        } else {
            val highlight = searchTarget?.key
                ?.takeIf { requestedSection == selected && savedInstanceState == null }
            showSection(
                sections.first { it.key == selected },
                highlight,
                replaceChild = childFragmentManager.findFragmentById(
                    R.id.settings_detail_container
                ) == null
            )
            if (highlight != null && searchTarget?.redirected == true) {
                Toast.makeText(
                    requireContext(),
                    R.string.settings_search_prerequisite,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun openSection(item: SectionNavigationItem) {
        showSection(item, highlightKey = null, replaceChild = true)
        binding.settingsSectionBack.post {
            binding.settingsSectionBack.requestFocus()
            binding.settingsDetail.announceForAccessibility(getString(item.title))
        }
    }

    private fun showSection(
        item: SectionNavigationItem,
        highlightKey: String?,
        replaceChild: Boolean
    ) {
        selectedSection = item.key
        binding.settingsOverview.isVisible = false
        binding.settingsDetail.isVisible = true
        binding.settingsSectionTitle.setText(item.title)
        binding.settingsSectionDescription.setText(item.description)
        binding.settingsSectionIcon.setImageResource(item.icon)
        backCallback?.isEnabled = true

        if (replaceChild) {
            childFragmentManager.beginTransaction()
                .replace(
                    R.id.settings_detail_container,
                    MiscSettingsFragment.newInstance(item.key, highlightKey)
                )
                .commitNow()
        }
        (activity as? MainActivity)?.applyAccentToView(binding.settingsDetail)
    }

    private fun showOverview(removeChild: Boolean = true) {
        val previousSection = selectedSection
        selectedSection = null
        binding.settingsOverview.isVisible = true
        binding.settingsDetail.isVisible = false
        backCallback?.isEnabled = false
        if (removeChild) {
            childFragmentManager.findFragmentById(R.id.settings_detail_container)?.let { child ->
                childFragmentManager.beginTransaction().remove(child).commitNow()
            }
        }
        focusOverviewSection(previousSection)
    }

    private fun focusOverviewSection(section: String?) {
        val position = sections.indexOfFirst { it.key == section }
        if (position < 0) return
        binding.settingsSections.scrollToPosition(position)
        binding.settingsSections.post {
            binding.settingsSections.findViewHolderForAdapterPosition(position)?.itemView?.let {
                it.requestFocus()
                it.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED)
            }
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
        outState.putString(STATE_SELECTED_SECTION, selectedSection)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        binding.settingsSections.adapter = null
        backCallback = null
        _binding = null
        super.onDestroyView()
    }
}
