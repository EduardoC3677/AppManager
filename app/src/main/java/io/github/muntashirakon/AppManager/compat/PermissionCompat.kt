// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat

import android.annotation.SuppressLint
import android.annotation.UserIdInt
import android.content.pm.*
import android.content.pm.permission.SplitPermissionInfoParcelable
import android.os.Build
import android.os.RemoteException
import android.permission.IPermissionManager
import android.util.SparseArray
import androidx.annotation.IntDef
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import dev.rikka.tools.refine.Refine
import io.github.muntashirakon.AppManager.compat.VirtualDeviceManagerCompat.PERSISTENT_DEVICE_ID_DEFAULT
import io.github.muntashirakon.AppManager.ipc.ProxyBinder
import io.github.muntashirakon.AppManager.utils.ContextUtils
import io.github.muntashirakon.AppManager.utils.ExUtils
import java.util.*

object PermissionCompat {
    const val FLAG_PERMISSION_NONE: Int = 0

    /**
     * Permission flag: The permission is set in its current state
     * by the user and apps can still request it at runtime.
     */
    @RequiresApi(Build.VERSION_CODES.M)
    const val FLAG_PERMISSION_USER_SET: Int = 1

    /**
     * Permission flag: The permission is set in its current state
     * by the user and it is fixed, i.e. apps can no longer request
     * this permission.
     */
    @RequiresApi(Build.VERSION_CODES.M)
    const val FLAG_PERMISSION_USER_FIXED: Int = 1 shl 1

    /**
     * Permission flag: The permission is set in its current state
     * by device policy and neither apps nor the user can change
     * its state.
     */
    @RequiresApi(Build.VERSION_CODES.M)
    const val FLAG_PERMISSION_POLICY_FIXED: Int = 1 shl 2

    /**
     * Permission flag: The permission is set in a granted state but
     * access to resources it guards is restricted by other means to
     * enable revoking a permission on legacy apps that do not support
     * runtime permissions. If this permission is upgraded to runtime
     * because the app was updated to support runtime permissions, the
     * the permission will be revoked in the upgrade process.
     *
     * @deprecated Renamed to {@link #FLAG_PERMISSION_REVOKED_COMPAT}.
     */
    @Deprecated("Renamed to FLAG_PERMISSION_REVOKED_COMPAT")
    @RequiresApi(Build.VERSION_CODES.M)
    const val FLAG_PERMISSION_REVOKE_ON_UPGRADE: Int = 1 shl 3

    /**
     * Permission flag: The permission is set in its current state
     * because the app is a component that is a part of the system.
     */
    @RequiresApi(Build.VERSION_CODES.M)
    const val FLAG_PERMISSION_SYSTEM_FIXED: Int = 1 shl 4

    /**
     * Permission flag: The permission is granted by default because it
     * enables app functionality that is expected to work out-of-the-box
     * for providing a smooth user experience. For example, the phone app
     * is expected to have the phone permission.
     */
    @RequiresApi(Build.VERSION_CODES.M)
    const val FLAG_PERMISSION_GRANTED_BY_DEFAULT: Int = 1 shl 5

    /**
     * Permission flag: The permission has to be reviewed before any of
     * the app components can run.
     */
    @RequiresApi(Build.VERSION_CODES.N)
    const val FLAG_PERMISSION_REVIEW_REQUIRED: Int = 1 shl 6

