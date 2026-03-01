// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.self

import android.Manifest
import android.annotation.UserIdInt
import android.app.AppOpsManager
import android.app.AppOpsManagerHidden
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.RemoteException
import android.os.UserHandleHidden
import androidx.core.content.ContextCompat
import io.github.muntashirakon.AppManager.BuildConfig
import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat
import io.github.muntashirakon.AppManager.compat.ManifestCompat
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat
import io.github.muntashirakon.AppManager.compat.PermissionCompat
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker
import io.github.muntashirakon.AppManager.settings.FeatureController
import io.github.muntashirakon.AppManager.settings.Ops
import io.github.muntashirakon.AppManager.users.Users
import io.github.muntashirakon.AppManager.utils.ContextUtils
import io.github.muntashirakon.AppManager.utils.Utils
import io.github.muntashirakon.io.Paths

object SelfPermissions {
    const val SHELL_PACKAGE_NAME = "com.android.shell"

    @JvmStatic
    fun init() {
        if (!canModifyPermissions()) {
            return
        }
        val permissions = arrayOf(
            Manifest.permission.DUMP,
            ManifestCompat.permission.GET_APP_OPS_STATS,
            ManifestCompat.permission.INTERACT_ACROSS_USERS,
            Manifest.permission.READ_LOGS,
            Manifest.permission.WRITE_SECURE_SETTINGS
        )
        val userId = UserHandleHidden.myUserId()
        for (permission in permissions) {
            if (!checkSelfPermission(permission)) {
                try {
                    PermissionCompat.grantPermission(BuildConfig.APPLICATION_ID, permission, userId)
                } catch (ignore: Exception) {
                }
            }
        }
        // Grant usage stats permission (both permission and app op needs to be granted)
        if (FeatureController.isUsageAccessEnabled()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !checkSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS)) {
                try {
                    PermissionCompat.grantPermission(BuildConfig.APPLICATION_ID, Manifest.permission.PACKAGE_USAGE_STATS, userId)
                } catch (ignore: Exception) {
                }
            }
            try {
                val appOps = AppOpsManagerCompat()
                appOps.setMode(AppOpsManagerHidden.OP_GET_USAGE_STATS, Process.myUid(), BuildConfig.APPLICATION_ID, AppOpsManager.MODE_ALLOWED)
            } catch (ignore: RemoteException) {
            }
        }
    }

    @JvmStatic
    fun canBlockByIFW(): Boolean {
        return Paths.get(ComponentsBlocker.SYSTEM_RULES_PATH).canWrite()
    }

    @JvmStatic
    fun canWriteToDataData(): Boolean {
        return Paths.get("/data/data").canWrite()
    }

    @JvmStatic
    fun canModifyAppComponentStates(@UserIdInt userId: Int, packageName: String?, testOnlyApp: Boolean): Boolean {
        if (!checkCrossUserPermission(userId, false)) {
            return false
        }
        val callingUid = Users.getSelfOrRemoteUid()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Since Oreo, shell can only disable components of test only apps
            if (callingUid == Ops.SHELL_UID && !testOnlyApp) {
                return false
            }
        }
        if (BuildConfig.APPLICATION_ID == packageName) {
            // We can change components for this package
            return true
        }
        return checkSelfOrRemotePermission(Manifest.permission.CHANGE_COMPONENT_ENABLED_STATE, callingUid)
    }

    @JvmStatic
    fun canModifyAppOpMode(): Boolean {
        val callingUid = Users.getSelfOrRemoteUid()
        var canModify = checkSelfOrRemotePermission(ManifestCompat.permission.UPDATE_APP_OPS_STATS, callingUid)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            canModify = canModify and checkSelfOrRemotePermission(ManifestCompat.permission.MANAGE_APP_OPS_MODES, callingUid)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                canModify = canModify and checkSelfOrRemotePermission(ManifestCompat.permission.MANAGE_APPOPS, callingUid)
            }
        }
        return canModify
    }

    @JvmStatic
    fun canModifyPermissions(): Boolean {
        val callingUid = Users.getSelfOrRemoteUid()
        return checkSelfOrRemotePermission(ManifestCompat.permission.GRANT_RUNTIME_PERMISSIONS, callingUid) ||
                checkSelfOrRemotePermission(ManifestCompat.permission.REVOKE_RUNTIME_PERMISSIONS, callingUid)
    }

    @JvmStatic
    fun checkGetGrantRevokeRuntimePermissions(): Boolean {
        val callingUid = Users.getSelfOrRemoteUid()
        return checkSelfOrRemotePermission(ManifestCompat.permission.GET_RUNTIME_PERMISSIONS, callingUid) ||
                checkSelfOrRemotePermission(ManifestCompat.permission.GRANT_RUNTIME_PERMISSIONS, callingUid) ||
                checkSelfOrRemotePermission(ManifestCompat.permission.REVOKE_RUNTIME_PERMISSIONS, callingUid)
    }

    @JvmStatic
    fun canInstallExistingPackages(): Boolean {
        val callingUid = Users.getSelfOrRemoteUid()
        if (checkSelfOrRemotePermission(Manifest.permission.INSTALL_PACKAGES, callingUid)) {
            return true
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return checkSelfOrRemotePermission(ManifestCompat.permission.INSTALL_EXISTING_PACKAGES, callingUid)
        }
        return false
    }

    @JvmStatic
    fun canFreezeUnfreezePackages(): Boolean {
        // 1. Suspend (7+): MANAGE_USERS (<= 9), SUSPEND_APPS (>= 9)
        // 2. Disable: CHANGE_COMPONENT_ENABLED_STATE
        // 2. HIDE: MANAGE_USERS
        val callingUid = Users.getSelfOrRemoteUid()
        var canFreezeUnfreeze = checkSelfOrRemotePermission(Manifest.permission.CHANGE_COMPONENT_ENABLED_STATE, callingUid) ||
                checkSelfOrRemotePermission(ManifestCompat.permission.MANAGE_USERS, callingUid)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            canFreezeUnfreeze = canFreezeUnfreeze or checkSelfOrRemotePermission(ManifestCompat.permission.SUSPEND_APPS, callingUid)
        }
        return canFreezeUnfreeze
    }

    @JvmStatic
    fun canClearAppCache(): Boolean {
        val callingUid = Users.getSelfOrRemoteUid()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            checkSelfOrRemotePermission(ManifestCompat.permission.INTERNAL_DELETE_CACHE_FILES, callingUid)
        } else {
            checkSelfOrRemotePermission(Manifest.permission.DELETE_CACHE_FILES, callingUid)
        }
    }

    @JvmStatic
    fun canKillUid(): Boolean {
        val callingUid = Users.getSelfOrRemoteUid()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkSelfOrRemotePermission(ManifestCompat.permission.KILL_UID, callingUid)
        } else {
            callingUid == Ops.SYSTEM_UID
        }
    }

    @JvmStatic
    fun checkNotificationListenerAccess(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
            return false
        }
        val callingUid = Users.getSelfOrRemoteUid()
        if (checkSelfOrRemotePermission(ManifestCompat.permission.MANAGE_NOTIFICATION_LISTENERS, callingUid)) {
            return true
        }
        return callingUid == Ops.ROOT_UID || callingUid == Ops.SYSTEM_UID || callingUid == Ops.PHONE_UID
    }

    @JvmStatic
    fun checkUsageStatsPermission(): Boolean {
        val appOps = AppOpsManagerCompat()
        val callingUid = Users.getSelfOrRemoteUid()
        if (callingUid == Ops.ROOT_UID || callingUid == Ops.SYSTEM_UID) {
            return true
        }
        val mode = appOps.checkOpNoThrow(AppOpsManagerHidden.OP_GET_USAGE_STATS, callingUid, getCallingPackage(callingUid))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && mode == AppOpsManager.MODE_DEFAULT) {
            return checkSelfOrRemotePermission(Manifest.permission.PACKAGE_USAGE_STATS, callingUid)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    @JvmStatic
    fun checkSelfStoragePermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Utils.isRoboUnitTest()) {
                return false
            }
            return Environment.isExternalStorageManager()
        }
        return checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    @JvmStatic
    fun checkStoragePermission(): Boolean {
        val callingUid = Users.getSelfOrRemoteUid()
        if (callingUid == Ops.ROOT_UID) {
            return true
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val packageName = getCallingPackage(callingUid)
            val appOps = AppOpsManagerCompat()
            val opMode = appOps.checkOpNoThrow(AppOpsManagerHidden.OP_MANAGE_EXTERNAL_STORAGE, callingUid, packageName)
            return when (opMode) {
                AppOpsManager.MODE_DEFAULT -> checkSelfOrRemotePermission(Manifest.permission.MANAGE_EXTERNAL_STORAGE, callingUid)
                AppOpsManager.MODE_ALLOWED -> true
                AppOpsManager.MODE_ERRORED, AppOpsManager.MODE_IGNORED -> false
                else -> throw IllegalStateException("Unknown AppOpsManager mode $opMode")
            }
        }
        return checkSelfOrRemotePermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, callingUid)
    }

    @JvmStatic
    fun checkCrossUserPermission(@UserIdInt userId: Int, requireFullPermission: Boolean): Boolean {
        val callingUid = Users.getSelfOrRemoteUid()
        return checkCrossUserPermission(userId, requireFullPermission, callingUid)
    }

    @JvmStatic
    fun checkCrossUserPermission(@UserIdInt userId: Int, requireFullPermission: Boolean, callingUid: Int): Boolean {
        var uId = userId
        if (uId == UserHandleHidden.USER_NULL) {
            uId = UserHandleHidden.myUserId()
        }
        if (uId < 0 && uId != UserHandleHidden.USER_ALL) {
            throw IllegalArgumentException("Invalid userId $uId")
        }
        if (isSystemOrRootOrShell(callingUid) || uId == UserHandleHidden.getUserId(callingUid)) {
            return true
        }
        if (requireFullPermission) {
            return checkSelfOrRemotePermission(ManifestCompat.permission.INTERACT_ACROSS_USERS_FULL, callingUid)
        }
        return checkSelfOrRemotePermission(ManifestCompat.permission.INTERACT_ACROSS_USERS_FULL, callingUid) ||
                checkSelfOrRemotePermission(ManifestCompat.permission.INTERACT_ACROSS_USERS, callingUid)
    }

    @JvmStatic
    fun isShell(): Boolean {
        return Users.getSelfOrRemoteUid() == Ops.SHELL_UID
    }

    @JvmStatic
    fun isSystem(): Boolean {
        return Users.getSelfOrRemoteUid() == Ops.SYSTEM_UID
    }

    @JvmStatic
    fun isSystemOrRoot(): Boolean {
        val callingUid = Users.getSelfOrRemoteUid()
        return callingUid == Ops.ROOT_UID || callingUid == Ops.SYSTEM_UID
    }

    @JvmStatic
    fun isSystemOrRootOrShell(): Boolean {
        return isSystemOrRootOrShell(Users.getSelfOrRemoteUid())
    }

    private fun isSystemOrRootOrShell(callingUid: Int): Boolean {
        return callingUid == Ops.ROOT_UID || callingUid == Ops.SYSTEM_UID || callingUid == Ops.SHELL_UID
    }

    @JvmStatic
    fun checkSelfOrRemotePermission(permissionName: String): Boolean {
        return checkSelfOrRemotePermission(permissionName, Users.getSelfOrRemoteUid())
    }

    @JvmStatic
    fun checkSelfOrRemotePermission(permissionName: String, uid: Int): Boolean {
        if (uid == Ops.ROOT_UID) {
            // Root UID has all the permissions granted
            return true
        }
        if (uid != Process.myUid()) {
            try {
                return PackageManagerCompat.getPackageManager().checkUidPermission(permissionName, uid) == PackageManager.PERMISSION_GRANTED
            } catch (ignore: RemoteException) {
            }
        }
        return checkSelfPermission(permissionName)
    }

    @JvmStatic
    fun checkSelfPermission(permissionName: String): Boolean {
        return ContextCompat.checkSelfPermission(ContextUtils.getContext(), permissionName) == PackageManager.PERMISSION_GRANTED
    }

    @JvmStatic
    @Throws(SecurityException::class)
    fun requireSelfPermission(permissionName: String) {
        if (!checkSelfPermission(permissionName)) {
            throw SecurityException("App Manager does not have the required permission $permissionName")
        }
    }

    @JvmStatic
    fun getCallingPackage(callingUid: Int): String {
        if (callingUid == Ops.ROOT_UID || callingUid == Ops.SHELL_UID) {
            return SHELL_PACKAGE_NAME
        }
        if (callingUid == Ops.SYSTEM_UID) {
            return "android"
        }
        return BuildConfig.APPLICATION_ID
    }
}
