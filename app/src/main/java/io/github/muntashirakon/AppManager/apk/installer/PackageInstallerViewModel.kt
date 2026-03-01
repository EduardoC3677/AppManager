// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer

import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.UserHandleHidden
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.github.muntashirakon.AppManager.apk.ApkFile
import io.github.muntashirakon.AppManager.apk.ApkSource
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils
import io.github.muntashirakon.AppManager.utils.PackageUtils
import io.github.muntashirakon.AppManager.utils.ThreadUtils
import io.github.muntashirakon.io.IoUtils
import java.io.IOException
import java.util.concurrent.Future

class PackageInstallerViewModel(application: Application) : AndroidViewModel(application) {
    private val mPm: PackageManager = application.packageManager
    private var mNewPackageInfo: PackageInfo? = null
    private var mInstalledPackageInfo: PackageInfo? = null
    private var mApkSource: ApkSource? = null
    private var mApkFile: ApkFile? = null
    private var mPackageName: String? = null
    private var mAppLabel: String? = null
    private var mAppIcon: Drawable? = null
    private var mIsSignatureDifferent = false
    private var mTrackerCount: Int = 0
    private var mPackageInfoResult: Future<*>? = null
    private val mPackageInfoLiveData = MutableLiveData<PackageInfo>()
    private val mPackageUninstalledLiveData = MutableLiveData<Boolean>()
    private val mSelectedSplits = mutableSetOf<String>()

    override fun onCleared() {
        IoUtils.closeQuietly(mApkFile)
        mPackageInfoResult?.cancel(true)
        super.onCleared()
    }

    fun packageInfoLiveData(): LiveData<PackageInfo> = mPackageInfoLiveData
    fun packageUninstalledLiveData(): LiveData<Boolean> = mPackageUninstalledLiveData

    @AnyThread
    fun getPackageInfo(apkQueueItem: ApkQueueItem) {
        mPackageInfoResult?.cancel(true)
        mSelectedSplits.clear()
        mPackageInfoResult = ThreadUtils.postOnBackgroundThread {
            try {
                if (apkQueueItem.isInstallExisting) {
                    val pkgName = apkQueueItem.packageName ?: throw IllegalArgumentException("Package name not set for install-existing.")
                    getExistingPackageInfoInternal(pkgName)
                } else if (apkQueueItem.apkSource != null) {
                    mApkSource = apkQueueItem.apkSource
                    getPackageInfoInternal()
                } else {
                    throw IllegalArgumentException("Invalid queue item.")
                }
                apkQueueItem.apkSource = mApkSource
                apkQueueItem.packageName = mPackageName
                apkQueueItem.appLabel = mAppLabel
            } catch (th: Throwable) {
                Log.e("PIVM", "Couldn't fetch package info", th)
                mPackageInfoLiveData.postValue(null)
            }
        }
    }

    fun uninstallPackage() {
        ThreadUtils.postOnBackgroundThread {
            val installer = PackageInstallerCompat.getNewInstance()
            installer.setAppLabel(mAppLabel)
            mPackageUninstalledLiveData.postValue(installer.uninstall(mPackageName!!, UserHandleHidden.myUserId(), false))
        }
    }

    fun getNewPackageInfo(): PackageInfo? = mNewPackageInfo
    fun getInstalledPackageInfo(): PackageInfo? = mInstalledPackageInfo
    fun getAppLabel(): String? = mAppLabel
    fun getAppIcon(): Drawable? = mAppIcon
    fun getPackageName(): String? = mPackageName
    fun getApkFile(): ApkFile? = mApkFile
    fun getApkSource(): ApkSource? = mApkSource
    fun getTrackerCount(): Int = mTrackerCount
    fun isSignatureDifferent(): Boolean = mIsSignatureDifferent
    fun getSelectedSplits(): MutableSet<String> = mSelectedSplits

    fun getSelectedSplitsForInstallation(): ArrayList<String> {
        val apkFile = mApkFile ?: throw IllegalStateException("ApkFile not initialized")
        if (apkFile.isSplit) {
            if (mSelectedSplits.isEmpty()) throw IllegalArgumentException("No splits selected.")
            return ArrayList(mSelectedSplits)
        }
        return arrayListOf(apkFile.baseEntry.id)
    }

