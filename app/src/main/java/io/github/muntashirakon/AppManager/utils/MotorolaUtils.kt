// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils

import io.github.muntashirakon.AppManager.misc.SystemProperties

object MotorolaUtils {
    @JvmStatic
    fun isMotorola(): Boolean {
        return SystemProperties.getInt("ro.mot.build.version.sdk_int", 0) != 0
    }

    @JvmStatic
    fun getMotorolaVersion(): String? {
        val sdk = SystemProperties.getInt("ro.mot.build.version.sdk_int", 0)
        val increment = SystemProperties.getInt("ro.mot.build.product.increment", 0)
        if (sdk == 0) {
            return null
        }
        return "$sdk.$increment"
    }
}
