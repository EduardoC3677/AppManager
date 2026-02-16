// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromInt(value: Int): Boolean = value != 0

    @TypeConverter
    fun toInt(value: Boolean): Int = if (value) 1 else 0

    @TypeConverter
    fun fromLong(value: Long?): Boolean? = value?.let { it != 0L }

    @TypeConverter
    fun toLong(value: Boolean?): Long? = value?.let { if (it) 1L else 0L }
}
