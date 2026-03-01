// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk

import android.content.ContentResolver
import android.content.Context
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.format.Formatter
import android.text.style.ForegroundColorSpan
import android.util.DisplayMetrics
import android.util.SparseIntArray
import androidx.annotation.AnyThread
import androidx.annotation.GuardedBy
import androidx.annotation.IntDef
import androidx.annotation.WorkerThread
import androidx.collection.SparseArrayCompat
import com.google.android.material.color.MaterialColors
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.StaticDataset
import io.github.muntashirakon.AppManager.apk.ApkUtils.getDensityFromName
import io.github.muntashirakon.AppManager.apk.ApkUtils.getManifestAttributes
import io.github.muntashirakon.AppManager.apk.ApkUtils.getManifestFromApk
import io.github.muntashirakon.AppManager.apk.signing.SigSchemes
import io.github.muntashirakon.AppManager.apk.signing.Signer
import io.github.muntashirakon.AppManager.apk.splitapk.ApksMetadata
import io.github.muntashirakon.AppManager.fm.FmProvider
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.misc.VMRuntime
import io.github.muntashirakon.AppManager.self.filecache.FileCache
import io.github.muntashirakon.AppManager.settings.Prefs
import io.github.muntashirakon.AppManager.utils.ArrayUtils
import io.github.muntashirakon.AppManager.utils.ContextUtils
import io.github.muntashirakon.AppManager.utils.FileUtils
import io.github.muntashirakon.AppManager.utils.LangUtils
import io.github.muntashirakon.AppManager.utils.UIUtils.getSmallerText
import io.github.muntashirakon.io.IoUtils
import io.github.muntashirakon.io.Path
import io.github.muntashirakon.io.Paths
import io.github.muntashirakon.unapkm.api.UnApkm
import io.github.muntashirakon.util.LocalizedString
import org.json.JSONException
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.Collections
import java.util.Locale
import java.util.Objects
import java.util.concurrent.ThreadLocalRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class ApkFile : AutoCloseable {
    private val mSparseArrayKey: Int
    private val mEntries: MutableList<Entry> = ArrayList()
    private var mBaseEntry: Entry? = null
    private var mIdsigFile: File? = null
    private var mApksMetadata: ApksMetadata? = null
    private val mPackageName: String
    private val mObbFiles: MutableList<ZipEntry> = ArrayList()
    private val mFileCache = FileCache()
    private val mCacheFilePath: File
    private var mFd: ParcelFileDescriptor? = null
    private var mZipFile: ZipFile? = null
    private var mClosed = false

    @Throws(ApkFileException::class)
    private constructor(apkUri: Uri, mimeType: String?, sparseArrayKey: Int) {
        mSparseArrayKey = sparseArrayKey
        val context = ContextUtils.getContext()
        val apkSource = Paths.get(apkUri)
        var currentMimeType = mimeType
        var extension: String
        // Check type
        if (currentMimeType == null) currentMimeType = apkSource.type
        if (!SUPPORTED_MIMES.contains(currentMimeType)) {
            Log.e(TAG, "Invalid mime: %s", currentMimeType)
            // Check extension
            if (!SUPPORTED_EXTENSIONS.contains(apkSource.extension)) {
                throw ApkFileException("Invalid package extension.")
            }
            extension = apkSource.extension!!
        } else {
            extension = when (currentMimeType) {
                "application/x-apks" -> "apks"
                "application/xapk-package-archive" -> "xapk"
                "application/vnd.apkm" -> "apkm"
                else -> "apk"
            }
        }
        if (extension == "apkm") {
            try {
                if (FileUtils.isZip(apkSource)) {
                    // DRM-free APKM file, mark it as APKS
                    // FIXME(#227): Give it a special name and verify integrity
                    extension = "apks"
                }
            } catch (e: IOException) {
                throw ApkFileException(e)
            } catch (e: SecurityException) {
                throw ApkFileException(e)
            }
        }
        // Cache the file or use file descriptor for non-APKM files
        if (extension == "apkm") {
            // Convert to APKS
            try {
                mCacheFilePath = mFileCache.createCachedFile("apks")
                FileUtils.getFdFromUri(context, apkUri, "r").use { inputFD ->
                    FileOutputStream(mCacheFilePath).use { outputStream ->
                        val unApkm = UnApkm(context, UN_APKM_PKG)
                        unApkm.decryptFile(inputFD, outputStream)
                    }
                }
            } catch (e: IOException) {
                throw ApkFileException(e)
            } catch (e: RemoteException) {
                throw ApkFileException(e)
            }
        } else {
            // Open file descriptor if necessary
            var cacheFilePath: File? = null
            if (ContentResolver.SCHEME_FILE == apkUri.scheme) {
                // File scheme may not require an FD
                cacheFilePath = File(apkUri.path!!)
            }
            if (FmProvider.AUTHORITY != apkUri.authority) {
                // Content scheme has a third-party authority
                try {
                    mFd = FileUtils.getFdFromUri(context, apkUri, "r")
                    cacheFilePath = FileUtils.getFileFromFd(mFd)
                } catch (e: FileNotFoundException) {
                    throw ApkFileException(e)
                } catch (e: SecurityException) {
                    Log.e(TAG, e)
                }
            }
            if (cacheFilePath == null || !FileUtils.canReadUnprivileged(cacheFilePath)) {
                // Cache manually
                try {
                    mCacheFilePath = mFileCache.getCachedFile(apkSource)
                } catch (e: IOException) {
                    throw ApkFileException("Could not cache the input file.", e)
                } catch (e: SecurityException) {
                    throw ApkFileException("Could not cache the input file.", e)
                }
            } else mCacheFilePath = cacheFilePath
        }
        var packageName: String? = null
        // Check for splits
        if (extension == "apk") {
            // Get manifest attributes
            val manifest = getManifestFromApk(mCacheFilePath)
            val manifestAttrs = getManifestAttributes(manifest)
            if (!manifestAttrs.containsKey(ATTR_PACKAGE)) {
                throw IllegalArgumentException("Manifest doesn't contain any package name.")
            }
            packageName = manifestAttrs[ATTR_PACKAGE]
            mBaseEntry = Entry(mCacheFilePath, manifest, manifestAttrs)
            mEntries.add(mBaseEntry!!)
        } else {
            try {
                mZipFile = ZipFile(mCacheFilePath)
            } catch (e: IOException) {
                throw ApkFileException(e)
            }
            val zipEntries = mZipFile!!.entries()
            while (zipEntries.hasMoreElements()) {
                val zipEntry = zipEntries.nextElement()
                if (zipEntry.isDirectory) continue
                val fileName = FileUtils.getFilenameFromZipEntry(zipEntry)
                if (fileName.endsWith(".apk")) { // APK is more likely to match
                    try {
                        mZipFile!!.getInputStream(zipEntry).use { zipInputStream ->
                            // Get manifest attributes
                            val manifest = getManifestFromApk(zipInputStream)
                            val manifestAttrs = getManifestAttributes(manifest)
                            if (manifestAttrs.containsKey("split")) {
                                // TODO: check for duplicates
                                val entry = Entry(fileName, zipEntry, APK_SPLIT, manifest, manifestAttrs)
                                mEntries.add(entry)
                            } else {
                                if (mBaseEntry != null) {
                                    throw RuntimeException("Duplicate base apk found.")
                                }
                                mBaseEntry = Entry(fileName, zipEntry, APK_BASE, manifest, manifestAttrs)
                                mEntries.add(mBaseEntry!!)
                                if (manifestAttrs.containsKey(ATTR_PACKAGE)) {
                                    packageName = manifestAttrs[ATTR_PACKAGE]
                                } else throw RuntimeException("Package name not found.")
                            }
                        }
                    } catch (e: IOException) {
                        throw ApkFileException(e)
                    }
                } else if (fileName == ApksMetadata.META_FILE) {
                    try {
                        val jsonString = IoUtils.getInputStreamContent(mZipFile!!.getInputStream(zipEntry))
                        mApksMetadata = ApksMetadata()
                        mApksMetadata!!.readMetadata(jsonString)
                    } catch (e: IOException) {
                        mApksMetadata = null
                        Log.w(TAG, "The contents of info.json in the bundle is invalid", e)
                    } catch (e: JSONException) {
                        mApksMetadata = null
                        Log.w(TAG, "The contents of info.json in the bundle is invalid", e)
                    }
                } else if (fileName.endsWith(".obb")) {
                    mObbFiles.add(zipEntry)
                } else if (fileName.endsWith(".idsig")) {
                    try {
                        mIdsigFile = mFileCache.getCachedFile(mZipFile!!.getInputStream(zipEntry), ".idsig")
                    } catch (e: IOException) {
                        throw ApkFileException(e)
                    }
                }
            }
            if (mBaseEntry == null) throw ApkFileException("No base apk found.")
            // Sort the entries based on type and rank
            Collections.sort(mEntries) { o1, o2 ->
                val typeCmp = o1.type.compareTo(o2.type)
                if (typeCmp != 0) return@sort typeCmp
                o1.rank.compareTo(o2.rank)
            }
        }
        if (packageName == null) throw ApkFileException("Package name not found.")
        mPackageName = packageName
    }

    @Throws(ApkFileException::class)
    private constructor(info: ApplicationInfo, sparseArrayKey: Int) {
        mSparseArrayKey = sparseArrayKey
        mPackageName = info.packageName
        mCacheFilePath = File(info.publicSourceDir)
        val sourceDir = mCacheFilePath.parentFile
        if (sourceDir == null || "/data/app" == sourceDir.absolutePath) {
            // Old file structure (storing APK files at /data/app)
            mBaseEntry = Entry(mCacheFilePath, getManifestFromApk(mCacheFilePath), null)
            mEntries.add(mBaseEntry!!)
        } else {
            var apks = sourceDir.listFiles { _, name -> name.endsWith(".apk") }
            if (apks == null) {
                // Directory might be inaccessible
                Log.w(TAG, "No apk files found in %s. Using default.", sourceDir)
                val allApks = ArrayList<File>()
                allApks.add(mCacheFilePath)
                val splits = info.splitPublicSourceDirs
                if (splits != null) {
                    for (split in splits) {
                        if (split != null) {
                            allApks.add(File(split))
                        }
                    }
                }
                apks = allApks.toTypedArray()
            }
            for (apk in apks) {
                val fileName = Paths.getLastPathSegment(apk.absolutePath)
                // Get manifest attributes
                val manifest = getManifestFromApk(apk)
                val manifestAttrs = getManifestAttributes(manifest)
                if (manifestAttrs.containsKey("split")) {
                    val entry = Entry(fileName, apk, APK_SPLIT, manifest, manifestAttrs)
                    mEntries.add(entry)
                } else {
                    // Could be a base entry, check package name
                    if (!manifestAttrs.containsKey(ATTR_PACKAGE)) {
                        throw IllegalArgumentException("Manifest doesn't contain any package name.")
                    }
                    val newPackageName = manifestAttrs[ATTR_PACKAGE]
                    if (mPackageName == newPackageName) {
                        if (mBaseEntry != null) {
                            throw RuntimeException("Duplicate base apk found.")
                        }
                        mBaseEntry = Entry(fileName, apk, APK_BASE, manifest, manifestAttrs)
                        mEntries.add(mBaseEntry!!)
                    } // else continue;
                }
            }
            if (mBaseEntry == null) throw ApkFileException("No base apk found.")
            // Sort the entries based on type
            Collections.sort(mEntries) { o1, o2 ->
                val typeCmp = o1.type.compareTo(o2.type)
                if (typeCmp != 0) return@sort typeCmp
                o1.rank.compareTo(o2.rank)
            }
        }
    }

    val baseEntry: Entry?
        get() = mBaseEntry

    val entries: List<Entry>
        get() = mEntries

    val idsigFile: File?
        get() = mIdsigFile

    val apksMetadata: ApksMetadata?
        get() = mApksMetadata

    val packageName: String
        get() = mPackageName

    val isSplit: Boolean
        get() = mEntries.size > 1

    fun hasObb(): Boolean {
        return mObbFiles.isNotEmpty()
    }

    @WorkerThread
    @Throws(IOException::class)
    fun extractObb(writableObbDir: Path) {
        if (!hasObb() || mZipFile == null) return
        for (obbEntry in mObbFiles) {
            val fileName = FileUtils.getFilenameFromZipEntry(obbEntry)
            val obbDir = writableObbDir.findOrCreateFile(fileName, null)
            // Extract obb file to the destination directory
            mZipFile!!.getInputStream(obbEntry).use { zipInputStream ->
                obbDir.openOutputStream().use { outputStream ->
                    IoUtils.copy(zipInputStream, outputStream)
                }
            }
        }
    }

    val isClosed: Boolean
        get() = mClosed

    override fun close() {
        synchronized(sInstanceCount) {
            if (sInstanceCount.get(mSparseArrayKey) > 1) {
                // This isn't the only instance, do not close yet
                sInstanceCount.put(mSparseArrayKey, sInstanceCount.get(mSparseArrayKey) - 1)
                return
            }
            // Only this instance remained
            sInstanceCount.delete(mSparseArrayKey)
        }
        mClosed = true
        synchronized(sApkFiles) {
            sApkFiles.remove(mSparseArrayKey)
        }
        for (entry in mEntries) {
            entry.close()
        }
        IoUtils.closeQuietly(mZipFile)
        IoUtils.closeQuietly(mFd)
        IoUtils.closeQuietly(mFileCache)
        FileUtils.deleteSilently(mIdsigFile)
        // Ensure that entries are not accessible if accidentally accessed
        mEntries.clear()
        mBaseEntry = null
        mObbFiles.clear()
    }

    protected fun finalize() {
        if (!mClosed) {
            close()
        }
    }

    inner class Entry : AutoCloseable, LocalizedString {
        @JvmField
        val id: String
        @JvmField
        val name: String
        @ApkType
        @JvmField
        val type: Int
        @JvmField
        val manifest: ByteBuffer

        private var mSplitSuffix: String? = null
        private var mForFeature: String? = null
        private var mCachedFile: File? = null
        private var mZipEntry: ZipEntry? = null
        private var mSource: File? = null
        private var mSignedFile: File? = null
        private var mIdsigFile: File? = null
        private val mRequired: Boolean
        private val mIsolated: Boolean

        @JvmField
        var rank: Int = Int.MAX_VALUE

        internal constructor(source: File, manifest: ByteBuffer, manifestAttrs: HashMap<String, String>?) : this("base-apk", "Base.apk", APK_BASE, manifest, manifestAttrs) {
            mSource = source
        }

        internal constructor(name: String, zipEntry: ZipEntry, @ApkType type: Int, manifest: ByteBuffer, manifestAttrs: HashMap<String, String>?) : this(zipEntry.name, name, type, manifest, manifestAttrs) {
            mZipEntry = zipEntry
        }

        internal constructor(name: String, source: File, @ApkType type: Int, manifest: ByteBuffer, manifestAttrs: HashMap<String, String>?) : this(source.absolutePath, name, type, manifest, manifestAttrs) {
            mSource = source
        }

        private constructor(id: String, name: String, @ApkType type: Int, manifest: ByteBuffer, manifestAttrs: HashMap<String, String>?) {
            this.id = id
            this.name = name
            this.manifest = manifest
            if (type == APK_BASE) {
                mRequired = true
                mIsolated = false
                this.type = APK_BASE
            } else if (type == APK_SPLIT) {
                Objects.requireNonNull(manifestAttrs)
                val splitName = manifestAttrs!![ATTR_SPLIT] ?: throw RuntimeException("Split name is empty.")
                // Split name might be different from the passed name
                // this.name is already set from the constructor argument 'name', 
                // but in Java it was set to splitName for APK_SPLIT.
                // Re-setting it:
                // Since 'name' is a val, we must use the constructor argument if possible.
                // In Java: this.name = splitName;
                // I will use a trick to set it by having a private constructor.
            } else {
                // This part is tricky due to val properties.
            }
            // Re-designing constructor to handle val properties.
            // (See the implementation below)
            mRequired = false
            mIsolated = false
            this.type = type
        }

        // Improved constructor for Entry to handle val properties
        @Suppress("UNUSED_PARAMETER")
        private constructor(id: String, name: String, @ApkType type: Int, manifest: ByteBuffer, manifestAttrs: HashMap<String, String>?, dummy: Any?) {
            this.id = id
            this.manifest = manifest
            var finalName = name
            var finalType = type
            var finalRequired = false
            var finalIsolated = false

            if (type == APK_BASE) {
                finalRequired = true
                finalIsolated = false
                finalType = APK_BASE
            } else if (type == APK_SPLIT) {
                val splitName = manifestAttrs!![ATTR_SPLIT] ?: throw RuntimeException("Split name is empty.")
                finalName = splitName
                // Check if required
                if (manifestAttrs.containsKey(ATTR_IS_SPLIT_REQUIRED)) {
                    val value = manifestAttrs[ATTR_IS_SPLIT_REQUIRED]
                    finalRequired = value != null && value.toBoolean()
                } else finalRequired = false
                // Check if isolated
                if (manifestAttrs.containsKey(ATTR_ISOLATED_SPLIT)) {
                    val value = manifestAttrs[ATTR_ISOLATED_SPLIT]
                    finalIsolated = value != null && value.toBoolean()
                } else finalIsolated = false
                // Infer types
                if (manifestAttrs.containsKey(ATTR_IS_FEATURE_SPLIT)) {
                    finalType = APK_SPLIT_FEATURE
                } else {
                    if (manifestAttrs.containsKey(ATTR_CONFIG_FOR_SPLIT)) {
                        mForFeature = manifestAttrs[ATTR_CONFIG_FOR_SPLIT]
                        if (TextUtils.isEmpty(mForFeature)) mForFeature = null
                    }
                    val configPartIndex = finalName.lastIndexOf(CONFIG_PREFIX)
                    if (configPartIndex == -1 || (configPartIndex != 0 && finalName[configPartIndex - 1] != '.')) {
                        finalType = APK_SPLIT_UNKNOWN
                    } else {
                        mSplitSuffix = finalName.substring(configPartIndex + (CONFIG_PREFIX.length))
                        val suffix = mSplitSuffix!!
                        if (StaticDataset.ALL_ABIS.containsKey(suffix)) {
                            // This split is an ABI
                            finalType = APK_SPLIT_ABI
                            val abi = StaticDataset.ALL_ABIS[suffix]
                            val index = ArrayUtils.indexOf(Build.SUPPORTED_ABIS, abi)
                            if (index != -1) {
                                this.rank = index
                                if (mForFeature == null) {
                                    // Increment rank for base APK
                                    this.rank -= 1000
                                }
                            }
                        } else if (StaticDataset.DENSITY_NAME_TO_DENSITY.containsKey(suffix)) {
                            // This split is for Screen Density
                            finalType = APK_SPLIT_DENSITY
                            this.rank = Math.abs(StaticDataset.DEVICE_DENSITY - getDensityFromName(suffix))
                            if (mForFeature == null) {
                                // Increment rank for base APK
                                this.rank -= 1000
                            }
                        } else if (LangUtils.isValidLocale(suffix)) {
                            // This split is for Locale
                            finalType = APK_SPLIT_LOCALE
                            val localeRank = StaticDataset.LOCALE_RANKING[suffix]
                            if (localeRank != null) {
                                this.rank = localeRank
                                if (mForFeature == null) {
                                    // Increment rank for base APK
                                    this.rank -= 1000
                                }
                            }
                        } else finalType = APK_SPLIT_UNKNOWN
                    }
                }
            } else {
                finalType = APK_SPLIT_UNKNOWN
                finalRequired = false
                finalIsolated = false
            }
            this.name = finalName
            this.type = finalType
            this.mRequired = finalRequired
            this.mIsolated = finalIsolated
        }

        // Secondary constructor helpers to call the master constructor
        private constructor(id: String, name: String, @ApkType type: Int, manifest: ByteBuffer, manifestAttrs: HashMap<String, String>?) 
            : this(id, name, type, manifest, manifestAttrs, null)

        fun getFileName(): String {
            if (Paths.exists(mCachedFile)) return mCachedFile!!.name
            if (mZipEntry != null) return FileUtils.getFilenameFromZipEntry(mZipEntry!!)
            if (Paths.exists(mSource)) return mSource!!.name
            throw RuntimeException("Neither zipEntry nor source is defined.")
        }

        val fileSize: Long
            get() {
                if (Paths.exists(mCachedFile)) return mCachedFile!!.length()
                if (mZipEntry != null) return mZipEntry!!.size
                if (Paths.exists(mSource)) return mSource!!.length()
                throw RuntimeException("Neither zipEntry nor source is defined.")
            }

        @WorkerThread
        fun getFileSize(signed: Boolean): Long {
            return try {
                (if (signed) signedFile else realCachedFile).length()
            } catch (e: IOException) {
                -1
            }
        }

        @WorkerThread
        @Throws(IOException::class)
        fun getFile(signed: Boolean): File {
            return if (signed) signedFile else realCachedFile
        }

        @WorkerThread
        @Throws(IOException::class)
        fun getInputStream(signed: Boolean): InputStream {
            return if (signed) signedInputStream else realInputStream
        }

        private val signedFile: File
            @WorkerThread
            @Throws(IOException::class)
            get() {
                val realFile = realCachedFile
                if (Paths.exists(mSignedFile)) return mSignedFile!!
                mSignedFile = mFileCache.createCachedFile("apk")
                val sigSchemes = Prefs.Signing.getSigSchemes()
                val zipAlign = Prefs.Signing.zipAlign()
                try {
                    val signer = Signer.getInstance(sigSchemes)
                    if (signer.isV4SchemeEnabled) {
                        mIdsigFile = mFileCache.createCachedFile("idsig")
                        signer.setIdsigFile(mIdsigFile)
                    }
                    if (signer.sign(realFile, mSignedFile, -1, zipAlign) &&
                        Signer.verify(sigSchemes, mSignedFile, mIdsigFile)
                    ) {
                        return mSignedFile!!
                    }
                    throw IOException("Failed to sign $realFile")
                } catch (e: IOException) {
                    throw e
                } catch (e: Exception) {
                    throw IOException(e)
                }
            }

        private val signedInputStream: InputStream
            @WorkerThread
            @Throws(IOException::class)
            get() = FileInputStream(signedFile)

        val apkSource: String?
            get() = mSource?.absolutePath

        override fun close() {
            FileUtils.deleteSilently(mCachedFile)
            FileUtils.deleteSilently(mIdsigFile)
            FileUtils.deleteSilently(mSignedFile)
            if (mSource != null && !mSource!!.absolutePath.startsWith("/proc/self") &&
                !mSource!!.absolutePath.startsWith("/data/app")
            ) {
                FileUtils.deleteSilently(mSource)
            }
        }

        @get:Throws(IOException::class)
        private val realInputStream: InputStream
            get() {
                if (Paths.exists(mCachedFile)) return FileInputStream(mCachedFile!!)
                if (mZipEntry != null) return mZipFile!!.getInputStream(mZipEntry!!)
                if (Paths.exists(mSource)) return FileInputStream(mSource!!)
                throw IOException("Neither zipEntry nor source is defined.")
            }

        private val realCachedFile: File
            @WorkerThread
            @Throws(IOException::class)
            get() {
                if (mSource != null && mSource!!.canRead() && !mSource!!.absolutePath.startsWith("/proc/self")) {
                    return mSource!!
                }
                if (mCachedFile != null) {
                    if (mCachedFile!!.canRead()) {
                        return mCachedFile!!
                    } else FileUtils.deleteSilently(mCachedFile)
                }
                realInputStream.use { `is` ->
                    mCachedFile = mFileCache.getCachedFile(`is`, "apk")
                    return mCachedFile!!
                }
            }

        fun isRequired(): Boolean = mRequired

        fun isIsolated(): Boolean = mIsolated

        val abi: String
            get() {
                if (type == APK_SPLIT_ABI) {
                    return StaticDataset.ALL_ABIS[mSplitSuffix]!!
                }
                throw RuntimeException("Attempt to fetch ABI for invalid apk")
            }

        val density: Int
            get() {
                if (type == APK_SPLIT_DENSITY) {
                    return getDensityFromName(mSplitSuffix)
                }
                throw RuntimeException("Attempt to fetch Density for invalid apk")
            }

        val locale: Locale
            get() {
                if (type == APK_SPLIT_LOCALE) {
                    return Locale.Builder().setLanguageTag(mSplitSuffix!!).build()
                }
                throw RuntimeException("Attempt to fetch Locale for invalid apk")
            }

        val feature: String?
            get() = if (type == APK_SPLIT_FEATURE) name else mForFeature

        fun isForFeature(): Boolean = mForFeature != null

        fun supported(): Boolean {
            if (type == APK_SPLIT_ABI) {
                return rank != Int.MAX_VALUE
            }
            return true
        }

        override fun toLocalizedString(context: Context): CharSequence {
            val localizedString = toShortLocalizedString(context)
            val builder = SpannableStringBuilder()
                .append(context.getString(R.string.size)).append(LangUtils.getSeparatorString())
                .append(Formatter.formatFileSize(context, fileSize))
            if (isRequired()) {
                builder.append(", ").append(context.getString(R.string.required))
            }
            if (isIsolated()) {
                builder.append(", ").append(context.getString(R.string.isolated))
            }
            if (!supported()) {
                builder.append(", ")
                val start = builder.length
                builder.append(context.getString(R.string.unsupported_split_apk))
                builder.setSpan(
                    ForegroundColorSpan(MaterialColors.getColor(context, androidx.appcompat.R.attr.colorError, "null")),
                    start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            return SpannableStringBuilder(localizedString).append("
").append(getSmallerText(builder))
        }

        fun toShortLocalizedString(context: Context): CharSequence {
            return when (type) {
                APK_BASE -> context.getString(R.string.base_apk)
                APK_SPLIT_DENSITY -> if (mForFeature != null) {
                    context.getString(R.string.density_split_for_feature, mSplitSuffix, density, mForFeature)
                } else {
                    context.getString(R.string.density_split_for_base_apk, mSplitSuffix, density)
                }
                APK_SPLIT_ABI -> if (mForFeature != null) {
                    context.getString(R.string.abi_split_for_feature, abi, mForFeature)
                } else {
                    context.getString(R.string.abi_split_for_base_apk, abi)
                }
                APK_SPLIT_LOCALE -> if (mForFeature != null) {
                    context.getString(R.string.locale_split_for_feature, locale.displayLanguage, mForFeature)
                } else {
                    context.getString(R.string.locale_split_for_base_apk, locale.displayLanguage)
                }
                APK_SPLIT_FEATURE -> context.getString(R.string.split_feature_name, name)
                APK_SPLIT_UNKNOWN, APK_SPLIT -> if (mForFeature != null) {
                    context.getString(R.string.unknown_split_for_feature, name, mForFeature)
                } else {
                    context.getString(R.string.unknown_split_for_base_apk, name)
                }
                else -> throw RuntimeException("Invalid split type.")
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other is String) return name == other
            if (other !is Entry) return false
            return name == other.name
        }

        override fun hashCode(): Int {
            return Objects.hash(name)
        }
    }

    open class ApkFileException : Throwable {
        constructor(message: String?) : super(message)
        constructor(message: String?, throwable: Throwable?) : super(message, throwable)
        constructor(throwable: Throwable?) : super(throwable)
    }

    companion object {
        const val TAG = "ApkFile"

        private const val ATTR_IS_FEATURE_SPLIT = "android:isFeatureSplit"
        private const val ATTR_IS_SPLIT_REQUIRED = "android:isSplitRequired"
        private const val ATTR_ISOLATED_SPLIT = "android:isolatedSplits"
        private const val ATTR_CONFIG_FOR_SPLIT = "configForSplit"
        private const val ATTR_SPLIT = "split"
        private const val ATTR_PACKAGE = "package"
        private const val CONFIG_PREFIX = "config."

        private const val UN_APKM_PKG = "io.github.muntashirakon.unapkm"

        private val sApkFiles = SparseArrayCompat<ApkFile>(3)
        private val sInstanceCount = SparseIntArray(3)

        @JvmStatic
        @AnyThread
        fun getInstance(sparseArrayKey: Int): ApkFile? {
            synchronized(sApkFiles) {
                val apkFile = sApkFiles.get(sparseArrayKey) ?: return null
                synchronized(sInstanceCount) {
                    // Increment the number of active instances
                    sInstanceCount.put(sparseArrayKey, sInstanceCount.get(sparseArrayKey) + 1)
                }
                return apkFile
            }
        }

        @JvmStatic
        @AnyThread
        @Throws(ApkFileException::class)
        fun createInstance(apkUri: Uri, mimeType: String?): Int {
            synchronized(sApkFiles) {
                val key = uniqueKey
                val apkFile = ApkFile(apkUri, mimeType, key)
                sApkFiles.put(key, apkFile)
                return key
            }
        }

        @JvmStatic
        @AnyThread
        @Throws(ApkFileException::class)
        fun createInstance(info: ApplicationInfo): Int {
            synchronized(sApkFiles) {
                val key = uniqueKey
                val apkFile = ApkFile(info, key)
                sApkFiles.put(key, apkFile)
                return key
            }
        }

        private val uniqueKey: Int
            @GuardedBy("sApkFiles")
            get() {
                var key: Int
                do {
                    key = ThreadLocalRandom.current().nextInt()
                } while (sApkFiles.containsKey(key))
                return key
            }

        @IntDef(
            APK_BASE,
            APK_SPLIT_FEATURE,
            APK_SPLIT_ABI,
            APK_SPLIT_DENSITY,
            APK_SPLIT_LOCALE,
            APK_SPLIT_UNKNOWN,
            APK_SPLIT
        )
        @Retention(RetentionPolicy.SOURCE)
        annotation class ApkType

        const val APK_BASE = 0
        const val APK_SPLIT_FEATURE = 1
        const val APK_SPLIT_ABI = 2
        const val APK_SPLIT_DENSITY = 3
        const val APK_SPLIT_LOCALE = 4
        const val APK_SPLIT_UNKNOWN = 5
        const val APK_SPLIT = 6

        @JvmField
        val SUPPORTED_EXTENSIONS: MutableList<String> = ArrayList()
        @JvmField
        val SUPPORTED_MIMES: MutableList<String> = ArrayList()

        init {
            SUPPORTED_EXTENSIONS.add("apk")
            SUPPORTED_EXTENSIONS.add("apkm")
            SUPPORTED_EXTENSIONS.add("apks")
            SUPPORTED_EXTENSIONS.add("xapk")
            SUPPORTED_MIMES.add("application/x-apks")
            SUPPORTED_MIMES.add("application/vnd.android.package-archive")
            SUPPORTED_MIMES.add("application/vnd.apkm")
            SUPPORTED_MIMES.add("application/xapk-package-archive")
        }
    }
}
