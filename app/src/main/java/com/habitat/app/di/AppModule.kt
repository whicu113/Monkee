package com.habitat.app.di

import com.habitat.app.domain.AppClock
import com.habitat.app.domain.SystemAppClock
import com.habitat.app.domain.logic.RewardRandom
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlin.random.Random

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppClock(): AppClock = SystemAppClock()

    @Provides
    @Singleton
    fun provideRewardRandom(): RewardRandom = RewardRandom { boundExclusive ->
        Random.Default.nextInt(boundExclusive)
    }
}
