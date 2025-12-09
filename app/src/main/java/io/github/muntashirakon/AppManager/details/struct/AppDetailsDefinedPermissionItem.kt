// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.struct

import android.content.pm.PermissionInfo

class AppDetailsDefinedPermissionItem(
    permissionInfo: PermissionInfo,
    val isExternal: Boolean
) : AppDetailsItem<PermissionInfo>(permissionInfo) {

    init {
        name = permissionInfo.name
    }
}
