package com.example.monkee.di

import com.example.monkee.domain.AppClock
import com.example.monkee.domain.SystemAppClock
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
