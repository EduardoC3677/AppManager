// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.convert

import android.annotation.UserIdInt
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.UserHandleHidden
import android.util.Base64
import io.github.muntashirakon.AppManager.backup.*
import io.github.muntashirakon.AppManager.backup.BackupManager.CERT_PREFIX
import io.github.muntashirakon.AppManager.backup.BackupManager.getExt
import io.github.muntashirakon.AppManager.backup.struct.BackupMetadataV2
import io.github.muntashirakon.AppManager.backup.struct.BackupMetadataV5
import io.github.muntashirakon.AppManager.crypto.CryptoException
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.settings.Prefs
import io.github.muntashirakon.AppManager.utils.*
import io.github.muntashirakon.AppManager.utils.TarUtils.*
import io.github.muntashirakon.io.IoUtils
import io.github.muntashirakon.io.Path
import io.github.muntashirakon.io.Paths
import io.github.muntashirakon.io.SplitOutputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.CompressorInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.*
import java.util.*
import java.util.regex.Pattern

class TBConverter(propFile: Path) : Converter() {
    companion object {
        @JvmField
        val TAG: String = TBConverter::class.java.simpleName
        const val PATH_SUFFIX = "TitaniumBackup"\nprivate const val INTERNAL_PREFIX = "data/data/"\nprivate const val EXTERNAL_PREFIX = "data/data/.external."\n}

    private val mBackupLocation: Path = propFile.parent!!
    @UserIdInt
    private val mUserId: Int = UserHandleHidden.myUserId()
    private val mPropFile: Path = propFile
    override val packageName: String?
    private val mBackupTime: Long
    private val mFilesToBeDeleted = mutableListOf<Path>(propFile)

    private var mChecksum: BackupItems.Checksum? = null
    private var mSourceMetadata: BackupMetadataV2? = null
    private var mDestMetadata: BackupMetadataV5? = null
    private var mBackupItem: BackupItems.BackupItem? = null
    private var mIcon: Bitmap? = null

    init {
        val dirtyName = propFile.getName()
        val idx = dirtyName.indexOf('-')
        packageName = if (idx == -1) null else dirtyName.substring(0, idx)
        mBackupTime = propFile.lastModified() // TODO: Grab from the file name
    }

