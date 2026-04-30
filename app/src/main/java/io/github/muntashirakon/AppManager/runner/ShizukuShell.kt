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
            // Commands are joined with ';' to avoid short-circuit failures on non-critical commands.
            // Using '&&' would abort the entire sequence if any intermediate command returns non-zero,
            // which is overly strict for diagnostic or cleanup commands.
            val combinedCommand = if (commands.size == 1) {
                commands[0]
            } else {
                commands.joinToString(" ; ")
            }

            // Check Shizuku availability before attempting execution.
            // If Shizuku went away mid-session, log a warning and return graceful error
            // so the caller can decide to retry with a different mode (e.g. ADB/rish).
            if (!ShizukuUtils.isShizukuAvailable()) {
                Log.w(TAG, "Shizuku not available mid-session; returning graceful error so caller can fall back")
                return Result(1)
            }

            // Execute via Shizuku; retry once after 500ms if result is null
            var result = ShizukuUtils.runCommand(ContextUtils.getContext(), combinedCommand)
            if (result == null && ShizukuUtils.isShizukuAvailable()) {
                Log.w(TAG, "Shizuku returned null result; retrying after 500ms delay")
                Thread.sleep(500)
                result = ShizukuUtils.runCommand(ContextUtils.getContext(), combinedCommand)
            }

            if (result == null) {
                // Shizuku still unavailable after retry — return graceful error
                Log.w(TAG, "Shizuku unavailable after retry; returning graceful error for: $combinedCommand")
                return Result(1)
            }

            // Parse stdout and stderr into lists
            val stdoutList = result.stdout
                .split("\n")
                .dropLastWhile { it.isEmpty() }
            val stderrList = result.stderr
                .split("\n")
                .dropLastWhile { it.isEmpty() }

            Result(stdoutList, stderrList, result.exitCode)
        } catch (e: Exception) {
            Log.e(TAG, "Shizuku shell execution failed", e)
            Result()
        }
    }
}
