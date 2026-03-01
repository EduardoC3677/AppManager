// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.runner

import androidx.annotation.WorkerThread
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.utils.ContextUtils
import io.github.muntashirakon.AppManager.utils.ShizukuUtils

/**
 * Runner implementation using Shizuku for privileged operations.
 * Shizuku allows executing commands with elevated privileges without requiring
 * root access directly in the app. Instead, it leverages ADB or root granted
 * to the Shizuku app itself.
 */
internal class ShizukuShell : Runner() {
    companion object {
        private val TAG = ShizukuShell::class.java.simpleName
    }

    override fun isRoot(): Boolean {
        // Shizuku runs with elevated privileges, similar to root
        return true
    }

    @WorkerThread
    @Synchronized
    override fun runCommand(): Result {
        return try {
            // Shizuku requires a single command string
            // Combine all commands with && separator
            val combinedCommand = if (commands.size == 1) {
                commands[0]
            } else {
                java.lang.String.join(" && ", commands)
            }

            // Execute via Shizuku
            val result = ShizukuUtils.runCommand(ContextUtils.getContext(), combinedCommand)

            if (result == null) {
                // Shizuku unavailable or failed
                Log.e(TAG, "Shizuku command failed: $combinedCommand")
                Result(1) // Return error exit code
            } else {
                // Parse stdout and stderr into lists
                val stdoutList = ArrayList<String>()
                if (result.stdout.isNotEmpty()) {
                    val lines = result.stdout.split("
".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    for (line in lines) {
                        stdoutList.add(line)
                    }
                }

                val stderrList = ArrayList<String>()
                if (result.stderr.isNotEmpty()) {
                    val lines = result.stderr.split("
".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    for (line in lines) {
                        stderrList.add(line)
                    }
                }

                Result(stdoutList, stderrList, result.exitCode)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Shizuku shell execution failed", e)
            Result()
        }
    }
}