    override fun convert() {
        if (packageName == null) {
            throw BackupException("Could not read package name.")
        }
        // Source metadata
        mSourceMetadata = readPropFile()
        // Simulate a backup creation
        try {
            mBackupItem = BackupItems.createBackupItemGracefully(mUserId, "TB", packageName)
        } catch (e: IOException) {
            throw BackupException("Could not get backup files", e)
        }
        var backupSuccess = false
        try {
            try {
                // Destination metadata
                mDestMetadata = ConvertUtils.getV5Metadata(mSourceMetadata!!, mBackupItem!!)
                // Destination APK will be renamed
                mDestMetadata!!.metadata.apkName = "base.apk"\n} catch (e: CryptoException) {
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
            // Replace current backup:
            // There's hardly any chance of getting a false here but checks are done anyway.
            try {
                mBackupItem!!.commit()
            } catch (e: Exception) {
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
        for (file in mFilesToBeDeleted) {
            file.delete()
        }
    }

    private fun backupApkFile() {
        // Decompress APK file
        val baseApkFile = FileUtils.getTempPath(packageName!!, mDestMetadata!!.metadata.apkName)
        try {
            BufferedInputStream(getApkFile(mSourceMetadata!!.apkName, mSourceMetadata!!.tarType).openInputStream()).use { bis ->
                val `is`: CompressorInputStream = when (mSourceMetadata!!.tarType) {
                    TAR_GZIP -> GzipCompressorInputStream(bis, true)
                    TAR_BZIP2 -> BZip2CompressorInputStream(bis, true)
                    else -> {
                        baseApkFile.requireParent().delete()
                        throw BackupException("Invalid source compression type: ${mSourceMetadata!!.tarType}")
                    }
                }
                try {
                    baseApkFile.openOutputStream().use { fos ->
                        // The whole file is the APK
                        IoUtils.copy(`is`, fos)
                    }
                } finally {
                    `is`.close()
                }
            }
        } catch (e: IOException) {
            baseApkFile.requireParent().delete()
            throw BackupException("Couldn't decompress ${mSourceMetadata!!.apkName}", e)
        }
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
        } finally {
            baseApkFile.requireParent().delete()
        }
        // Overwrite with the new files
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
        val dataFile: Path = try {
            getDataFile(Paths.trimPathExtension(mPropFile.getName()), mSourceMetadata!!.tarType)
        } catch (e: FileNotFoundException) {
            throw BackupException("Could not get data file", e)
        }
        val tarType = mDestMetadata!!.info.tarType
        var i = 0
        var intBackupFilePrefix: String? = null
        var extBackupFilePrefix: String? = null
        if (mDestMetadata!!.info.flags.backupInternalData()) {
            intBackupFilePrefix = BackupUtils.getDataFilePrefix(i++, getExt(tarType))
        }
        if (mDestMetadata!!.info.flags.backupExternalData()) {
            extBackupFilePrefix = BackupUtils.getDataFilePrefix(i, getExt(tarType))
        }
        try {
            BufferedInputStream(dataFile.openInputStream()).use { bis ->
                val cis: CompressorInputStream = when (mSourceMetadata!!.tarType) {
                    TAR_GZIP -> GzipCompressorInputStream(bis)
                    TAR_BZIP2 -> BZip2CompressorInputStream(bis)
                    else -> throw BackupException("Invalid compression type: $tarType")
                }
                val tis = TarArchiveInputStream(cis)
                var intSos: SplitOutputStream? = null
                var extSos: SplitOutputStream? = null
                var intTos: TarArchiveOutputStream? = null
                var extTos: TarArchiveOutputStream? = null
                if (intBackupFilePrefix != null) {
                    intSos = SplitOutputStream(mBackupItem!!.unencryptedBackupPath, intBackupFilePrefix, DEFAULT_SPLIT_SIZE)
                    intTos = TarArchiveOutputStream(TarUtils.createCompressedStream(BufferedOutputStream(intSos), tarType))
                    intTos.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                    intTos.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)
                }
                if (extBackupFilePrefix != null) {
                    extSos = SplitOutputStream(mBackupItem!!.unencryptedBackupPath, extBackupFilePrefix, DEFAULT_SPLIT_SIZE)
                    extTos = TarArchiveOutputStream(TarUtils.createCompressedStream(BufferedOutputStream(extSos), tarType))
                    extTos.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                    extTos.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)
                }
                try {
                    var tarEntry: TarArchiveEntry?
                    while (tis.nextTarEntry.also { tarEntry = it } != null) {
                        val name = tarEntry!!.name
                        if (name.startsWith(INTERNAL_PREFIX) && intTos != null) {
                            val newEntry = TarArchiveEntry(name.substring(INTERNAL_PREFIX.length))
                            newEntry.size = tarEntry!!.size
                            newEntry.mode = tarEntry!!.mode
                            newEntry.modTime = tarEntry!!.modTime
                            intTos.putArchiveEntry(newEntry)
                            if (tarEntry!!.isFile) {
                                IoUtils.copy(tis, intTos)
                            }
                            intTos.closeArchiveEntry()
                        } else if (name.startsWith(EXTERNAL_PREFIX) && extTos != null) {
                            val newEntry = TarArchiveEntry(name.substring(EXTERNAL_PREFIX.length))
                            newEntry.size = tarEntry!!.size
                            newEntry.mode = tarEntry!!.mode
                            newEntry.modTime = tarEntry!!.modTime
                            extTos.putArchiveEntry(newEntry)
                            if (tarEntry!!.isFile) {
                                IoUtils.copy(tis, extTos)
                            }
                            extTos.closeArchiveEntry()
                        }
                    }
                } finally {
                    intTos?.finish()
                    intTos?.close()
                    extTos?.finish()
                    extTos?.close()
                    tis.close()
                }
                // Encrypt data files
                if (intSos != null) {
                    val encryptedFiles = mBackupItem!!.encrypt(intSos.files.toTypedArray())
                    for (file in encryptedFiles) {
                        mChecksum!!.add(file.getName(), DigestUtils.getHexDigest(mDestMetadata!!.info.checksumAlgo, file))
                    }
                }
                if (extSos != null) {
                    val encryptedFiles = mBackupItem!!.encrypt(extSos.files.toTypedArray())
                    for (file in encryptedFiles) {
                        mChecksum!!.add(file.getName(), DigestUtils.getHexDigest(mDestMetadata!!.info.checksumAlgo, file))
                    }
                }
            }
        } catch (e: IOException) {
            throw BackupException("Backup failed for $dataFile", e)
        }
    }

