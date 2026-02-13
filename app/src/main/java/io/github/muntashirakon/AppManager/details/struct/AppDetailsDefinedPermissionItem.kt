// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.struct

import android.content.pm.PermissionInfo

class AppDetailsDefinedPermissionItem(
    @JvmField val permissionInfo: PermissionInfo,
    @JvmField val isExternal: Boolean
) : AppDetailsItem<PermissionInfo>(permissionInfo) {
    @JvmField val permission: PermissionInfo = item

    init {
        name = permissionInfo.name
    }
}
