// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.convert

import android.annotation.SuppressLint
import android.annotation.UserIdInt
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.RemoteException
import android.os.UserHandleHidden
import io.github.muntashirakon.AppManager.backup.*
import io.github.muntashirakon.AppManager.backup.BackupManager.CERT_PREFIX
import io.github.muntashirakon.AppManager.backup.BackupManager.getExt
import io.github.muntashirakon.AppManager.backup.struct.BackupMetadataV2
import io.github.muntashirakon.AppManager.backup.struct.BackupMetadataV5
import io.github.muntashirakon.AppManager.crypto.CryptoException
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.self.filecache.FileCache
import io.github.muntashirakon.AppManager.settings.Prefs
import io.github.muntashirakon.AppManager.utils.*
import io.github.muntashirakon.AppManager.utils.TarUtils.DEFAULT_SPLIT_SIZE
import io.github.muntashirakon.io.IoUtils
import io.github.muntashirakon.io.Path
import io.github.muntashirakon.io.Paths
import io.github.muntashirakon.io.SplitOutputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import java.io.*
import java.util.*
import java.util.regex.Pattern
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class SBConverter(xmlFile: Path) : Converter() {
    companion object {
        @JvmField
        val TAG: String = SBConverter::class.java.simpleName
    }

    private val mBackupLocation: Path = xmlFile.parent!!
    @UserIdInt
    private val mUserId: Int = UserHandleHidden.myUserId()
    override val packageName: String = Paths.trimPathExtension(xmlFile.getName())
    private val mBackupTime: Long = xmlFile.lastModified()
    private val mPm: PackageManager = ContextUtils.getContext().packageManager
    private val mFilesToBeDeleted = mutableListOf<Path>(xmlFile)

    private var mChecksum: BackupItems.Checksum? = null
    private var mDestMetadata: BackupMetadataV5? = null
    private var mBackupItem: BackupItems.BackupItem? = null
    private var mPackageInfo: PackageInfo? = null
    private var mCachedApk: Path? = null

    override fun convert() {
        // Source metadata
        val sourceMetadata = generateMetadata()
        // Simulate a backup creation
        try {
            mBackupItem = BackupItems.createBackupItemGracefully(mUserId, "SB", packageName)
        } catch (e: IOException) {
            throw BackupException("Could not get backup files.", e)
        }
        var backupSuccess = false
        try {
            try {
                // Destination metadata
                mDestMetadata = ConvertUtils.getV5Metadata(sourceMetadata, mBackupItem!!)
            } catch (e: CryptoException) {
                throw BackupException("Failed to get crypto ${mDestMetadata!!.info.crypto}", e)
            }
            try {
                mChecksum = mBackupItem!!.checksum
            } catch (e: IOException) {
                throw BackupException("Failed to create checksum file.", e)
            }
            // Backup icon
            backupIcon()
            if (mDestMetadata!!.info.flags.backupApkFiles()) {
                backupApkFile()
            }
            if (mDestMetadata!!.info.flags.backupData()) {
                backupData()
            }
            // Write modified metadata
            try {
                val filenameChecksumMap = MetadataManager.writeMetadata(mDestMetadata!!, mBackupItem!!)
                for ((key, value) in filenameChecksumMap) {
                    mChecksum!!.add(key, value)
                }
            } catch (e: IOException) {
                throw BackupException("Failed to write metadata.")
            }
            mChecksum!!.close()
            // Encrypt checksum
            try {
                mBackupItem!!.encrypt(arrayOf(mChecksum!!.file))
            } catch (e: IOException) {
                throw BackupException("Failed to encrypt checksums.txt")
            }
            // Replace current backup
            try {
                mBackupItem!!.commit()
            } catch (e: IOException) {
                throw BackupException("Could not finalise backup.", e)
            }
            backupSuccess = true
        } catch (e: BackupException) {
            throw e
        } catch (th: Throwable) {
            throw BackupException("Unknown error occurred.", th)
        } finally {
            mBackupItem?.cleanup()
            mCachedApk?.requireParent()?.delete()
            if (backupSuccess) {
                BackupUtils.putBackupToDbAndBroadcast(ContextUtils.getContext(), mDestMetadata!!)
            }
        }
    }

    override fun cleanup() {
        for (file in mFilesToBeDeleted) {
            file.delete()
        }
    }

    private fun backupApkFile() {
        val sourceDir = mCachedApk!!.requireParent()
        // Get certificate checksums
        try {
            val checksums = ConvertUtils.getChecksumsFromApk(mCachedApk!!, mDestMetadata!!.info.checksumAlgo)
            for (i in checksums.indices) {
                mChecksum!!.add(CERT_PREFIX + i, checksums[i])
            }
        } catch (ignore: Exception) {
        }
        // Backup APK files
        val apkFiles = ArrayUtils.appendElement(String::class.java, mDestMetadata!!.metadata.splitConfigs, mDestMetadata!!.metadata.apkName)
        val sourceBackupFilePrefix = BackupUtils.getSourceFilePrefix(getExt(mDestMetadata!!.info.tarType))
        var sourceFiles: Array<Path> = try {
            // We have to specify APK files because the folder may contain many
            TarUtils.create(
                mDestMetadata!!.info.tarType, sourceDir, mBackupItem!!.unencryptedBackupPath, sourceBackupFilePrefix,
                apkFiles, null, null, false
            ).toTypedArray()
        } catch (th: Throwable) {
            throw BackupException("APK files backup is requested but no APK files have been backed up.", th)
        }
        try {
            sourceFiles = mBackupItem!!.encrypt(sourceFiles)
        } catch (e: IOException) {
            throw BackupException("Failed to encrypt ${sourceFiles.contentToString()}")
        }
        for (file in sourceFiles) {
            mChecksum!!.add(file.getName(), DigestUtils.getHexDigest(mDestMetadata!!.info.checksumAlgo, file))
        }
    }

    private fun backupData() {
        val dataFiles = mutableListOf<Path>()
        try {
            if (mDestMetadata!!.info.flags.backupInternalData()) {
                dataFiles.add(getIntDataFile())
            }
            if (mDestMetadata!!.info.flags.backupExternalData()) {
                dataFiles.add(getExtDataFile())
            }
            if (mDestMetadata!!.info.flags.backupMediaObb()) {
                dataFiles.add(getObbFile())
            }
        } catch (e: FileNotFoundException) {
            throw BackupException("Could not get data files", e)
        }
        val tarType = mDestMetadata!!.info.tarType
        var i = 0
        for (dataFile in dataFiles) {
            val dataBackupFilePrefix = BackupUtils.getDataFilePrefix(i++, getExt(tarType))
            try {
                ZipInputStream(BufferedInputStream(dataFile.openInputStream())).use { zis ->
                    SplitOutputStream(
                        mBackupItem!!.unencryptedBackupPath,
                        dataBackupFilePrefix,
                        DEFAULT_SPLIT_SIZE
                    ).use { sos ->
                        BufferedOutputStream(sos).use { bos ->
                            TarUtils.createCompressedStream(bos, tarType).use { os ->
                                TarArchiveOutputStream(os).use { tos ->
                                    tos.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                                    tos.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)
                                    var zipEntry: ZipEntry?
                                    while (zis.nextEntry.also { zipEntry = it } != null) {
                                        var tmpFile: File? = null
                                        if (!zipEntry!!.isDirectory) {
                                            // We need to use a temporary file
                                            tmpFile = FileCache.getGlobalFileCache().createCachedFile(dataFile.getExtension())
                                            FileOutputStream(tmpFile).use { fos ->
                                                IoUtils.copy(zis, fos)
                                            }
                                        }
                                        val fileName = zipEntry!!.name.replaceFirst(Pattern.quote("$packageName/").toRegex(), "")
                                        if (fileName.isEmpty()) continue
                                        // New tar entry
                                        val tarArchiveEntry = TarArchiveEntry(fileName)
                                        if (tmpFile != null) {
                                            tarArchiveEntry.size = tmpFile.length()
                                        }
                                        tos.putArchiveEntry(tarArchiveEntry)
                                        if (tmpFile != null) {
                                            // Copy from the temporary file
                                            try {
                                                FileInputStream(tmpFile).use { fis ->
                                                    IoUtils.copy(fis, tos)
                                                }
                                            } finally {
                                                FileCache.getGlobalFileCache().delete(tmpFile)
                                            }
                                        }
                                        tos.closeArchiveEntry()
                                    }
                                    tos.finish()
                                }
                                // Encrypt backups
                                val newBackupFiles = mBackupItem!!.encrypt(sos.files.toTypedArray())
                                for (file in newBackupFiles) {
                                    mChecksum!!.add(
                                        file.getName(),
                                        DigestUtils.getHexDigest(mDestMetadata!!.info.checksumAlgo, file)
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (e: IOException) {
                throw BackupException("Backup failed for $dataFile", e)
            }
        }
    }

    @SuppressLint("WrongConstant")
    private fun generateMetadata(): BackupMetadataV2 {
        val metadataV2 = BackupMetadataV2()
        mCachedApk = FileUtils.getTempPath(packageName, "base.apk")
        try {
            getApkFile().openInputStream().use { pis ->
                mCachedApk!!.openOutputStream().use { fos ->
                    IoUtils.copy(pis, fos)
                }
            }
            mFilesToBeDeleted.add(getApkFile())
        } catch (e: IOException) {
            throw BackupException("Could not cache APK file", e)
        }
        val filePath = mCachedApk!!.getFilePath()!!
        val packageInfo = mPm.getPackageArchiveInfo(filePath, 0)
            ?: throw BackupException("Could not fetch package info")
        mPackageInfo = packageInfo
        Objects.requireNonNull(mPackageInfo!!.applicationInfo)
        mPackageInfo!!.applicationInfo!!.publicSourceDir = filePath
        mPackageInfo!!.applicationInfo!!.sourceDir = filePath
        val applicationInfo = mPackageInfo!!.applicationInfo!!

        if (mPackageInfo!!.packageName != packageName) {
            throw BackupException("Package name mismatch: Expected=$packageName, Actual=${mPackageInfo!!.packageName}")
        }

        metadataV2.label = applicationInfo.loadLabel(mPm).toString()
        metadataV2.packageName = packageName
        metadataV2.versionName = mPackageInfo!!.versionName!!
        metadataV2.versionCode = PackageInfoCompat.getLongVersionCode(mPackageInfo!!)
        metadataV2.isSystem = false
        metadataV2.hasRules = false
        metadataV2.backupTime = mBackupTime
        metadataV2.crypto = CryptoUtils.MODE_NO_ENCRYPTION
        metadataV2.apkName = "base.apk"\n// Backup flags
        val flags = BackupFlags(BackupFlags.BACKUP_APK_FILES)
        try {
            mFilesToBeDeleted.add(getObbFile())
            flags.addFlag(BackupFlags.BACKUP_EXT_OBB_MEDIA)
        } catch (ignore: FileNotFoundException) {
        }
        try {
            mFilesToBeDeleted.add(getIntDataFile())
            flags.addFlag(BackupFlags.BACKUP_INT_DATA)
            flags.addFlag(BackupFlags.BACKUP_CACHE)
        } catch (ignore: FileNotFoundException) {
        }
        try {
            mFilesToBeDeleted.add(getExtDataFile())
            flags.addFlag(BackupFlags.BACKUP_EXT_DATA)
            flags.addFlag(BackupFlags.BACKUP_CACHE)
        } catch (ignore: FileNotFoundException) {
        }
        metadataV2.flags = flags
        metadataV2.dataDirs = ConvertUtils.getDataDirs(
            packageName, mUserId, flags.backupInternalData(),
            flags.backupExternalData(), flags.backupMediaObb()
        )
        try {
            mFilesToBeDeleted.add(getSplitFile())
            metadataV2.isSplitApk = true
        } catch (e: FileNotFoundException) {
            metadataV2.isSplitApk = false
        }
        try {
            metadataV2.splitConfigs = cacheAndGetSplitConfigs()
        } catch (e: IOException) {
            throw BackupException("Could not cache splits", e)
        } catch (e: RemoteException) {
            throw BackupException("Could not cache splits", e)
        }
        metadataV2.userId = mUserId
        metadataV2.tarType = Prefs.BackupRestore.getCompressionMethod()
        metadataV2.keyStore = false
        metadataV2.installer = Prefs.Installer.getInstallerPackageName()
        return metadataV2
    }

    private fun getApkFile(): Path {
        return mBackupLocation.findFile("$packageName.app")
    }

    private fun getSplitFile(): Path {
        return mBackupLocation.findFile("$packageName.splits")
    }

    private fun getObbFile(): Path {
        return mBackupLocation.findFile("$packageName.exp")
    }

    private fun getIntDataFile(): Path {
        return mBackupLocation.findFile("$packageName.dat")
    }

    private fun getExtDataFile(): Path {
        return mBackupLocation.findFile("$packageName.extdat")
    }

    @Throws(IOException::class, RemoteException::class)
    private fun cacheAndGetSplitConfigs(): Array<String> {
        val splits = mutableListOf<String>()
        val splitFile: Path = try {
            getSplitFile()
        } catch (e: FileNotFoundException) {
            return emptyArray()
        }
        BufferedInputStream(splitFile.openInputStream()).use { bis ->
            ZipInputStream(bis).use { zis ->
                var zipEntry: ZipEntry?
                while (zis.nextEntry.also { zipEntry = it } != null) {
                    if (zipEntry!!.isDirectory) continue
                    val splitName = FileUtils.getFilenameFromZipEntry(zipEntry!!)
                    splits.add(splitName)
                    val file = mCachedApk!!.requireParent().findOrCreateFile(splitName, null)
                    try {
                        file.openOutputStream().use { fos -> IoUtils.copy(zis, fos) }
                    } catch (e: IOException) {
                        file.delete()
                        throw e
                    }
                }
            }
        }
        return splits.toTypedArray()
    }

    private fun backupIcon() {
        try {
            val iconFile = mBackupItem!!.iconFile
            iconFile.openOutputStream().use { outputStream ->
                val bitmap = UIUtils.getBitmapFromDrawable(mPackageInfo!!.applicationInfo!!.loadIcon(mPm))
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                outputStream.flush()
            }
        } catch (th: Throwable) {
            Log.w(TAG, "Could not back up icon.", th)
        }
    }
}
