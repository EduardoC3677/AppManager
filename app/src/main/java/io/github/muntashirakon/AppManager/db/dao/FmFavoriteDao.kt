// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.github.muntashirakon.AppManager.db.entity.FmFavorite

@Dao
interface FmFavoriteDao {
    @Query("SELECT * FROM fm_favorite")
    suspend fun getAll(): List<FmFavorite>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(fmFavorite: FmFavorite): Long

    @Query("UPDATE fm_favorite SET name = :newName WHERE id = :id")
    suspend fun rename(id: Long, newName: String)

    @Query("DELETE FROM fm_favorite WHERE id = :id")
    suspend fun delete(id: Long)
}
