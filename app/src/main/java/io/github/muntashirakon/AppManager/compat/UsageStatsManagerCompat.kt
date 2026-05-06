// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat

import android.annotation.UserIdInt
import android.app.usage.IUsageStatsManager
import android.app.usage.UsageEvents
import android.content.Context
import android.os.Build
import android.os.RemoteException
import android.os.UserHandleHidden
import androidx.annotation.RequiresPermission
import io.github.muntashirakon.AppManager.ipc.ProxyBinder
import io.github.muntashirakon.AppManager.self.SelfPermissions
import io.github.muntashirakon.AppManager.users.Users
import io.github.muntashirakon.AppManager.utils.ArrayUtils
import io.github.muntashirakon.AppManager.utils.BroadcastUtils
import io.github.muntashirakon.AppManager.utils.ContextUtils
import io.github.muntashirakon.AppManager.utils.ExUtils
import java.util.*

object UsageStatsManagerCompat {
    private const val SYS_USAGE_STATS_SERVICE = "usagestats"\nprivate val USAGE_STATS_SERVICE_NAME: String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
        Context.USAGE_STATS_SERVICE
    } else {
        SYS_USAGE_STATS_SERVICE
    }

    @JvmStatic
    @RequiresPermission("android.permission.PACKAGE_USAGE_STATS")
    fun queryEvents(beginTime: Long, endTime: Long, userId: Int): UsageEvents? {
        return try {
            val usm = usageStatsManager
            val callingPackage = SelfPermissions.getCallingPackage(Users.getSelfOrRemoteUid())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                usm.queryEventsForUser(beginTime, endTime, userId, callingPackage)
            } else {
                usm.queryEvents(beginTime, endTime, callingPackage)
            }
        } catch (e: RemoteException) {
            ExUtils.rethrowFromSystemServer(e)
        }
    }

    /**
     * Note: This method should only be used when sorted entries are required as the operations done
     * here are expensive.
     */
    @JvmStatic
    @RequiresPermission("android.permission.PACKAGE_USAGE_STATS")
    fun queryEventsSorted(beginTime: Long, endTime: Long, userId: Int, filterEvents: IntArray): List<UsageEvents.Event> {
        val filteredEvents: MutableList<UsageEvents.Event> = ArrayList()
        val events = queryEvents(beginTime, endTime, userId)
        if (events != null) {
            while (events.hasNextEvent()) {
                val event = UsageEvents.Event()
                events.getNextEvent(event)
                if (ArrayUtils.contains(filterEvents, event.eventType)) {
                    filteredEvents.add(event)
                }
            }
            filteredEvents.sortWith { o1, o2 -> -o1.timeStamp.compareTo(o2.timeStamp) }
        }
        return filteredEvents
    }

    @JvmStatic
    fun setAppInactive(packageName: String, @UserIdInt userId: Int, inactive: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                usageStatsManager.setAppInactive(packageName, inactive, userId)
                if (userId != UserHandleHidden.myUserId()) {
                    BroadcastUtils.sendPackageAltered(ContextUtils.getContext(), arrayOf(packageName))
                }
            } catch (e: RemoteException) {
                ExUtils.rethrowFromSystemServer(e)
            }
        }
    }

    @JvmStatic
    fun isAppInactive(packageName: String, @UserIdInt userId: Int): Boolean {
        val usm = usageStatsManager
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val callingPackage = SelfPermissions.getCallingPackage(Users.getSelfOrRemoteUid())
                usm.isAppInactive(packageName, userId, callingPackage)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                usm.isAppInactive(packageName, userId)
            } else {
                // Unsupported Android version: return false
                false
            }
        } catch (e: RemoteException) {
            ExUtils.rethrowFromSystemServer(e)
        }
    }

    @get:JvmStatic
    val usageStatsManager: IUsageStatsManager
        get() = IUsageStatsManager.Stub.asInterface(ProxyBinder.getService(USAGE_STATS_SERVICE_NAME))
}
