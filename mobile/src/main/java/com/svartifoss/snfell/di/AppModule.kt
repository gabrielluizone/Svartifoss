package com.svartifoss.snfell.di

import android.app.Application
import android.content.Context
import com.svartifoss.snfell.config.ActionConfig
import com.svartifoss.snfell.config.DefaultConfigGenerator
import com.svartifoss.snfell.config.GlobalActionConfig
import com.svartifoss.snfell.config.WatchInfoProvider
import com.svartifoss.snfell.config.actionlist.GlobalActionList
import com.svartifoss.snfell.config.buttons.GlobalButtonConfigFactory
import dagger.Lazy
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module(subcomponents = [MusicServiceSubComponent::class])
class AppModule {
    @Provides
    @Singleton
    fun provideApplicationContext(application: Application): Context = application

    @Provides
    @Singleton
    fun provideWatchStateProvider(context: Context) = WatchInfoProvider(context)

    @Provides
    @Singleton
    @GlobalConfig
    fun provideConfigStorage(context: Context,
                             watchInfoProvider: WatchInfoProvider,
                             defaultConfigGenerator: DefaultConfigGenerator,
                             actionListConfig: Lazy<GlobalActionList>,
                             globalButtonConfigFactory: GlobalButtonConfigFactory): ActionConfig
            = GlobalActionConfig(context,
            watchInfoProvider,
            defaultConfigGenerator,
            actionListConfig,
            globalButtonConfigFactory)
}
