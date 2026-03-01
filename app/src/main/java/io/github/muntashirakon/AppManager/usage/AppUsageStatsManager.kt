// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.usage

import android.Manifest
import android.annotation.SuppressLint
import android.annotation.UserIdInt
import android.app.usage.UsageEvents
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.NetworkCapabilities.TRANSPORT_CELLULAR
import android.net.NetworkCapabilities.TRANSPORT_WIFI
import android.net.NetworkStats
import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import android.os.RemoteException
import android.telephony.SubscriptionInfo
import android.telephony.TelephonyManager
import androidx.annotation.IntDef
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.collection.SparseArrayCompat
import androidx.core.util.Pair
import io.github.muntashirakon.AppManager.compat.*
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.self.SelfPermissions
import io.github.muntashirakon.AppManager.utils.ContextUtils
import io.github.muntashirakon.AppManager.utils.ExUtils
import io.github.muntashirakon.AppManager.utils.Utils
import io.github.muntashirakon.proc.ProcFs
import java.util.*

class AppUsageStatsManager private constructor() {
    private val mContext: Context = ContextUtils.getContext()

    @Retention(AnnotationRetention.SOURCE)
    @IntDef(TRANSPORT_CELLULAR, TRANSPORT_WIFI)
    annotation class Transport

    class DataUsage : Pair<Long, Long>, Parcelable, Comparable<DataUsage> {
        val total: Long

        constructor(tx: Long, rx: Long) : super(tx, rx) {
            total = tx + rx
        }

        private constructor(`in`: Parcel) : super(`in`.readLong(), `in`.readLong()) {
            total = first!! + second!!
        }

        fun getTx(): Long = first!!
        fun getRx(): Long = second!!

        override fun describeContents(): Int = 0

        override fun writeToParcel(dest: Parcel, flags: Int) {
            dest.writeLong(first!!)
            dest.writeLong(second!!)
        }

        override fun compareTo(other: DataUsage): Int {
            return total.compareTo(other.total)
        }

        companion object {
            @JvmField
            val EMPTY = DataUsage(0, 0)

            @JvmStatic
            fun fromDataUsage(vararg dataUsages: DataUsage?): DataUsage {
                var tx = 0L
                var rx = 0L
                for (dataUsage in dataUsages) {
                    if (dataUsage != null) {
                        tx += dataUsage.getTx()
                        rx += dataUsage.getRx()
                    }
                }
                return DataUsage(tx, rx)
            }

            @JvmField
            val CREATOR: Parcelable.Creator<DataUsage> = object : Parcelable.Creator<DataUsage> {
                override fun createFromParcel(`in`: Parcel): DataUsage = DataUsage(`in`)
                override fun newArray(size: Int): Array<DataUsage?> = arrayOfNulls(size)
            }
        }
    }

    @RequiresPermission("android.permission.PACKAGE_USAGE_STATS")
    @Throws(RemoteException::class, SecurityException::class)
    fun getUsageStats(interval: TimeInterval, @UserIdInt userId: Int): List<PackageUsageInfo> {
        val packageUsageInfoList = mutableListOf<PackageUsageInfo>()
        var tries = 5
        var re: Throwable? = null
        do {
            try {
                packageUsageInfoList.addAll(getUsageStatsInternal(interval, userId))
                re = null
            } catch (e: Throwable) {
                re = e
            }
        } while (--tries != 0 && packageUsageInfoList.isEmpty())
        if (re != null) {
            throw RemoteException(re.message).apply { initCause(re) }
        }
        return packageUsageInfoList
    }

    @RequiresPermission("android.permission.PACKAGE_USAGE_STATS")
    @Throws(RemoteException::class, PackageManager.NameNotFoundException::class)
    fun getUsageStatsForPackage(packageName: String, range: TimeInterval, @UserIdInt userId: Int): PackageUsageInfo {
        val applicationInfo = PackageManagerCompat.getApplicationInfo(packageName,
            PackageManagerCompat.MATCH_UNINSTALLED_PACKAGES or PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, userId)
        val packageUsageInfo = PackageUsageInfo(mContext, packageName, userId, applicationInfo)
        val usage = PerPackageUsageInternal(packageName)
        val events = UsageStatsManagerCompat.queryEventsSorted(range.startTime, range.endTime, userId, USUAL_ACTIVITY_EVENTS)
        var lastShutdownTime = 0L
        for (event in events) {
            val eventType = event.eventType
            if (eventType == UsageEvents.Event.DEVICE_SHUTDOWN) {
                lastShutdownTime = event.timeStamp
            } else if (packageName == event.packageName) {
                if (isActivityClosed(eventType)) {
                    usage.setLastEndTime(event.timeStamp)
                } else if (isActivityOpened(eventType)) {
                    if (lastShutdownTime != 0L) {
                        usage.setLastEndTime(lastShutdownTime)
                    }
                    usage.setLastStartTime(event.timeStamp)
                }
            }
        }
        packageUsageInfo.entries = usage.entries
        return packageUsageInfo
    }

