// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters

import android.app.Application
import androidx.annotation.WorkerThread
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import io.github.muntashirakon.AppManager.compat.UserHandleHidden
import io.github.muntashirakon.AppManager.utils.ThreadUtils
import java.util.concurrent.Future

class FinderViewModel(application: Application) : AndroidViewModel(application) {
    val lastUpdateTimeLiveData = MutableLiveData<Long>()
    val filteredAppListLiveData = MutableLiveData<List<FilterItem.FilteredItemInfo<FilterableAppInfo>>>()
    private var mAppListLoaderFuture: Future<*>? = null
    private var mFilterableAppInfoList: List<FilterableAppInfo>? = null
    val filterItem = FilterItem()

    fun loadFilteredAppList(refresh: Boolean) {
        mAppListLoaderFuture?.cancel(true)
        mAppListLoaderFuture = ThreadUtils.postOnBackgroundThread {
            lastUpdateTimeLiveData.postValue(-1L)
            if (mFilterableAppInfoList == null || refresh) {
                loadAppList()
            }
            if (ThreadUtils.isInterrupted() || mFilterableAppInfoList == null) return@postOnBackgroundThread
            filteredAppListLiveData.postValue(filterItem.getFilteredList(mFilterableAppInfoList!!))
            lastUpdateTimeLiveData.postValue(System.currentTimeMillis())
        }
    }

    @WorkerThread
    private fun loadAppList() {
        val userIds = intArrayOf(UserHandleHidden.myUserId())
        mFilterableAppInfoList = FilteringUtils.loadFilterableAppInfo(userIds)
    }

    companion object {
        val TAG: String = FinderViewModel::class.java.simpleName
    }
}
