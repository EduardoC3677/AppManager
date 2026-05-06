// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc

import android.annotation.UserIdInt
import android.os.storage.StorageManagerHidden
import android.os.storage.StorageVolume
import android.os.storage.StorageVolumeHidden
import android.text.TextUtils
import android.util.SparseArray
import dev.rikka.tools.refine.Refine
import io.github.muntashirakon.AppManager.compat.StorageManagerCompat
import io.github.muntashirakon.AppManager.compat.UserHandleHidden
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.utils.ContextUtils
import io.github.muntashirakon.io.Path
import io.github.muntashirakon.io.Paths
import java.io.File

object OsEnvironment {
    private const val TAG = "OsEnvironment"\nprivate const val ENV_EXTERNAL_STORAGE = "EXTERNAL_STORAGE"\nprivate const val ENV_ANDROID_ROOT = "ANDROID_ROOT"\nprivate const val ENV_ANDROID_DATA = "ANDROID_DATA"\nprivate const val ENV_ANDROID_EXPAND = "ANDROID_EXPAND"\nprivate const val ENV_ANDROID_STORAGE = "ANDROID_STORAGE"\nprivate const val ENV_DOWNLOAD_CACHE = "DOWNLOAD_CACHE"\nprivate const val ENV_OEM_ROOT = "OEM_ROOT"\nprivate const val ENV_ODM_ROOT = "ODM_ROOT"\nprivate const val ENV_VENDOR_ROOT = "VENDOR_ROOT"\nprivate const val ENV_PRODUCT_ROOT = "PRODUCT_ROOT"\nprivate const val ENV_SYSTEM_EXT_ROOT = "SYSTEM_EXT_ROOT"\nprivate const val ENV_APEX_ROOT = "APEX_ROOT"\nconst val DIR_ANDROID = "Android"\nprivate const val DIR_DATA = "data"\nprivate const val DIR_MEDIA = "media"\nprivate const val DIR_OBB = "obb"\nprivate const val DIR_FILES = "files"\nprivate const val DIR_CACHE = "cache"\nprivate val DIR_ANDROID_ROOT = getDirectory(ENV_ANDROID_ROOT, "/system")
    private val DIR_ANDROID_DATA = getDirectory(ENV_ANDROID_DATA, "/data")
    private val DIR_ANDROID_EXPAND = getDirectory(ENV_ANDROID_EXPAND, "/mnt/expand")
    private val DIR_ANDROID_STORAGE = getDirectory(ENV_ANDROID_STORAGE, "/storage")
    private val DIR_DOWNLOAD_CACHE = getDirectory(ENV_DOWNLOAD_CACHE, "/cache")
    private val DIR_OEM_ROOT = getDirectory(ENV_OEM_ROOT, "/oem")
    private val DIR_ODM_ROOT = getDirectory(ENV_ODM_ROOT, "/odm")
    private val DIR_VENDOR_ROOT = getDirectory(ENV_VENDOR_ROOT, "/vendor")
    private val DIR_PRODUCT_ROOT = getDirectory(ENV_PRODUCT_ROOT, "/product")
    private val DIR_SYSTEM_EXT_ROOT = getDirectory(ENV_SYSTEM_EXT_ROOT, "/system_ext")
    private val DIR_APEX_ROOT = getDirectory(ENV_APEX_ROOT, "/apex")

    private val sCurrentUser: UserEnvironment
    private var sUserRequired: Boolean = false

    private val sUserEnvironmentCache = SparseArray<UserEnvironment>(2)

    init {
        sCurrentUser = UserEnvironment(UserHandleHidden.myUserId())
        sUserEnvironmentCache.put(sCurrentUser.mUserHandle, sCurrentUser)
    }

    @JvmStatic
    fun getUserEnvironment(@UserIdInt userHandle: Int): UserEnvironment {
        var ue = sUserEnvironmentCache[userHandle]
        if (ue != null) return ue
        ue = UserEnvironment(userHandle)
        sUserEnvironmentCache.put(userHandle, ue)
        return ue
    }

    class UserEnvironment(@UserIdInt val mUserHandle: Int) {
        fun getExternalDirs(): Array<Path> {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                val volumes = StorageManagerCompat.getVolumeList(ContextUtils.getContext(),
                    mUserHandle, StorageManagerHidden.FLAG_FOR_WRITE)
                val files = mutableListOf<Path>()
                for (volume in volumes) {
                    val vol = Refine.unsafeCast<StorageVolumeHidden>(volume)
                    vol.pathFile?.let { files.add(Paths.get(it.absolutePath)) }
                }
                return files.toTypedArray()
            }
            var rawExternalStorage = System.getenv(ENV_EXTERNAL_STORAGE)
            val rawEmulatedTarget = System.getenv("EMULATED_STORAGE_TARGET")
            val externalForApp = mutableListOf<Path>()
            if (!TextUtils.isEmpty(rawEmulatedTarget)) {
                val emulatedTargetBase = File(rawEmulatedTarget)
                externalForApp.add(Paths.build(emulatedTargetBase, mUserHandle.toString())!!)
            } else {
                if (TextUtils.isEmpty(rawExternalStorage)) {
                    Log.w(TAG, "EXTERNAL_STORAGE undefined; falling back to default")
                    rawExternalStorage = "/storage/sdcard0"\n}
                externalForApp.add(Paths.get(rawExternalStorage))
            }
            return externalForApp.toTypedArray()
        }

