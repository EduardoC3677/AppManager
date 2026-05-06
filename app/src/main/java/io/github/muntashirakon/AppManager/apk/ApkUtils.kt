// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk

import android.annotation.UserIdInt
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.RemoteException
import android.os.UserHandleHidden
import android.text.TextUtils
import androidx.annotation.WorkerThread
import androidx.core.content.pm.PackageInfoCompat
import com.android.apksig.apk.ApkFormatException
import com.android.apksig.internal.zip.CentralDirectoryRecord
import com.android.apksig.internal.zip.LocalFileRecord
import com.android.apksig.internal.zip.ZipUtils
import com.android.apksig.util.DataSource
import com.android.apksig.util.DataSources
import com.android.apksig.zip.ZipFormatException
import com.reandroid.arsc.chunk.xml.ResXmlAttribute
import com.reandroid.arsc.chunk.xml.ResXmlDocument
import com.reandroid.arsc.io.BlockReader
import io.github.muntashirakon.AppManager.StaticDataset
import io.github.muntashirakon.AppManager.apk.parser.AndroidBinXmlDecoder
import io.github.muntashirakon.AppManager.apk.splitapk.SplitApkExporter
import io.github.muntashirakon.AppManager.backup.BackupItems
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.misc.OsEnvironment
import io.github.muntashirakon.AppManager.self.filecache.FileCache
import io.github.muntashirakon.AppManager.utils.AppPref
import io.github.muntashirakon.AppManager.utils.ContextUtils
import io.github.muntashirakon.AppManager.utils.DateUtils
import io.github.muntashirakon.AppManager.utils.FileUtils
import io.github.muntashirakon.io.IoUtils
import io.github.muntashirakon.io.Path
import io.github.muntashirakon.io.Paths
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.util.zip.ZipInputStream

object ApkUtils {
    @JvmField
    val TAG: String = ApkUtils::class.java.simpleName

    const val EXT_APK = ".apk"\nconst val EXT_APKS = ".apks"\nprivate val sLock = Any()
    private const val MANIFEST_FILE = "AndroidManifest.xml"\n@JvmStatic
    @WorkerThread
    @Throws(IOException::class)
    fun getSharableApkFile(ctx: Context, packageInfo: PackageInfo): Path {
        synchronized(sLock) {
            val pm = ctx.packageManager
            val info = packageInfo.applicationInfo!!
            var outputName = Paths.sanitizeFilename(
                getFormattedApkFilename(ctx, packageInfo, pm), "_",
                Paths.SANITIZE_FLAG_FAT_ILLEGAL_CHARS or Paths.SANITIZE_FLAG_UNIX_RESERVED
            )
            if (outputName == null) outputName = info.packageName
            val tmpPublicSource: Path
            if (isSplitApk(info) || hasObbFiles(info.packageName, UserHandleHidden.getUserId(info.uid))) {
                // Split apk
                tmpPublicSource = Paths.get(File(FileUtils.getExternalCachePath(ContextUtils.getContext()), outputName + EXT_APKS))
                SplitApkExporter.saveApks(packageInfo, tmpPublicSource)
            } else {
                // Regular apk
                tmpPublicSource = Paths.get(File(FileUtils.getExternalCachePath(ContextUtils.getContext()), outputName + EXT_APK))
                IoUtils.copy(Paths.get(info.publicSourceDir), tmpPublicSource)
            }
            return tmpPublicSource
        }
    }

    /**
     * Backup the given apk (both root and no-root). This is similar to apk sharing feature except
     * that these are saved at /sdcard/AppManager/apks
     */
    @JvmStatic
    @WorkerThread
    @Throws(IOException::class, PackageManager.NameNotFoundException::class, RemoteException::class)
    fun backupApk(ctx: Context, packageName: String, @UserIdInt userId: Int) {
        val backupPath = BackupItems.getApkBackupDirectory()
        // Fetch package info
        val pm = ctx.packageManager
        val packageInfo = PackageManagerCompat.getPackageInfo(
            packageName,
            PackageManagerCompat.MATCH_UNINSTALLED_PACKAGES or PackageManager.GET_SHARED_LIBRARY_FILES
                    or PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, userId
        )
        val info = packageInfo.applicationInfo!!
        var outputName = Paths.sanitizeFilename(
            getFormattedApkFilename(ctx, packageInfo, pm), "_",
            Paths.SANITIZE_FLAG_FAT_ILLEGAL_CHARS or Paths.SANITIZE_FLAG_UNIX_RESERVED
        )
        if (outputName == null) outputName = packageName
        val apkFile: Path
        if (isSplitApk(info) || hasObbFiles(packageName, userId)) {
            // Split apk
            apkFile = backupPath.createNewFile(outputName + EXT_APKS, null)
            SplitApkExporter.saveApks(packageInfo, apkFile)
        } else {
            // Regular apk
            apkFile = backupPath.createNewFile(outputName + EXT_APK, null)
            IoUtils.copy(Paths.get(info.publicSourceDir), apkFile)
        }
    }

