// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.annotation.WorkerThread
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat
import io.github.muntashirakon.AppManager.self.SelfPermissions
import io.github.muntashirakon.AppManager.settings.FeatureController
import io.github.muntashirakon.AppManager.usage.AppUsageStatsManager
import io.github.muntashirakon.AppManager.usage.PackageUsageInfo
import io.github.muntashirakon.AppManager.usage.UsageUtils
import io.github.muntashirakon.AppManager.utils.ExUtils
import io.github.muntashirakon.AppManager.utils.ThreadUtils

object FilteringUtils {
    @WorkerThread
    @JvmStatic
    fun loadFilterableAppInfo(userIds: IntArray): List<FilterableAppInfo> {
        val filterableAppInfoList = mutableListOf<FilterableAppInfo>()
        val hasUsageAccess = FeatureController.isUsageAccessEnabled() && SelfPermissions.checkUsageStatsPermission()
        for (userId in userIds) {
            if (ThreadUtils.isInterrupted()) return emptyList()
            if (!SelfPermissions.checkCrossUserPermission(userId, false)) continue

            val packageInfoList = PackageManagerCompat.getInstalledPackages(
                PackageManager.GET_META_DATA or PackageManagerCompat.GET_SIGNING_CERTIFICATES
                        or PackageManager.GET_ACTIVITIES or PackageManager.GET_RECEIVERS
                        or PackageManager.GET_PROVIDERS or PackageManager.GET_SERVICES
                        or PackageManager.GET_CONFIGURATIONS or PackageManager.GET_PERMISSIONS
                        or PackageManager.GET_URI_PERMISSION_PATTERNS
                        or PackageManagerCompat.MATCH_DISABLED_COMPONENTS or PackageManagerCompat.MATCH_UNINSTALLED_PACKAGES
                        or PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, userId)

            val packageUsageInfoMap = mutableMapOf<String, PackageUsageInfo>()
            if (hasUsageAccess) {
                val interval = UsageUtils.getLastWeek()
                val usageInfoList = ExUtils.exceptionAsNull { AppUsageStatsManager.getInstance().getUsageStats(interval, userId) }
                usageInfoList?.forEach { info ->
                    if (ThreadUtils.isInterrupted()) return emptyList()
                    packageUsageInfoMap[info.packageName] = info
                }
            }
            for (packageInfo in packageInfoList) {
                if (ThreadUtils.isInterrupted()) return emptyList()
                filterableAppInfoList.add(FilterableAppInfo(packageInfo, packageUsageInfoMap[packageInfo.packageName]))
            }
        }
        return filterableAppInfoList
    }
}
