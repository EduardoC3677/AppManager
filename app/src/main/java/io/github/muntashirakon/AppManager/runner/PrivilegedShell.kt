// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.runner

import android.os.RemoteException
import androidx.annotation.WorkerThread
import io.github.muntashirakon.AppManager.ipc.LocalServices
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.utils.ParcelFileDescriptorUtil
import java.io.IOException

internal class PrivilegedShell : Runner() {
    override fun isRoot(): Boolean {
        // ADB shell in App Manager always runs in no-root
        return false
    }

    @WorkerThread
    @Synchronized
    override fun runCommand(): Result {
        return try {
            val amService = LocalServices.amService
            val shell = amService.getShell(commands.toTypedArray())
            for (inputStream in inputStreams) {
                shell.addInputStream(ParcelFileDescriptorUtil.pipeFrom(inputStream))
            }
            val result = shell.exec()
            Result(result.stdout.list, result.stderr.list, result.exitCode)
        } catch (e: RemoteException) {
            Log.e(PrivilegedShell::class.java.simpleName, e)
            Result()
        } catch (e: IOException) {
            Log.e(PrivilegedShell::class.java.simpleName, e)
            Result()
        }
    }
}
