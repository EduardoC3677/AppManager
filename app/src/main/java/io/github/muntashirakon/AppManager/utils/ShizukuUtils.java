// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import rikka.shizuku.Shizuku;
import io.github.muntashirakon.AppManager.logs.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ShizukuUtils {

    public static boolean isShizukuInstalled() {
        try {
            Shizuku.pingBinder();
            return !Shizuku.isPreV11();
        } catch (Exception e) {
            io.github.muntashirakon.AppManager.logs.Log.w("ShizukuUtils", "isShizukuInstalled check failed: " + e.getMessage());
            return false;
        }
    }

    public static boolean isShizukuAvailable() {
        try {
            if (Shizuku.isPreV11()) {
                return false;
            }
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Exception e) {
            io.github.muntashirakon.AppManager.logs.Log.w("ShizukuUtils", "isShizukuAvailable check failed: " + e.getMessage());
            return false;
        }
    }

    public static boolean needsPermission() {
        return isShizukuInstalled() && !isShizukuAvailable();
    }

    public static class CommandResult {
        public final int exitCode;
        public final String stdout;
        public final String stderr;

        public CommandResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }

        public int getExitCode() {
            return exitCode;
        }

        public String getStdout() {
            return stdout;
        }

        public String getStderr() {
            return stderr;
        }
    }

    @Nullable
    public static CommandResult runCommand(@NonNull Context context, @NonNull String command) {
        if (!isShizukuAvailable()) {
            io.github.muntashirakon.AppManager.logs.Log.w("ShizukuUtils", "Shizuku not available for command: " + command);
            return null;
        }

        final CommandResult[] result = new CommandResult[1];
        final CountDownLatch latch = new CountDownLatch(1);

        Shizuku.UserServiceArgs args = new Shizuku.UserServiceArgs(
                new ComponentName(context.getPackageName(), RemoteCommandService.class.getName()))
                .daemon(false)
                .processNameSuffix("command")
                .tag("RemoteCommandService");

        ServiceConnection connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                IRemoteCommandService remoteCommandService = IRemoteCommandService.Stub.asInterface(service);
                try {
                    android.os.Bundle bundle = remoteCommandService.runCommand(command);
                    int exitCode = bundle.getInt("exitCode", -1);
                    String stdout = bundle.getString("stdout", "");
                    String stderr = bundle.getString("stderr", "");
                    result[0] = new CommandResult(exitCode, stdout, stderr);
                } catch (RemoteException e) {
                    io.github.muntashirakon.AppManager.logs.Log.e("ShizukuUtils", "RemoteException during command execution: " + command, e);
                    result[0] = new CommandResult(-1, "", "RemoteException: " + e.getMessage());
                } finally {
                    Shizuku.unbindUserService(args, this, true);
                    latch.countDown();
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                latch.countDown();
            }
        };

        Shizuku.bindUserService(args, connection);

        try {
            latch.await(35, TimeUnit.SECONDS); // Increased timeout to 35s (30s command + 5s overhead)
        } catch (InterruptedException e) {
            io.github.muntashirakon.AppManager.logs.Log.e("ShizukuUtils", "InterruptedException while waiting for command: " + command, e);
        }

        return result[0];
    }

    public static void requestPermission(@NonNull Runnable onGranted, @NonNull Runnable onDenied) {
        if (isShizukuAvailable()) {
            onGranted.run();
            return;
        }

        try {
            if (Shizuku.isPreV11()) {
                onDenied.run();
                return;
            }

            final int requestCode = 100;
            Shizuku.addRequestPermissionResultListener((reqCode, grantResult) -> {
                if (reqCode == requestCode) {
                    if (grantResult == PackageManager.PERMISSION_GRANTED) {
                        onGranted.run();
                    } else {
                        onDenied.run();
                    }
                }
            });

            Shizuku.requestPermission(requestCode);
        } catch (Exception e) {
            io.github.muntashirakon.AppManager.logs.Log.e("ShizukuUtils", "Exception during requestPermission", e);
            onDenied.run();
        }
    }
}
