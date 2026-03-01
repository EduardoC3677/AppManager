// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.runner

import android.text.TextUtils
import androidx.annotation.WorkerThread
import com.topjohnwu.superuser.internal.UiThreadHandler
import io.github.muntashirakon.AppManager.ipc.LocalServices
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.settings.Ops
import java.io.InputStream
import java.util.concurrent.Executor

abstract class Runner {
    companion object {
        @JvmField
        val TAG: String = Runner::class.java.simpleName

        private var sRootShell: NormalShell? = null
        private var sPrivilegedShell: PrivilegedShell? = null
        private var sShizukuShell: ShizukuShell? = null
        private var sNoRootShell: NormalShell? = null

        @get:JvmStatic
        private val instance: Runner
            get() {
                return if (Ops.isDirectRoot()) {
                    getRootInstance()
                } else if (Ops.isShizuku()) {
                    getShizukuInstance()
                } else if (LocalServices.alive()) {
                    getPrivilegedInstance()
                } else {
                    getNoRootInstance()
                }
            }

        @JvmStatic
        internal fun getRootInstance(): Runner {
            if (sRootShell == null) {
                sRootShell = NormalShell(true)
                Log.d(TAG, "RootShell")
            }
            return sRootShell!!
        }

        @JvmStatic
        private fun getPrivilegedInstance(): Runner {
            if (sPrivilegedShell == null) {
                sPrivilegedShell = PrivilegedShell()
                Log.d(TAG, "PrivilegedShell")
            }
            return sPrivilegedShell!!
        }

        @JvmStatic
        private fun getShizukuInstance(): Runner {
            if (sShizukuShell == null) {
                sShizukuShell = ShizukuShell()
                Log.d(TAG, "ShizukuShell")
            }
            return sShizukuShell!!
        }

        @JvmStatic
        private fun getNoRootInstance(): Runner {
            if (sNoRootShell == null) {
                sNoRootShell = NormalShell(false)
                Log.d(TAG, "NoRootShell")
            }
            return sNoRootShell!!
        }

        @JvmStatic
        @Synchronized
        fun runCommand(command: String): Result {
            return runCommand(instance, command, null)
        }

        @JvmStatic
        @Synchronized
        fun runCommand(command: Array<String>): Result {
            return runCommand(instance, command, null)
        }

        @JvmStatic
        @Synchronized
        fun runCommand(command: String, inputStream: InputStream?): Result {
            return runCommand(instance, command, inputStream)
        }

        @JvmStatic
        @Synchronized
        fun runCommand(command: Array<String>, inputStream: InputStream?): Result {
            return runCommand(instance, command, inputStream)
        }

        @JvmStatic
        @Synchronized
        private fun runCommand(
            runner: Runner, command: String,
            inputStream: InputStream?
        ): Result {
            return runner.run(command, inputStream)
        }

        @JvmStatic
        @Synchronized
        private fun runCommand(
            runner: Runner, command: Array<String>,
            inputStream: InputStream?
        ): Result {
            val cmd = StringBuilder()
            for (part in command) {
                cmd.append(RunnerUtils.escape(part)).append(" ")
            }
            return runCommand(runner, cmd.toString(), inputStream)
        }
    }

    class Result @JvmOverloads constructor(
        private val mStdout: List<String> = emptyList(),
        private val mStderr: List<String> = emptyList(),
        private val mExitCode: Int = 1
    ) {
        init {
            // Print stderr
            if (mStderr.isNotEmpty()) {
                Log.e(TAG, TextUtils.join("
", mStderr))
            }
        }

        constructor(exitCode: Int) : this(emptyList(), emptyList(), exitCode)

        fun isSuccessful(): Boolean {
            return mExitCode == 0
        }

        fun getExitCode(): Int {
            return mExitCode
        }

        fun getOutputAsList(): List<String> {
            return mStdout
        }

        fun getOutputAsList(firstIndex: Int): List<String> {
            if (firstIndex >= mStdout.size) {
                return emptyList()
            }
            return mStdout.subList(firstIndex, mStdout.size)
        }

        fun getOutput(): String {
            return TextUtils.join("
", mStdout)
        }

        fun getStderr(): List<String> {
            return mStderr
        }
    }

    protected val commands: MutableList<String> = ArrayList()
    protected val inputStreams: MutableList<InputStream> = ArrayList()

    fun addCommand(command: String) {
        commands.add(command)
    }

    fun add(inputStream: InputStream) {
        inputStreams.add(inputStream)
    }

    fun clear() {
        commands.clear()
        inputStreams.clear()
    }

    abstract fun isRoot(): Boolean

    @WorkerThread
    protected abstract fun runCommand(): Result

    private fun run(command: String, inputStream: InputStream?): Result {
        return try {
            clear()
            addCommand(command)
            if (inputStream != null) {
                add(inputStream)
            }
            runCommand()
        } finally {
            clear()
        }
    }
}
