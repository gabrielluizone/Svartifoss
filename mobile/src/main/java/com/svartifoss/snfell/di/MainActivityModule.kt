package com.svartifoss.snfell.di

import androidx.lifecycle.lifecycleScope
import com.svartifoss.snfell.config.ActionConfig
import com.svartifoss.snfell.view.actionlist.ActionListFragment
import com.svartifoss.snfell.view.buttonconfig.ButtonConfigFragment
import com.svartifoss.snfell.view.buttonconfig.GesturePickerFragment
import com.svartifoss.snfell.view.mainactivity.MainActivity
import com.svartifoss.snfell.view.settings.MiscSettingsFragment
import dagger.Module
import dagger.Provides
import dagger.android.ContributesAndroidInjector
import kotlinx.coroutines.CoroutineScope

@Module
class MainActivityModule {
    // Main Activity does not provide any different config local config - just reuse global config
    @Provides
    @PerContextLifecycle
    @LocalActivityConfig
    fun provideLocalConfig(@GlobalConfig globalConfig: ActionConfig): ActionConfig = globalConfig

    @Provides
    fun provideCoroutineScope(activity: MainActivity): CoroutineScope {
        return activity.lifecycleScope
    }
}

@Module
abstract class MainActivityFragments {
    @ContributesAndroidInjector(modules = [ButtonConfigFragment.Module::class])
    abstract fun contributeButtonConfigFragment(): ButtonConfigFragment

    @ContributesAndroidInjector
    abstract fun contributeActionListFragment(): ActionListFragment

    @ContributesAndroidInjector
    abstract fun contributeGesturePickerFragment(): GesturePickerFragment

    @ContributesAndroidInjector
    abstract fun contributeMiscSettingsFragment(): MiscSettingsFragment
}
