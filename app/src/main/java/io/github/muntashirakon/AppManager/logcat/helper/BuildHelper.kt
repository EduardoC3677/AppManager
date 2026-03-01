// SPDX-License-Identifier: WTFPL AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat.helper

import android.os.Build
import java.lang.reflect.Field
import java.util.*

/**
 * Copyright 2012 Nolan Lawson
 */
object BuildHelper {
    private val BUILD_FIELDS = listOf(
        "BOARD", "BOOTLOADER", "BRAND", "CPU_ABI", "CPU_ABI2",
        "DEVICE", "DISPLAY", "FINGERPRINT", "HARDWARE", "HOST",
        "ID", "MANUFACTURER", "MODEL", "PRODUCT", "RADIO",
        "SERIAL", "TAGS", "TIME", "TYPE", "USER"
    )

    private val BUILD_VERSION_FIELDS = listOf(
        "CODENAME", "INCREMENTAL", "RELEASE", "SDK_INT"
    )

    @JvmStatic
    fun getBuildInformationAsString(): String {
        val keysToValues: SortedMap<String, String> = TreeMap()
        for (buildField in BUILD_FIELDS) {
            putKeyValue(Build::class.java, buildField, keysToValues)
        }
        for (buildVersionField in BUILD_VERSION_FIELDS) {
            putKeyValue(Build.VERSION::class.java, buildVersionField, keysToValues)
        }
        val stringBuilder = StringBuilder()
        for ((key, value) in keysToValues) {
            stringBuilder.append(key).append(": ").append(value).append('
')
        }
        return stringBuilder.toString()
    }

    private fun putKeyValue(clazz: Class<*>, buildField: String, keysToValues: MutableMap<String, String>) {
        try {
            val field: Field = clazz.getField(buildField)
            val value = field.get(null)
            val key = clazz.simpleName.lowercase(Locale.ROOT) + "." + buildField.lowercase(Locale.ROOT)
            keysToValues[key] = value.toString()
        } catch (ignore: SecurityException) {
        } catch (ignore: NoSuchFieldException) {
        } catch (ignore: IllegalAccessException) {
        }
    }
}
