// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.struct

import android.app.AppOpsManager
import android.content.pm.PackageInfo
import android.content.pm.PermissionInfo
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.annotation.WorkerThread
import androidx.core.content.pm.PermissionInfoCompat
import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat
import io.github.muntashirakon.AppManager.compat.ManifestCompat
import io.github.muntashirakon.AppManager.permission.*
import java.util.*

class AppDetailsAppOpItem : AppDetailsItem<Int> {
    val permission: Permission?
    val permissionInfo: PermissionInfo?
    val isDangerous: Boolean
    val hasModifiablePermission: Boolean
    val appContainsPermission: Boolean

    private var mOpEntry: AppOpsManagerCompat.OpEntry? = null

    constructor(opEntry: AppOpsManagerCompat.OpEntry) : this(opEntry.op) {
        name = opEntry.name
        mOpEntry = opEntry
    }

    constructor(op: Int) : super(op, "") {
        name = AppOpsManagerCompat.opToName(op)
        mOpEntry = null
        permissionInfo = null
        permission = null
        isDangerous = false
        hasModifiablePermission = false
        appContainsPermission = false
    }

    constructor(opEntry: AppOpsManagerCompat.OpEntry, permissionInfo: PermissionInfo, isGranted: Boolean, permissionFlags: Int, appContainsPermission: Boolean) : super(opEntry.op, "") {
        name = opEntry.name
        mOpEntry = opEntry
        this.permissionInfo = permissionInfo
        this.appContainsPermission = appContainsPermission
        isDangerous = PermissionInfoCompat.getProtection(permissionInfo) == PermissionInfo.PROTECTION_DANGEROUS
        val protectionFlags = PermissionInfoCompat.getProtectionFlags(permissionInfo)
        permission = when {
            isDangerous && PermUtils.systemSupportsRuntimePermissions() -> RuntimePermission(permissionInfo.name, isGranted, opEntry.op, isAllowed, permissionFlags)
            (protectionFlags and PermissionInfo.PROTECTION_FLAG_DEVELOPMENT) != 0 -> DevelopmentPermission(permissionInfo.name, isGranted, opEntry.op, isAllowed, permissionFlags)
            else -> ReadOnlyPermission(permissionInfo.name, isGranted, opEntry.op, isAllowed, permissionFlags)
        }
        hasModifiablePermission = PermUtils.isModifiable(permission)
    }

    constructor(op: Int, permissionInfo: PermissionInfo, isGranted: Boolean, permissionFlags: Int, appContainsPermission: Boolean) : super(op, "") {
        name = AppOpsManagerCompat.opToName(op)
        mOpEntry = null
        this.permissionInfo = permissionInfo
        this.appContainsPermission = appContainsPermission
        isDangerous = PermissionInfoCompat.getProtection(permissionInfo) == PermissionInfo.PROTECTION_DANGEROUS
        val protectionFlags = PermissionInfoCompat.getProtectionFlags(permissionInfo)
        permission = when {
            isDangerous && PermUtils.systemSupportsRuntimePermissions() -> RuntimePermission(permissionInfo.name, isGranted, op, isAllowed, permissionFlags)
            (protectionFlags and PermissionInfo.PROTECTION_FLAG_DEVELOPMENT) != 0 -> DevelopmentPermission(permissionInfo.name, isGranted, op, isAllowed, permissionFlags)
            else -> ReadOnlyPermission(permissionInfo.name, isGranted, op, isAllowed, permissionFlags)
        }
        hasModifiablePermission = PermUtils.isModifiable(permission)
    }

    val op: Int
        get() = item

    val mode: Int
        get() = mOpEntry?.mode ?: AppOpsManagerCompat.opToDefaultMode(op)

    val duration: Long
        get() = mOpEntry?.duration ?: 0L

    val time: Long
        get() = mOpEntry?.time ?: 0L

    val rejectTime: Long
        get() = mOpEntry?.rejectTime ?: 0L

    val isRunning: Boolean
        get() = mOpEntry?.isRunning ?: false

    val isAllowed: Boolean
        get() {
            var allowed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) mode == AppOpsManager.MODE_FOREGROUND else false
            allowed = allowed or (mode == AppOpsManager.MODE_ALLOWED)
            if (mode == AppOpsManager.MODE_DEFAULT) allowed = allowed or (permission?.isGranted() ?: false)
            return allowed
        }

    @RequiresPermission(allOf = ["android.permission.MANAGE_APP_OPS_MODES", ManifestCompat.permission.GRANT_RUNTIME_PERMISSIONS])
    @WorkerThread
    @Throws(PermissionException::class)
    fun allowAppOp(packageInfo: PackageInfo, appOpsManager: AppOpsManagerCompat) {
        if (hasModifiablePermission && permission != null) PermUtils.grantPermission(packageInfo, permission, appOpsManager, true, true)
        else PermUtils.allowAppOp(appOpsManager, op, packageInfo.packageName, packageInfo.applicationInfo.uid)
        invalidate(appOpsManager, packageInfo)
    }

    @RequiresPermission(allOf = ["android.permission.MANAGE_APP_OPS_MODES", ManifestCompat.permission.REVOKE_RUNTIME_PERMISSIONS])
    @WorkerThread
    @Throws(PermissionException::class)
    fun disallowAppOp(packageInfo: PackageInfo, appOpsManager: AppOpsManagerCompat) {
        if (hasModifiablePermission && permission != null) PermUtils.revokePermission(packageInfo, permission, appOpsManager, true)
        else PermUtils.disallowAppOp(appOpsManager, op, packageInfo.packageName, packageInfo.applicationInfo.uid)
        invalidate(appOpsManager, packageInfo)
    }

    @RequiresPermission(allOf = ["android.permission.MANAGE_APP_OPS_MODES", ManifestCompat.permission.GRANT_RUNTIME_PERMISSIONS, ManifestCompat.permission.REVOKE_RUNTIME_PERMISSIONS])
    @WorkerThread
    @Throws(PermissionException::class)
    fun setAppOp(packageInfo: PackageInfo, appOpsManager: AppOpsManagerCompat, mode: Int) {
        if (hasModifiablePermission && permission != null) {
            var isAllowed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) this.mode == AppOpsManager.MODE_FOREGROUND else false
            isAllowed = isAllowed or (this.mode == AppOpsManager.MODE_ALLOWED)
            if (isAllowed) PermUtils.grantPermission(packageInfo, permission, appOpsManager, true, true)
            else PermUtils.revokePermission(packageInfo, permission, appOpsManager, true)
        }
        PermUtils.setAppOpMode(appOpsManager, op, packageInfo.packageName, packageInfo.applicationInfo.uid, mode)
        invalidate(appOpsManager, packageInfo)
    }

    @RequiresPermission("android.permission.MANAGE_APP_OPS_MODES")
    @Throws(PermissionException::class)
    fun invalidate(appOpsManager: AppOpsManagerCompat, packageInfo: PackageInfo) {
        try {
            val opEntryList = appOpsManager.getOpsForPackage(packageInfo.applicationInfo.uid, packageInfo.packageName, intArrayOf(op))[0].ops
            mOpEntry = if (opEntryList.isNotEmpty()) opEntryList[0] else null
        } catch (e: Exception) { throw PermissionException(e) }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AppDetailsAppOpItem) return false
        if (!super.equals(other)) return false
        return item == other.item
    }

    override fun hashCode(): Int = Objects.hash(item)
}
