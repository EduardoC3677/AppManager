// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * A singleton class for managing a shared thread pool for the application.
 * This is designed to replace the problematic MultithreadedExecutor.
 */
object AppExecutor {

    @JvmField
    val SHARED_EXECUTOR: ExecutorService = Executors.newFixedThreadPool(getThreadCount())

    @JvmStatic
    fun getExecutor(): ExecutorService {
        return SHARED_EXECUTOR
    }

    @JvmStatic
    fun getThreadCount(): Int {
        val configuredCount = AppPref.getInt(AppPref.PrefKey.PREF_CONCURRENCY_THREAD_COUNT_INT)
        val totalCores = Utils.getTotalCores()
        if (configuredCount <= 0 || configuredCount > totalCores) return totalCores
        return configuredCount
    }

    /**
     *
     * @param threadCount 1 - total cores. 0 = Total cores.
     */
    @JvmStatic
    fun setThreadCount(threadCount: Int) {
        AppPref.set(AppPref.PrefKey.PREF_CONCURRENCY_THREAD_COUNT_INT, threadCount)
    }
}
