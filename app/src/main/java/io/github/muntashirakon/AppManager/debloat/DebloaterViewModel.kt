// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.debloat

import android.app.Application
import android.os.UserHandleHidden
import android.text.TextUtils
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import io.github.muntashirakon.AppManager.StaticDataset
import io.github.muntashirakon.AppManager.misc.AdvancedSearchView
import io.github.muntashirakon.AppManager.types.UserPackagePair
import io.github.muntashirakon.AppManager.users.Users
import io.github.muntashirakon.AppManager.utils.AppPref
import io.github.muntashirakon.AppManager.utils.ArrayUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

class DebloaterViewModel(application: Application) : AndroidViewModel(application) {
    @DebloaterListOptions.Filter
    private var filterFlags: Int = AppPref.getInt(AppPref.PrefKey.PREF_DEBLOATER_FILTER_FLAGS_INT)

    private var queryString: String? = null

    @AdvancedSearchView.SearchType
    private var queryType: Int = 0

    private val debloatObjects = ArrayList<DebloatObject>()
    private val selectedPackages = HashMap<String, IntArray>()

    private val _debloatObjectListLiveData = MutableLiveData<List<DebloatObject>>()
    val debloatObjectListLiveData: LiveData<List<DebloatObject>> = _debloatObjectListLiveData

    fun hasFilterFlag(@DebloaterListOptions.Filter flag: Int): Boolean {
        return (filterFlags and flag) != 0
    }

    fun addFilterFlag(@DebloaterListOptions.Filter flag: Int) {
        filterFlags = filterFlags or flag
        AppPref.set(AppPref.PrefKey.PREF_DEBLOATER_FILTER_FLAGS_INT, filterFlags)
        loadPackages()
    }

    fun removeFilterFlag(@DebloaterListOptions.Filter flag: Int) {
        filterFlags = filterFlags and flag.inv()
        AppPref.set(AppPref.PrefKey.PREF_DEBLOATER_FILTER_FLAGS_INT, filterFlags)
        loadPackages()
    }

    fun setQuery(queryString: String?, @AdvancedSearchView.SearchType searchType: Int) {
        this.queryString = queryString
        this.queryType = searchType
        loadPackages()
    }

    fun getTotalItemCount(): Int = debloatObjects.size

    fun getSelectedItemCount(): Int = selectedPackages.size

    fun select(debloatObject: DebloatObject) {
        selectedPackages[debloatObject.packageName] = debloatObject.users ?: IntArray(0)
    }

    fun deselect(debloatObject: DebloatObject) {
        selectedPackages.remove(debloatObject.packageName)
    }

    fun deselectAll() {
        selectedPackages.clear()
    }

    fun isSelected(debloatObject: DebloatObject): Boolean {
        return selectedPackages.containsKey(debloatObject.packageName)
    }

    fun getSelectedPackages(): Map<String, IntArray> = selectedPackages

    fun getSelectedPackagesWithUsers(): ArrayList<UserPackagePair> {
        val userPackagePairs = ArrayList<UserPackagePair>()
        val myUserId = UserHandleHidden.myUserId()
        val userIds = Users.getUsersIds()
        for ((packageName, userHandles) in selectedPackages) {
            if (userHandles.isEmpty()) {
                // Assign current user in it
                userPackagePairs.add(UserPackagePair(packageName, myUserId))
            } else {
                for (userHandle in userHandles) {
                    if (!ArrayUtils.contains(userIds, userHandle)) continue
                    userPackagePairs.add(UserPackagePair(packageName, userHandle))
                }
            }
        }
        return userPackagePairs
    }

    fun loadPackages() {
        viewModelScope.launch(Dispatchers.IO) {
            loadDebloatObjects()
            val filtered = if (filterFlags != DebloaterListOptions.FILTER_NO_FILTER) {
                debloatObjects.filter { debloatObject ->
                    // List filters
                    if ((filterFlags and DebloaterListOptions.FILTER_LIST_AOSP) == 0 && debloatObject.type == "aosp") {
                        return@filter false
                    }
                    if ((filterFlags and DebloaterListOptions.FILTER_LIST_CARRIER) == 0 && debloatObject.type == "carrier") {
                        return@filter false
                    }
                    if ((filterFlags and DebloaterListOptions.FILTER_LIST_GOOGLE) == 0 && debloatObject.type == "google") {
                        return@filter false
                    }
                    if ((filterFlags and DebloaterListOptions.FILTER_LIST_MISC) == 0 && debloatObject.type == "misc") {
                        return@filter false
                    }
                    if ((filterFlags and DebloaterListOptions.FILTER_LIST_OEM) == 0 && debloatObject.type == "oem") {
                        return@filter false
                    }
                    // Removal filters
                    val removalType = debloatObject.getRemoval()
                    if ((filterFlags and DebloaterListOptions.FILTER_REMOVAL_SAFE) == 0 && removalType == DebloatObject.REMOVAL_SAFE) {
                        return@filter false
                    }
                    if ((filterFlags and DebloaterListOptions.FILTER_REMOVAL_REPLACE) == 0 && removalType == DebloatObject.REMOVAL_REPLACE) {
                        return@filter false
                    }
                    if ((filterFlags and DebloaterListOptions.FILTER_REMOVAL_CAUTION) == 0 && removalType == DebloatObject.REMOVAL_CAUTION) {
                        return@filter false
                    }
                    if ((filterFlags and DebloaterListOptions.FILTER_REMOVAL_UNSAFE) == 0 && removalType == DebloatObject.REMOVAL_UNSAFE) {
                        return@filter false
                    }
                    // Other filters
                    if ((filterFlags and DebloaterListOptions.FILTER_INSTALLED_APPS) != 0 && !debloatObject.isInstalled()) {
                        return@filter false
                    }
                    if ((filterFlags and DebloaterListOptions.FILTER_UNINSTALLED_APPS) != 0 && debloatObject.isInstalled()) {
                        return@filter false
                    }
                    if ((filterFlags and DebloaterListOptions.FILTER_USER_APPS) != 0 && !debloatObject.isUserApp()) {
                        return@filter false
                    }
                    if ((filterFlags and DebloaterListOptions.FILTER_SYSTEM_APPS) != 0 && !debloatObject.isSystemApp()) {
                        return@filter false
                    }
                    if ((filterFlags and DebloaterListOptions.FILTER_FROZEN_APPS) != 0 && !debloatObject.isFrozen()) {
                        return@filter false
                    }
                    if ((filterFlags and DebloaterListOptions.FILTER_UNFROZEN_APPS) != 0 && debloatObject.isFrozen()) {
                        return@filter false
                    }
                    true
                }
            } else {
                debloatObjects
            }

            if (TextUtils.isEmpty(queryString)) {
                _debloatObjectListLiveData.postValue(filtered)
                return@launch
            }

            // Apply searching
            val searchResult = AdvancedSearchView.matches(
                queryString,
                filtered,
                AdvancedSearchView.ChoicesGenerator { item ->
                    val label = item.getLabel()
                    if (label != null) {
                        listOf(item.packageName, label.toString().lowercase(Locale.getDefault()))
                    } else {
                        listOf(item.packageName)
                    }
                },
                queryType
            )
            _debloatObjectListLiveData.postValue(searchResult)
        }
    }

    private fun loadDebloatObjects() {
        if (debloatObjects.isNotEmpty()) {
            return
        }
        debloatObjects.addAll(StaticDataset.getDebloatObjectsWithInstalledInfo(getApplication()))
        debloatObjects.sortWith { o1, o2 ->
            CharSequence.compare(o1.getLabelOrPackageName(), o2.getLabelOrPackageName())
        }
    }
}
