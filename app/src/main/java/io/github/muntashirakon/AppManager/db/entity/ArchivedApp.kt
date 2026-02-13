// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

@Entity(tableName = "archived_apps")
data class ArchivedApp(
    @PrimaryKey
    @ColumnInfo(name = "package_name")
    var packageName: String,

    @ColumnInfo(name = "app_name")
    var appName: String? = null,

    @ColumnInfo(name = "archive_timestamp")
    var archiveTimestamp: Long = 0,

    @ColumnInfo(name = "apk_path")
    var apkPath: String? = null
) {
    @Ignore
    constructor(packageName: String, appName: String?, archiveTimestamp: Long) : this(
        packageName,
        appName,
        archiveTimestamp,
        null
    )
}
