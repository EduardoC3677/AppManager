// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sysconfig

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SysConfigViewModel(application: Application) : AndroidViewModel(application) {
    private val _sysConfigInfoListLiveData = MutableLiveData<List<SysConfigInfo>>()
    val sysConfigInfoListLiveData: LiveData<List<SysConfigInfo>> = _sysConfigInfoListLiveData

    fun loadSysConfigInfo(@SysConfigType sysConfigType: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val sysConfigInfoList = SysConfigWrapper.getSysConfigs(sysConfigType)
            sysConfigInfoList.sortedBy { it.name.lowercase() }
            _sysConfigInfoListLiveData.postValue(sysConfigInfoList)
        }
    }
}
