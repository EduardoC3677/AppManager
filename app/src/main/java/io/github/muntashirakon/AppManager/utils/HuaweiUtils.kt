// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils

import android.os.Build

object HuaweiUtils {
    @JvmStatic
    fun isHuaweiDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER
        val brand = Build.BRAND
        return manufacturer.equals("HUAWEI", ignoreCase = true) || brand.equals("HUAWEI", ignoreCase = true)
    }

    @JvmStatic
    fun isEmui(): Boolean {
        val emuiVersion = System.getProperty("ro.build.version.emui")
        return !emuiVersion.isNullOrEmpty()
    }

    @JvmStatic
    fun isHarmonyOs(): Boolean {
        val harmonyVersion = System.getProperty("ro.harmony.version")
        return !harmonyVersion.isNullOrEmpty()
    }

    @JvmStatic
    fun isStockHuawei(): Boolean {
        return isHuaweiDevice() && (isHarmonyOs() || isEmui())
    }
}
