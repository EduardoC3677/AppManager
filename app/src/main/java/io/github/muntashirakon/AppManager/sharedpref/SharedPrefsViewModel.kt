// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sharedpref

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import io.github.muntashirakon.io.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException

class SharedPrefsViewModel(application: Application) : AndroidViewModel(application) {
    private val _sharedPrefsMapLiveData = MutableLiveData<Map<String, Any>>()
    val sharedPrefsMapLiveData: LiveData<Map<String, Any>> = _sharedPrefsMapLiveData

    private val _sharedPrefsSavedLiveData = MutableLiveData<Boolean>()
    val sharedPrefsSavedLiveData: LiveData<Boolean> = _sharedPrefsSavedLiveData

    private val _sharedPrefsDeletedLiveData = MutableLiveData<Boolean>()
    val sharedPrefsDeletedLiveData: LiveData<Boolean> = _sharedPrefsDeletedLiveData

    private val _sharedPrefsModifiedLiveData = MutableLiveData<Boolean>()
    val sharedPrefsModifiedLiveData: LiveData<Boolean> = _sharedPrefsModifiedLiveData

    // TODO: 8/2/22 Use AtomicExtendedFile to better handle errors
    private var sharedPrefsFile: Path? = null
    private var sharedPrefsMap: MutableMap<String, Any> = HashMap()
    var isModified = false
        private set

    fun setSharedPrefsFile(sharedPrefFile: Path) {
        sharedPrefsFile = sharedPrefFile
    }

    fun getSharedPrefFilename(): String? = sharedPrefsFile?.getName()

    fun getValue(key: String): Any? = sharedPrefsMap[key]

    fun remove(key: String) {
        isModified = true
        _sharedPrefsModifiedLiveData.postValue(isModified)
        sharedPrefsMap.remove(key)
        _sharedPrefsMapLiveData.postValue(sharedPrefsMap)
    }

    fun add(key: String, value: Any) {
        isModified = true
        _sharedPrefsModifiedLiveData.postValue(isModified)
        sharedPrefsMap[key] = value
        _sharedPrefsMapLiveData.postValue(sharedPrefsMap)
    }

    fun deleteSharedPrefFile() {
        viewModelScope.launch(Dispatchers.IO) {
            _sharedPrefsDeletedLiveData.postValue(sharedPrefsFile?.delete() ?: false)
        }
    }

    fun writeSharedPrefs() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                sharedPrefsFile?.openOutputStream()?.use { xmlFile ->
                    SharedPrefsUtil.writeSharedPref(xmlFile, sharedPrefsMap)
                    // TODO: 9/7/21 Investigate the state of permission (should be unchanged)
                    _sharedPrefsSavedLiveData.postValue(true)
                    isModified = false
                    _sharedPrefsModifiedLiveData.postValue(isModified)
                }
            } catch (e: IOException) {
                e.printStackTrace()
                _sharedPrefsSavedLiveData.postValue(false)
            }
        }
    }

    fun loadSharedPrefs() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                sharedPrefsFile?.openInputStream()?.use { rulesStream ->
                    isModified = false
                    _sharedPrefsModifiedLiveData.postValue(isModified)
                    sharedPrefsMap = SharedPrefsUtil.readSharedPref(rulesStream).toMutableMap()
                    _sharedPrefsMapLiveData.postValue(sharedPrefsMap)
                }
            } catch (e: IOException) {
                e.printStackTrace()
                sharedPrefsMap = HashMap()
                _sharedPrefsMapLiveData.postValue(sharedPrefsMap)
            } catch (e: XmlPullParserException) {
                e.printStackTrace()
                sharedPrefsMap = HashMap()
                _sharedPrefsMapLiveData.postValue(sharedPrefsMap)
            }
        }
    }
}