    /**
     * Permission flag: The permission has not been explicitly requested by
     * the app but has been added automatically by the system. Revoke once
     * the app does explicitly request it.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    const val FLAG_PERMISSION_REVOKE_WHEN_REQUESTED: Int = 1 shl 7

    /**
     * Permission flag: The permission's usage should be made highly visible to the user
     * when granted.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    const val FLAG_PERMISSION_USER_SENSITIVE_WHEN_GRANTED: Int = 1 shl 8

    /**
     * Permission flag: The permission's usage should be made highly visible to the user
     * when denied.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    const val FLAG_PERMISSION_USER_SENSITIVE_WHEN_DENIED: Int = 1 shl 9

    /**
     * Permission flag: The permission is restricted but the app is exempt
     * from the restriction and is allowed to hold this permission in its
     * full form and the exemption is provided by the installer on record.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    const val FLAG_PERMISSION_RESTRICTION_INSTALLER_EXEMPT: Int = 1 shl 11

    /**
     * Permission flag: The permission is restricted but the app is exempt
     * from the restriction and is allowed to hold this permission in its
     * full form and the exemption is provided by the system due to its
     * permission policy.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    const val FLAG_PERMISSION_RESTRICTION_SYSTEM_EXEMPT: Int = 1 shl 12

    /**
     * Permission flag: The permission is restricted but the app is exempt
     * from the restriction and is allowed to hold this permission and the
     * exemption is provided by the system when upgrading from an OS version
     * where the permission was not restricted to an OS version where the
     * permission is restricted.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    const val FLAG_PERMISSION_RESTRICTION_UPGRADE_EXEMPT: Int = 1 shl 13

    /**
     * Permission flag: The permission is disabled but may be granted. If
     * disabled the data protected by the permission should be protected
     * by a no-op (empty list, default error, etc) instead of crashing the
     * client.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    const val FLAG_PERMISSION_APPLY_RESTRICTION: Int = 1 shl 14

    /**
     * Permission flag: The permission is granted because the application holds a role.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    const val FLAG_PERMISSION_GRANTED_BY_ROLE: Int = 1 shl 15

    /**
     * Permission flag: The permission should have been revoked but is kept granted for
     * compatibility. The data protected by the permission should be protected by a no-op (empty
     * list, default error, etc) instead of crashing the client. The permission will be revoked if
     * the app is upgraded to supports it.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    const val FLAG_PERMISSION_REVOKED_COMPAT: Int = FLAG_PERMISSION_REVOKE_ON_UPGRADE

    /**
     * Permission flag: The permission is one-time and should be revoked automatically on app
     * inactivity
     */
    @RequiresApi(Build.VERSION_CODES.R)
    const val FLAG_PERMISSION_ONE_TIME: Int = 1 shl 16

    /**
     * Permission flag: Whether permission was revoked by auto-revoke.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    const val FLAG_PERMISSION_AUTO_REVOKED: Int = 1 shl 17

    /**
     * Permission flags: Reserved for use by the permission controller. The platform and any
     * packages besides the permission controller should not assume any definition about these
     * flags.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    const val FLAGS_PERMISSION_RESERVED_PERMISSION_CONTROLLER: Int = -0x10000000 // 1 << 28 | 1 << 29 | 1 << 30 | 1 shl 31

    /**
     * Permission flags: Bitwise or of all permission flags allowing an
     * exemption for a restricted permission.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    const val FLAGS_PERMISSION_RESTRICTION_ANY_EXEMPT: Int = FLAG_PERMISSION_RESTRICTION_INSTALLER_EXEMPT or
            FLAG_PERMISSION_RESTRICTION_SYSTEM_EXEMPT or
            FLAG_PERMISSION_RESTRICTION_UPGRADE_EXEMPT

    /**
     * Mask for all permission flags.
     */
    @JvmField
    val MASK_PERMISSION_FLAGS_ALL: Int

