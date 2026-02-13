// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.muntashirakon.AppManager.utils.AlphanumComparator

@Entity(
    tableName = "log_filter",
    indices = [Index(name = "index_name", value = ["name"], unique = true)]
)
data class LogFilter(
    @JvmField
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    var id: Long = 0,

    @JvmField
    @ColumnInfo(name = "name")
    var name: String = ""
) : Comparable<LogFilter> {

    override fun compareTo(other: LogFilter): Int {
        return COMPARATOR.compare(this, other)
    }

    companion object {
        @JvmField
        val COMPARATOR = Comparator<LogFilter> { o1, o2 ->
            AlphanumComparator.compareStringIgnoreCase(o1.name, o2.name)
        }
    }
}
