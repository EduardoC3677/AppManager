// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ipc

import android.os.ParcelFileDescriptor
import com.topjohnwu.superuser.Shell
import aosp.android.content.pm.StringParceledListSlice
import io.github.muntashirakon.AppManager.BuildConfig
import io.github.muntashirakon.AppManager.IRemoteShell
import io.github.muntashirakon.AppManager.IShellResult

internal class RemoteShellImpl(cmd: Array<String>) : IRemoteShell.Stub() {
    companion object {
        init {
            Shell.enableVerboseLogging = BuildConfig.DEBUG
            Shell.setDefaultBuilder(
                Shell.Builder.create()
                    .setFlags(Shell.FLAG_MOUNT_MASTER)
                    .setTimeout(10)
            )
        }
    }

    private val mJob: Shell.Job = Shell.cmd(*cmd)

    override fun addCommand(commands: Array<String>) {
        mJob.add(*commands)
    }

    override fun addInputStream(inputStream: ParcelFileDescriptor) {
        mJob.add(ParcelFileDescriptor.AutoCloseInputStream(inputStream))
    }

    override fun exec(): IShellResult {
        val result = mJob.exec()
        return object : IShellResult.Stub() {
            override fun getStdout(): StringParceledListSlice {
                return StringParceledListSlice(result.out)
            }

            override fun getStderr(): StringParceledListSlice {
                return StringParceledListSlice(result.err)
            }

            override fun getExitCode(): Int {
                return result.code
            }

            override fun isSuccessful(): Boolean {
                return result.isSuccess
            }
        }
    }
}
