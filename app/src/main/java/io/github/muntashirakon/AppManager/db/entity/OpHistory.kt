// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "op_history")
data class OpHistory(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    var id: Long = 0,

    @ColumnInfo(name = "type")
    var type: String = "",

    @ColumnInfo(name = "time")
    var execTime: Long = 0,

    @ColumnInfo(name = "data")
    var serializedData: String = "",

    @ColumnInfo(name = "status")
    var status: String = "",

    @ColumnInfo(name = "extra")
    var serializedExtra: String? = null
)
