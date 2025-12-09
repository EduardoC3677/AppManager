// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.manifest

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import io.github.muntashirakon.AppManager.apk.ApkFile
import io.github.muntashirakon.AppManager.apk.ApkSource
import io.github.muntashirakon.AppManager.apk.parser.AndroidBinXmlDecoder
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.self.filecache.FileCache
import io.github.muntashirakon.io.IoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.PrintStream
import java.nio.ByteOrder

class ManifestViewerViewModel(application: Application) : AndroidViewModel(application) {

    private var apkFile: ApkFile? = null
    private var manifestLoaderJob: Job? = null

    private val fileCache = FileCache()
    private val _manifestLiveData = MutableLiveData<Uri>()
    val manifestLiveData: LiveData<Uri> = _manifestLiveData

    override fun onCleared() {
        manifestLoaderJob?.cancel()
        IoUtils.closeQuietly(apkFile)
        IoUtils.closeQuietly(fileCache)
        super.onCleared()
    }

    fun loadApkFile(apkSource: ApkSource?, packageName: String?) {
        manifestLoaderJob = viewModelScope.launch(Dispatchers.IO) {
            val pm = getApplication<Application>().packageManager
            val realApkSource = apkSource ?: try {
                val applicationInfo = pm.getApplicationInfo(packageName, 0)
                ApkSource.getApkSource(applicationInfo)
            } catch (e: PackageManager.NameNotFoundException) {
                Log.e(TAG, "Error: ", e)
                return@launch
            }

            try {
                apkFile = realApkSource.resolve()
            } catch (e: ApkFile.ApkFileException) {
                Log.e(TAG, "Error: ", e)
                return@launch
            }

            if (!isActive) {
                return@launch
            }

            apkFile?.let { apk ->
                val byteBuffer = apk.baseEntry.manifest
                // Reset properties
                byteBuffer.position(0)
                byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
                try {
                    val cachedFile = fileCache.createCachedFile("xml")
                    PrintStream(cachedFile).use { ps ->
                        AndroidBinXmlDecoder.decode(byteBuffer, ps)
                    }
                    _manifestLiveData.postValue(Uri.fromFile(cachedFile))
                } catch (e: Throwable) {
                    Log.e(TAG, "Could not parse APK", e)
                }
            }
        }
    }

    companion object {
        const val TAG = "ManifestViewerViewModel"
    }
}
