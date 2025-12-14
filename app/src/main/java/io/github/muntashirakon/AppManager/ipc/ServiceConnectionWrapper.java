// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ipc;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.misc.NoOps;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;

class ServiceConnectionWrapper {
    public static final String TAG = ServiceConnectionWrapper.class.getSimpleName();

    @Nullable
    private IBinder mIBinder;

    private class ServiceConnectionImpl implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "service onServiceConnected: %s", name);
            mIBinder = service;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "service onServiceDisconnected: %s", name);
            mIBinder = null;
        }

        @Override
        public void onBindingDied(ComponentName name) {
            Log.d(TAG, "service onBindingDied: %s", name);
            mIBinder = null;
        }

        @Override
        public void onNullBinding(ComponentName name) {
            Log.d(TAG, "service onNullBinding: %s", name);
            mIBinder = null;
        }
    }

    @NonNull
    private final ComponentName mComponentName;
    @NonNull
    private final ServiceConnectionImpl mServiceConnection;

    public ServiceConnectionWrapper(@NonNull String pkgName, @NonNull String className) {
        this(new ComponentName(pkgName, className));
    }

    public ServiceConnectionWrapper(@NonNull ComponentName cn) {
        mComponentName = cn;
        mServiceConnection = new ServiceConnectionImpl();
    }

    @NonNull
    public IBinder getService() throws RemoteException {
        if (!isBinderActive()) {
            throw new RemoteException("Binder not running.");
        }
        return Objects.requireNonNull(mIBinder);
    }

    @NonNull
    @NoOps(used = true)
    public IBinder bindService() throws RemoteException {
        synchronized (mServiceConnection) {
            if (!isBinderActive()) {
                startDaemon();
            }
            // Return immediately without waiting for the service to bind.
            // Callers must check isBinderActive() or handle RemoteException.
            if (!isBinderActive()) {
                throw new RemoteException("Service binding initiated, but not yet active.");
            }
            return Objects.requireNonNull(mIBinder);
        }
    }

    @MainThread
    public void unbindService() {
        synchronized (mServiceConnection) {
            RootService.unbind(mServiceConnection);
        }
    }

    @WorkerThread
    private void startDaemon() {
        synchronized (mServiceConnection) {
            if (isBinderActive()) {
                Log.d(TAG, "Binder is already active?");
                return;
            }
            Log.d(TAG, "Launching service...");
            Intent intent = new Intent();
            intent.setComponent(mComponentName);
            // This is still called on the main thread, so we post it
            ThreadUtils.postOnMainThread(() -> {
                if (mIBinder != null) {
                    RootService.stop(intent);
                }
                RootService.bind(intent, mServiceConnection);
            });
            // Removed await() - this method is now truly asynchronous
        }
    }

    @WorkerThread
    public void stopDaemon() {
        Intent intent = new Intent();
        intent.setComponent(mComponentName);
        ThreadUtils.postOnMainThread(() -> RootService.stop(intent));
        mIBinder = null;
    }

    boolean isBinderActive() {
        return mIBinder != null && mIBinder.pingBinder();
    }
}