    private class PerPackageUsageInternal(val packageName: String) {
        val entries = Stack<PackageUsageInfo.Entry>()
        var screenTime: Long = 0
        var lastUsed: Long = 0
        var accessCount: Int = 0

        private var mLastStartTime: Long = 0
        private var mLastEndTime: Long = 0
        private var mOverrideLastEntry: Boolean = false

        fun setLastStartTime(startTime: Long) {
            if (mLastEndTime == 0L) return
            mLastStartTime = startTime
            if (mOverrideLastEntry) {
                mOverrideLastEntry = false
                val entry = entries.pop()
                entries.push(PackageUsageInfo.Entry(mLastStartTime, entry.endTime))
                screenTime -= entry.duration
            } else {
                entries.push(PackageUsageInfo.Entry(mLastStartTime, mLastEndTime))
            }
            screenTime += entries.peek().duration
            mLastEndTime = 0
        }

        fun setLastEndTime(endTime: Long) {
            if (mLastEndTime != 0L) return
            mLastEndTime = endTime
            if (mLastStartTime > 0 && (mLastStartTime - mLastEndTime) <= 500) {
                mOverrideLastEntry = true
            } else accessCount++
            if (lastUsed == 0L) {
                lastUsed = endTime
            }
        }
    }

    private fun getUsageStatsInternal(interval: TimeInterval, @UserIdInt userId: Int): List<PackageUsageInfo> {
        val screenTimeList = mutableListOf<PackageUsageInfo>()
        val perPackageUsageMap = mutableMapOf<String, PerPackageUsageInternal>()
        val events = UsageStatsManagerCompat.queryEventsSorted(interval.startTime, interval.endTime, userId, USUAL_ACTIVITY_EVENTS)
        var lastShutdownTime = 0L
        for (event in events) {
            val eventType = event.eventType
            val packageName = event.packageName
            if (packageName == null) {
                Log.i(TAG, "Ignored event with empty package name: ${Utils.prettyPrintObject(event)}")
                continue
            }
            if (eventType == UsageEvents.Event.DEVICE_SHUTDOWN) {
                lastShutdownTime = event.timeStamp
            } else if (isActivityClosed(eventType)) {
                val usage = perPackageUsageMap.getOrPut(packageName) { PerPackageUsageInternal(packageName) }
                usage.setLastEndTime(event.timeStamp)
            } else if (isActivityOpened(eventType)) {
                val usage = perPackageUsageMap.getOrPut(packageName) { PerPackageUsageInternal(packageName) }
                if (lastShutdownTime != 0L) {
                    usage.setLastEndTime(lastShutdownTime)
                }
                usage.setLastStartTime(event.timeStamp)
            }
        }
        val mobileData = getMobileData(interval)
        val wifiData = getWifiData(interval)
        for (usage in perPackageUsageMap.values) {
            val applicationInfo = ExUtils.exceptionAsNull {
                PackageManagerCompat.getApplicationInfo(usage.packageName,
                    PackageManagerCompat.MATCH_UNINSTALLED_PACKAGES or PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, userId)
            }
            val packageUsageInfo = PackageUsageInfo(mContext, usage.packageName, userId, applicationInfo)
            packageUsageInfo.timesOpened = usage.accessCount
            packageUsageInfo.lastUsageTime = usage.lastUsed
            packageUsageInfo.screenTime = usage.screenTime
            val uid = applicationInfo?.uid ?: 0
            packageUsageInfo.mobileData = mobileData[uid] ?: DataUsage.EMPTY
            packageUsageInfo.wifiData = wifiData[uid] ?: DataUsage.EMPTY
            packageUsageInfo.entries = usage.entries
            screenTimeList.add(packageUsageInfo)
        }
        return screenTimeList
    }

