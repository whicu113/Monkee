package com.habitat.app.di

import com.habitat.app.domain.usecase.CompletionReminderHandler
import com.habitat.app.platform.PlatformCompletionReminderHandler
import dagger.Binds
import dagger.Module
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
}
