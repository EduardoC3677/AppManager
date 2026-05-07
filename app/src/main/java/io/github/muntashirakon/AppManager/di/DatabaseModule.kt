// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.muntashirakon.AppManager.db.AppsDb
import io.github.muntashirakon.AppManager.db.dao.*
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppsDb {
        return Room.databaseBuilder(context, AppsDb::class.java, "apps.db")
            .addMigrations(
                AppsDb.M_2_3, AppsDb.M_3_4, AppsDb.M_4_5, AppsDb.M_5_6,
                AppsDb.M_6_7, AppsDb.M_7_8, AppsDb.M_8_9, AppsDb.M_9_10, AppsDb.M_10_11
            )
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
    }

    @Provides
    fun provideAppDao(db: AppsDb): AppDao = db.appDao()

    @Provides
    fun provideBackupDao(db: AppsDb): BackupDao = db.backupDao()

    @Provides
    fun provideLogFilterDao(db: AppsDb): LogFilterDao = db.logFilterDao()

    @Provides
    fun provideOpHistoryDao(db: AppsDb): OpHistoryDao = db.opHistoryDao()

    @Provides
    fun provideFmFavoriteDao(db: AppsDb): FmFavoriteDao = db.fmFavoriteDao()

    @Provides
    fun provideFreezeTypeDao(db: AppsDb): FreezeTypeDao = db.freezeTypeDao()

    @Provides
    fun provideArchivedAppDao(db: AppsDb): ArchivedAppDao = db.archivedAppDao()
}
