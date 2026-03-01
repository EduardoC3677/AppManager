// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission

import android.Manifest
import android.app.AppOpsManager
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build
import android.os.RemoteException
import android.os.UserHandleHidden
import android.provider.Settings
import androidx.annotation.RequiresPermission
import androidx.annotation.WorkerThread
import io.github.muntashirakon.AppManager.compat.ActivityManagerCompat
import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat
import io.github.muntashirakon.AppManager.compat.ManifestCompat
import io.github.muntashirakon.AppManager.compat.PermissionCompat
import io.github.muntashirakon.AppManager.compat.PermissionCompat.FLAG_PERMISSION_AUTO_REVOKED
import io.github.muntashirakon.AppManager.compat.PermissionCompat.FLAG_PERMISSION_ONE_TIME
import io.github.muntashirakon.AppManager.compat.PermissionCompat.FLAG_PERMISSION_REVIEW_REQUIRED
import io.github.muntashirakon.AppManager.compat.PermissionCompat.FLAG_PERMISSION_REVOKED_COMPAT
import io.github.muntashirakon.AppManager.compat.PermissionCompat.FLAG_PERMISSION_USER_FIXED
import io.github.muntashirakon.AppManager.compat.PermissionCompat.FLAG_PERMISSION_USER_SET
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.self.SelfPermissions
import io.github.muntashirakon.AppManager.utils.BroadcastUtils
import io.github.muntashirakon.AppManager.utils.ContextUtils
import io.github.muntashirakon.AppManager.utils.IntentUtils

object PermUtils {
    private const val KILL_REASON_APP_OP_CHANGE = "Permission related app op changed"

    class SettingItem(val action: String, val supportPkg: Boolean = true) {
        fun toIntent(packageName: String?): Intent {
            return IntentUtils.getSettings(action, if (supportPkg) packageName else null)
        }
    }

