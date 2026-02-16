// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.github.muntashirakon.AppManager.db.entity.Backup

@Dao
interface BackupDao {
    @Query("SELECT * FROM backup")
    suspend fun getAll(): List<Backup>

    @Query("SELECT * FROM backup")
    fun getAllSync(): List<Backup>

    @Query("SELECT * FROM backup WHERE package_name = :packageName")
    suspend fun get(packageName: String): List<Backup>

    @Query("SELECT * FROM backup WHERE package_name = :packageName")
    fun getSync(packageName: String): List<Backup>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(backups: List<Backup>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSync(backups: List<Backup>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(backup: Backup)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSync(backup: Backup)

    @Update
    suspend fun update(backup: Backup)

    @Delete
    suspend fun delete(backup: Backup)

    @Delete
    fun deleteSync(backup: Backup)

    @Delete
    suspend fun delete(backups: List<Backup>)

    @Delete
    fun deleteSync(backups: List<Backup>)

    @Query("DELETE FROM backup WHERE package_name = :packageName AND backup_name = :backupName")
    suspend fun delete(packageName: String, backupName: String)

    @Query("DELETE FROM backup WHERE 1")
    suspend fun deleteAll()

    @Query("DELETE FROM backup WHERE 1")
    fun deleteAllSync()
}
