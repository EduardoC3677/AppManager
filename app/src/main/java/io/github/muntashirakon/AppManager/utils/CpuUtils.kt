// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils

import android.content.Context
import android.os.PowerManager
import androidx.annotation.Keep

object CpuUtils {
    init {
        System.loadLibrary("am")
    }

    @Keep
    @JvmStatic
    external fun getClockTicksPerSecond(): Long

    @Keep
    @JvmStatic
    external fun getCpuModel(): String?

    @JvmStatic
    fun getPartialWakeLock(tagPostfix: String): PowerManager.WakeLock {
        val pm = ContextUtils.getContext().getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AppManager::$tagPostfix")
    }

    @JvmStatic
    fun releaseWakeLock(wakeLock: PowerManager.WakeLock?) {
        if (wakeLock != null && wakeLock.isHeld) {
            wakeLock.release()
        }
    }
}