    init {
        var allPerms = FLAG_PERMISSION_NONE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            allPerms = allPerms or (FLAG_PERMISSION_USER_SET
                    or FLAG_PERMISSION_USER_FIXED
                    or FLAG_PERMISSION_POLICY_FIXED
                    or FLAG_PERMISSION_REVOKE_ON_UPGRADE
                    or FLAG_PERMISSION_SYSTEM_FIXED
                    or FLAG_PERMISSION_GRANTED_BY_DEFAULT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            allPerms = allPerms or FLAG_PERMISSION_REVIEW_REQUIRED
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            allPerms = allPerms or (FLAG_PERMISSION_REVOKE_WHEN_REQUESTED
                    or FLAG_PERMISSION_USER_SENSITIVE_WHEN_GRANTED
                    or FLAG_PERMISSION_USER_SENSITIVE_WHEN_DENIED
                    or FLAG_PERMISSION_RESTRICTION_INSTALLER_EXEMPT
                    or FLAG_PERMISSION_RESTRICTION_SYSTEM_EXEMPT
                    or FLAG_PERMISSION_RESTRICTION_UPGRADE_EXEMPT
                    or FLAG_PERMISSION_APPLY_RESTRICTION
                    or FLAG_PERMISSION_GRANTED_BY_ROLE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            allPerms = allPerms or (FLAG_PERMISSION_REVOKED_COMPAT
                    or FLAG_PERMISSION_ONE_TIME
                    or FLAG_PERMISSION_AUTO_REVOKED)
        }
        @Suppress("ANNOTATION_TARGET_WRONG_TYPE")
        MASK_PERMISSION_FLAGS_ALL = allPerms
    }

    /**
     * Permission flags set when granting or revoking a permission.
     */
    @SuppressLint("NewApi")
    @RequiresApi(Build.VERSION_CODES.M)
    @IntDef(
        flag = true, value = [
            FLAG_PERMISSION_NONE,
            FLAG_PERMISSION_USER_SET,
            FLAG_PERMISSION_USER_FIXED,
            FLAG_PERMISSION_POLICY_FIXED,
            FLAG_PERMISSION_SYSTEM_FIXED,
            FLAG_PERMISSION_GRANTED_BY_DEFAULT,
            FLAG_PERMISSION_REVIEW_REQUIRED,
            FLAG_PERMISSION_USER_SENSITIVE_WHEN_GRANTED,
            FLAG_PERMISSION_USER_SENSITIVE_WHEN_DENIED,
            FLAG_PERMISSION_REVOKE_WHEN_REQUESTED,
            FLAG_PERMISSION_RESTRICTION_UPGRADE_EXEMPT,
            FLAG_PERMISSION_RESTRICTION_SYSTEM_EXEMPT,
            FLAG_PERMISSION_RESTRICTION_INSTALLER_EXEMPT,
            FLAG_PERMISSION_APPLY_RESTRICTION,
            FLAG_PERMISSION_GRANTED_BY_ROLE,
            FLAG_PERMISSION_REVOKED_COMPAT,
            FLAG_PERMISSION_ONE_TIME,
            FLAG_PERMISSION_AUTO_REVOKED
        ]
    )
    @Retention(AnnotationRetention.SOURCE)
    annotation class PermissionFlags

    @JvmStatic
    @RequiresPermission(
        anyOf = [
            ManifestCompat.permission.GET_RUNTIME_PERMISSIONS,
            ManifestCompat.permission.GRANT_RUNTIME_PERMISSIONS,
            ManifestCompat.permission.REVOKE_RUNTIME_PERMISSIONS,
        ]
    )
    @PermissionFlags
    fun getPermissionFlags(
        permissionName: String,
        packageName: String,
        @UserIdInt userId: Int
    ): Int {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val permissionManager = permissionManager
                return try {
                    permissionManager.getPermissionFlags(packageName, permissionName, userId)
                } catch (e: NoSuchMethodError) {
                    try {
                        permissionManager.getPermissionFlags(
                            packageName, permissionName,
                            ContextUtils.getContext().deviceId, userId
                        )
                    } catch (e2: NoSuchMethodError) {
                        permissionManager.getPermissionFlags(
                            packageName, permissionName,
                            PERSISTENT_DEVICE_ID_DEFAULT, userId
                        )
                    }
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                return permissionManager.getPermissionFlags(packageName, permissionName, userId)
            } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R) {
                return permissionManager.getPermissionFlags(permissionName, packageName, userId)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                return PackageManagerCompat.getPackageManager().getPermissionFlags(permissionName, packageName, userId)
            }
        } catch (e: RemoteException) {
            return ExUtils.rethrowFromSystemServer(e)
        }
        return FLAG_PERMISSION_NONE
    }

    /**
     * Replace a set of flags with another or {@code 0}. Requires {@link ManifestCompat.permission#ADJUST_RUNTIME_PERMISSIONS_POLICY}
     * when checkAdjustPolicyFlagPermission is {@code true} and flagMask has {@link #FLAG_PERMISSION_POLICY_FIXED}.
     *
     * @param flagMask   The flags to be replaced
     * @param flagValues The new flags to set (is a subset of flagMask)
     * @see <a href="https://cs.android.com/android/platform/superproject/+/master:cts/tests/tests/permission/src/android/permission/cts/PermissionFlagsTest.java">PermissionFlagsTest.java</a>
     */
    @JvmStatic
    @RequiresPermission(
        anyOf = [
            ManifestCompat.permission.GET_RUNTIME_PERMISSIONS,
            ManifestCompat.permission.GRANT_RUNTIME_PERMISSIONS,
            ManifestCompat.permission.REVOKE_RUNTIME_PERMISSIONS,
        ]
    )
    @Throws(RemoteException::class)
    fun updatePermissionFlags(
        permissionName: String,
        packageName: String,
        @PermissionFlags flagMask: Int,
        @PermissionFlags flagValues: Int,
        checkAdjustPolicyFlagPermission: Boolean,
        @UserIdInt userId: Int
    ) {
        val pm = PackageManagerCompat.getPackageManager()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val permissionManager = permissionManager
            try {
                permissionManager.updatePermissionFlags(
                    packageName, permissionName, flagMask, flagValues,
                    checkAdjustPolicyFlagPermission, userId
                )
            } catch (e: NoSuchMethodError) {
                try {
                    permissionManager.updatePermissionFlags(
                        packageName, permissionName, flagMask, flagValues,
                        checkAdjustPolicyFlagPermission, ContextUtils.getContext().deviceId, userId
                    )
                } catch (e2: NoSuchMethodError) {
                    permissionManager.updatePermissionFlags(
                        packageName, permissionName, flagMask, flagValues,
                        checkAdjustPolicyFlagPermission, PERSISTENT_DEVICE_ID_DEFAULT, userId
                    )
                }
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionManager.updatePermissionFlags(
                packageName, permissionName, flagMask, flagValues,
                checkAdjustPolicyFlagPermission, userId
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            permissionManager.updatePermissionFlags(
                permissionName, packageName, flagMask, flagValues,
                checkAdjustPolicyFlagPermission, userId
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            pm.updatePermissionFlags(
                permissionName, packageName, flagMask, flagValues,
                checkAdjustPolicyFlagPermission, userId
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pm.updatePermissionFlags(permissionName, packageName, flagMask, flagValues, userId)
        }
    }

    /**
     * Grant a permission. May also require {@link ManifestCompat.permission#ADJUST_RUNTIME_PERMISSIONS_POLICY}.
     */
    @JvmStatic
    @RequiresPermission(ManifestCompat.permission.GRANT_RUNTIME_PERMISSIONS)
    @Throws(RemoteException::class)
    fun grantPermission(
        packageName: String,
        permissionName: String,
        @UserIdInt userId: Int
    ) {
        val pm = PackageManagerCompat.getPackageManager()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val permissionManager = permissionManager
            try {
                permissionManager.grantRuntimePermission(packageName, permissionName, userId)
            } catch (e: NoSuchMethodError) {
                try {
                    permissionManager.grantRuntimePermission(
                        packageName, permissionName,
                        ContextUtils.getContext().deviceId, userId
                    )
                } catch (e2: NoSuchMethodError) {
                    permissionManager.grantRuntimePermission(
                        packageName, permissionName,
                        PERSISTENT_DEVICE_ID_DEFAULT, userId
                    )
                }
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            permissionManager.grantRuntimePermission(packageName, permissionName, userId)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pm.grantRuntimePermission(packageName, permissionName, userId)
        } else {
            pm.grantPermission(packageName, permissionName)
        }
    }

    /**
     * Revoke a permission. May also require {@link ManifestCompat.permission#ADJUST_RUNTIME_PERMISSIONS_POLICY}.
     */
    @JvmStatic
    @RequiresPermission(ManifestCompat.permission.REVOKE_RUNTIME_PERMISSIONS)
    @Throws(RemoteException::class)
    fun revokePermission(
        packageName: String,
        permissionName: String,
        @UserIdInt userId: Int
    ) {
        revokePermission(packageName, permissionName, userId, null)
    }

    /**
     * Revoke a permission. May also require {@link ManifestCompat.permission#ADJUST_RUNTIME_PERMISSIONS_POLICY}.
     */
    @JvmStatic
    @RequiresPermission(ManifestCompat.permission.REVOKE_RUNTIME_PERMISSIONS)
    @Throws(RemoteException::class)
    fun revokePermission(
        packageName: String,
        permissionName: String,
        @UserIdInt userId: Int,
        reason: String?
    ) {
        val pm = PackageManagerCompat.getPackageManager()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val permissionManager = permissionManager
            try {
                permissionManager.revokeRuntimePermission(packageName, permissionName, userId, reason)
            } catch (e: NoSuchMethodError) {
                try {
                    permissionManager.revokeRuntimePermission(
                        packageName, permissionName,
                        ContextUtils.getContext().deviceId, userId, reason
                    )
                } catch (e2: NoSuchMethodError) {
                    permissionManager.revokeRuntimePermission(
                        packageName, permissionName,
                        PERSISTENT_DEVICE_ID_DEFAULT, userId, reason
                    )
                }
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            permissionManager.revokeRuntimePermission(packageName, permissionName, userId, reason)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pm.revokeRuntimePermission(packageName, permissionName, userId)
        } else {
            pm.revokePermission(packageName, permissionName)
        }
    }

    @JvmStatic
    fun checkPermission(
        permissionName: String,
        packageName: String,
        @UserIdInt userId: Int
    ): Int {
        val pm = PackageManagerCompat.getPackageManager()
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                pm.checkPermission(permissionName, packageName, userId)
            } else {
                pm.checkPermission(permissionName, packageName)
            }
        } catch (e: RemoteException) {
            ExUtils.rethrowFromSystemServer(e)
        }
    }

    @JvmStatic
    @Throws(RemoteException::class)
    fun getPermissionInfo(permissionName: String?, packageName: String?, flags: Int): PermissionInfo? {
        val pm = PackageManagerCompat.getPackageManager()
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> permissionManager.getPermissionInfo(permissionName, packageName, flags)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> pm.getPermissionInfo(permissionName, packageName, flags)
            else -> pm.getPermissionInfo(permissionName, flags)
        }
    }

    @JvmStatic
    @Throws(RemoteException::class)
    fun getPermissionGroupInfo(groupName: String?, flags: Int): PermissionGroupInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            permissionManager.getPermissionGroupInfo(groupName, flags)
        } else {
            PackageManagerCompat.getPackageManager().getPermissionGroupInfo(groupName, flags)
        }
    }

    @JvmStatic
    @Throws(RemoteException::class)
    fun queryPermissionsByGroup(groupName: String?, flags: Int): List<PermissionInfo> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            permissionManager.queryPermissionsByGroup(groupName, flags).list
        } else {
            val pm = PackageManagerCompat.getPackageManager()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val pmN = Refine.unsafeCast<IPackageManagerN>(pm)
                pmN.queryPermissionsByGroup(groupName, flags).list
            } else {
                pm.queryPermissionsByGroup(groupName, flags)
            }
        }
    }

    @JvmStatic
    @Throws(RemoteException::class)
    fun getSplitPermissions(): List<SplitPermissionInfoParcelable> {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> permissionManager.splitPermissions
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> PackageManagerCompat.getPackageManager().splitPermissions
            else -> Collections.emptyList()
        }
    }

    @JvmStatic
    fun getCheckAdjustPolicyFlagPermission(info: ApplicationInfo): Boolean {
        return info.targetSdkVersion >= Build.VERSION_CODES.Q
    }

    @get:JvmStatic
    val permissionManager: IPermissionManager
        get() = IPermissionManager.Stub.asInterface(ProxyBinder.getService("permissionmgr"))

    @JvmStatic
    @SuppressLint("WrongConstant")
    fun getPermissionFlagsWithString(@PermissionFlags flags: Int): SparseArray<String> {
        val permissionFlagsWithString = SparseArray<String>()
        for (i in 0..17) {
            if (flags and (1 shl i) != 0) {
                permissionFlagsWithString.put(1 shl i, permissionFlagToString(1 shl i))
            }
        }
        return permissionFlagsWithString
    }

    @JvmStatic
    @SuppressLint("NewApi")
    fun permissionFlagToString(@PermissionFlags flag: Int): String {
        return when (flag) {
            FLAG_PERMISSION_GRANTED_BY_DEFAULT -> "GRANTED_BY_DEFAULT"
            FLAG_PERMISSION_POLICY_FIXED -> "POLICY_FIXED"
            FLAG_PERMISSION_SYSTEM_FIXED -> "SYSTEM_FIXED"
            FLAG_PERMISSION_USER_SET -> "USER_SET"
            FLAG_PERMISSION_USER_FIXED -> "USER_FIXED"
            FLAG_PERMISSION_REVIEW_REQUIRED -> "REVIEW_REQUIRED"
            FLAG_PERMISSION_REVOKE_WHEN_REQUESTED -> "REVOKE_WHEN_REQUESTED"
            FLAG_PERMISSION_USER_SENSITIVE_WHEN_GRANTED -> "USER_SENSITIVE_WHEN_GRANTED"
            FLAG_PERMISSION_USER_SENSITIVE_WHEN_DENIED -> "USER_SENSITIVE_WHEN_DENIED"
            FLAG_PERMISSION_RESTRICTION_INSTALLER_EXEMPT -> "RESTRICTION_INSTALLER_EXEMPT"
            FLAG_PERMISSION_RESTRICTION_SYSTEM_EXEMPT -> "RESTRICTION_SYSTEM_EXEMPT"
            FLAG_PERMISSION_RESTRICTION_UPGRADE_EXEMPT -> "RESTRICTION_UPGRADE_EXEMPT"
            FLAG_PERMISSION_APPLY_RESTRICTION -> "APPLY_RESTRICTION"
            FLAG_PERMISSION_GRANTED_BY_ROLE -> "GRANTED_BY_ROLE"
            FLAG_PERMISSION_REVOKED_COMPAT -> "REVOKED_COMPAT"
            FLAG_PERMISSION_ONE_TIME -> "ONE_TIME"
            FLAG_PERMISSION_AUTO_REVOKED -> "AUTO_REVOKED"
            FLAG_PERMISSION_NONE -> "0"
            else -> flag.toString()
        }
    }
}
