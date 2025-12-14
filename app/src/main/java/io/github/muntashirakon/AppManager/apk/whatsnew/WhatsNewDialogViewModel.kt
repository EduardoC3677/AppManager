// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.whatsnew

import android.app.Application
import android.content.pm.PackageInfo
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import io.github.muntashirakon.AppManager.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WhatsNewDialogViewModel(application: Application) : AndroidViewModel(application) {
    private val _changesLiveData = MutableLiveData<List<ApkWhatsNewFinder.Change>>()
    val changesLiveData: LiveData<List<ApkWhatsNewFinder.Change>> = _changesLiveData

    fun loadChanges(newPkgInfo: PackageInfo, oldPkgInfo: PackageInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            val changes = ApkWhatsNewFinder.getInstance().getWhatsNew(
                getApplication(),
                newPkgInfo,
                oldPkgInfo
            )
            val changeList = ArrayList<ApkWhatsNewFinder.Change>()
            for (changes1 in changes) {
                if (changes1.isNotEmpty()) {
                    changeList.addAll(changes1)
                }
            }
            if (changeList.isEmpty()) {
                val app = getApplication<Application>()
                changeList.add(
                    ApkWhatsNewFinder.Change(
                        ApkWhatsNewFinder.CHANGE_INFO,
                        app.getString(R.string.no_changes)
                    )
                )
            }
            _changesLiveData.postValue(changeList)
        }
    }
}
