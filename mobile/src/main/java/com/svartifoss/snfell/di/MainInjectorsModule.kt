package com.svartifoss.snfell.di

import com.svartifoss.snfell.music.MusicService
import com.svartifoss.snfell.view.actionlist.ActionEditorActivity
import com.svartifoss.snfell.view.buttonconfig.ActionPickerActivity
import com.svartifoss.snfell.view.mainactivity.MainActivity
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
abstract class MainInjectorsModule {
    @ContributesAndroidInjector
    abstract fun contributeMusicServiceInjector(): MusicService

    @PerContextLifecycle
    @ContributesAndroidInjector(modules = [MainActivityModule::class, MainActivityFragments::class])
    abstract fun contributeMainActivity(): MainActivity

    @ContributesAndroidInjector(modules = [ActionPickerActivity.Module::class])
    abstract fun contributeActionPickerActivity(): ActionPickerActivity

    @ContributesAndroidInjector
    abstract fun contributeActionEditorActivity(): ActionEditorActivity
}
