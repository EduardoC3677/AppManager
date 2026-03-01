// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.runner

import androidx.annotation.WorkerThread

internal class AdbShell(private val deviceId: String) : Runner() {
    override fun isRoot(): Boolean {
        return false
    }

    @WorkerThread
    @Synchronized
    override fun runCommand(): Result {
        // To be implemented
        return Result()
    }
}
