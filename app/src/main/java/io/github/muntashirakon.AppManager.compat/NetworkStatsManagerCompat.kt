// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat

import android.Manifest
import android.content.Context
import android.net.INetworkStatsService
import android.net.NetworkCapabilities
import android.net.NetworkTemplate
import android.os.Build
import android.os.RemoteException
import android.text.TextUtils
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import io.github.muntashirakon.AppManager.ipc.ProxyBinder

object NetworkStatsManagerCompat {
    @JvmStatic
    @RequiresApi(Build.VERSION_CODES.M)
    @RequiresPermission(Manifest.permission.PACKAGE_USAGE_STATS)
    @Throws(RemoteException::class, SecurityException::class)
    fun querySummary(networkType: Int, subscriberId: String?, startTime: Long, endTime: Long): NetworkStatsCompat {
        val statsService = INetworkStatsService.Stub.asInterface(ProxyBinder.getService(Context.NETWORK_STATS_SERVICE))
        val template = createTemplate(networkType, subscriberId)
        val networkStats = NetworkStatsCompat(template, 0, startTime, endTime, statsService)
        networkStats.startSummaryEnumeration()
        return networkStats
    }

    private fun createTemplate(networkType: Int, subscriberId: String?): NetworkTemplate {
        return when (networkType) {
            NetworkCapabilities.TRANSPORT_CELLULAR -> if (subscriberId == null) {
                NetworkTemplate.buildTemplateMobileWildcard()
            } else {
                NetworkTemplate.buildTemplateMobileAll(subscriberId)
            }
            NetworkCapabilities.TRANSPORT_WIFI -> if (TextUtils.isEmpty(subscriberId)) {
                NetworkTemplate.buildTemplateWifiWildcard()
            } else {
                NetworkTemplate(NetworkTemplate.MATCH_WIFI, subscriberId, null)
            }
            else -> throw IllegalArgumentException("Cannot create template for network type $networkType'.")
        }
    }
}
