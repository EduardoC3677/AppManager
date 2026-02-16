// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import io.github.muntashirakon.AppManager.utils.FreezeUtils.FreezeMethod

@Entity(tableName = "freeze_type")
class FreezeType {
    @JvmField
    @PrimaryKey
    @ColumnInfo(name = "package_name")
    var packageName: String = ""

    @JvmField
    @ColumnInfo(name = "type")
    @FreezeMethod
    var type: Int = 0

    constructor()

    constructor(packageName: String, @FreezeMethod type: Int) {
        this.packageName = packageName
        this.type = type
    }
}
