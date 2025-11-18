// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.runner;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.ShizukuUtils;

/**
 * Runner implementation using Shizuku for privileged operations.
 * Shizuku allows executing commands with elevated privileges without requiring
 * root access directly in the app. Instead, it leverages ADB or root granted
 * to the Shizuku app itself.
 */
class ShizukuShell extends Runner {
    private static final String TAG = ShizukuShell.class.getSimpleName();

    @Override
    public boolean isRoot() {
        // Shizuku runs with elevated privileges, similar to root
        return true;
    }

    @WorkerThread
    @NonNull
    @Override
    protected synchronized Result runCommand() {
        try {
            // Shizuku requires a single command string
            // Combine all commands with && separator
            String combinedCommand;
            if (commands.size() == 1) {
                combinedCommand = commands.get(0);
            } else {
                combinedCommand = String.join(" && ", commands);
            }

            // Execute via Shizuku
            Integer exitCode = ShizukuUtils.runCommand(ContextUtils.getContext(), combinedCommand);

            if (exitCode == null) {
                // Shizuku unavailable or failed
                Log.e(TAG, "Shizuku command failed: " + combinedCommand);
                return new Result(1); // Return error exit code
            }

            // Note: Current ShizukuUtils.runCommand doesn't capture stdout/stderr
            // This is a limitation of the current implementation
            // TODO: Enhance ShizukuUtils to capture command output
            return new Result(Collections.emptyList(), Collections.emptyList(), exitCode);

        } catch (Exception e) {
            Log.e(TAG, "Shizuku shell execution failed", e);
            return new Result();
        }
    }
}
