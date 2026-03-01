// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner

import android.app.Application
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Pair
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.android.apksig.ApkVerifier
import com.android.apksig.apk.ApkFormatException
import io.github.muntashirakon.AppManager.BuildConfig
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.StaticDataset
import io.github.muntashirakon.AppManager.fm.ContentType2
import io.github.muntashirakon.AppManager.scanner.vt.VirusTotal
import io.github.muntashirakon.AppManager.scanner.vt.VtFileReport
import io.github.muntashirakon.AppManager.self.filecache.FileCache
import io.github.muntashirakon.AppManager.settings.FeatureController
import io.github.muntashirakon.AppManager.utils.*
import io.github.muntashirakon.algo.AhoCorasick
import io.github.muntashirakon.io.IoUtils
import io.github.muntashirakon.io.Path
import io.github.muntashirakon.io.Paths
import io.github.muntashirakon.io.fs.DexFileSystem
import io.github.muntashirakon.io.fs.VirtualFileSystem
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.security.NoSuchAlgorithmException
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicIntegerArray
import java.util.regex.Pattern

class ScannerViewModel(application: Application) : AndroidViewModel(application), VirusTotal.FullScanResponseInterface {
    private var mApkFile: File? = null
    private var mIsSummaryLoaded = false
    private var mApkUri: Uri? = null
    private var mDexVfsId: Int = 0
    private val mVt: VirusTotal? = if (FeatureController.isVirusTotalEnabled()) VirusTotal(this) else null
    var packageName: String? = null
        private set

    private var mAllClasses: List<String>? = null
    private var mTrackerClasses: List<String>? = null
    private var mNativeLibraries: Collection<String>? = null

    private var mWaitForFile: CountDownLatch? = null
    private val mFileCache = FileCache()
    private val mExecutor: ExecutorService = AppExecutor.getExecutor()
    private val mApkChecksumsLiveData = MutableLiveData<Array<Pair<String, String>>>()
    private val mApkVerifierResultLiveData = MutableLiveData<ApkVerifier.Result>()
    private val mPackageInfoLiveData = MutableLiveData<PackageInfo>()
    private val mAllClassesLiveData = MutableLiveData<List<String>>()
    private val mTrackerClassesLiveData = MutableLiveData<List<SignatureInfo>>()
    private val mLibraryClassesLiveData = MutableLiveData<List<SignatureInfo>>()
    private val mMissingClassesLiveData = MutableLiveData<ArrayList<String>>()
    private val mVtFileUploadLiveData = MutableLiveData<String>() // Null = Uploading, NonNull = Queued
    private val mVtFileReportLiveData = MutableLiveData<VtFileReport>() // Null = Failed, NonNull = Result generated
    private val mProgressLiveData = MutableLiveData<Int>() // 0-100

    fun getApkChecksumsLiveData(): LiveData<Array<Pair<String, String>>> = mApkChecksumsLiveData
    fun getApkVerifierResultLiveData(): LiveData<ApkVerifier.Result> = mApkVerifierResultLiveData
    fun getPackageInfoLiveData(): LiveData<PackageInfo> = mPackageInfoLiveData
    fun getAllClassesLiveData(): LiveData<List<String>> = mAllClassesLiveData
    fun getTrackerClassesLiveData(): LiveData<List<SignatureInfo>> = mTrackerClassesLiveData
    fun getLibraryClassesLiveData(): LiveData<List<SignatureInfo>> = mLibraryClassesLiveData
    fun getMissingClassesLiveData(): LiveData<ArrayList<String>> = mMissingClassesLiveData
    fun getVtFileUploadLiveData(): LiveData<String> = mVtFileUploadLiveData
    fun getVtFileReportLiveData(): LiveData<VtFileReport> = mVtFileReportLiveData
    fun getProgressLiveData(): LiveData<Int> = mProgressLiveData

