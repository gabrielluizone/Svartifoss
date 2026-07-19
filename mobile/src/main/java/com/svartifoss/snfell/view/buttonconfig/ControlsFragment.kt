package com.svartifoss.snfell.view.buttonconfig

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import com.svartifoss.snfell.R
import com.svartifoss.snfell.databinding.FragmentControlsBinding
import com.svartifoss.snfell.view.ActivityResultReceiver
import com.svartifoss.snfell.view.TitledActivity
import com.svartifoss.snfell.view.mainactivity.MainActivity

/**
 * One destination for configuring controls in both playback states. The two configurations are
 * deliberately presented as states of the same feature instead of unrelated bottom-nav pages.
 */
class ControlsFragment : Fragment(), ActivityResultReceiver {
    companion object {
        private const val STATE_SELECTED_MODE = "selectedControlMode"
        private const val MODE_PLAYING = 0
        private const val MODE_STOPPED = 1

        // Bottom-nav destinations are replaced rather than kept alive. Remember the last mode for
        // the current process so returning to Controls does not unexpectedly jump to another state.
        private var lastSelectedMode = MODE_PLAYING
    }

    private var _binding: FragmentControlsBinding? = null
    private val binding get() = _binding!!
    private var selectedMode = MODE_PLAYING
    private var tabsMediator: TabLayoutMediator? = null
    private var pageChangeCallback: ViewPager2.OnPageChangeCallback? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentControlsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.controlsPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = 2

            override fun createFragment(position: Int): Fragment =
                ButtonConfigFragment.newInstance(position == MODE_PLAYING)
        }

        tabsMediator = TabLayoutMediator(binding.controlsTabs, binding.controlsPager) { tab, position ->
            tab.setText(
                if (position == MODE_PLAYING) R.string.controls_state_playing
                else R.string.controls_state_stopped
            )
        }.also { it.attach() }

        selectedMode = (savedInstanceState?.getInt(STATE_SELECTED_MODE) ?: lastSelectedMode)
            .coerceIn(MODE_PLAYING, MODE_STOPPED)

        pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                showMode(position)
            }
        }.also { binding.controlsPager.registerOnPageChangeCallback(it) }

        binding.controlsPager.setCurrentItem(selectedMode, false)
        showMode(selectedMode)

        tintTabs()
    }

    private fun showMode(mode: Int) {
        selectedMode = mode
        lastSelectedMode = mode
        val playing = mode == MODE_PLAYING
        binding.controlsDescription.setText(
            if (playing) R.string.controls_playing_description
            else R.string.controls_stopped_description
        )
    }

    private fun tintTabs() {
        val activity = activity as? MainActivity ?: return
        activity.applyAccentToView(binding.controlsTabs)
    }

    override fun onStart() {
        super.onStart()
        (activity as? TitledActivity)?.updateActivityTitle(getString(R.string.controls_header))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_SELECTED_MODE, selectedMode)
        super.onSaveInstanceState(outState)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val activeModePlaysMusic = (_binding?.controlsPager?.currentItem ?: selectedMode) == MODE_PLAYING
        childFragmentManager.fragments
            .asSequence()
            .filterIsInstance<ButtonConfigFragment>()
            .firstOrNull { it.configuresPlaybackActions() == activeModePlaysMusic }
            ?.onActivityResult(requestCode, resultCode, data)
    }

    override fun onDestroyView() {
        pageChangeCallback?.let(binding.controlsPager::unregisterOnPageChangeCallback)
        pageChangeCallback = null
        tabsMediator?.detach()
        tabsMediator = null
        binding.controlsPager.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
