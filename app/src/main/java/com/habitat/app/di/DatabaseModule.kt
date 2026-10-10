package com.habitat.app.di

import android.content.Context
import androidx.room.Room
import com.habitat.app.data.local.AppDatabase
import com.habitat.app.data.local.CompletionDao
import com.habitat.app.data.local.DropRecordDao
import com.habitat.app.data.local.HabitDao
import com.habitat.app.data.local.HabitGroupDao
import com.habitat.app.data.local.LedgerEntryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "habitat.db"
        )
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
            .build()
    }

    @Provides
    fun provideHabitDao(database: AppDatabase): HabitDao {
        return database.habitDao()
    }

    @Provides
    fun provideHabitGroupDao(database: AppDatabase): HabitGroupDao {
        return database.habitGroupDao()
    }

    @Provides
    fun provideCompletionDao(database: AppDatabase): CompletionDao {
        return database.completionDao()
    }

    @Provides
    fun provideLedgerEntryDao(database: AppDatabase): LedgerEntryDao {
        return database.ledgerEntryDao()
    }

    @Provides
    fun provideDropRecordDao(database: AppDatabase): DropRecordDao {
        return database.dropRecordDao()
    }
}