    private fun getPackageInfoInternal() {
        mApkFile = mApkSource!!.resolve()
        mNewPackageInfo = loadNewPackageInfo()
        mPackageName = mNewPackageInfo!!.packageName
        if (ThreadUtils.isInterrupted()) return
        try {
            mInstalledPackageInfo = loadInstalledPackageInfo(mPackageName!!)
            if (ThreadUtils.isInterrupted()) return
        } catch (ignore: PackageManager.NameNotFoundException) {}
        mAppLabel = mPm.getApplicationLabel(mNewPackageInfo!!.applicationInfo).toString()
        mAppIcon = mPm.getApplicationIcon(mNewPackageInfo!!.applicationInfo)
        mTrackerCount = ComponentUtils.getTrackerComponentsCountForPackage(mNewPackageInfo!!)
        if (ThreadUtils.isInterrupted()) return
        if (mNewPackageInfo != null && mInstalledPackageInfo != null) {
            mIsSignatureDifferent = PackageUtils.isSignatureDifferent(mNewPackageInfo!!, mInstalledPackageInfo!!)
        }
        mPackageInfoLiveData.postValue(mNewPackageInfo)
    }

    private fun getExistingPackageInfoInternal(packageName: String) {
        mPackageName = packageName
        mInstalledPackageInfo = loadInstalledPackageInfo(packageName)
        mApkSource = ApkSource.getApkSource(mInstalledPackageInfo!!.applicationInfo)
        mApkFile = mApkSource!!.resolve()
        mNewPackageInfo = loadNewPackageInfo()
        mAppLabel = mPm.getApplicationLabel(mNewPackageInfo!!.applicationInfo).toString()
        mAppIcon = mPm.getApplicationIcon(mNewPackageInfo!!.applicationInfo)
        mTrackerCount = ComponentUtils.getTrackerComponentsCountForPackage(mNewPackageInfo!!)
        if (mNewPackageInfo != null && mInstalledPackageInfo != null) {
            mIsSignatureDifferent = PackageUtils.isSignatureDifferent(mNewPackageInfo!!, mInstalledPackageInfo!!)
        }
        mPackageInfoLiveData.postValue(mNewPackageInfo)
    }

    @WorkerThread
    private fun loadNewPackageInfo(): PackageInfo {
        val apkPath = mApkFile!!.baseEntry.getFile(false).absolutePath
        val flags = PackageManager.GET_PERMISSIONS or PackageManager.GET_ACTIVITIES or PackageManager.GET_RECEIVERS or
                PackageManager.GET_PROVIDERS or PackageManager.GET_SERVICES or PackageManagerCompat.MATCH_DISABLED_COMPONENTS or
                PackageManagerCompat.GET_SIGNING_CERTIFICATES_APK or PackageManager.GET_CONFIGURATIONS or PackageManager.GET_SHARED_LIBRARY_FILES
        var packageInfo = mPm.getPackageArchiveInfo(apkPath, flags)
        if (packageInfo == null) {
            packageInfo = mPm.getPackageArchiveInfo(apkPath, flags and PackageManagerCompat.GET_SIGNING_CERTIFICATES_APK.inv())
        }
        if (packageInfo == null) throw PackageManager.NameNotFoundException("Package cannot be parsed.")
        packageInfo.applicationInfo.sourceDir = apkPath
        packageInfo.applicationInfo.publicSourceDir = apkPath
        return packageInfo
    }

    @WorkerThread
    private fun loadInstalledPackageInfo(packageName: String): PackageInfo {
        @SuppressLint("WrongConstant")
        val packageInfo = mPm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS or PackageManager.GET_ACTIVITIES or
                PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS or PackageManager.GET_SERVICES or
                PackageManagerCompat.MATCH_DISABLED_COMPONENTS or PackageManagerCompat.GET_SIGNING_CERTIFICATES or
                PackageManagerCompat.MATCH_UNINSTALLED_PACKAGES or PackageManager.GET_CONFIGURATIONS or PackageManager.GET_SHARED_LIBRARY_FILES)
        if (packageInfo == null) throw PackageManager.NameNotFoundException("Package not found.")
        return packageInfo
    }
}
