// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.github.muntashirakon.AppManager.db.entity.OpHistory

@Dao
interface OpHistoryDao {
    @Query("SELECT * FROM op_history")
    suspend fun getAll(): List<OpHistory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(opHistory: OpHistory): Long

    @Query("DELETE FROM op_history WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM op_history WHERE 1")
    suspend fun deleteAll()
}
