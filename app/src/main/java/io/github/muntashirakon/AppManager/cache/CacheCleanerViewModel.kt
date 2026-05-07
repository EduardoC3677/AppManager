// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.cache

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat
import io.github.muntashirakon.AppManager.users.Users
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * ViewModel for Cache Cleaner
 * Handles cache size calculation and data management
 */
@HiltViewModel
class CacheCleanerViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {
    
    private val _cacheData = MutableStateFlow<CacheData>(CacheData(emptyList(), 0L, 0))
    val cacheData: StateFlow<CacheData> = _cacheData.asStateFlow()
    
    private val _cleaningStatus = MutableStateFlow<CleaningStatus>(CleaningStatus.Idle)
    val cleaningStatus: StateFlow<CleaningStatus> = _cleaningStatus.asStateFlow()
    
    private val mPackageManager = application.packageManager
    private val mUserId = Users.myUserId()
    
    fun loadCacheData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val installedPackages = PackageManagerCompat.getInstalledPackages(
                    PackageManagerCompat.MATCH_UNINSTALLED_PACKAGES,
                    mUserId
                )
                
                val appCacheList = mutableListOf<AppCacheInfo>()
                var totalCacheSize = 0L
                
                for (pkg in installedPackages) {
                    try {
                        val cacheSize = getPackageCacheSize(pkg.packageName)
                        if (cacheSize > 0) {
                            val appName = pkg.applicationInfo?.loadLabel(mPackageManager)?.toString() 
                                ?: pkg.packageName
                            
                            appCacheList.add(
                                AppCacheInfo(
                                    packageName = pkg.packageName,
                                    appName = appName,
                                    cacheSize = cacheSize,
                                    userId = mUserId
                                )
                            )
                            totalCacheSize += cacheSize
                        }
                    } catch (e: Exception) {
                    }
                }
                
                appCacheList.sortByDescending { it.cacheSize }
                
                _cacheData.value = CacheData(
                    apps = appCacheList,
                    totalCacheSize = totalCacheSize,
                    appCount = appCacheList.size
                )
            } catch (e: Exception) {
                _cacheData.value = CacheData(emptyList(), 0L, 0)
            }
        }
    }
    
    private fun getPackageCacheSize(packageName: String): Long {
        return try {
            val cacheDir = File("/data/data/$packageName/cache")
            if (cacheDir.exists() && cacheDir.isDirectory) {
                cacheDir.walkTopDown()
                    .filter { it.isFile }
                    .map { it.length() }
                    .sum()
            } else {
                0L
            }
        } catch (e: Exception) {
            0L
        }
    }
    
    fun startCleaning() {
        _cleaningStatus.value = CleaningStatus.Cleaning
    }
    
    fun cleaningCompleted(spaceFreed: Long) {
        _cleaningStatus.value = CleaningStatus.Completed(spaceFreed)
    }
    
    fun resetCleaningStatus() {
        _cleaningStatus.value = CleaningStatus.Idle
    }
    
    data class CacheData(
        val apps: List<AppCacheInfo>,
        val totalCacheSize: Long,
        val appCount: Int
    )
    
    data class AppCacheInfo(
        val packageName: String,
        val appName: String,
        val cacheSize: Long,
        val userId: Int
    )
    
    sealed class CleaningStatus {
        object Idle : CleaningStatus()
        object Cleaning : CleaningStatus()
        data class Completed(val spaceFreed: Long) : CleaningStatus()
    }
}
