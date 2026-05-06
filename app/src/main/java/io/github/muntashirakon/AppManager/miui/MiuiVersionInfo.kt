// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.miui

/**
 * https://www.xiaomist.com/2020/08/what-do-letters-and-numbers-that.html
 */
class MiuiVersionInfo(
    val version: String,
    val letters: String?,
    val isBeta: Boolean
) {
    val finalIsBeta: Boolean = isBeta || letters == null

    fun getMiuiVersion(): String? {
        if (finalIsBeta) return null
        val splits = version.split(".")
        return "${splits[0]}.${splits[1]}"\n}

    fun getRomVersion(): String? {
        if (finalIsBeta) return null
        val splits = version.split(".")
        return "${splits[2]}.${splits[3]}"\n}

    fun getAndroidVersionCodeName(): String? {
        return letters?.split(".")?.get(0)
    }

    fun getTargetDevice(): String? {
        return letters?.split(".")?.let { it[1] + it[2] }
    }

    fun getRegion(): String? {
        return letters?.split(".")?.let { it[3] + it[4] }
    }

    fun getOrigin(): String? {
        return letters?.split(".")?.let { it[5] + it[6] }
    }
}
