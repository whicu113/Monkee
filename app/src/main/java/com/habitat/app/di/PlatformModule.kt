package com.habitat.app.di

import com.habitat.app.domain.WidgetUpdater
import com.habitat.app.domain.WidgetRefreshSignal
import com.habitat.app.domain.usecase.CompletionReminderHandler
import com.habitat.app.platform.PlatformCompletionReminderHandler
import com.habitat.app.platform.NotificationNotifier
import com.habitat.app.platform.SystemNotificationNotifier
import com.habitat.app.platform.widget.GlanceWidgetUpdater
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PlatformModule {

    @Binds
    @Singleton
    abstract fun bindCompletionReminderHandler(
        impl: PlatformCompletionReminderHandler
    ): CompletionReminderHandler

    @Binds
    @Singleton
    abstract fun bindWidgetUpdater(impl: GlanceWidgetUpdater): WidgetUpdater

    @Binds
    @Singleton
    abstract fun bindWidgetRefreshSignal(impl: GlanceWidgetUpdater): WidgetRefreshSignal

    companion object {
        @Provides
        @Singleton
        fun provideNotificationNotifier(): NotificationNotifier = SystemNotificationNotifier()
    }
}
