package com.habitat.app.di

import com.habitat.app.domain.usecase.CompletionReminderHandler
import com.habitat.app.platform.PlatformCompletionReminderHandler
import com.habitat.app.platform.NotificationNotifier
import com.habitat.app.platform.SystemNotificationNotifier
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

    companion object {
        @Provides
        @Singleton
        fun provideNotificationNotifier(): NotificationNotifier = SystemNotificationNotifier()
    }
}
