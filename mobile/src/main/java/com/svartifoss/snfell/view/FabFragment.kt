package com.svartifoss.snfell.view

import com.google.android.material.floatingactionbutton.FloatingActionButton

interface FabFragment {
    fun onFabClicked()

    fun prepareFab(fab: FloatingActionButton) {

    }
}
