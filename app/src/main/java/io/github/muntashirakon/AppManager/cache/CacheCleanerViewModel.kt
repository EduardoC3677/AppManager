// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.cache

import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat
import io.github.muntashirakon.AppManager.users.Users
import java.io.File

/**
 * ViewModel for Cache Cleaner
 * Handles cache size calculation and data management
 */
class CacheCleanerViewModel(application: Application) : AndroidViewModel(application) {
    
    private val mCacheData = MutableLiveData<CacheData>()
    private val mCleaningStatus = MutableLiveData<CleaningStatus>()
    
    val cacheData: LiveData<CacheData> = mCacheData
    val cleaningStatus: LiveData<CleaningStatus> = mCleaningStatus
    
    private val mPackageManager = application.packageManager
    private val mUserId = Users.myUserId()
    
    fun loadCacheData() {
        Thread {
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
                        // Skip apps we can't access
                    }
                }
                
                // Sort by cache size (largest first)
                appCacheList.sortByDescending { it.cacheSize }
                
                mCacheData.postValue(
                    CacheData(
                        apps = appCacheList,
                        totalCacheSize = totalCacheSize,
                        appCount = appCacheList.size
                    )
                )
            } catch (e: Exception) {
                // Handle error
                mCacheData.postValue(CacheData(emptyList(), 0L, 0))
            }
        }.start()
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
        mCleaningStatus.postValue(CleaningStatus.Cleaning)
    }
    
    fun cleaningCompleted(spaceFreed: Long) {
        mCleaningStatus.postValue(CleaningStatus.Completed(spaceFreed))
    }
    
    fun resetCleaningStatus() {
        mCleaningStatus.postValue(CleaningStatus.Idle)
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
