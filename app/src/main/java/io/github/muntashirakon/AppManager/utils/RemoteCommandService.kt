// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils

import android.os.Bundle
import android.os.RemoteException
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class RemoteCommandService : IRemoteCommandService.Stub() {
    @Throws(RemoteException::class)
    override fun runCommand(command: String): Bundle {
        Log.d(TAG, "Executing command: $command")
        val result = Bundle()

        try {
            // Execute command using sh shell
            val process = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(false) // Keep stdout and stderr separate
                .start()

            // Read stdout
            val stdout = StringBuilder()
            BufferedReader(InputStreamReader(process.inputStream)).use { stdoutReader ->
                var line: String?
                while (stdoutReader.readLine().also { line = it } != null) {
                    if (stdout.isNotEmpty()) stdout.append("\n")
                    stdout.append(line)
                }
            }

            // Read stderr
            val stderr = StringBuilder()
            BufferedReader(InputStreamReader(process.errorStream)).use { stderrReader ->
                var line: String?
                while (stderrReader.readLine().also { line = it } != null) {
                    if (stderr.isNotEmpty()) stderr.append("\n")
                    stderr.append(line)
                }
            }

            // Wait for process to complete with timeout
            val completed = process.waitFor(30, TimeUnit.SECONDS)
            val exitCode = if (completed) process.exitValue() else -1

            if (!completed) {
                process.destroy()
                result.putInt("exitCode", -1)
                result.putString("stdout", stdout.toString())
                result.putString("stderr", stderr.toString() + "\nCommand timed out after 30 seconds")
                Log.w(TAG, "Command timed out: $command")
            } else {
                result.putInt("exitCode", exitCode)
                result.putString("stdout", stdout.toString())
                result.putString("stderr", stderr.toString())
                Log.d(TAG, "Command completed with exit code $exitCode")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error executing command: $command", e)
            result.putInt("exitCode", -1)
            result.putString("stdout", "")
            result.putString("stderr", "Error (" + e.javaClass.simpleName + "): " + e.message)
        }

        return result
    }

    @Throws(RemoteException::class)
    override fun executeJavaCode(className: String, methodName: String, args: Bundle): Bundle {
        Log.d(TAG, "Executing Java code: $className, method: $methodName, args: $args")
        // For now, return an empty bundle. Implement actual code execution here.
        return Bundle()
    }

    companion object {
        private const val TAG = "RemoteCommandService"
    }
}
