package com.svartifoss.snfell.di

import android.app.Application
import com.svartifoss.snfell.WearMusicCenter
import com.svartifoss.snfell.actions.PhoneAction
import dagger.BindsInstance
import dagger.Component
import dagger.android.AndroidInjectionModule
import javax.inject.Singleton

@Singleton
@Component(modules = [AndroidInjectionModule::class, AppModule::class, MainInjectorsModule::class])
interface AppComponent {
    @Component.Builder
    interface Builder {
        @BindsInstance
        fun application(application: Application): Builder

        fun build(): AppComponent
    }


    fun inject(wearMusicCenter: WearMusicCenter)
    fun inject(phoneAction: PhoneAction)
}
