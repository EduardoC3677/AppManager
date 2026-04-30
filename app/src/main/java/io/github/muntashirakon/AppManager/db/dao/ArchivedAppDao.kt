// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.github.muntashirakon.AppManager.db.entity.ArchivedApp

@Dao
interface ArchivedAppDao {
    @Query("SELECT * FROM archived_apps ORDER BY app_name ASC")
    fun getAll(): LiveData<List<ArchivedApp>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(archivedApp: ArchivedApp)

    @Delete
    suspend fun delete(archivedApp: ArchivedApp)

    @Query("DELETE FROM archived_apps WHERE package_name = :packageName")
    suspend fun deleteByPackageName(packageName: String)

    @Query("DELETE FROM archived_apps WHERE package_name = :packageName")
    fun deleteByPackageNameSync(packageName: String)

    @Query("SELECT package_name FROM archived_apps")
    fun getAllPackageNamesSync(): List<String>
}
