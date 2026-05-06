// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.convert

import android.annotation.UserIdInt
import android.os.UserHandleHidden
import android.text.TextUtils
import io.github.muntashirakon.AppManager.backup.*
import io.github.muntashirakon.AppManager.backup.BackupManager.CERT_PREFIX
import io.github.muntashirakon.AppManager.backup.BackupManager.getExt
import io.github.muntashirakon.AppManager.backup.struct.BackupMetadataV2
import io.github.muntashirakon.AppManager.backup.struct.BackupMetadataV5
import io.github.muntashirakon.AppManager.crypto.Crypto
import io.github.muntashirakon.AppManager.crypto.CryptoException
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.self.filecache.FileCache
import io.github.muntashirakon.AppManager.settings.Prefs
import io.github.muntashirakon.AppManager.utils.*
import io.github.muntashirakon.AppManager.utils.TarUtils.DEFAULT_SPLIT_SIZE
import io.github.muntashirakon.io.IoUtils
import io.github.muntashirakon.io.Path
import io.github.muntashirakon.io.SplitOutputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.json.JSONException
import org.json.JSONObject
import java.io.*
import java.util.*
import java.util.regex.Pattern
import java.util.zip.ZipInputStream

/**
 * A documentation about OAndBackup is located at
 * [GH#371](https://github.com/MuntashirAkon/AppManager/issues/371#issuecomment-818429082).
 */
class OABConverter(private val mBackupLocation: Path) : Converter() {
    companion object {
        @JvmField
        val TAG: String = OABConverter::class.java.simpleName
        const val PATH_SUFFIX = "oandbackups"\nprivate val SPECIAL_BACKUPS = listOf(
            "accounts",
            "appwidgets",
            "bluetooth",
            "data.usage.policy",
            "wallpaper",
            "wifi.access.points"\n)

        private const val MODE_UNSET = 0
        private const val MODE_APK = 1
        private const val MODE_DATA = 2
        private const val MODE_BOTH = 3

        private const val EXTERNAL_FILES = "external_files"\n}

    override val packageName: String = mBackupLocation.getName()
    @UserIdInt
    private val mUserId: Int = UserHandleHidden.myUserId()

    private var mChecksum: BackupItems.Checksum? = null
    private var mSourceMetadata: BackupMetadataV2? = null
    private var mSourceCryptoMode: String? = null
    private var mSourceCrypto: Crypto? = null
    private var mDestMetadata: BackupMetadataV5? = null
    private var mBackupItem: BackupItems.BackupItem? = null