    private fun readPropFile(): BackupMetadataV2 {
        return try {
            val metadataV2 = BackupMetadataV2()
            val props = Properties()
            mPropFile.openInputStream().use { props.load(it) }
            metadataV2.label = props.getProperty("app_label")
            metadataV2.packageName = packageName!!
            metadataV2.versionName = props.getProperty("version_name")
            metadataV2.versionCode = props.getProperty("version_code").toLong()
            metadataV2.isSystem = props.getProperty("is_system") == "true"\nmetadataV2.isSplitApk = false
            metadataV2.splitConfigs = emptyArray()
            metadataV2.hasRules = false
            metadataV2.backupTime = mBackupTime
            metadataV2.crypto = CryptoUtils.MODE_NO_ENCRYPTION
            metadataV2.apkName = props.getProperty("apk_name")
            metadataV2.tarType = if (metadataV2.apkName.endsWith(".gz")) TAR_GZIP else TAR_BZIP2
            // Flags
            val flags = BackupFlags(BackupFlags.BACKUP_APK_FILES)
            if (props.getProperty("has_data") == "true") {
                flags.addFlag(BackupFlags.BACKUP_INT_DATA)
                flags.addFlag(BackupFlags.BACKUP_CACHE)
            }
            if (props.getProperty("has_external_data") == "true") {
                flags.addFlag(BackupFlags.BACKUP_EXT_DATA)
                flags.addFlag(BackupFlags.BACKUP_CACHE)
            }
            metadataV2.flags = flags
            metadataV2.userId = mUserId
            metadataV2.dataDirs = ConvertUtils.getDataDirs(
                packageName, mUserId, flags.backupInternalData(),
                flags.backupExternalData(), false
            )
            metadataV2.keyStore = false
            metadataV2.installer = Prefs.Installer.getInstallerPackageName()
            metadataV2.version = 2
            val iconBase64 = props.getProperty("icon")
            if (iconBase64 != null) {
                val decodedString = Base64.decode(iconBase64, Base64.DEFAULT)
                mIcon = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
            }
            metadataV2
        } catch (e: Exception) {
            throw BackupException("Could not read properties file.", e)
        }
    }

    private fun getApkFile(apkName: String, tarType: String): Path {
        return mBackupLocation.findFile(apkName)
    }

    private fun getDataFile(prefix: String, tarType: String): Path {
        val ext = if (tarType == TAR_GZIP) ".tar.gz" else ".tar.bz2"\nreturn mBackupLocation.findFile(prefix + ext)
    }

    private fun backupIcon() {
        if (mIcon == null) return
        try {
            val iconFile = mBackupItem!!.iconFile
            iconFile.openOutputStream().use { outputStream ->
                mIcon!!.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                outputStream.flush()
            }
        } catch (th: Throwable) {
            Log.w(TAG, "Could not back up icon.", th)
        }
    }
}
