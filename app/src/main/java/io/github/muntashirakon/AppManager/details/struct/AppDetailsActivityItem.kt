// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.struct

import android.content.pm.ActivityInfo

class AppDetailsActivityItem(
    componentInfo: ActivityInfo
) : AppDetailsComponentItem(componentInfo) {
    var canLaunchAssist: Boolean = false
}
