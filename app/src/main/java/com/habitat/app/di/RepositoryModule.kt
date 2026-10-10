package com.habitat.app.di

import com.habitat.app.data.repository.CompletionRepositoryImpl
import com.habitat.app.data.repository.HabitRepositoryImpl
import com.habitat.app.data.repository.SettingsRepositoryImpl
import com.habitat.app.data.local.RoomRewardTransactionRunner
import com.habitat.app.domain.RewardTransactionRunner
import com.habitat.app.domain.usecase.LogCompletionResultUseCase
import com.habitat.app.domain.usecase.LogCompletionUseCase
import com.habitat.app.domain.repository.CompletionRepository
import com.habitat.app.domain.repository.HabitRepository
import com.habitat.app.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindLogCompletionResultUseCase(
        impl: LogCompletionUseCase
    ): LogCompletionResultUseCase

    @Binds
    @Singleton
    abstract fun bindRewardTransactionRunner(
        impl: RoomRewardTransactionRunner
    ): RewardTransactionRunner

    @Binds
    @Singleton
    abstract fun bindHabitRepository(
        impl: HabitRepositoryImpl
    ): HabitRepository

    @Binds
    @Singleton
    abstract fun bindCompletionRepository(
        impl: CompletionRepositoryImpl
    ): CompletionRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        impl: SettingsRepositoryImpl
    ): SettingsRepository
}
