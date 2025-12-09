// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "fm_favorite")
data class FmFavorite(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    var id: Long = 0,

    @ColumnInfo(name = "name")
    var name: String = "",

    @ColumnInfo(name = "uri")
    var uri: String = "",

    @ColumnInfo(name = "init_uri")
    var initUri: String? = null,

    @ColumnInfo(name = "options")
    var options: Int = 0,

    @ColumnInfo(name = "order")
    var order: Long = 0,

    @ColumnInfo(name = "type")
    var type: Int = 0
)
