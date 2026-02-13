// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings

import android.app.Application
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import io.github.muntashirakon.AppManager.BuildConfig
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.self.Migrations
import io.github.muntashirakon.AppManager.utils.AppPref
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SecurityAndOpsViewModel(application: Application) : AndroidViewModel(application),
    Ops.AdbConnectionInterface {

    var isAuthenticating = false

    private val _authenticationStatus = MutableLiveData<Int>()
    @get:JvmName("authenticationStatus")
    val authenticationStatus: LiveData<Int> = _authenticationStatus

    fun setModeOfOps() {
        viewModelScope.launch(Dispatchers.IO) {
            // Migration
            val thisVersion = BuildConfig.VERSION_CODE.toLong()
            val lastVersion = AppPref.getLong(AppPref.PrefKey.PREF_LAST_VERSION_CODE_LONG)
            if (lastVersion == 0L) {
                // First version: set this as the last version
                AppPref.set(AppPref.PrefKey.PREF_LAST_VERSION_CODE_LONG, BuildConfig.VERSION_CODE.toLong())
                AppPref.set(AppPref.PrefKey.PREF_DISPLAY_CHANGELOG_LAST_VERSION_LONG, BuildConfig.VERSION_CODE.toLong())
            }
            if (lastVersion < thisVersion) {
                Log.d(TAG, "Start migration")
                // App is updated
                AppPref.set(AppPref.PrefKey.PREF_DISPLAY_CHANGELOG_BOOL, true)
                Migrations.startMigration(lastVersion)
                // Migration is done: set this as the last version
                AppPref.set(AppPref.PrefKey.PREF_LAST_VERSION_CODE_LONG, BuildConfig.VERSION_CODE.toLong())
                Log.d(TAG, "End migration")
            }
            // Ops
            Log.d(TAG, "Before Ops::init")
            val status = Ops.init(getApplication(), false)
            Log.d(TAG, "After Ops::init")
            _authenticationStatus.postValue(status)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun autoConnectWirelessDebugging() {
        viewModelScope.launch(Dispatchers.IO) {
            Log.d(TAG, "Before Ops::autoConnectWirelessDebugging")
            val status = Ops.autoConnectWirelessDebugging(getApplication())
            Log.d(TAG, "After Ops::autoConnectWirelessDebugging")
            _authenticationStatus.postValue(status)
        }
    }

    override fun connectAdb(port: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            Log.d(TAG, "Before Ops::connectAdb")
            val status = Ops.connectAdb(getApplication(), port, Ops.STATUS_FAILURE)
            Log.d(TAG, "After Ops::connectAdb")
            _authenticationStatus.postValue(status)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun pairAdb() {
        viewModelScope.launch(Dispatchers.IO) {
            Log.d(TAG, "Before Ops::pairAdb")
            val status = Ops.pairAdb(getApplication())
            Log.d(TAG, "After Ops::pairAdb")
            _authenticationStatus.postValue(status)
        }
    }

    override fun onStatusReceived(status: Int) {
        _authenticationStatus.postValue(status)
    }

    companion object {
        const val TAG = "SecurityAndOpsViewModel"
    }
}
