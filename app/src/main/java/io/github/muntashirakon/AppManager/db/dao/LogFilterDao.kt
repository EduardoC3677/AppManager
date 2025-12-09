// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.github.muntashirakon.AppManager.db.entity.LogFilter

@Dao
interface LogFilterDao {
    @Query("SELECT * FROM log_filter")
    suspend fun getAll(): List<LogFilter>

    @Query("SELECT * FROM log_filter WHERE id = :id LIMIT 1")
    suspend fun get(id: Long): LogFilter?

    @Query("INSERT INTO log_filter (name) VALUES(:filterName)")
    suspend fun insert(filterName: String): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(logFilter: LogFilter)

    @Delete
    suspend fun delete(logFilter: LogFilter)

    @Query("DELETE FROM log_filter WHERE id = :id")
    suspend fun delete(id: Int)
}
