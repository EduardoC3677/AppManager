// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils

import android.annotation.UserIdInt
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.RemoteException
import android.os.UserHandleHidden
import androidx.annotation.IntDef
import androidx.annotation.WorkerThread
import io.github.muntashirakon.AppManager.BuildConfig
import io.github.muntashirakon.AppManager.compat.ApplicationInfoCompat
import io.github.muntashirakon.AppManager.compat.ManifestCompat
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat
import io.github.muntashirakon.AppManager.db.AppsDb
import io.github.muntashirakon.AppManager.db.entity.FreezeType
import io.github.muntashirakon.AppManager.self.SelfPermissions
import io.github.muntashirakon.AppManager.settings.Prefs
import kotlinx.coroutines.runBlocking

object FreezeUtils {
    @IntDef(value = [1, 2, 4, 8])
    @Retention(AnnotationRetention.SOURCE)
    annotation class FreezeMethod

    @JvmField
    val FREEZE_DISABLE = 1

    @JvmField
    val FREEZE_SUSPEND = 1 shl 1

    @JvmField
    val FREEZE_HIDE = 1 shl 2

    @JvmField
    val FREEZE_ADV_SUSPEND = 1 shl 3

    @JvmStatic
    @WorkerThread
    fun storeFreezeMethod(packageName: String, @FreezeMethod freezeType: Int) {
        runBlocking {
            AppsDb.getInstance().freezeTypeDao().insert(FreezeType(packageName, freezeType))
        }
    }

    @JvmStatic
    @WorkerThread
    fun deleteFreezeMethod(packageName: String) {
        runBlocking {
            AppsDb.getInstance().freezeTypeDao().delete(packageName)
        }
    }

    @JvmStatic
    @WorkerThread
    @FreezeMethod
    fun loadFreezeMethod(packageName: String?): Int? {
        if (packageName != null) {
            val freezeType: FreezeType? = runBlocking {
                AppsDb.getInstance().freezeTypeDao().get(packageName)
            }
            if (freezeType != null) {
                return freezeType.type
            }
        }
        // No package-specific freezing method exists
        return null
    }

    @JvmStatic
    fun isFrozen(applicationInfo: ApplicationInfo): Boolean {
        // An app is frozen if one of the following operations return true: suspend, disable or hide
        if (!applicationInfo.enabled) {
            return true
        }
        if (ApplicationInfoCompat.isSuspended(applicationInfo)) {
            return true
        }
        return ApplicationInfoCompat.isHidden(applicationInfo)
    }

    @JvmStatic
    @Deprecated("Use freeze(String, int, int) instead")
    @Throws(RemoteException::class)
    fun freeze(packageName: String, @UserIdInt userId: Int) {
        freeze(packageName, userId, Prefs.Blocking.getDefaultFreezingMethod())
    }

    @JvmStatic
    @Throws(RemoteException::class)
    fun freeze(packageName: String, @UserIdInt userId: Int, @FreezeMethod freezeType: Int) {
        if (BuildConfig.APPLICATION_ID == packageName && userId == UserHandleHidden.myUserId()) {
            throw RemoteException("Could not freeze myself.")
        }
        if (freezeType == FREEZE_HIDE) {
            if (SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.MANAGE_USERS)) {
                PackageManagerCompat.hidePackage(packageName, userId, true)
                return
            }
            // No permission, fall-through
        } else if ((freezeType == FREEZE_SUSPEND || freezeType == FREEZE_ADV_SUSPEND) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (freezeType == FREEZE_ADV_SUSPEND) {
                // Force-stop app
                if (SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.FORCE_STOP_PACKAGES)) {
                    PackageManagerCompat.forceStopPackage(packageName, userId)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                if (SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.SUSPEND_APPS)) {
                    PackageManagerCompat.suspendPackages(arrayOf(packageName), userId, true)
                    return
                }
                // No permission, fall-through
            } else {
                if (SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.MANAGE_USERS)) {
                    PackageManagerCompat.suspendPackages(arrayOf(packageName), userId, true)
                    return
                }
                // No permission, fall-through
            }
        }
        PackageManagerCompat.setApplicationEnabledSetting(packageName, PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER, 0, userId)
    }

    @JvmStatic
    @Throws(RemoteException::class)
    fun unfreeze(packageName: String, @UserIdInt userId: Int) {
        // Ignore checking preference, unfreeze for all types
        if (PackageManagerCompat.isPackageHidden(packageName, userId)) {
            PackageManagerCompat.hidePackage(packageName, userId, false)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && PackageManagerCompat.isPackageSuspended(packageName, userId)) {
            PackageManagerCompat.suspendPackages(arrayOf(packageName), userId, false)
        }
        if (PackageManagerCompat.getApplicationEnabledSetting(packageName, userId) != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
            PackageManagerCompat.setApplicationEnabledSetting(packageName, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, 0, userId)
        }
    }
}
