// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.struct

import android.app.ActivityManager
import android.content.pm.ServiceInfo

class AppDetailsServiceItem(
    serviceInfo: ServiceInfo
) : AppDetailsComponentItem(serviceInfo) {

    var runningServiceInfo: ActivityManager.RunningServiceInfo? = null

    fun isRunning(): Boolean = runningServiceInfo != null
}
