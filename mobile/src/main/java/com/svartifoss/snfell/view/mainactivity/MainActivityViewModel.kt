package com.svartifoss.snfell.view.mainactivity

import androidx.lifecycle.ViewModel
import com.svartifoss.snfell.config.WatchInfoProvider
import com.svartifoss.snfell.music.ActiveMediaSessionProvider
import javax.inject.Inject

class MainActivityViewModel @Inject constructor(
    val watchInfoProvider: WatchInfoProvider,
    val activeMediaSessionProvider: ActiveMediaSessionProvider
) : ViewModel()
