// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup

import android.annotation.UserIdInt
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import io.github.muntashirakon.AppManager.apk.ApkSource
import io.github.muntashirakon.AppManager.backup.BackupManager.CERT_PREFIX
import io.github.muntashirakon.AppManager.backup.BackupManager.DATA_BACKUP_SPECIAL_ADB
import io.github.muntashirakon.AppManager.backup.BackupManager.KEYSTORE_PREFIX
import io.github.muntashirakon.AppManager.backup.BackupManager.getExt
import io.github.muntashirakon.AppManager.backup.struct.BackupMetadataV5
import io.github.muntashirakon.AppManager.compat.BackupCompat
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat
import io.github.muntashirakon.AppManager.crypto.CryptoException
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.misc.OsEnvironment
import io.github.muntashirakon.AppManager.progress.ProgressHandler
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker
import io.github.muntashirakon.AppManager.settings.Prefs
import io.github.muntashirakon.AppManager.utils.*
import io.github.muntashirakon.io.Path
import io.github.muntashirakon.io.Paths
import java.io.Closeable
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.*

internal class BackupOp @Throws(BackupException::class) constructor(
    private val mPackageName: String,
    private val mBackupFlags: BackupFlags,
    private val mBackupItem: BackupItems.BackupItem,
    @UserIdInt private val mUserId: Int
) : Closeable {
    val metadata: BackupMetadataV5
    private val mPackageInfo: PackageInfo
    private val mApplicationInfo: ApplicationInfo
    private val mChecksum: BackupItems.Checksum
    private val mPm: PackageManager = ContextUtils.getContext().packageManager

    init {
        try {
            mPackageInfo = PackageManagerCompat.getPackageInfo(
                mPackageName,
                PackageManager.GET_META_DATA or PackageManagerCompat.GET_SIGNING_CERTIFICATES or PackageManager.GET_PERMISSIONS
                        or PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, mUserId
            )!!
            mApplicationInfo = mPackageInfo.applicationInfo!!
            // Override existing metadata
            metadata = setupMetadataAndCrypto()
        } catch (e: Throwable) {
            mBackupItem.cleanup()
            throw BackupException("Failed to setup metadata.", e)
        }
        try {
            mChecksum = mBackupItem.checksum
            val certChecksums = PackageUtils.getSigningCertChecksums(metadata.info.checksumAlgo, mPackageInfo, false)
            for (i in certChecksums.indices) {
                mChecksum.add(CERT_PREFIX + i, certChecksums[i])
            }
        } catch (e: Throwable) {
            mBackupItem.cleanup()
            throw BackupException("Failed to create checksum file.", e)
        }
    }

    override fun close() {
        mBackupItem.cleanup()
    }

    @Throws(BackupException::class)
    fun runBackup(progressHandler: ProgressHandler?) {
        try {
            // Fail backup if the app has items in Android KeyStore and backup isn't enabled
            if (mBackupFlags.backupData() && metadata.metadata.keyStore && !Prefs.BackupRestore.backupAppsWithKeyStore()) {
                throw BackupException("The app has keystore items and KeyStore backup isn't enabled.")
            }
            incrementProgress(progressHandler)
            // Backup icon
            backupIcon()
            // Backup source
            if (mBackupFlags.backupApkFiles()) {
                backupApkFiles()
                incrementProgress(progressHandler)
            }
            // Backup data
            if (mBackupFlags.backupData()) {
                backupData()
                // Backup KeyStore
                if (metadata.metadata.keyStore) {
                    backupKeyStore()
                }
                incrementProgress(progressHandler)
            }
            // Backup extras
            if (mBackupFlags.backupExtras()) {
                backupExtras()
                incrementProgress(progressHandler)
            }
            // Export rules
            if (metadata.metadata.hasRules) {
                backupRules()
                incrementProgress(progressHandler)
            }
            // Write modified metadata
            try {
                val filenameChecksumMap = MetadataManager.writeMetadata(metadata, mBackupItem)
                for ((key, value) in filenameChecksumMap) {
                    mChecksum.add(key, value)
                }
            } catch (e: IOException) {
                throw BackupException("Failed to write metadata.", e)
            }
            mChecksum.close()
            // Encrypt checksum
            try {
                mBackupItem.encrypt(arrayOf(mChecksum.file))
            } catch (e: IOException) {
                throw BackupException("Failed to write checksums.txt", e)
            }
            // Replace current backup
            try {
                mBackupItem.commit()
            } catch (e: IOException) {
                throw BackupException("Could not finalise backup.", e)
            }
        } catch (e: BackupException) {
            throw e
        } catch (th: Throwable) {
            throw BackupException("Unknown error occurred.", th)
        }
    }

    @Throws(CryptoException::class)
    private fun setupMetadataAndCrypto(): BackupMetadataV5 {
        // We don't need to backup custom users or multiple backup flags
        // mBackupFlags.removeFlag(BackupFlags.BACKUP_CUSTOM_USERS | BackupFlags.BACKUP_MULTIPLE);
        val backupName = mBackupItem.backupName
        val backupTime = System.currentTimeMillis()
        var tarType = Prefs.BackupRestore.getCompressionMethod()
        // Verify tar type
        if (ArrayUtils.indexOf(BackupUtils.TAR_TYPES, tarType) == -1) {
            // Unknown tar type, set default
            tarType = TarUtils.TAR_GZIP
        }
        val crypto = CryptoUtils.mode
        val cryptoHelper = BackupCryptSetupHelper(crypto, MetadataManager.getCurrentBackupMetaVersion())
        mBackupItem.setCrypto(cryptoHelper.crypto)
        val backupInfo = BackupMetadataV5.Info(
            backupTime, mBackupFlags,
            mUserId, tarType, DigestUtils.SHA_256, crypto, cryptoHelper.iv,
            cryptoHelper.aes, cryptoHelper.keyIds
        )
        backupInfo.setBackupItem(mBackupItem)
        val metadata = BackupMetadataV5.Metadata(backupName)
        metadata.keyStore = KeyStoreUtils.hasKeyStore(mApplicationInfo.uid)
        metadata.label = mApplicationInfo.loadLabel(mPm).toString()
        metadata.packageName = mPackageName
        metadata.versionName = mPackageInfo.versionName!!
        metadata.versionCode = PackageInfoCompat.getLongVersionCode(mPackageInfo)
        metadata.apkName = File(mApplicationInfo.sourceDir).name
        var dataDirs: Array<String>? = null
        // ADB backup support logic simplified for this snippet
        if (dataDirs == null) {
            // Non-ADB backup: default
            dataDirs = io.github.muntashirakon.AppManager.backup.BackupUtils.getDataDirectories(
                mApplicationInfo, mBackupFlags.backupInternalData(),
                mBackupFlags.backupExternalData(), mBackupFlags.backupMediaObb()
            ).toTypedArray()
        }
        metadata.dataDirs = dataDirs
        metadata.isSystem = (mApplicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        metadata.isSplitApk = false
        try {
            ApkSource.getApkSource(mApplicationInfo).resolve().use { apkFile ->
                if (apkFile.isSplit) {
                    val apkEntries = apkFile.entries
                    val splitCount = apkEntries.size - 1
                    metadata.isSplitApk = splitCount > 0
                    val splits = Array(splitCount) { i -> apkEntries[i + 1].fileName }
                    metadata.splitConfigs = splits
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        metadata.splitConfigs = ArrayUtils.defeatNullable(metadata.splitConfigs)
        metadata.hasRules = false
        if (mBackupFlags.backupRules()) {
            ComponentsBlocker.getInstance(mPackageInfo.packageName, mUserId, false).use { cb ->
                metadata.hasRules = cb.entryCount() > 0
            }
        }
        metadata.installer = PackageManagerCompat.getInstallerPackageName(mPackageInfo.packageName, mUserId)
        return BackupMetadataV5(backupInfo, metadata)
    }

    private fun backupIcon() {
        try {
            val iconFile = mBackupItem.iconFile
            iconFile.openOutputStream().use { outputStream ->
                val bitmap = UIUtils.getMutableBitmapFromDrawable(mApplicationInfo.loadIcon(mPm))
                BitmapRandomizer.randomizePixel(bitmap)
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }
        } catch (e: IOException) {
            Log.w(TAG, "Could not back up icon.")
        }
    }

    @Throws(BackupException::class)
    private fun backupApkFiles() {
        val dataAppPath = OsEnvironment.getDataAppDirectory()
        val sourceBackupFilePrefix = BackupUtils.getSourceFilePrefix(getExt(metadata.info.tarType))
        var sourceDir = Paths.get(PackageUtils.getSourceDir(mApplicationInfo))
        if (dataAppPath == sourceDir) {
            // APK located inside /data/app directory
            // Backup only the apk file (no split apk support for this type of apk)
            sourceDir = try {
                sourceDir.findFile(metadata.metadata.apkName)
            } catch (e: FileNotFoundException) {
                throw BackupException("${metadata.metadata.apkName} not found at $sourceDir")
            }
        }
        var sourceFiles: Array<Path> = try {
            TarUtils.create(
                metadata.info.tarType, sourceDir, mBackupItem.requireUnencryptedBackupPath(), sourceBackupFilePrefix,
                arrayOf(".*\.apk"), null, null, false
            ).toTypedArray()
        } catch (th: Throwable) {
            throw BackupException("APK files backup is requested but no source directory has been backed up.", th)
        }
        try {
            sourceFiles = mBackupItem.encrypt(sourceFiles)
        } catch (e: IOException) {
            throw BackupException("Failed to encrypt ${sourceFiles.contentToString()}", e)
        }
        for (file in sourceFiles) {
            mChecksum.add(file.getName(), DigestUtils.getHexDigest(metadata.info.checksumAlgo, file))
        }
    }

    @Throws(BackupException::class)
    private fun backupData() {
        for (i in metadata.metadata.dataDirs!!.indices) {
            val backupDataDir = metadata.metadata.dataDirs!![i]
            if (backupDataDir == DATA_BACKUP_SPECIAL_ADB) {
                backupAdb(i)
            } else {
                backupDirectory(backupDataDir, i)
            }
        }
    }

    private fun backupDirectory(dir: String, index: Int) {
        val dataDirectoryInfo = BackupDataDirectoryInfo.getInfo(dir, mUserId)
        val dataSource = dataDirectoryInfo.getDirectory()
        if (!dataSource.exists()) return
        val dataBackupFilePrefix = BackupUtils.getDataFilePrefix(index, getExt(metadata.info.tarType))
        var dataFiles: Array<Path> = try {
            TarUtils.create(
                metadata.info.tarType, dataSource, mBackupItem.requireUnencryptedBackupPath(), dataBackupFilePrefix,
                null, BackupUtils.getExcludeDirs(!mBackupFlags.backupCache(), mApplicationInfo.publicSourceDir), null, false
            ).toTypedArray()
        } catch (th: Throwable) {
            // Log and skip if failed
            Log.w(TAG, "Failed to backup directory $dir", th)
            return
        }
        try {
            dataFiles = mBackupItem.encrypt(dataFiles)
        } catch (e: IOException) {
            Log.w(TAG, "Failed to encrypt data files for $dir", e)
            return
        }
        for (file in dataFiles) {
            mChecksum.add(file.getName(), DigestUtils.getHexDigest(metadata.info.checksumAlgo, file))
        }
    }

    private fun backupAdb(index: Int) {
        // ADB backup logic simplified
    }

    private fun backupKeyStore() {
        // KeyStore backup logic simplified
    }

    private fun backupExtras() {
        // Extras backup logic simplified
    }

    private fun backupRules() {
        // Rules backup logic simplified
    }

    companion object {
        val TAG: String = BackupOp::class.java.simpleName

        private fun incrementProgress(progressHandler: ProgressHandler?) {
            if (progressHandler == null) return
            val current = progressHandler.lastProgress + 1
            progressHandler.postUpdate(current)
        }
    }
}
