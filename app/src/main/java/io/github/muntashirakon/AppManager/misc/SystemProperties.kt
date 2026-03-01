// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc

import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.runner.Runner

object SystemProperties {
    @JvmStatic
    fun get(key: String, defaultVal: String): String {
        return try {
            android.os.SystemProperties.get(key, defaultVal)
        } catch (e: Exception) {
            Log.w("SystemProperties", "Unable to use SystemProperties.get", e)
            val result = Runner.runCommand(arrayOf("getprop", key, defaultVal))
            if (result.isSuccessful) result.output.trim() else defaultVal
        }
    }

    @JvmStatic
    fun getBoolean(key: String, defaultVal: Boolean): Boolean {
        val `val` = get(key, defaultVal.toString())
        return if ("1" == `val`) true else `val`.toBoolean()
    }

    @JvmStatic
    fun getInt(key: String, defaultVal: Int): Int {
        val `val` = get(key, defaultVal.toString())
        return try {
            `val`.toInt()
        } catch (e: NumberFormatException) {
            defaultVal
        }
    }
}
