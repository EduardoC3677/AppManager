// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.usage

import android.app.Application
import androidx.annotation.AnyThread
import androidx.core.util.Pair
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.github.muntashirakon.AppManager.users.Users
import io.github.muntashirakon.AppManager.utils.ExUtils
import io.github.muntashirakon.AppManager.utils.ThreadUtils
import java.text.Collator
import java.util.*

class AppUsageViewModel(application: Application) : AndroidViewModel(application) {
    private val mPackageUsageInfoListLiveData = MutableLiveData<List<PackageUsageInfo>>()
    private val mPackageUsageInfoLiveData = MutableLiveData<PackageUsageInfo>()
    private val mPackageUsageInfoList: MutableList<PackageUsageInfo> = Collections.synchronizedList(ArrayList())
    private val mPackageUsageEntries: MutableList<PackageUsageInfo.Entry> = Collections.synchronizedList(ArrayList())

    var totalScreenTime: Long = 0
        private set
    var hasMultipleUsers: Boolean = false
        private set
    @get:IntervalType
    @set:IntervalType
    var currentInterval: Int = IntervalType.INTERVAL_DAILY
        set(value) {
            field = value
            currentDate = System.currentTimeMillis()
            loadPackageUsageInfoList()
        }
    var currentDate: Long = System.currentTimeMillis()
        set(value) {
            field = value
            loadPackageUsageInfoList()
        }
    var sortOrder: Int = SortOrder.SORT_BY_SCREEN_TIME
        set(value) {
            field = value
            ThreadUtils.postOnBackgroundThread { sortItems() }
        }

    fun getPackageUsageInfoList(): LiveData<List<PackageUsageInfo>> = mPackageUsageInfoListLiveData
    fun getPackageUsageInfo(): LiveData<PackageUsageInfo> = mPackageUsageInfoLiveData
    fun getPackageUsageEntries(): List<PackageUsageInfo.Entry> = mPackageUsageEntries

    fun loadNext() {
        currentDate = UsageUtils.getNextDateFromInterval(currentInterval, currentDate)
    }

    fun loadPrevious() {
        currentDate = UsageUtils.getPreviousDateFromInterval(currentInterval, currentDate)
    }

    fun loadPackageUsageInfo(usageInfo: PackageUsageInfo) {
        if (ThreadUtils.isMainThread()) {
            mPackageUsageInfoLiveData.value = usageInfo
        } else {
            mPackageUsageInfoLiveData.postValue(usageInfo)
        }
    }

    @AnyThread
    fun loadPackageUsageInfoList() {
        ThreadUtils.postOnBackgroundThread {
            val userIds = Users.getUsersIds()
            val usageStatsManager = AppUsageStatsManager.getInstance()
            val interval = UsageUtils.getTimeInterval(currentInterval, currentDate)
            mPackageUsageInfoList.clear()
            for (userId in userIds) {
                ExUtils.exceptionAsIgnored { mPackageUsageInfoList.addAll(usageStatsManager.getUsageStats(interval, userId)) }
            }
            totalScreenTime = 0
            val users = HashSet<Int>(3)
            mPackageUsageEntries.clear()
            for (appItem in mPackageUsageInfoList) {
                appItem.entries?.let { mPackageUsageEntries.addAll(it) }
                totalScreenTime += appItem.screenTime
                users.add(appItem.userId)
            }
            hasMultipleUsers = users.size > 1
            sortItems()
        }
    }

    private fun sortItems() {
        val collator = Collator.getInstance()
        mPackageUsageInfoList.sortWith { o1, o2 ->
            when (sortOrder) {
                SortOrder.SORT_BY_APP_LABEL -> collator.compare(o1.appLabel, o2.appLabel)
                SortOrder.SORT_BY_LAST_USED -> (-o1.lastUsageTime.compareTo(o2.lastUsageTime))
                SortOrder.SORT_BY_MOBILE_DATA -> {
                    if (o1.mobileData == null) return@sortWith if (o2.mobileData == null) 0 else -1
                    -o1.mobileData!!.compareTo(o2.mobileData!!)
                }
                SortOrder.SORT_BY_PACKAGE_NAME -> o1.packageName.compareTo(o2.packageName, ignoreCase = true)
                SortOrder.SORT_BY_SCREEN_TIME -> (-o1.screenTime.compareTo(o2.screenTime))
                SortOrder.SORT_BY_TIMES_OPENED -> (-o1.timesOpened.compareTo(o2.timesOpened))
                SortOrder.SORT_BY_WIFI_DATA -> {
                    if (o1.wifiData == null) return@sortWith if (o2.wifiData == null) 0 else -1
                    -o1.wifiData!!.compareTo(o2.wifiData!!)
                }
                else -> 0
            }
        }
        mPackageUsageInfoListLiveData.postValue(mPackageUsageInfoList)
    }
}