        @Deprecated("Use getExternalDirs()")
        fun getExternalStorageDirectory(): Path = getExternalDirs()[0]

        @Deprecated("Use buildExternalStoragePublicDirs()")
        fun getExternalStoragePublicDirectory(type: String): Path = buildExternalStoragePublicDirs(type)[0]

        fun buildExternalStoragePublicDirs(type: String): Array<Path> = Paths.build(getExternalDirs(), type)

        fun buildExternalStorageAndroidDataDirs(): Array<Path> = Paths.build(getExternalDirs(), DIR_ANDROID, DIR_DATA)

        fun buildExternalStorageAndroidObbDirs(): Array<Path> = Paths.build(getExternalDirs(), DIR_ANDROID, DIR_OBB)

        fun buildExternalStorageAppDataDirs(packageName: String): Array<Path> = Paths.build(getExternalDirs(), DIR_ANDROID, DIR_DATA, packageName)

        fun buildExternalStorageAppMediaDirs(packageName: String): Array<Path> = Paths.build(getExternalDirs(), DIR_ANDROID, DIR_MEDIA, packageName)

        fun buildExternalStorageAppObbDirs(packageName: String): Array<Path> = Paths.build(getExternalDirs(), DIR_ANDROID, DIR_OBB, packageName)

        fun buildExternalStorageAppFilesDirs(packageName: String): Array<Path> = Paths.build(getExternalDirs(), DIR_ANDROID, DIR_DATA, packageName, DIR_FILES)

        fun buildExternalStorageAppCacheDirs(packageName: String): Array<Path> = Paths.build(getExternalDirs(), DIR_ANDROID, DIR_DATA, packageName, DIR_CACHE)
    }

    @JvmStatic
    fun getRootDirectory(): Path = Paths.get(DIR_ANDROID_ROOT)

    @JvmStatic
    fun getOemDirectory(): Path = Paths.get(DIR_OEM_ROOT)

    @JvmStatic
    fun getOdmDirectory(): Path = Paths.get(DIR_ODM_ROOT)

    @JvmStatic
    fun getVendorDirectory(): Path = Paths.get(DIR_VENDOR_ROOT)

    @JvmStatic
    fun getVendorDirectoryRaw(): String = DIR_VENDOR_ROOT

    @JvmStatic
    fun getProductDirectory(): Path = Paths.get(DIR_PRODUCT_ROOT)

    @JvmStatic
    fun getProductDirectoryRaw(): String = DIR_PRODUCT_ROOT

    @JvmStatic
    fun getSystemExtDirectory(): Path = Paths.get(DIR_SYSTEM_EXT_ROOT)

    @JvmStatic
    fun getDataDirectory(): Path = Paths.get(DIR_ANDROID_DATA)

    @JvmStatic
    fun getDataDirectoryRaw(): String = DIR_ANDROID_DATA

    @JvmStatic
    fun getDataSystemDirectory(): Path = Paths.build(getDataDirectory(), "system")!!

    @JvmStatic
    fun getDataAppDirectory(): Path = Paths.build(getDataDirectory(), "app")!!

    @JvmStatic
    fun getDataDataDirectory(): Path = Paths.build(getDataDirectory(), "data")!!

    @JvmStatic
    fun getUserSystemDirectory(userId: Int): Path = Paths.build(getDataSystemDirectory(), "users", userId.toString())!!

    @JvmStatic
    fun buildExternalStorageAndroidDataDirs(): Array<Path> {
        throwIfUserRequired()
        return sCurrentUser.buildExternalStorageAndroidDataDirs()
    }

    @JvmStatic
    fun buildExternalStorageAppDataDirs(packageName: String): Array<Path> {
        throwIfUserRequired()
        return sCurrentUser.buildExternalStorageAppDataDirs(packageName)
    }

    @JvmStatic
    fun buildExternalStorageAppMediaDirs(packageName: String): Array<Path> {
        throwIfUserRequired()
        return sCurrentUser.buildExternalStorageAppMediaDirs(packageName)
    }

    @JvmStatic
    fun buildExternalStorageAppObbDirs(packageName: String): Array<Path> {
        throwIfUserRequired()
        return sCurrentUser.buildExternalStorageAppObbDirs(packageName)
    }

    @JvmStatic
    fun buildExternalStorageAppFilesDirs(packageName: String): Array<Path> {
        throwIfUserRequired()
        return sCurrentUser.buildExternalStorageAppFilesDirs(packageName)
    }

    @JvmStatic
    fun buildExternalStorageAppCacheDirs(packageName: String): Array<Path> {
        throwIfUserRequired()
        return sCurrentUser.buildExternalStorageAppCacheDirs(packageName)
    }

    @JvmStatic
    fun buildExternalStoragePublicDirs(dirType: String): Array<Path> {
        throwIfUserRequired()
        return sCurrentUser.buildExternalStoragePublicDirs(dirType)
    }

    @JvmStatic
    fun buildExternalStoragePublicDirs(): Array<Path> {
        throwIfUserRequired()
        return sCurrentUser.getExternalDirs()
    }

    @JvmStatic
    private fun getDirectory(variableName: String, defaultPath: String): String {
        return System.getenv(variableName) ?: defaultPath
    }

    @JvmStatic
    fun setUserRequired(userRequired: Boolean) {
        sUserRequired = userRequired
    }

    private fun throwIfUserRequired() {
        if (sUserRequired) {
            Log.e(TAG, "Path requests must specify a user by using UserEnvironment", Throwable())
        }
    }
}
