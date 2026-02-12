// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat

import androidx.annotation.WorkerThread
import io.github.muntashirakon.AppManager.db.AppsDb
import io.github.muntashirakon.AppManager.db.entity.LogFilter
import kotlinx.coroutines.runBlocking

object LogFilterManager {
    @JvmStatic
    @WorkerThread
    fun getAllFilters(): List<LogFilter> {
        return runBlocking { AppsDb.getInstance().logFilterDao().getAll() }
    }

    @JvmStatic
    @WorkerThread
    fun deleteFilter(logFilter: LogFilter) {
        runBlocking { AppsDb.getInstance().logFilterDao().delete(logFilter) }
    }

    @JvmStatic
    @WorkerThread
    fun insertFilter(filterName: String): Long {
        return runBlocking { AppsDb.getInstance().logFilterDao().insert(filterName) }
    }

    @JvmStatic
    @WorkerThread
    fun getFilter(id: Long): LogFilter? {
        return runBlocking { AppsDb.getInstance().logFilterDao().get(id) }
    }
}