    @JvmStatic
    private fun getFormattedApkFilename(
        context: Context, packageInfo: PackageInfo,
        pm: PackageManager
    ): String {
        // TODO: 15/3/22 Optimize this
        var apkName = AppPref.getString(AppPref.PrefKey.PREF_SAVED_APK_FORMAT_STR)
            .replace("%label%", packageInfo.applicationInfo!!.loadLabel(pm).toString())
            .replace("%package_name%", packageInfo.packageName)
            .replace("%version%", packageInfo.versionName ?: "")
            .replace("%version_code%", PackageInfoCompat.getLongVersionCode(packageInfo).toString())
            .replace("%target_sdk%", packageInfo.applicationInfo!!.targetSdkVersion.toString())
            .replace("%datetime%", DateUtils.formatDateTime(context, System.currentTimeMillis()))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            apkName = apkName.replace("%min_sdk%", packageInfo.applicationInfo!!.minSdkVersion.toString())
        }
        return apkName
    }

    @JvmStatic
    fun isSplitApk(info: ApplicationInfo): Boolean {
        return info.splitPublicSourceDirs != null && info.splitPublicSourceDirs!!.isNotEmpty()
    }

    @JvmStatic
    @Throws(ApkFile.ApkFileException::class)
    fun getManifestFromApk(apkFile: File): ByteBuffer {
        try {
            RandomAccessFile(apkFile, "r").use { `in` ->
                val apk = DataSources.asDataSource(`in`)
                val apkSections: com.android.apksig.apk.ApkUtils.ZipSections = try {
                    com.android.apksig.apk.ApkUtils.findZipSections(apk)
                } catch (e: ZipFormatException) {
                    throw ApkFile.ApkFileException("Malformed APK: not a ZIP archive", e)
                }
                val cdRecords: List<CentralDirectoryRecord> = try {
                    ZipUtils.parseZipCentralDirectory(apk, apkSections)
                } catch (e: ApkFormatException) {
                    throw ApkFile.ApkFileException(e.message, e)
                }
                return try {
                    getAndroidManifestFromApk(
                        cdRecords,
                        apk.slice(0, apkSections.zipCentralDirectoryOffset)
                    )
                } catch (e: ApkFormatException) {
                    throw ApkFile.ApkFileException(e.message, e)
                } catch (e: ZipFormatException) {
                    throw ApkFile.ApkFileException("Failed to read $MANIFEST_FILE", e)
                }
            }
        } catch (e: IOException) {
            throw ApkFile.ApkFileException(e.message, e)
        }
    }

    @JvmStatic
    @Throws(ApkFile.ApkFileException::class)
    fun getManifestFromApk(apkInputStream: InputStream): ByteBuffer {
        try {
            ZipInputStream(BufferedInputStream(apkInputStream)).use { zipInputStream ->
                var zipEntry: java.util.zip.ZipEntry?
                while (zipInputStream.nextEntry.also { zipEntry = it } != null) {
                    if (zipEntry!!.name != MANIFEST_FILE) {
                        continue
                    }
                    val buffer = ByteArrayOutputStream()
                    val buf = ByteArray(IoUtils.DEFAULT_BUFFER_SIZE)
                    var n: Int
                    while (-1 != zipInputStream.read(buf).also { n = it }) {
                        buffer.write(buf, 0, n)
                    }
                    return ByteBuffer.wrap(buffer.toByteArray())
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "Could not fetch AndroidManifest.xml from APK stream, trying an alternative...", e)
        }
        // This could be due to a Zip error, try caching the APK
        val cachedApk: File = try {
            FileCache.getGlobalFileCache().getCachedFile(apkInputStream, "apk")
        } catch (e: IOException) {
            throw ApkFile.ApkFileException("Could not cache the APK file", e)
        }
        val byteBuffer: ByteBuffer
        try {
            byteBuffer = getManifestFromApk(cachedApk)
        } finally {
            FileCache.getGlobalFileCache().delete(cachedApk)
        }
        return byteBuffer
    }

    @JvmStatic
    @Throws(ApkFile.ApkFileException::class)
    fun getManifestAttributes(manifestBytes: ByteBuffer): HashMap<String, String> {
        try {
            BlockReader(manifestBytes.array()).use { reader ->
                val manifestAttrs = HashMap<String, String>()
                val xmlBlock = ResXmlDocument()
                try {
                    xmlBlock.readBytes(reader)
                } catch (e: IOException) {
                    throw ApkFile.ApkFileException(e)
                }
                xmlBlock.setPackageBlock(AndroidBinXmlDecoder.getFrameworkPackageBlock())
                val resManifestElement = xmlBlock.documentElement
                // manifest
                if ("manifest" != resManifestElement.name) {
                    throw ApkFile.ApkFileException("No manifest found.")
                }
                var attrIt: Iterator<ResXmlAttribute> = resManifestElement.attributes
                var attr: ResXmlAttribute
                var attrName: String?
                while (attrIt.hasNext()) {
                    attr = attrIt.next()
                    attrName = attr.name
                    if (TextUtils.isEmpty(attrName)) {
                        continue
                    }
                    manifestAttrs[attrName!!] = attr.valueAsString
                }
                // application
                var resApplicationElement: com.reandroid.arsc.chunk.xml.ResXmlElement? = null
                val resXmlElementIt = resManifestElement.getElements("application")
                if (resXmlElementIt.hasNext()) {
                    resApplicationElement = resXmlElementIt.next()
                }
                if (resXmlElementIt.hasNext()) {
                    throw ApkFile.ApkFileException(""manifest" has duplicate "application" tags.")
                }
                if (resApplicationElement == null) {
                    Log.w(TAG, "No application tag found while parsing APK.")
                    return manifestAttrs
                }
                attrIt = resApplicationElement.attributes
                while (attrIt.hasNext()) {
                    attr = attrIt.next()
                    attrName = attr.name
                    if (TextUtils.isEmpty(attrName)) {
                        continue
                    }
                    if (manifestAttrs.containsKey(attrName)) {
                        Log.w(TAG, "Ignoring invalid attribute in the application tag: $attrName")
                        continue
                    }
                    manifestAttrs[attrName!!] = attr.valueAsString
                }
                return manifestAttrs
            }
        }
    }

    @JvmStatic
    fun hasObbFiles(packageName: String, @UserIdInt userId: Int): Boolean {
        return try {
            val list = getObbDir(packageName, userId).listFiles()
            list != null && list.isNotEmpty()
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
            false
        }
    }

    @JvmStatic
    @Throws(FileNotFoundException::class)
    fun getObbDir(packageName: String, @UserIdInt userId: Int): Path {
        // Get writable OBB directory
        val obbDir = getWritableExternalDirectory(userId)
            .findFile("Android")
            .findFile("obb")
            .findFile(packageName)
        return Paths.get(obbDir.uri)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun getOrCreateObbDir(packageName: String, @UserIdInt userId: Int): Path {
        // Get writable OBB directory
        val obbDir = getWritableExternalDirectory(userId)
            .findOrCreateDirectory("Android")
            .findOrCreateDirectory("obb")
            .findOrCreateDirectory(packageName)
        return Paths.get(obbDir.uri)
    }

    @JvmStatic
    @Throws(FileNotFoundException::class)
    fun getWritableExternalDirectory(@UserIdInt userId: Int): Path {
        // Get the first writable external storage directory
        val userEnvironment = OsEnvironment.getUserEnvironment(userId)
        val extDirs = userEnvironment.externalDirs
        var writableExtDir: Path? = null
        for (extDir in extDirs) {
            if (extDir.canWrite() || extDir.filePath!!.startsWith("/storage/emulated")) {
                writableExtDir = extDir
                break
            }
        }
        if (writableExtDir == null) {
            throw FileNotFoundException("Couldn't find any writable Obb dir")
        }
        return writableExtDir
    }

    @JvmStatic
    fun getDensityFromName(densityName: String?): Int {
        val density = StaticDataset.DENSITY_NAME_TO_DENSITY[densityName]
            ?: throw IllegalArgumentException("Unknown density $densityName")
        return density
    }

    @JvmStatic
    @Throws(IOException::class, ApkFormatException::class, ZipFormatException::class)
    private fun getAndroidManifestFromApk(
        cdRecords: List<CentralDirectoryRecord>, lhfSection: DataSource
    ): ByteBuffer {
        val androidManifestCdRecord = findCdRecord(cdRecords, MANIFEST_FILE)
            ?: throw ApkFormatException("Missing $MANIFEST_FILE")
        return ByteBuffer.wrap(
            LocalFileRecord.getUncompressedData(
                lhfSection, androidManifestCdRecord, lhfSection.size()
            )
        )
    }

    @JvmStatic
    private fun findCdRecord(
        cdRecords: List<CentralDirectoryRecord>, name: String
    ): CentralDirectoryRecord? {
        for (cdRecord in cdRecords) {
            if (name == cdRecord.name) {
                return cdRecord
            }
        }
        return null
    }
}
