// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import rikka.shizuku.Shizuku;
import rikka.shizuku.Shizuku.UserServiceArgs;
import io.github.muntashirakon.AppManager.utils.IRemoteCommandService;


public class ShizukuUtils {

    public static boolean isShizukuAvailable() {
        try {
            if (Shizuku.isPreV11()) {
                return false;
            }
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Exception e) {
            return false;
        }
    }

    @Nullable
    public static Integer runCommand(@NonNull Context context, @NonNull String command) {
        if (!isShizukuAvailable()) {
            return null;
        }

        final Integer[] result = {null};
        final CountDownLatch latch = new CountDownLatch(1);

        UserServiceArgs args = new UserServiceArgs(
                new ComponentName(context.getPackageName(), RemoteCommandService.class.getName())
        )
                .daemon(false)
                .processNameSuffix("command")
                .tag("RemoteCommandService");

        ServiceConnection connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                IRemoteCommandService remoteCommandService = IRemoteCommandService.Stub.asInterface(service);
                try {
                    result[0] = remoteCommandService.runCommand(command);
                } catch (RemoteException e) {
                    e.printStackTrace();
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
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return result[0];
    }
}