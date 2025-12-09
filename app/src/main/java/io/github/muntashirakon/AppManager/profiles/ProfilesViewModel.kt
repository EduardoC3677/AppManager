// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles

import android.app.Application
import android.os.FileObserver
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import io.github.muntashirakon.AppManager.profiles.struct.BaseProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONException
import java.io.IOException

class ProfilesViewModel(application: Application) : AndroidViewModel(application) {
    private val _profilesLiveData = MutableLiveData<HashMap<BaseProfile, CharSequence>>()
    val profilesLiveData: LiveData<HashMap<BaseProfile, CharSequence>> = _profilesLiveData

    private var fileObserver: FileObserver? = null
    private val mutex = Mutex()

    override fun onCleared() {
        fileObserver?.stopWatching()
        super.onCleared()
    }

    fun loadProfiles() {
        viewModelScope.launch(Dispatchers.IO) {
            mutex.withLock {
                try {
                    val profiles = ProfileManager.getProfileSummaries(getApplication())
                    setUpObserverAndStart()
                    _profilesLiveData.postValue(profiles)
                } catch (e: IOException) {
                    e.printStackTrace()
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun setUpObserverAndStart() {
        if (fileObserver != null) {
            fileObserver?.startWatching()
            return
        }
        val profilePath = ProfileManager.getProfilesDir().getFile()
        if (profilePath == null || !profilePath.exists()) {
            // Do not set up observer yet
            return
        }
        val mask = FileObserver.CREATE or
                FileObserver.DELETE or
                FileObserver.DELETE_SELF or
                FileObserver.MOVED_TO or
                FileObserver.MODIFY or
                FileObserver.MOVED_FROM

        fileObserver = object : FileObserver(profilePath, mask) {
            override fun onEvent(event: Int, path: String?) {
                loadProfiles()
            }
        }
        fileObserver?.startWatching()
    }
}
