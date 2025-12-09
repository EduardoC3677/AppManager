// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

public class RemoteCommandService extends IRemoteCommandService.Stub {
    private static final String TAG = "RemoteCommandService";

    @Override
    public Bundle runCommand(String command) throws RemoteException {
        Log.d(TAG, "Executing command: " + command);
        Bundle result = new Bundle();

        try {
            // Execute command using sh shell
            Process process = new ProcessBuilder("sh", "-c", command)
                    .redirectErrorStream(false) // Keep stdout and stderr separate
                    .start();

            // Read stdout
            StringBuilder stdout = new StringBuilder();
            BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = stdoutReader.readLine()) != null) {
                if (stdout.length() > 0) stdout.append("\n");
                stdout.append(line);
            }

            // Read stderr
            StringBuilder stderr = new StringBuilder();
            BufferedReader stderrReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            while ((line = stderrReader.readLine()) != null) {
                if (stderr.length() > 0) stderr.append("\n");
                stderr.append(line);
            }

            // Wait for process to complete with timeout
            boolean completed = process.waitFor(30, TimeUnit.SECONDS);
            int exitCode = completed ? process.exitValue() : -1;

            if (!completed) {
                process.destroy();
                result.putInt("exitCode", -1);
                result.putString("stdout", stdout.toString());
                result.putString("stderr", stderr.toString() + "\nCommand timed out after 30 seconds");
                Log.w(TAG, "Command timed out: " + command);
            } else {
                result.putInt("exitCode", exitCode);
                result.putString("stdout", stdout.toString());
                result.putString("stderr", stderr.toString());
                Log.d(TAG, "Command completed with exit code " + exitCode);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error executing command: " + command, e);
            result.putInt("exitCode", -1);
            result.putString("stdout", "");
            result.putString("stderr", "Error: " + e.getMessage());
        }

        return result;
    }

    @Override
    public Bundle executeJavaCode(String className, String methodName, Bundle args) throws RemoteException {
        Log.d(TAG, "Executing Java code: " + className + ", method: " + methodName + ", args: " + args);
        // For now, return an empty bundle. Implement actual code execution here.
        return new Bundle();
    }
}
