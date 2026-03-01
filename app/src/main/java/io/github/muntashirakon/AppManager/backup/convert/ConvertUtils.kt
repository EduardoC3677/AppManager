// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.convert

import android.annotation.SuppressLint
import android.os.Build
import com.android.apksig.ApkVerifier
import com.android.apksig.apk.ApkFormatException
import com.android.apksig.util.DataSources
import io.github.muntashirakon.AppManager.backup.BackupCryptSetupHelper
import io.github.muntashirakon.AppManager.backup.BackupItems
import io.github.muntashirakon.AppManager.backup.BackupUtils
import io.github.muntashirakon.AppManager.backup.CryptoUtils
import io.github.muntashirakon.AppManager.backup.MetadataManager
import io.github.muntashirakon.AppManager.backup.struct.BackupMetadataV2
import io.github.muntashirakon.AppManager.backup.struct.BackupMetadataV5
import io.github.muntashirakon.AppManager.crypto.Crypto
import io.github.muntashirakon.AppManager.crypto.CryptoException
import io.github.muntashirakon.AppManager.crypto.DummyCrypto
import io.github.muntashirakon.AppManager.ipc.LocalServices
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.self.filecache.FileCache
import io.github.muntashirakon.AppManager.settings.Prefs
import io.github.muntashirakon.AppManager.utils.DigestUtils
import io.github.muntashirakon.io.FileSystemManager
import io.github.muntashirakon.io.Path
import io.github.muntashirakon.io.Paths
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.security.NoSuchAlgorithmException
import java.security.cert.CertificateEncodingException
import java.security.cert.X509Certificate

object ConvertUtils {
    @JvmField
    val TAG: String = ConvertUtils::class.java.simpleName

    @JvmStatic
    @Throws(CryptoException::class)
    fun getV5Metadata(
        metadataV2: BackupMetadataV2,
        backupItem: BackupItems.BackupItem
    ): BackupMetadataV5 {
        // Here we don't care about the crypto we had for metdataV2, because the crypto that the
        // imported backups use may be different from the one configured for this app
        val compressionMethod = Prefs.BackupRestore.getCompressionMethod()
        val crypto = CryptoUtils.getMode()
        val cryptoHelper = BackupCryptSetupHelper(crypto, MetadataManager.getCurrentBackupMetaVersion())
        val info = BackupMetadataV5.Info(
            metadataV2.backupTime,
            metadataV2.flags, metadataV2.userId, compressionMethod, DigestUtils.SHA_256, crypto,
            cryptoHelper.iv, cryptoHelper.aes, cryptoHelper.keyIds
        )
        info.setBackupItem(backupItem)
        val metadata = BackupMetadataV5.Metadata(backupItem.backupName)
        metadata.hasRules = metadataV2.hasRules
        metadata.label = metadataV2.label
        metadata.packageName = metadataV2.packageName
        metadata.versionName = metadataV2.versionName
        metadata.versionCode = metadataV2.versionCode
        metadata.dataDirs = metadataV2.dataDirs.clone()
        metadata.isSystem = metadataV2.isSystem
        metadata.isSplitApk = metadataV2.isSplitApk
        metadata.splitConfigs = metadataV2.splitConfigs.clone()
        metadata.apkName = metadataV2.apkName
        metadata.instructionSet = metadataV2.instructionSet
        metadata.keyStore = metadataV2.keyStore
        metadata.installer = metadataV2.installer
        return BackupMetadataV5(info, metadata)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun decryptSourceFiles(
        files: Array<Path>,
        crypto: Crypto,
        cryptoMode: String,
        backupItem: BackupItems.BackupItem
    ): Array<Path> {
        if (crypto is DummyCrypto) {
            return files
        }
        val newFileList = mutableListOf<Path>()
        // Get desired extension
        val ext = CryptoUtils.getExtension(cryptoMode)
        // Create necessary files (1-1 correspondence)
        for (inputFile in files) {
            val parent = backupItem.requireUnencryptedBackupPath()
            val filename = inputFile.getName()
            val outputFilename = filename.substring(0, filename.lastIndexOf(ext))
            val outputPath = parent.createNewFile(outputFilename, null)
            newFileList.add(outputPath)
            Log.i(TAG, "Input: $inputFile
Output: $outputPath")
        }
        val newFiles = newFileList.toTypedArray()
        // Perform actual decryption
        crypto.decrypt(files, newFiles)
        return newFiles
    }

    @JvmStatic
    fun getConversionUtil(@ImportType backupType: Int, file: Path): Converter {
        return when (backupType) {
            ImportType.OAndBackup -> OABConverter(file)
            ImportType.TitaniumBackup -> TBConverter(file)
            ImportType.SwiftBackup -> SBConverter(file)
            ImportType.SmartLauncher -> SLConverter(file)
            else -> throw IllegalArgumentException("Unsupported import type $backupType")
        }
    }

    @JvmStatic
    fun getRelevantImportFiles(baseLocation: Path, @ImportType backupType: Int): Array<Path> {
        return when (backupType) {
            ImportType.OAndBackup ->                 // Package directories
                baseLocation.listFiles { pathname -> pathname.isDirectory() }
            ImportType.TitaniumBackup ->                 // Properties files
                baseLocation.listFiles { dir, name -> name.endsWith(".properties") }
            ImportType.SwiftBackup ->                 // XML files
                baseLocation.listFiles { dir, name -> name.endsWith(".xml") }
            ImportType.SmartLauncher ->                 // JSON files (Smart Launcher backup format)
                baseLocation.listFiles { dir, name -> 
                    name.endsWith(".json") || name.endsWith(".slbackup") || !name.contains(".")
                }
            else -> throw IllegalArgumentException("Unsupported import type $backupType")
        }
    }

    @SuppressLint("SdCardPath")
    @JvmStatic
    internal fun getDataDirs(
        packageName: String,
        userHandle: Int,
        hasInternal: Boolean,
        hasExternal: Boolean,
        hasObb: Boolean
    ): Array<String> {
        val dataDirs = mutableListOf<String>()
        if (hasInternal) {
            dataDirs.add("/data/user/$userHandle/$packageName")
        }
        if (hasExternal) {
            dataDirs.add("/storage/emulated/$userHandle/Android/data/$packageName")
        }
        if (hasObb) {
            dataDirs.add("/storage/emulated/$userHandle/Android/obb/$packageName")
        }
        return dataDirs.toTypedArray()
    }

    @JvmStatic
    @Throws(IOException::class, ApkFormatException::class, NoSuchAlgorithmException::class, CertificateEncodingException::class)
    internal fun getChecksumsFromApk(apkFile: Path, @DigestUtils.Algorithm algo: String): Array<String> {
        // Since we can't directly work with ProxyFile, we need to cache it and read the signature
        val fileChannel: FileChannel = try {
            apkFile.openFileChannel(FileSystemManager.MODE_READ_ONLY)
        } catch (e: IOException) {
            val cachedFile = FileCache.getGlobalFileCache().getCachedFile(apkFile)
            RandomAccessFile(cachedFile, "r").channel
        }
        val dataSource = DataSources.asDataSource(fileChannel)
        val checksums = mutableListOf<String>()
        val verifier = ApkVerifier.Builder(dataSource)
            .setMaxCheckedPlatformVersion(Build.VERSION.SDK_INT)
            .build()
        val apkVerifierResult = verifier.verify()
        // Get signer certificates
        val certificates = apkVerifierResult.signerCertificates
        for (certificate in certificates) {
            checksums.add(DigestUtils.getHexDigest(algo, certificate.encoded))
        }
        return checksums.toTypedArray()
    }
}