    @JvmField
    val permissionNameToSettingItem: Map<String, SettingItem> = mutableMapOf<String, SettingItem>().apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            put(Manifest.permission.ACCESS_NOTIFICATION_POLICY, SettingItem(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS, false))
            put(Manifest.permission.PACKAGE_USAGE_STATS, SettingItem(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            put(Manifest.permission.SYSTEM_ALERT_WINDOW, SettingItem(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
            put(Manifest.permission.WRITE_SETTINGS, SettingItem(Settings.ACTION_MANAGE_WRITE_SETTINGS))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            put(Manifest.permission.REQUEST_INSTALL_PACKAGES, SettingItem(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            put(Manifest.permission.MANAGE_EXTERNAL_STORAGE, SettingItem(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            put(Manifest.permission.MANAGE_MEDIA, SettingItem(Settings.ACTION_REQUEST_MANAGE_MEDIA))
            put(Manifest.permission.SCHEDULE_EXACT_ALARM, SettingItem(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            put(Manifest.permission.POST_NOTIFICATIONS, SettingItem(Settings.ACTION_APP_NOTIFICATION_SETTINGS))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            put(Manifest.permission.RUN_USER_INITIATED_JOBS, SettingItem("android.settings.MANAGE_APP_LONG_RUNNING_JOBS"))
            put(Manifest.permission.USE_FULL_SCREEN_INTENT, SettingItem(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            put(Manifest.permission.MEDIA_ROUTING_CONTROL, SettingItem(Settings.ACTION_REQUEST_MEDIA_ROUTING_CONTROL))
        }
        put(Manifest.permission.BIND_ACCESSIBILITY_SERVICE, SettingItem(Settings.ACTION_ACCESSIBILITY_SETTINGS, false))
        put(Manifest.permission.BIND_INPUT_METHOD, SettingItem(Settings.ACTION_INPUT_METHOD_SETTINGS, false))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            put(Manifest.permission.BIND_AUTOFILL_SERVICE, SettingItem(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            put(Manifest.permission.BIND_CREDENTIAL_PROVIDER_SERVICE, SettingItem(Settings.ACTION_CREDENTIAL_PROVIDER))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            put(Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE, SettingItem(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS, false))
        }
    }

    /**
     * Grant the permission.
     *
     * <p>This also automatically grants app op if it has app op.
     *
     * @param setByTheUser   If the user has made the decision. This does not unset the flag
     * @param fixedByTheUser If the user requested that she/he does not want to be asked again
     */
    @JvmStatic
    @RequiresPermission(allOf = [
        "android.permission.MANAGE_APP_OPS_MODES",
        ManifestCompat.permission.GRANT_RUNTIME_PERMISSIONS,
        ManifestCompat.permission.REVOKE_RUNTIME_PERMISSIONS
    ])
    @WorkerThread
    @Throws(PermissionException::class)
    fun grantPermission(
        packageInfo: PackageInfo,
        permission: Permission,
        appOpsManager: AppOpsManagerCompat,
        setByTheUser: Boolean,
        fixedByTheUser: Boolean
    ) {
        var killApp = false

        if (!isModifiable(permission)) {
            throw PermissionException("Unmodifiable permission ${permission.getName()}")
        }

        if (!permission.isReadOnly && (!permission.isRuntime || supportsRuntimePermissions(packageInfo.applicationInfo))) {
            if (permission.affectsAppOp() && !permission.isAppOpAllowed()) {
                permission.setAppOpAllowed(true)
            }

            if (!permission.isGranted()) {
                permission.setGranted(true)
            }

            if (!fixedByTheUser) {
                if (permission.isUserFixed()) {
                    permission.setUserFixed(false)
                }
                if (setByTheUser) {
                    if (!permission.isUserSet()) {
                        permission.setUserSet(true)
                    }
                }
            } else {
                if (!permission.isUserFixed()) {
                    permission.setUserFixed(true)
                }
                if (permission.isUserSet()) {
                    permission.setUserSet(false)
                }
            }
        } else {
            if (permission.isRuntime && !permission.isGranted()) {
                throw PermissionException("Legacy app cannot have not-granted runtime permission ${permission.getName()}")
            }

            if (permission.affectsAppOp()) {
                if (!permission.isAppOpAllowed()) {
                    permission.setAppOpAllowed(true)
                    killApp = true
                }

                if (permission.isRevokedCompat()) {
                    permission.setRevokedCompat(false)
                }
            }

            if (permission.isReviewRequired()) {
                permission.unsetReviewRequired()
            }
        }

        try {
            persistChanges(packageInfo.applicationInfo, permission, appOpsManager, false, null)

            if (killApp && SelfPermissions.canKillUid()) {
                ActivityManagerCompat.killUid(packageInfo.applicationInfo.uid, KILL_REASON_APP_OP_CHANGE)
            }
        } catch (e: Exception) {
            throw PermissionException(e)
        }
    }

    /**
     * Revoke the permission.
     *
     * <p>This also disallows the app op for the permission if it has app op.
     *
     * @param fixedByTheUser If the user requested that she/he does not want to be asked again
     */
    @JvmStatic
    @RequiresPermission(allOf = [
        "android.permission.MANAGE_APP_OPS_MODES",
        ManifestCompat.permission.GRANT_RUNTIME_PERMISSIONS,
        ManifestCompat.permission.REVOKE_RUNTIME_PERMISSIONS
    ])
    @WorkerThread
    @Throws(PermissionException::class)
    fun revokePermission(
        packageInfo: PackageInfo,
        permission: Permission,
        appOpsManager: AppOpsManagerCompat,
        fixedByTheUser: Boolean
    ) {
        var killApp = false

        if (!isModifiable(permission)) {
            throw PermissionException("Unmodifiable permission ${permission.getName()}")
        }

        if (!permission.isReadOnly && (!permission.isRuntime || supportsRuntimePermissions(packageInfo.applicationInfo))) {
            if (permission.isGranted()) {
                permission.setGranted(false)
            }

            if (fixedByTheUser) {
                if (permission.isUserSet() || !permission.isUserFixed()) {
                    permission.setUserSet(false)
                    permission.setUserFixed(true)
                }
            } else {
                if (!permission.isUserSet() || permission.isUserFixed()) {
                    permission.setUserSet(true)
                    permission.setUserFixed(false)
                }
            }

            if (permission.affectsAppOp()) {
                permission.setAppOpAllowed(false)
            }
        } else {
            if (permission.isRuntime && !permission.isGranted()) {
                throw PermissionException("Legacy app cannot have not-granted runtime permission ${permission.getName()}")
            }

            if (permission.affectsAppOp()) {
                if (permission.isAppOpAllowed()) {
                    permission.setAppOpAllowed(false)
                    killApp = true
                }

                if (!permission.isRevokedCompat()) {
                    permission.setRevokedCompat(true)
                }
            }
        }

        try {
            persistChanges(packageInfo.applicationInfo, permission, appOpsManager, false, null)

            if (killApp && SelfPermissions.canKillUid()) {
                ActivityManagerCompat.killUid(packageInfo.applicationInfo.uid, KILL_REASON_APP_OP_CHANGE)
            }
        } catch (e: Exception) {
            throw PermissionException(e)
        }
    }

    @RequiresPermission(allOf = [
        "android.permission.MANAGE_APP_OPS_MODES",
        ManifestCompat.permission.GRANT_RUNTIME_PERMISSIONS,
        ManifestCompat.permission.REVOKE_RUNTIME_PERMISSIONS
    ])
    @WorkerThread
    @Throws(PermissionException::class, RemoteException::class)
    private fun persistChanges(
        applicationInfo: ApplicationInfo,
        permission: Permission,
        appOpsManager: AppOpsManagerCompat,
        mayKillBecauseOfAppOpsChange: Boolean,
        revokeReason: String?
    ) {
        val uid = applicationInfo.uid
        val userId = UserHandleHidden.getUserId(uid)

        var shouldKillApp = false

        if (!permission.isReadOnly) {
            if (permission.isGranted()) {
                PermissionCompat.grantPermission(applicationInfo.packageName, permission.getName(), userId)
                Log.d("PERM", "Granted %s", permission.getName())
            } else {
                val isCurrentlyGranted = PermissionCompat.checkPermission(permission.getName(),
                    applicationInfo.packageName, userId) == PERMISSION_GRANTED

                if (isCurrentlyGranted) {
                    if (revokeReason == null) {
                        PermissionCompat.revokePermission(applicationInfo.packageName, permission.getName(), userId)
                    } else {
                        PermissionCompat.revokePermission(applicationInfo.packageName, permission.getName(), userId, revokeReason)
                    }
                    Log.d("PERM", "Revoked %s", permission.getName())
                }
            }
        }

        if (!permission.isReadOnly) {
            updateFlags(applicationInfo, permission, userId)
        }

        if (permission.affectsAppOp()) {
            if (!permission.isSystemFixed()) {
                if (permission.isAppOpAllowed()) {
                    val wasChanged = allowAppOp(appOpsManager, permission.getAppOp(), applicationInfo.packageName, uid)
                    shouldKillApp = wasChanged && !supportsRuntimePermissions(applicationInfo)
                } else {
                    shouldKillApp = disallowAppOp(appOpsManager, permission.getAppOp(), applicationInfo.packageName, uid)
                }
            }
        }

        if (mayKillBecauseOfAppOpsChange && shouldKillApp && SelfPermissions.canKillUid()) {
            ActivityManagerCompat.killUid(uid, KILL_REASON_APP_OP_CHANGE)
        }
        if (userId != UserHandleHidden.myUserId()) {
            BroadcastUtils.sendPackageAltered(ContextUtils.getContext(), arrayOf(applicationInfo.packageName))
        }
    }

    @RequiresPermission(anyOf = [
        ManifestCompat.permission.GET_RUNTIME_PERMISSIONS,
        ManifestCompat.permission.GRANT_RUNTIME_PERMISSIONS,
        ManifestCompat.permission.REVOKE_RUNTIME_PERMISSIONS
    ])
    @Throws(RemoteException::class)
    private fun updateFlags(applicationInfo: ApplicationInfo, permission: Permission, userId: Int) {
        val flags = (if (permission.isUserSet()) FLAG_PERMISSION_USER_SET else 0) or
                (if (permission.isUserFixed()) FLAG_PERMISSION_USER_FIXED else 0) or
                (if (permission.isRevokedCompat()) FLAG_PERMISSION_REVOKED_COMPAT else 0) or
                (if (permission.isReviewRequired()) FLAG_PERMISSION_REVIEW_REQUIRED else 0)

        val checkAdjustPolicy = PermissionCompat.getCheckAdjustPolicyFlagPermission(applicationInfo)

        PermissionCompat.updatePermissionFlags(permission.getName(),
            applicationInfo.packageName,
            FLAG_PERMISSION_USER_SET or
                    FLAG_PERMISSION_USER_FIXED or
                    FLAG_PERMISSION_REVOKED_COMPAT or
                    (if (permission.isReviewRequired()) 0 else FLAG_PERMISSION_REVIEW_REQUIRED) or
                    FLAG_PERMISSION_ONE_TIME or
                    FLAG_PERMISSION_AUTO_REVOKED,
            flags, checkAdjustPolicy, userId)
    }

    @JvmStatic
    @RequiresPermission("android.permission.MANAGE_APP_OPS_MODES")
    @Throws(PermissionException::class)
    fun allowAppOp(appOpsManager: AppOpsManagerCompat, appOp: Int, packageName: String, uid: Int): Boolean {
        return setAppOpMode(appOpsManager, appOp, packageName, uid, AppOpsManager.MODE_ALLOWED)
    }

    @JvmStatic
    @RequiresPermission("android.permission.MANAGE_APP_OPS_MODES")
    @Throws(PermissionException::class)
    fun disallowAppOp(appOpsManager: AppOpsManagerCompat, appOp: Int, packageName: String, uid: Int): Boolean {
        return setAppOpMode(appOpsManager, appOp, packageName, uid, AppOpsManager.MODE_IGNORED)
    }

    @JvmStatic
    @RequiresPermission("android.permission.MANAGE_APP_OPS_MODES")
    @Throws(PermissionException::class)
    fun setAppOpMode(
        appOpsManager: AppOpsManagerCompat,
        appOp: Int,
        packageName: String,
        uid: Int,
        @AppOpsManagerCompat.Mode mode: Int
    ): Boolean {
        try {
            val currentMode = appOpsManager.checkOperation(appOp, uid, packageName)
            if (currentMode == mode) {
                return false
            }
            appOpsManager.setMode(appOp, uid, packageName, mode)
            return true
        } catch (e: Exception) {
            throw PermissionException(e)
        }
    }

    @JvmStatic
    private fun supportsRuntimePermissions(applicationInfo: ApplicationInfo): Boolean {
        return applicationInfo.targetSdkVersion > Build.VERSION_CODES.LOLLIPOP_MR1
    }

    @JvmStatic
    fun systemSupportsRuntimePermissions(): Boolean {
        return Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1
    }

    @JvmStatic
    fun isModifiable(permission: Permission): Boolean {
        return SelfPermissions.canModifyPermissions() && (!permission.isReadOnly || permission.affectsAppOp())
    }
}
