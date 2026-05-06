// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils

import androidx.annotation.IntDef
import io.github.muntashirakon.AppManager.runner.Runner
import java.util.Locale

object RestartUtils {
    @IntDef(
        RESTART_NORMAL,
        RESTART_RECOVERY,
        RESTART_BOOTLOADER,
        RESTART_USERSPACE,
        RESTART_DOWNLOAD,
        RESTART_EDL
    )
    @Retention(AnnotationRetention.SOURCE)
    annotation class RestartType

    const val RESTART_NORMAL = 0
    const val RESTART_RECOVERY = 1
    const val RESTART_BOOTLOADER = 2
    const val RESTART_USERSPACE = 3
    const val RESTART_DOWNLOAD = 4
    const val RESTART_EDL = 5

    private val RESTART_REASON = arrayOf(
        // Mapped to above
        "", "recovery", "bootloader", "userspace", "download", "edl"\n)

    @JvmStatic
    fun restart(@RestartType type: Int) {
        restart(RESTART_REASON[type])
    }

    private fun restart(reason: String) {
        // https://github.com/topjohnwu/Magisk/blob/5512917ec123e815dec1e3af871357f760acc1f3/app/src/main/java/com/topjohnwu/magisk/ktx/XSU.kt
        if (RESTART_REASON[RESTART_RECOVERY] == reason) {
            // KEYCODE_POWER = 26, hide incorrect "Factory data reset" message
            Runner.runCommand(arrayOf("/system/bin/input", "keyevent", "26"))
        }
        val cmd = String.format(
            Locale.ROOT,
            "/system/bin/svc power reboot %s || /system/bin/reboot %s",
            reason,
            reason
        )
        Runner.runCommand(cmd)
    }
}
