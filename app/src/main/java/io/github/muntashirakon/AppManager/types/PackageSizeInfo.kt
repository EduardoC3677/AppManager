// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.types

import android.annotation.UserIdInt
import android.app.usage.StorageStats
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.WorkerThread
import io.github.muntashirakon.AppManager.misc.OsEnvironment
import io.github.muntashirakon.io.Paths

data class PackageSizeInfo(
    val packageName: String,
    val codeSize: Long,
    val dataSize: Long,
    val cacheSize: Long,
    val mediaSize: Long,
    val obbSize: Long
) {
    @Suppress("DEPRECATION")
    constructor(packageStats: android.content.pm.PackageStats) : this(
        packageName = packageStats.packageName,
        codeSize = packageStats.codeSize + packageStats.externalCodeSize,
        dataSize = packageStats.dataSize + packageStats.externalDataSize,
        cacheSize = packageStats.cacheSize + packageStats.externalCacheSize,
        obbSize = packageStats.externalObbSize,
        mediaSize = packageStats.externalMediaSize
    )

    @RequiresApi(Build.VERSION_CODES.O)
    @WorkerThread
    constructor(
        packageName: String,
        storageStats: StorageStats,
        @UserIdInt userHandle: Int
    ) : this(
        packageName = packageName,
        cacheSize = storageStats.cacheBytes,
        codeSize = storageStats.appBytes,
        dataSize = storageStats.dataBytes - storageStats.cacheBytes,
        mediaSize = getMediaSizeInternal(packageName, userHandle),
        obbSize = getObbSizeInternal(packageName, userHandle)
    )

    fun getTotalSize(): Long {
        return codeSize + dataSize + cacheSize + mediaSize + obbSize
    }

    companion object {
        @RequiresApi(Build.VERSION_CODES.O)
        private fun getMediaSizeInternal(packageName: String, @UserIdInt userHandle: Int): Long {
            val ue = OsEnvironment.getUserEnvironment(userHandle)
            val files = ue.buildExternalStorageAppMediaDirs(packageName)
            return files.filter { it.exists() }.sumOf { Paths.size(it) }
        }

        @RequiresApi(Build.VERSION_CODES.O)
        private fun getObbSizeInternal(packageName: String, @UserIdInt userHandle: Int): Long {
            val ue = OsEnvironment.getUserEnvironment(userHandle)
            val files = ue.buildExternalStorageAppObbDirs(packageName)
            return files.filter { it.exists() }.sumOf { Paths.size(it) }
        }
    }
}
