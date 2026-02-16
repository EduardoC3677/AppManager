// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db;

import androidx.room.TypeConverter;

public class Converters {
    @TypeConverter
    public static boolean fromInt(int value) {
        return value != 0;
    }

    @TypeConverter
    public static int toInt(boolean value) {
        return value ? 1 : 0;
    }

    @TypeConverter
    public static Boolean fromLong(Long value) {
        return value == null ? null : value != 0L;
    }

    @TypeConverter
    public static Long toLong(Boolean value) {
        return value == null ? null : (value ? 1L : 0L);
    }
}