    override fun onCleared() {
        if (mDexVfsId != 0) ExUtils.exceptionAsIgnored { VirtualFileSystem.unmount(mDexVfsId) }
        IoUtils.closeQuietly(mFileCache)
        super.onCleared()
    }

    @AnyThread
    fun setApkFile(apkUri: Uri, packageName: String?) {
        mApkUri = apkUri
        this.packageName = packageName
        mWaitForFile = CountDownLatch(1)
        mExecutor.submit {
            try {
                if (apkUri.scheme == "file") {
                    mApkFile = File(apkUri.path)
                } else {
                    val path = Paths.get(apkUri)
                    mApkFile = mFileCache.getCachedFile(path)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                mWaitForFile!!.countDown()
            }
        }
    }

    fun startScan() {
        if (mApkFile == null) throw IllegalStateException("APK file not set")
        if (mIsSummaryLoaded) return
        mExecutor.submit {
            try {
                mWaitForFile!!.await(10, TimeUnit.SECONDS)
                if (mApkFile == null) throw FileNotFoundException("APK file not set")

                val pkgManager = getApplication<Application>().packageManager
                val packageInfo = pkgManager.getPackageArchiveInfo(mApkFile!!.path, 0)
                    ?: throw PackageManager.NameNotFoundException("Cannot parse package: " + mApkFile!!.path)

                mPackageInfoLiveData.postValue(packageInfo)
                mApkChecksumsLiveData.postValue(getApkChecksums(mApkFile!!))
                mApkVerifierResultLiveData.postValue(verifyApk(mApkFile!!))
                mDexVfsId = VirtualFileSystem.mount(mApkUri!!, Paths.get(mApkFile!!), ContentType2.DEX.mimeType)
                loadAllClasses(mApkFile!!)
                loadNativeLibs(mApkFile!!)
                loadTrackersAndLibraries(packageName)
                if (mVt != null) {
                    val checksum = getApkChecksums(mApkFile!!).first { it.first == DigestUtils.SHA_256 }.second
                    mVt.getFullScanReport(checksum)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                mIsSummaryLoaded = true
                mProgressLiveData.postValue(100)
            }
        }
    }

    private fun loadAllClasses(apkFile: File) {
        mExecutor.submit {
            mAllClasses = NativeLibraries.getClasses(apkFile).sorted()
            mAllClassesLiveData.postValue(mAllClasses)
        }
    }

    private fun loadNativeLibs(apkFile: File) {
        mExecutor.submit {
            mNativeLibraries = NativeLibraries.getNativeLibs(apkFile).map { it.getPath() }
        }
    }

    private fun loadTrackersAndLibraries(packageName: String?) {
        mExecutor.submit {
            val a = AtomicIntegerArray(2) // 0=Trackers, 1=Libraries
            val c = CountDownLatch(2)

            mExecutor.submit {
                mTrackerClasses = getTrackers(packageName, a, c)
                mTrackerClassesLiveData.postValue(mTrackerClasses)
            }
            mExecutor.submit {
                mLibraryClassesLiveData.postValue(getLibraries(packageName, a, c))
            }
            c.await()
            val missingClasses = a[0] + a[1]
            if (missingClasses > 0) mMissingClassesLiveData.postValue(ArrayList()) // Dummy data
        }
    }

    override fun onQueue(resource: String) {
        mVtFileUploadLiveData.postValue(resource)
    }

    override fun onResult(result: VtFileReport) {
        mVtFileReportLiveData.postValue(result)
    }

    @WorkerThread
    @Throws(IOException::class, NoSuchAlgorithmException::class, ApkFormatException::class)
    private fun getApkChecksums(apkFile: File): Array<Pair<String, String>> {
        val path = Paths.get(apkFile)
        val sha1 = DigestUtils.getHexDigest(DigestUtils.SHA_1, path)
        val sha256 = DigestUtils.getHexDigest(DigestUtils.SHA_256, path)
        val md5 = DigestUtils.getHexDigest(DigestUtils.MD5, path)
        return arrayOf(Pair("SHA1", sha1), Pair("SHA256", sha256), Pair("MD5", md5))
    }

    @WorkerThread
    @Throws(IOException::class, NoSuchAlgorithmException::class)
    private fun verifyApk(apkFile: File): ApkVerifier.Result {
        val verifier = ApkVerifier.Builder(apkFile).build()
        return verifier.verify()
    }

    @WorkerThread
    private fun getTrackers(packageName: String?, a: AtomicIntegerArray, c: CountDownLatch): List<SignatureInfo> {
        val classes = mAllClasses ?: return Collections.emptyList()
        val trackers = StaticDataset.getTrackers()
        val signatureInfos = mutableListOf<SignatureInfo>()
        val ahoCorasick = AhoCorasick()
        val patternMap = mutableMapOf<Pattern, SignatureInfo>()

        trackers.forEach { tracker ->
            tracker.signatures.forEach { sig ->
                val p = Pattern.compile(sig.signature)
                ahoCorasick.addPattern(p)
                patternMap[p] = SignatureInfo(sig.signature, tracker.label)
            }
        }
        val matcher = ahoCorasick.build().matcher("")
        for (clazz in classes) {
            if (packageName != null && clazz.startsWith(packageName) && !clazz.contains(".R$") && !clazz.contains(".BuildConfig")) {
                continue
            }
            matcher.reset(clazz)
            while (matcher.find()) {
                val pattern = matcher.foundPattern()
                val info = patternMap[pattern] ?: continue
                info.addClass(clazz)
                if (!signatureInfos.contains(info)) {
                    signatureInfos.add(info)
                }
            }
        }
        signatureInfos.forEach {
            it.count = it.classes.size
        }
        signatureInfos.sortByDescending { it.count }
        a.set(0, signatureInfos.size)
        c.countDown()
        return signatureInfos
    }

    @WorkerThread
    private fun getLibraries(packageName: String?, a: AtomicIntegerArray, c: CountDownLatch): List<SignatureInfo> {
        val classes = mAllClasses ?: return Collections.emptyList()
        val libraries = StaticDataset.getLibraries()
        val signatureInfos = mutableListOf<SignatureInfo>()
        val ahoCorasick = AhoCorasick()
        val patternMap = mutableMapOf<Pattern, SignatureInfo>()

        libraries.forEach { library ->
            library.signatures.forEach { sig ->
                val p = Pattern.compile(sig.signature)
                ahoCorasick.addPattern(p)
                patternMap[p] = SignatureInfo(sig.signature, library.label, library.type)
            }
        }
        val matcher = ahoCorasick.build().matcher("")
        for (clazz in classes) {
            if (packageName != null && clazz.startsWith(packageName) && !clazz.contains(".R$") && !clazz.contains(".BuildConfig")) {
                continue
            }
            matcher.reset(clazz)
            while (matcher.find()) {
                val pattern = matcher.foundPattern()
                val info = patternMap[pattern] ?: continue
                info.addClass(clazz)
                if (!signatureInfos.contains(info)) {
                    signatureInfos.add(info)
                }
            }
        }
        signatureInfos.forEach {
            it.count = it.classes.size
        }
        signatureInfos.sortByDescending { it.count }
        a.set(1, signatureInfos.size)
        c.countDown()
        return signatureInfos
    }

    fun getDexFile(dexVfsId: Int = mDexVfsId): Path? {
        if (dexVfsId == 0) return null
        val fs = VirtualFileSystem.getFileSystem(dexVfsId)
        return (fs as DexFileSystem).rootPath
    }

    companion object {
        private val SIG_TO_IGNORE = Pattern.compile("^(android(|x)|com\.android|com\.google\.android|java(|x)|j\$\.(util|time)|\w\d?(\.\w\d?)+)\..*$")
    }
}