    override fun convert() {
        if (SPECIAL_BACKUPS.contains(packageName)) {
            throw BackupException("Cannot convert special backup $packageName")
        }
        // Source metadata
        mSourceMetadata = readLogFile()
        // Simulate a backup creation
        try {
            mBackupItem = BackupItems.createBackupItemGracefully(mUserId, "OAndBackup", packageName)
        } catch (e: IOException) {
            throw BackupException("Could not get backup files.", e)
        }
        var backupSuccess = false
        try {
            try {
                // Destination metadata
                mDestMetadata = ConvertUtils.getV5Metadata(mSourceMetadata!!, mBackupItem!!)
            } catch (e: CryptoException) {
                throw BackupException("Failed to get crypto ${mDestMetadata!!.info.crypto}", e)
            }
            try {
                mChecksum = mBackupItem!!.checksum
            } catch (e: IOException) {
                throw BackupException("Failed to create checksum file.", e)
            }
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
            // Store checksum for metadata
            mChecksum!!.close()
            // Encrypt checksum
            try {
                mBackupItem!!.encrypt(arrayOf(mChecksum!!.file))
            } catch (e: IOException) {
                throw BackupException("Failed to encrypt checksums.txt", e)
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
            if (backupSuccess) {
                BackupUtils.putBackupToDbAndBroadcast(ContextUtils.getContext(), mDestMetadata!!)
            }
        }
    }

    override fun cleanup() {
        mSourceCrypto?.close()
        mBackupLocation.delete()
    }

    private fun readLogFile(): BackupMetadataV2 {
        return try {
            val metadataV2 = BackupMetadataV2()
            val logFile = mBackupLocation.findFile("$packageName.log")
            val jsonString = logFile.getContentAsString()
            if (TextUtils.isEmpty(jsonString)) throw JSONException("Empty JSON string.")
            val jsonObject = JSONObject(jsonString!!)
            metadataV2.label = jsonObject.getString("label")
            metadataV2.packageName = jsonObject.getString("packageName")
            metadataV2.versionName = jsonObject.getString("versionName")
            metadataV2.versionCode = jsonObject.getInt("versionCode").toLong()
            metadataV2.isSystem = jsonObject.optBoolean("isSystem")
            metadataV2.isSplitApk = false
            metadataV2.splitConfigs = emptyArray()
            metadataV2.hasRules = false
            metadataV2.backupTime = jsonObject.getLong("lastBackupMillis")
            metadataV2.crypto = if (jsonObject.optBoolean("isEncrypted")) CryptoUtils.MODE_OPEN_PGP else CryptoUtils.MODE_NO_ENCRYPTION
            mSourceCryptoMode = metadataV2.crypto
            mSourceCrypto = CryptoUtils.setupCrypto(metadataV2)
            metadataV2.apkName = File(jsonObject.getString("sourceDir")).name
            // Flags
            metadataV2.flags = BackupFlags(BackupFlags.BACKUP_MULTIPLE)
            val backupMode = jsonObject.optInt("backupMode", MODE_UNSET)
            if (backupMode == MODE_UNSET) {
                throw BackupException("Destination doesn't contain any backup.")
            }
            if (backupMode == MODE_APK || backupMode == MODE_BOTH) {
                if (mBackupLocation.hasFile(
                        CryptoUtils.getAppropriateFilename(
                            metadataV2.apkName,
                            mSourceCryptoMode!!
                        )
                    )
                ) {
                    metadataV2.flags.addFlag(BackupFlags.BACKUP_APK_FILES)
                } else {
                    throw BackupException("Destination doesn't contain any APK files.")
                }
            }
            if (backupMode == MODE_DATA || backupMode == MODE_BOTH) {
                var hasBackup = false
                if (mBackupLocation.hasFile(
                        CryptoUtils.getAppropriateFilename(
                            "$packageName.zip",
                            mSourceCryptoMode!!
                        )
                    )
                ) {
                    metadataV2.flags.addFlag(BackupFlags.BACKUP_INT_DATA)
                    hasBackup = true
                }
                if (mBackupLocation.hasFile(EXTERNAL_FILES) && mBackupLocation.findFile(EXTERNAL_FILES).hasFile(
                        CryptoUtils.getAppropriateFilename("$packageName.zip", mSourceCryptoMode!!)
                    )
                ) {
                    metadataV2.flags.addFlag(BackupFlags.BACKUP_EXT_DATA)
                    hasBackup = true
                }
                if (!hasBackup) {
                    throw BackupException("Destination doesn't contain any data files.")
                }
                metadataV2.flags.addFlag(BackupFlags.BACKUP_CACHE)
            }
            metadataV2.userId = UserHandleHidden.myUserId()
            metadataV2.dataDirs = ConvertUtils.getDataDirs(
                packageName, mUserId, metadataV2.flags
                    .backupInternalData(), metadataV2.flags.backupExternalData(), false
            )
            metadataV2.tarType = Prefs.BackupRestore.getCompressionMethod()
            metadataV2.keyStore = false
            metadataV2.installer = Prefs.Installer.getInstallerPackageName()
            metadataV2.version = 2 // Old version is used so that we know that it needs permission fixes
            metadataV2
        } catch (e: Exception) {
            when (e) {
                is JSONException, is IOException, is CryptoException ->
                    throw ExUtils.rethrowAsBackupException("Could not parse JSON file.", e)
                else -> throw e
            }
        }
    }

    private fun backupApkFile() {
        var baseApkFiles: Array<Path> = try {
            arrayOf(
                mBackupLocation.findFile(
                    CryptoUtils.getAppropriateFilename(
                        mSourceMetadata!!.apkName, mSourceCryptoMode!!
                    )
                )
            )
        } catch (e: FileNotFoundException) {
            throw BackupException("Could not get base.apk file.", e)
        }
        // Decrypt APK file if needed
        try {
            baseApkFiles = ConvertUtils.decryptSourceFiles(baseApkFiles, mSourceCrypto!!, mSourceCryptoMode!!, mBackupItem!!)
        } catch (e: IOException) {
            throw BackupException("Failed to decrypt ${baseApkFiles.contentToString()}", e)
        }
        // baseApkFiles should be a singleton array
        if (baseApkFiles.size != 1) {
            throw BackupException("Incorrect number of APK files: ${baseApkFiles.size}")
        }
        val baseApkFile = baseApkFiles[0]
        // Get certificate checksums
        try {
            val checksums = ConvertUtils.getChecksumsFromApk(baseApkFile, mDestMetadata!!.info.checksumAlgo)
            for (i in checksums.indices) {
                mChecksum!!.add(CERT_PREFIX + i, checksums[i])
            }
        } catch (ignore: Exception) {
        }
        // Backup APK file
        val sourceBackupFilePrefix = BackupUtils.getSourceFilePrefix(getExt(mDestMetadata!!.info.tarType))
        var sourceFiles: Array<Path> = try {
            TarUtils.create(
                mDestMetadata!!.info.tarType, baseApkFile, mBackupItem!!.unencryptedBackupPath, sourceBackupFilePrefix,
                arrayOf(".*\.apk"), null, null, false
            ).toTypedArray()
        } catch (th: Throwable) {
            throw BackupException("APK files backup is requested but no APK files have been backed up.", th)
        }
        // Overwrite with the new files
        try {
            sourceFiles = mBackupItem!!.encrypt(sourceFiles)
        } catch (e: IOException) {
            throw BackupException("Failed to encrypt ${sourceFiles.contentToString()}", e)
        }
        for (file in sourceFiles) {
            mChecksum!!.add(file.getName(), DigestUtils.getHexDigest(mDestMetadata!!.info.checksumAlgo, file))
        }
    }

    private fun backupData() {
        val dataFiles = mutableListOf<Path>()
        if (mDestMetadata!!.info.flags.backupInternalData()) {
            try {
                dataFiles.add(
                    mBackupLocation.findFile(
                        CryptoUtils.getAppropriateFilename(
                            "$packageName.zip",
                            mSourceCryptoMode!!
                        )
                    )
                )
            } catch (e: FileNotFoundException) {
                throw BackupException("Could not get internal data backup.", e)
            }
        }
        if (mDestMetadata!!.info.flags.backupExternalData()) {
            try {
                dataFiles.add(
                    mBackupLocation.findFile(EXTERNAL_FILES).findFile(
                        CryptoUtils.getAppropriateFilename(
                            "$packageName.zip", mSourceCryptoMode!!
                        )
                    )
                )
            } catch (e: FileNotFoundException) {
                throw BackupException("Could not get external data backup.", e)
            }
        }
        val tarType = mDestMetadata!!.info.tarType
        var i = 0
        for (dataFile in dataFiles) {
            var files = arrayOf(dataFile)
            // Decrypt APK file if needed
            try {
                files = ConvertUtils.decryptSourceFiles(files, mSourceCrypto!!, mSourceCryptoMode!!, mBackupItem!!)
            } catch (e: IOException) {
                throw BackupException("Failed to decrypt ${files.contentToString()}", e)
            }
            // baseApkFiles should be a singleton array
            if (files.size != 1) {
                throw BackupException("Incorrect number of APK files: ${files.size}")
            }
            val dataBackupFilePrefix = BackupUtils.getDataFilePrefix(i++, getExt(tarType))
            try {
                ZipInputStream(BufferedInputStream(files[0].openInputStream())).use { zis ->
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
                                            tmpFile = FileCache.getGlobalFileCache().createCachedFile(files[0].getExtension())
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
}
