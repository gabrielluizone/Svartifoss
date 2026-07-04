package com.svartifoss.snfell.di

import com.svartifoss.snfell.actions.ActionHandler
import com.svartifoss.snfell.music.MusicService
import dagger.BindsInstance
import dagger.Subcomponent

@Subcomponent(modules = [ActionHandlersModule::class])
abstract class MusicServiceSubComponent {
    @Subcomponent.Factory
    interface Factory {
        fun build(@BindsInstance musicService: MusicService): MusicServiceSubComponent
    }

    abstract fun getActionHandlers(): Map<Class<*>, ActionHandler<*>>
}
