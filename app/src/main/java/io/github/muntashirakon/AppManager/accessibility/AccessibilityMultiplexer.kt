// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.accessibility

import android.os.Bundle
import androidx.annotation.IntDef

class AccessibilityMultiplexer private constructor() {
    @Retention(AnnotationRetention.SOURCE)
    @IntDef(flag = true, value = [M_INSTALL, M_UNINSTALL, M_CLEAR_CACHE, M_CLEAR_DATA, M_FORCE_STOP, M_NAVIGATE_TO_STORAGE_AND_CACHE, M_LEADING_ACTIVITY_TRACKER])
    private annotation class Flags

    private var mFlags = 0
    private val mArgs = Bundle()

    fun isInstallEnabled(): Boolean = (mFlags and M_INSTALL) != 0
    fun isUninstallEnabled(): Boolean = (mFlags and M_UNINSTALL) != 0
    fun isClearCacheEnabled(): Boolean = (mFlags and M_CLEAR_CACHE) != 0
    fun isClearDataEnabled(): Boolean = (mFlags and M_CLEAR_DATA) != 0
    fun isForceStopEnabled(): Boolean = (mFlags and M_FORCE_STOP) != 0
    fun isNavigateToStorageAndCache(): Boolean = (mFlags and M_NAVIGATE_TO_STORAGE_AND_CACHE) != 0
    fun isLeadingActivityTracker(): Boolean = (mFlags and M_LEADING_ACTIVITY_TRACKER) != 0

    fun clearFlags() { mFlags = 0 }

    fun enableInstall(enable: Boolean) { addOrRemoveFlag(M_INSTALL, enable) }
    fun enableUninstall(enable: Boolean) { addOrRemoveFlag(M_UNINSTALL, enable) }
    fun enableClearCache(enable: Boolean) { addOrRemoveFlag(M_CLEAR_CACHE, enable) }
    fun enableClearData(enable: Boolean) { addOrRemoveFlag(M_CLEAR_DATA, enable) }
    fun enableForceStop(enable: Boolean) { addOrRemoveFlag(M_FORCE_STOP, enable) }
    fun enableNavigateToStorageAndCache(enable: Boolean) { addOrRemoveFlag(M_NAVIGATE_TO_STORAGE_AND_CACHE, enable) }
    fun enableLeadingActivityTracker(enable: Boolean) { addOrRemoveFlag(M_LEADING_ACTIVITY_TRACKER, enable) }

    var titleText: String?
        get() = mArgs.getString("title")
        set(value) = mArgs.putString("title", value)

    private fun addOrRemoveFlag(@Flags flag: Int, add: Boolean) {
        if (add) mFlags = mFlags or flag else mFlags = mFlags and flag.inv()
    }

    companion object {
        private const val M_INSTALL = 1
        private const val M_UNINSTALL = 1 shl 1
        private const val M_CLEAR_CACHE = 1 shl 2
        private const val M_CLEAR_DATA = 1 shl 3
        private const val M_FORCE_STOP = 1 shl 4
        private const val M_NAVIGATE_TO_STORAGE_AND_CACHE = 1 shl 5
        private const val M_LEADING_ACTIVITY_TRACKER = 1 shl 6

        @JvmStatic
        val instance = AccessibilityMultiplexer()
    }
}
