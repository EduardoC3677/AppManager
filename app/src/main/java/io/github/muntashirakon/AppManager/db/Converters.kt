// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromInt(value: Int): Boolean {
        return value != 0
    }

    @TypeConverter
    fun toInt(value: Boolean): Int {
        return if (value) 1 else 0
    }

    @TypeConverter
    fun fromLong(value: Long?): Boolean? {
        return if (value == null) null else value != 0L
    }

    @TypeConverter
    fun toLong(value: Boolean?): Long? {
        return if (value == null) null else (if (value) 1L else 0L)
    }
}
