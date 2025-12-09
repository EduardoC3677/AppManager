// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils

import android.annotation.SuppressLint
import android.miui.AppOpsUtils
import android.os.Build
import android.text.TextUtils
import io.github.muntashirakon.AppManager.misc.SystemProperties
import io.github.muntashirakon.AppManager.miui.MiuiVersionInfo

// Copyright 2020 Aefyr
object MiuiUtils {
    private var sMiuiVersionInfo: MiuiVersionInfo? = null

    @JvmStatic
    fun isMiui(): Boolean {
        return !TextUtils.isEmpty(SystemProperties.get("ro.miui.ui.version.name", ""))
    }

    @JvmStatic
    fun getMiuiVersionInfo(): MiuiVersionInfo? {
        if (sMiuiVersionInfo != null) {
            return sMiuiVersionInfo
        }
        if (!isMiui()) {
            return null
        }
        var versionString = Build.VERSION.INCREMENTAL
        if (TextUtils.isDigitsOnly(versionString)) {
            return MiuiVersionInfo(versionString, null, true).also { sMiuiVersionInfo = it }
        }
        if (!versionString.startsWith("V")) {
            throw IllegalStateException("Stable version must begin with `V`")
        }
        versionString = versionString.substring(1)
        var firstNoDigitIndex = -1
        val len = versionString.length
        for (i in 0 until len) {
            val cp = versionString[i]
            if (cp in '0'..'9' || cp == '.') {
                continue
            }
            // Non-digit
            firstNoDigitIndex = i
            break
        }
        return MiuiVersionInfo(
            versionString.substring(0, firstNoDigitIndex),
            versionString.substring(firstNoDigitIndex),
            false
        ).also { sMiuiVersionInfo = it }
    }

    private fun parseVersionIntoParts(version: String): IntArray {
        return try {
            val versionParts = version.split("\\.".toRegex()).toTypedArray()
            val intVersionParts = IntArray(versionParts.size)

            for (i in versionParts.indices) {
                intVersionParts[i] = versionParts[i].toInt()
            }

            intVersionParts
        } catch (e: Exception) {
            intArrayOf(-1)
        }
    }

    /**
     * @return 0 if versions are equal, values less than 0 if ver1 is lower than ver2, value more than 0 if ver1 is higher than ver2
     */
    private fun compareVersions(version1: String, version2: String): Int {
        if (version1 == version2) {
            return 0
        }

        val version1Parts = parseVersionIntoParts(version1)
        val version2Parts = parseVersionIntoParts(version2)

        for (i in version2Parts.indices) {
            if (i >= version1Parts.size) return -1

            if (version1Parts[i] < version2Parts[i]) return -1

            if (version1Parts[i] > version2Parts[i]) return 1
        }

        return 1
    }

    @JvmStatic
    fun isActualMiuiVersionAtLeast(targetVersion: String, targetVersionBeta: String): Boolean {
        val actualVersionInfo = getMiuiVersionInfo()
            ?: // Not MIUI
            return false
        val sourceVersion = actualVersionInfo.miuiVersion
        if (sourceVersion == null) {
            // Beta versions do not have MIUI version string.
            // Compare beta versions
            return compareVersions(actualVersionInfo.version, targetVersionBeta) >= 0
        }
        // This is a stable version
        return compareVersions(sourceVersion, targetVersion) >= 0
    }

    @SuppressLint("PrivateApi")
    @JvmStatic
    fun isMiuiOptimizationDisabled(): Boolean {
        // ApplicationPackageManager#isXOptMode()
        if (!SystemProperties.getBoolean(
                "persist.sys.miui_optimization",
                "1" != SystemProperties.get("ro.miui.cts", "0")
            )
        ) {
            return true
        }
        return try {
            AppOpsUtils.isXOptMode()
        } catch (e: Throwable) {
            false
        }
    }
}
