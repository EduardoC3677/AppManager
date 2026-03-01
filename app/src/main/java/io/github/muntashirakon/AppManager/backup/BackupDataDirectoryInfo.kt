// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup

import android.annotation.SuppressLint
import android.annotation.UserIdInt
import androidx.annotation.IntDef
import androidx.annotation.VisibleForTesting
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.io.Path
import io.github.muntashirakon.io.Paths
import java.util.*

class BackupDataDirectoryInfo private constructor(
    val rawPath: String,
    val isMounted: Boolean,
    @Type val type: Int,
    @SubType val subtype: Int
) {
    val path: Path = Paths.get(rawPath)

    fun getDirectory(): Path = path

    fun isExternal(): Boolean = type == TYPE_EXTERNAL

    @IntDef(TYPE_INTERNAL, TYPE_EXTERNAL, TYPE_UNKNOWN)
    @Retention(AnnotationRetention.SOURCE)
    annotation class Type

    @IntDef(
        TYPE_CUSTOM,
        TYPE_ANDROID_DATA,
        TYPE_ANDROID_MEDIA,
        TYPE_ANDROID_OBB,
        TYPE_CREDENTIAL_PROTECTED,
        TYPE_DEVICE_PROTECTED
    )
    @Retention(AnnotationRetention.SOURCE)
    annotation class SubType

    companion object {
        @JvmField
        val TAG: String = BackupDataDirectoryInfo::class.java.simpleName

        const val TYPE_CUSTOM = 0
        const val TYPE_ANDROID_DATA = 1
        const val TYPE_ANDROID_MEDIA = 2
        const val TYPE_ANDROID_OBB = 3
        const val TYPE_CREDENTIAL_PROTECTED = 4
        const val TYPE_DEVICE_PROTECTED = 5

        const val TYPE_INTERNAL = 1
        const val TYPE_EXTERNAL = 2
        const val TYPE_UNKNOWN = 3

        @SuppressLint("SdCardPath")
        @JvmStatic
        fun getInfo(dataDir: String, @UserIdInt userId: Int): BackupDataDirectoryInfo {
            val storageCe = String.format(Locale.ROOT, "/data/user/%d/", userId)
            if (dataDir.startsWith("/data/data/") || dataDir.startsWith(storageCe)) {
                return BackupDataDirectoryInfo(dataDir, true, TYPE_INTERNAL, TYPE_CREDENTIAL_PROTECTED)
            }
            val storageDe = String.format(Locale.ROOT, "/data/user_de/%d/", userId)
            if (dataDir.startsWith(storageDe)) {
                return BackupDataDirectoryInfo(dataDir, true, TYPE_INTERNAL, TYPE_DEVICE_PROTECTED)
            }
            if (dataDir.startsWith("/sdcard/")) {
                return getExternalInfo(dataDir, "/sdcard/")
            }
            if (dataDir.startsWith("/storage/sdcard/")) {
                return getExternalInfo(dataDir, "/storage/sdcard/")
            }
            if (dataDir.startsWith("/storage/sdcard0/")) {
                return getExternalInfo(dataDir, "/storage/sdcard0/")
            }
            val storageEmulatedDir = String.format(Locale.ROOT, "/storage/emulated/%d/", userId)
            if (dataDir.startsWith(storageEmulatedDir)) {
                return getExternalInfo(dataDir, storageEmulatedDir)
            }
            val dataMediaDir = String.format(Locale.ROOT, "/data/media/%d/", userId)
            if (dataDir.startsWith(dataMediaDir)) {
                return getExternalInfo(dataDir, dataMediaDir)
            }
            Log.i(TAG, "getInfo: Unrecognized path $dataDir, returning true as fallback.")
            return BackupDataDirectoryInfo(dataDir, true, TYPE_UNKNOWN, TYPE_CUSTOM)
        }

        private fun getExternalInfo(dataDir: String, baseDir: String): BackupDataDirectoryInfo {
            val relativeDir = dataDir.substring(baseDir.length) // No starting separator
            val subType = when {
                relativeDir.startsWith("Android/data/") -> TYPE_ANDROID_DATA
                relativeDir.startsWith("Android/obb/") -> TYPE_ANDROID_OBB
                relativeDir.startsWith("Android/media/") -> TYPE_ANDROID_MEDIA
                else -> TYPE_CUSTOM
            }
            return BackupDataDirectoryInfo(dataDir, Paths.get(baseDir).isDirectory(), TYPE_EXTERNAL, subType)
        }
    }
}
