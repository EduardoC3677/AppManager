// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ipc;

import android.os.Process;
import android.os.RemoteException;

import androidx.annotation.AnyThread;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.IAMService;
import io.github.muntashirakon.AppManager.misc.NoOps;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.io.FileSystemManager;
import io.github.muntashirakon.AppManager.logs.Log;

public class LocalServices {
    private static final Object sBindLock = new Object();

    @NonNull
    private static final ServiceConnectionWrapper sFileSystemServiceConnectionWrapper
            = new ServiceConnectionWrapper(BuildConfig.APPLICATION_ID, FileSystemService.class.getName());

    @WorkerThread
    public static void bindServicesIfNotAlready() {
        if (!alive()) {
            bindServices();
        }
    }

    @WorkerThread
    public static void bindServices() {
        synchronized (sBindLock) {
            unbindServicesIfRunning();
            try {
                bindAmService();
            } catch (RemoteException e) {
                io.github.muntashirakon.AppManager.logs.Log.e("LocalServices", "Failed to bind AmService", e);
            }
            try {
                bindFileSystemManager();
            } catch (RemoteException e) {
                io.github.muntashirakon.AppManager.logs.Log.e("LocalServices", "Failed to bind FileSystemService", e);
            }

            // Verification and UID update should now happen asynchronously once services are connected
            // Or consumers of getAmService/getFileSystemManager should handle the non-availability
        }
    }

    public static boolean alive() {
        synchronized (sAMServiceConnectionWrapper) {
            return sAMServiceConnectionWrapper.isBinderActive();
        }
    }

    @WorkerThread
    @NoOps(used = true)
    private static void bindFileSystemManager() throws RemoteException {
        synchronized (sFileSystemServiceConnectionWrapper) {
            sFileSystemServiceConnectionWrapper.bindService();
        }
    }

    @AnyThread
    @Nullable // Changed to Nullable
    @NoOps
    public static FileSystemManager getFileSystemManager() { // Removed throws RemoteException
        synchronized (sFileSystemServiceConnectionWrapper) {
            try {
                return FileSystemManager.getRemote(sFileSystemServiceConnectionWrapper.getService());
            } catch (RemoteException e) {
                return null; // Return null if not active
            }
        }
    }

    @NonNull
    private static final ServiceConnectionWrapper sAMServiceConnectionWrapper
            = new ServiceConnectionWrapper(BuildConfig.APPLICATION_ID, AMService.class.getName());

    @WorkerThread
    @NoOps(used = true)
    private static void bindAmService() throws RemoteException {
        synchronized (sAMServiceConnectionWrapper) {
            sAMServiceConnectionWrapper.bindService();
        }
    }

    @AnyThread
    @Nullable // Changed to Nullable
    @NoOps
    public static IAMService getAmService() { // Removed throws RemoteException
        synchronized (sAMServiceConnectionWrapper) {
            try {
                return IAMService.Stub.asInterface(sAMServiceConnectionWrapper.getService());
            } catch (RemoteException e) {
                return null; // Return null if not active
            }
        }
    }

    @WorkerThread
    @NoOps
    public static void stopServices() {
        synchronized (sAMServiceConnectionWrapper) {
            sAMServiceConnectionWrapper.stopDaemon();
        }
        synchronized (sFileSystemServiceConnectionWrapper) {
            sFileSystemServiceConnectionWrapper.stopDaemon();
        }
        Ops.setWorkingUid(Process.myUid());
    }

    @MainThread
    public static void unbindServices() {
        synchronized (sAMServiceConnectionWrapper) {
            sAMServiceConnectionWrapper.unbindService();
        }
        synchronized (sFileSystemServiceConnectionWrapper) {
            sFileSystemServiceConnectionWrapper.unbindService();
        }
        Ops.setWorkingUid(Process.myUid());
    }

    @WorkerThread
    private static void unbindServicesIfRunning() {
        // Unbinding should also be non-blocking.
        // The original CountDownLatch.await() causes blocking.
        // We'll just call unbindServices and not wait.
        ThreadUtils.postOnMainThread(LocalServices::unbindServices);
    }
}
