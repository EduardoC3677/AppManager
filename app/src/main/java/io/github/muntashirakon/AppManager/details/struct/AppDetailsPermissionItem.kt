// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.struct

import android.content.pm.PackageInfo
import android.content.pm.PermissionInfo
import android.os.RemoteException
import androidx.annotation.WorkerThread
import androidx.core.content.pm.PermissionInfoCompat
import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat
import io.github.muntashirakon.AppManager.permission.PermUtils
import io.github.muntashirakon.AppManager.permission.Permission
import io.github.muntashirakon.AppManager.permission.PermissionException

/**
 * Stores individual app details item
 */
class AppDetailsPermissionItem(permissionInfo: PermissionInfo, val permission: Permission, val flags: Int) : AppDetailsItem<PermissionInfo>(permissionInfo, "") {
    val isDangerous: Boolean = PermissionInfoCompat.getProtection(permissionInfo) == PermissionInfo.PROTECTION_DANGEROUS
    val modifiable: Boolean = PermUtils.isModifiable(permission)
    val protectionFlags: Int = PermissionInfoCompat.getProtectionFlags(permissionInfo)
    val settingItem: PermUtils.SettingItem? = PermUtils.permissionNameToSettingItem[permissionInfo.name]

    fun isGranted(): Boolean {
        if (!permission.isReadOnly) return permission.isGrantedIncludingAppOp()
        if (permission.affectsAppOp()) return permission.isAppOpAllowed()
        return permission.isGranted()
    }

    /**
     * Grant the permission.
     *
     * This also automatically grants app op if it has app op.
     */
    @WorkerThread
    @Throws(RemoteException::class, PermissionException::class)
    fun grantPermission(packageInfo: PackageInfo, appOpsManager: AppOpsManagerCompat) {
        PermUtils.grantPermission(packageInfo, permission, appOpsManager, true, true)
    }

    /**
     * Revoke the permission.
     *
     * This also disallows the app op for the permission if it has app op.
     */
    @WorkerThread
    @Throws(RemoteException::class, PermissionException::class)
    fun revokePermission(packageInfo: PackageInfo, appOpsManager: AppOpsManagerCompat?) {
        PermUtils.revokePermission(packageInfo, permission, appOpsManager!!, true)
    }
}
