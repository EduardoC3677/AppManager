// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils

import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import android.os.ResultReceiver
import android.text.TextUtils
import androidx.annotation.RequiresApi
import io.github.muntashirakon.AppManager.BuildConfig
import io.github.muntashirakon.AppManager.ipc.ProxyBinder
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.io.Path
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

/**
 * A utility class for executing shell commands via a Binder interface.
 *
 * **Warning:** The `execute` methods in this class are synchronous and will
 * block the calling thread until the shell command completes. To avoid UI freezes,
 * always call these methods from a background thread.
 */
@RequiresApi(Build.VERSION_CODES.N)
class BinderShellExecutor {
    class ShellResult {
        var resultCode: Int = 0
            internal set
        var stdout: String? = null
            internal set
        var stderr: String? = null
            internal set
    }

    companion object {
        @JvmStatic
        @Throws(IOException::class)
        fun execute(binder: IBinder, command: Array<String>, input: File?): ShellResult {
            if (input == null) {
                return execute(binder, command)
            }
            FileInputStream(input).use { inputStream ->
                return execute(binder, command, inputStream)
            }
        }

        @JvmStatic
        @Throws(IOException::class)
        fun execute(binder: IBinder, command: Array<String>, input: Path?): ShellResult {
            if (input == null) {
                return execute(binder, command)
            }
            input.openInputStream().use { os ->
                return execute(binder, command, os)
            }
        }

        @JvmStatic
        @Throws(IOException::class)
        fun execute(binder: IBinder, command: Array<String>, input: FileInputStream?): ShellResult {
            if (input == null) {
                return execute(binder, command)
            }
            return execute(binder, command, input.fd)
        }

        @JvmStatic
        @Throws(IOException::class)
        fun execute(binder: IBinder, command: Array<String>, input: InputStream?): ShellResult {
            if (input == null) {
                return execute(binder, command)
            }
            val inputFd = ParcelFileDescriptorUtil.pipeFrom(input)
            return execute(binder, command, inputFd.fileDescriptor)
        }

        @JvmStatic
        @Throws(IOException::class)
        fun execute(binder: IBinder, command: Array<String>, input: FileDescriptor?): ShellResult {
            if (input == null) {
                return execute(binder, command)
            }
            return executeInternal(binder, command, input)
        }

        @JvmStatic
        @Throws(IOException::class)
        fun execute(binder: IBinder, command: Array<String>): ShellResult {
            val pipe = ParcelFileDescriptor.createPipe()
            val readSide = pipe[0]
            val writeSide = pipe[1]
            return executeInternal(binder, command, readSide.fileDescriptor)
        }

        @Throws(IOException::class)
        private fun executeInternal(binder: IBinder, command: Array<String>, input: FileDescriptor): ShellResult {
            try {
                ByteArrayOutputStream().use { stdoutStream ->
                    ByteArrayOutputStream().use { stderrStream ->
                        val stdoutFd = ParcelFileDescriptorUtil.pipeTo(stdoutStream)
                        val stderrFd = ParcelFileDescriptorUtil.pipeTo(stderrStream)
                        val atomicResultCode = AtomicInteger(-1)
                        val sem = CountDownLatch(1)
                        ProxyBinder.shellCommand(
                            binder,
                            input,
                            stdoutFd.fileDescriptor,
                            stderrFd.fileDescriptor,
                            command,
                            null,
                            object : ResultReceiver(ThreadUtils.getUiThreadHandler()) {
                                override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                                    atomicResultCode.set(resultCode)
                                    sem.countDown()
                                }
                            }
                        )
                        try {
                            sem.await()
                        } catch (ignore: InterruptedException) {
                        }
                        val resultCode = atomicResultCode.get()
                        if (resultCode == -1) {
                            throw IOException("Invalid result code $resultCode")
                        }
                        val result = ShellResult()
                        result.resultCode = resultCode
                        result.stdout = stdoutStream.toString()
                        result.stderr = stderrStream.toString()
                        if (BuildConfig.DEBUG) {
                            Log.d("BinderShell_IN", TextUtils.join(" ", command))
                            Log.d("BinderShell_OUT", "(exit code: ${result.resultCode})")
                            Log.d("BinderShell_OUT", result.stdout ?: "")
                        }
                        return result
                    }
                }
            } catch (e: RemoteException) {
                return ExUtils.rethrowFromSystemServer(e)
            } catch (th: Throwable) {
                return ExUtils.rethrowAsIOException(th)
            }
        }
    }
}
