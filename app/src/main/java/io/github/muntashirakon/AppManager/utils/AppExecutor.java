// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A singleton class for managing a shared thread pool for the application.
 * This is designed to replace the problematic MultithreadedExecutor.
 */
public class AppExecutor {

    private static final ExecutorService SHARED_EXECUTOR =
            Executors.newFixedThreadPool(getThreadCount());

    private AppExecutor() {
        // Private constructor to prevent instantiation
    }

    public static ExecutorService getExecutor() {
        return SHARED_EXECUTOR;
    }

    public static int getThreadCount() {
        int configuredCount = AppPref.getInt(AppPref.PrefKey.PREF_CONCURRENCY_THREAD_COUNT_INT);
        int totalCores = Utils.getTotalCores();
        if (configuredCount <= 0 || configuredCount > totalCores) return totalCores;
        return configuredCount;
    }

    /**
     *
     * @param threadCount 1 - total cores. 0 = Total cores.
     */
    public static void setThreadCount(int threadCount) {
        AppPref.set(AppPref.PrefKey.PREF_CONCURRENCY_THREAD_COUNT_INT, threadCount);
    }
}
