// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.github.muntashirakon.AppManager.db.entity.App

@Dao
interface AppDao {
    @Query("SELECT * FROM app")
    suspend fun getAll(): List<App>

    @Query("SELECT * FROM app WHERE is_installed = 1")
    suspend fun getAllInstalled(): List<App>

    @Query("SELECT * FROM app WHERE package_name = :packageName")
    suspend fun getAll(packageName: String): List<App>

    @Query("SELECT * FROM app WHERE package_name = :packageName AND user_id = :userId")
    suspend fun getAll(packageName: String, userId: Int): List<App>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(apps: List<App>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(app: App)

    @Update
    suspend fun update(app: App)

    @Query("DELETE FROM app WHERE 1")
    suspend fun deleteAll()

    @Delete
    suspend fun delete(app: App)

    @Delete
    suspend fun delete(apps: List<App>)

    @Query("DELETE FROM app WHERE package_name = :packageName AND user_id = :userId")
    suspend fun delete(packageName: String, userId: Int)
}
