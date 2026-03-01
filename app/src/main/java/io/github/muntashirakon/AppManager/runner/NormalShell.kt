// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.runner

import androidx.annotation.WorkerThread
import com.topjohnwu.superuser.Shell

internal class NormalShell(isRoot: Boolean) : Runner() {
    private val mShell: Shell

    init {
        if (isRoot == Shell.getShell().isRoot) {
            mShell = Shell.getShell()
        } else {
            val flags = if (isRoot) Shell.FLAG_MOUNT_MASTER else Shell.FLAG_NON_ROOT_SHELL
            mShell = Shell.Builder.create().setFlags(flags).setTimeout(10).build()
        }
    }

    override fun isRoot(): Boolean {
        return mShell.isRoot
    }

    @WorkerThread
    @Synchronized
    override fun runCommand(): Result {
        val stdout = mutableListOf<String>()
        val stderr = mutableListOf<String>()
        val job = mShell.newJob().add(*commands.toTypedArray()).to(stdout, stderr)
        for (inputStream in inputStreams) {
            job.add(inputStream)
        }
        val result = job.exec()
        return Result(stdout, stderr, result.code)
    }
}
