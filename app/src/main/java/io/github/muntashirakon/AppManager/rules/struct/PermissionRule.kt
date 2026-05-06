// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.struct

import android.content.pm.PermissionInfo
import android.os.RemoteException
import androidx.core.content.pm.PermissionInfoCompat
import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat
import io.github.muntashirakon.AppManager.compat.PermissionCompat
import io.github.muntashirakon.AppManager.compat.PermissionCompat.PermissionFlags
import io.github.muntashirakon.AppManager.permission.DevelopmentPermission
import io.github.muntashirakon.AppManager.permission.PermUtils
import io.github.muntashirakon.AppManager.permission.Permission
import io.github.muntashirakon.AppManager.permission.ReadOnlyPermission
import io.github.muntashirakon.AppManager.permission.RuntimePermission
import io.github.muntashirakon.AppManager.rules.RuleType
import java.util.StringTokenizer

class PermissionRule : RuleEntry {
    val appOp: Int

    var isGranted: Boolean

    @PermissionFlags
    var flags: Int

    constructor(
        packageName: String,
        permName: String,
        isGranted: Boolean,
        @PermissionFlags flags: Int
    ) : super(packageName, permName, RuleType.PERMISSION) {
        this.isGranted = isGranted
        this.flags = flags
        this.appOp = AppOpsManagerCompat.permissionToOpCode(name)
    }

    @Throws(IllegalArgumentException::class)
    constructor(
        packageName: String,
        permName: String,
        tokenizer: StringTokenizer
    ) : super(packageName, permName, RuleType.PERMISSION) {
        if (tokenizer.hasMoreElements()) {
            this.isGranted = tokenizer.nextElement().toString().toBoolean()
        } else {
            throw IllegalArgumentException("Invalid format: isGranted not found")
        }
        if (tokenizer.hasMoreElements()) {
            this.flags = tokenizer.nextElement().toString().toInt()
        } else {
            // Don't throw exception in order to provide backward compatibility
            this.flags = 0
        }
        this.appOp = AppOpsManagerCompat.permissionToOpCode(name)
    }

    fun getPermission(appOpAllowed: Boolean): Permission {
        var permissionInfo: PermissionInfo? = null
        try {
            permissionInfo = PermissionCompat.getPermissionInfo(name, packageName, 0)
        } catch (ignore: RemoteException) {
        }
        if (permissionInfo == null) {
            permissionInfo = PermissionInfo().apply {
                name = this@PermissionRule.name
            }
        }
        val protection = PermissionInfoCompat.getProtection(permissionInfo)
        val protectionFlags = PermissionInfoCompat.getProtectionFlags(permissionInfo)

        return when {
            protection == PermissionInfo.PROTECTION_DANGEROUS && PermUtils.systemSupportsRuntimePermissions() ->
                RuntimePermission(name, isGranted, appOp, appOpAllowed, flags)
            (protectionFlags and PermissionInfo.PROTECTION_FLAG_DEVELOPMENT) != 0 ->
                DevelopmentPermission(name, isGranted, appOp, appOpAllowed, flags)
            else ->
                ReadOnlyPermission(name, isGranted, appOp, appOpAllowed, flags)
        }
    }

    override fun toString(): String {
        return "PermissionRule{packageName='$packageName', name='$name', isGranted=$isGranted, flags=$flags}"\n}

    override fun flattenToString(isExternal: Boolean): String {
        return addPackageWithTab(isExternal) + "$name\t${type.name}\t$isGranted\t$flags"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PermissionRule) return false
        if (!super.equals(other)) return false
        return isGranted == other.isGranted && flags == other.flags
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + isGranted.hashCode()
        result = 31 * result + flags
        return result
    }
}
