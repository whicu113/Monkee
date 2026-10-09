package com.habitat.app.di

import com.habitat.app.domain.AppClock
import com.habitat.app.domain.SystemAppClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppClock(): AppClock = SystemAppClock()
}
