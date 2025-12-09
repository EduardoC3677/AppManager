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
            ShizukuUtils.CommandResult result = ShizukuUtils.runCommand(ContextUtils.getContext(), combinedCommand);

            if (result == null) {
                // Shizuku unavailable or failed
                Log.e(TAG, "Shizuku command failed: " + combinedCommand);
                return new Result(1); // Return error exit code
            }

            // Parse stdout and stderr into lists
            ArrayList<String> stdoutList = new ArrayList<>();
            if (!result.getStdout().isEmpty()) {
                String[] lines = result.getStdout().split("\n");
                for (String line : lines) {
                    stdoutList.add(line);
                }
            }

            ArrayList<String> stderrList = new ArrayList<>();
            if (!result.getStderr().isEmpty()) {
                String[] lines = result.getStderr().split("\n");
                for (String line : lines) {
                    stderrList.add(line);
                }
            }

            return new Result(stdoutList, stderrList, result.getExitCode());

        } catch (Exception e) {
            Log.e(TAG, "Shizuku shell execution failed", e);
            return new Result();
        }
    }
}
