// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils

import android.os.Handler
import android.os.Looper
import io.github.muntashirakon.AppManager.logs.Log
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

// Copyright 2016 The Android Open Source Project
object ThreadUtils {
    private val sMainThread: Thread by lazy { Looper.getMainLooper().thread }

    private val sMainThreadHandler: Handler by lazy { Handler(Looper.getMainLooper()) }

    private val sThreadExecutor: ExecutorService by lazy { AppExecutor.getExecutor() }

    /**
     * Returns true if the current thread is the UI thread.
     */
    @JvmStatic
    fun isMainThread(): Boolean {
        return Thread.currentThread() == sMainThread
    }

    /**
     * Returns a shared UI thread handler.
     */
    @JvmStatic
    fun getUiThreadHandler(): Handler {
        return sMainThreadHandler
    }

    /**
     * Checks that the current thread is the UI thread. Otherwise, throws an exception.
     */
    @JvmStatic
    fun ensureMainThread() {
        if (!isMainThread()) {
            throw RuntimeException("Must be called on the UI thread")
        }
    }

    /**
     * Checks that the current thread is a worker thread. Otherwise, throws an exception.
     */
    @JvmStatic
    fun ensureWorkerThread() {
        if (isMainThread()) {
            throw RuntimeException("Must be called on a worker thread")
        }
    }

    /**
     * Tests whether this thread has been interrupted. The <i>interrupted status</i> of the thread is unaffected by this
     * method.
     *
     * <p>A thread interruption ignored because a thread was not alive at the time of the interrupt will be reflected by
     * this method returning false.
     *
     * @return `true` if this thread has been interrupted; `false` otherwise.
     */
    @JvmStatic
    fun isInterrupted(): Boolean {
        val interrupted = Thread.currentThread().isInterrupted
        if (interrupted) {
            Log.d("ThreadUtils", "Thread interrupted.")
        }
        return interrupted
    }

    /**
     * Posts runnable in background using shared background thread pool.
     *
     * @return A future of the task that can be monitored for updates or cancelled.
     */
    @JvmStatic
    fun postOnBackgroundThread(runnable: Runnable): Future<*> {
        return getBackgroundThreadExecutor().submit(runnable)
    }

    /**
     * Posts callable in background using shared background thread pool.
     *
     * @return A future of the task that can be monitored for updates or cancelled.
     */
    @JvmStatic
    fun <T> postOnBackgroundThread(callable: Callable<T>): Future<T> {
        return getBackgroundThreadExecutor().submit(callable)
    }

    /**
     * Posts the runnable on the main thread.
     */
    @JvmStatic
    fun postOnMainThread(runnable: Runnable) {
        getUiThreadHandler().post(runnable)
    }

    /**
     * Posts the runnable on the main thread with a delay.
     */
    @JvmStatic
    fun postOnMainThreadDelayed(runnable: Runnable, delayMillis: Long) {
        getUiThreadHandler().postDelayed(runnable, delayMillis)
    }

    @JvmStatic
    fun getBackgroundThreadExecutor(): ExecutorService {
        return sThreadExecutor
    }
}
