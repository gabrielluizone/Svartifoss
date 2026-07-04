package com.svartifoss.snfell.view.mainactivity

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.svartifoss.snfell.R
import com.svartifoss.snfell.view.TitledActivity
import com.svartifoss.snfell.common.R as commonR
class NoWatchFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_no_watch, container, false)
    }

    override fun onStart() {
        super.onStart()
        if (parentFragmentManager.findFragmentById(R.id.fragment_container) !== this) return

        val activity = activity
        if (activity is TitledActivity) {
            activity.title = getString(commonR.string.app_name_short)
        }
    }
}
