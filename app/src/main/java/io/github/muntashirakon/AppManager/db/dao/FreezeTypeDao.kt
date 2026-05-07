// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db.dao

import kotlinx.coroutines.flow.Flow
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.github.muntashirakon.AppManager.db.entity.FreezeType

@Dao
interface FreezeTypeDao {
    @Query("SELECT * FROM freeze_type WHERE package_name = :packageName LIMIT 1")
    fun getFlow(packageName: String): Flow<FreezeType?>

    @Query("SELECT * FROM freeze_type WHERE package_name = :packageName LIMIT 1")
    suspend fun get(packageName: String): FreezeType?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(freezeType: FreezeType)

    @Query("DELETE FROM freeze_type WHERE package_name = :packageName")
    suspend fun delete(packageName: String)
}