    companion object {
        val TAG: String = AppUsageStatsManager::class.java.simpleName

        @JvmStatic
        fun requireReadPhoneStatePermission(): Boolean {
            if (Build.VERSION.SDK_INT in Build.VERSION_CODES.M until Build.VERSION_CODES.P) {
                return !SelfPermissions.checkSelfOrRemotePermission(Manifest.permission.READ_PHONE_STATE)
            }
            return false
        }

        private var appUsageStatsManager: AppUsageStatsManager? = null

        @JvmStatic
        fun getInstance(): AppUsageStatsManager {
            if (appUsageStatsManager == null) appUsageStatsManager = AppUsageStatsManager()
            return appUsageStatsManager!!
        }

        private val USUAL_ACTIVITY_EVENTS = intArrayOf(
            UsageEvents.Event.ACTIVITY_RESUMED,
            UsageEvents.Event.ACTIVITY_PAUSED,
            UsageEvents.Event.ACTIVITY_STOPPED,
            UsageEvents.Event.DEVICE_SHUTDOWN
        )

        private fun isActivityClosed(eventType: Int): Boolean {
            return eventType == UsageEvents.Event.ACTIVITY_STOPPED || eventType == UsageEvents.Event.ACTIVITY_PAUSED
        }

        private fun isActivityOpened(eventType: Int): Boolean {
            return eventType == UsageEvents.Event.ACTIVITY_RESUMED
        }

        @JvmStatic
        @RequiresPermission("android.permission.PACKAGE_USAGE_STATS")
        fun getLastActivityTime(packageName: String, interval: TimeInterval): Long {
            val events = UsageStatsManagerCompat.queryEvents(interval.startTime, interval.endTime, UserHandleHidden.myUserId())
                ?: return 0L
            val event = UsageEvents.Event()
            var lastTime = 0L
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.packageName == packageName && lastTime < event.timeStamp) {
                    lastTime = event.timeStamp
                }
            }
            return lastTime
        }

        @JvmStatic
        @RequiresPermission("android.permission.PACKAGE_USAGE_STATS")
        fun getMobileData(interval: TimeInterval): SparseArrayCompat<DataUsage> {
            return getDataUsageForNetwork(TRANSPORT_CELLULAR, interval)
        }

        @JvmStatic
        @RequiresPermission("android.permission.PACKAGE_USAGE_STATS")
        fun getWifiData(interval: TimeInterval): SparseArrayCompat<DataUsage> {
            return getDataUsageForNetwork(TRANSPORT_WIFI, interval)
        }

        @JvmStatic
        @RequiresPermission("android.permission.PACKAGE_USAGE_STATS")
        fun getDataUsageForNetwork(@Transport networkType: Int, interval: TimeInterval): SparseArrayCompat<DataUsage> {
            val dataUsageSparseArray = SparseArrayCompat<DataUsage>()
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                val netStats = ProcFs.getInstance().allUidNetStat
                for (netStat in netStats) {
                    dataUsageSparseArray.put(netStat.uid, DataUsage(netStat.txBytes, netStat.rxBytes))
                }
                return dataUsageSparseArray
            }
            val subscriberIds = getSubscriberIds(networkType)
            for (subscriberId in subscriberIds) {
                try {
                    NetworkStatsManagerCompat.querySummary(networkType, subscriberId, interval.startTime, interval.endTime).use { networkStats ->
                        while (networkStats.hasNextEntry()) {
                            val entry = networkStats.getNextEntry(true) ?: continue
                            val dataUsage = dataUsageSparseArray[entry.uid]?.let {
                                DataUsage(entry.txBytes + it.getTx(), entry.rxBytes + it.getRx())
                            } ?: DataUsage(entry.txBytes, entry.rxBytes)
                            dataUsageSparseArray.put(entry.uid, dataUsage)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            return dataUsageSparseArray
        }

        @JvmStatic
        @RequiresPermission("android.permission.PACKAGE_USAGE_STATS")
        fun getDataUsageForPackage(uid: Int, range: TimeInterval): DataUsage {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                val netStat = ProcFs.getInstance().getUidNetStat(uid)
                return netStat?.let { DataUsage(it.txBytes, it.rxBytes) } ?: DataUsage.EMPTY
            }
            var totalTx = 0L
            var totalRx = 0L
            for (networkId in 0 until 2) {
                val subscriberIds = getSubscriberIds(networkId)
                for (subscriberId in subscriberIds) {
                    try {
                        NetworkStatsManagerCompat.querySummary(networkId, subscriberId, range.startTime, range.endTime).use { networkStats ->
                            while (networkStats.hasNextEntry()) {
                                val entry = networkStats.getNextEntry(true)
                                if (entry != null && entry.uid == uid) {
                                    totalTx += entry.txBytes
                                    totalRx += entry.rxBytes
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            return DataUsage(totalTx, totalRx)
        }

        @SuppressLint("HardwareIds", "MissingPermission")
        @RequiresApi(Build.VERSION_CODES.M)
        private fun getSubscriberIds(@Transport networkType: Int): List<String?> {
            if (networkType != TRANSPORT_CELLULAR) return listOf(null)
            val ctx = ContextUtils.getContext()
            val pm = ctx.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION)) {
                return emptyList()
            } else if (!pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
                return emptyList()
            }
            val telephonyManager = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            if (telephonyManager.phoneType == TelephonyManager.PHONE_TYPE_NONE) return emptyList()
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && !SelfPermissions.checkSelfOrRemotePermission(Manifest.permission.READ_PHONE_STATE)) {
                return emptyList()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.READ_PRIVILEGED_PHONE_STATE)) {
                return listOf(null)
            }
            val subscriptionInfoList = SubscriptionManagerCompat.getActiveSubscriptionInfoList() ?: return listOf(null)
            val subscriberIds = mutableListOf<String?>()
            for (info in subscriptionInfoList) {
                try {
                    subscriberIds.add(SubscriptionManagerCompat.getSubscriberIdForSubscriber(info.subscriptionId))
                } catch (ignore: SecurityException) {}
            }
            return if (subscriberIds.isEmpty()) listOf(null) else subscriberIds
        }
    }
}
