// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup

import androidx.annotation.WorkerThread
import io.github.muntashirakon.AppManager.backup.struct.BackupMetadataV5
import io.github.muntashirakon.AppManager.crypto.CryptoException
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.utils.DigestUtils
import io.github.muntashirakon.io.Path
import java.io.Closeable
import java.io.IOException

@WorkerThread
internal class VerifyOp @Throws(BackupException::class) constructor(
    private val mBackupItem: BackupItems.BackupItem
) : Closeable {
    private val mBackupFlags: BackupFlags
    private val mBackupInfo: BackupMetadataV5.Info
    private val mBackupMetadata: BackupMetadataV5.Metadata
    private val mChecksum: BackupItems.Checksum

    init {
        try {
            mBackupInfo = mBackupItem.info
            mBackupFlags = mBackupInfo.flags
        } catch (e: IOException) {
            mBackupItem.cleanup()
            throw BackupException("Could not read backup info. Possibly due to a malformed json file.", e)
        }
        // Setup crypto
        if (!CryptoUtils.isAvailable(mBackupInfo.crypto)) {
            mBackupItem.cleanup()
            throw BackupException("Mode ${mBackupInfo.crypto} is currently unavailable.")
        }
        try {
            mBackupItem.setCrypto(mBackupInfo.getCrypto())
        } catch (e: CryptoException) {
            mBackupItem.cleanup()
            throw BackupException("Could not get crypto ${mBackupInfo.crypto}", e)
        }
        try {
            mBackupMetadata = mBackupItem.getMetadata(mBackupInfo).metadata
        } catch (e: IOException) {
            mBackupItem.cleanup()
            throw BackupException("Could not read backup metadata. Possibly due to a malformed json file.", e)
        }
        // Get checksums
        try {
            mChecksum = mBackupItem.checksum
        } catch (e: Throwable) {
            mBackupItem.cleanup()
            throw BackupException("Could not get checksums.", e)
        }
        // Verify metadata
        try {
            verifyMetadata()
        } catch (e: BackupException) {
            mBackupItem.cleanup()
            throw e
        }
    }

    override fun close() {
        Log.d(TAG, "Close called")
        mChecksum.close()
        mBackupItem.cleanup()
    }

    @Throws(BackupException::class)
    fun verify() {
        try {
            // No need to check master key as it varies from device to device and APK signing key checksum as it would
            // remain intact if the APK files are not modified.
            if (mBackupFlags.backupApkFiles()) {
                verifyApkFiles()
            }
            if (mBackupFlags.backupData()) {
                verifyData()
                if (mBackupMetadata.keyStore) {
                    verifyKeyStore()
                }
            }
            if (mBackupFlags.backupExtras()) {
                verifyExtras()
            }
            if (mBackupFlags.backupRules()) {
                verifyRules()
            }
        } catch (e: BackupException) {
            throw e
        } catch (th: Throwable) {
            throw BackupException("Unknown error occurred", th)
        }
    }

    @Throws(BackupException::class)
    private fun verifyMetadata() {
        val isV5AndUp = mBackupItem.isV5AndUp()
        if (isV5AndUp) {
            val infoFile: Path = try {
                mBackupItem.infoFile
            } catch (e: IOException) {
                throw BackupException("Could not get metadata file.", e)
            }
            val checksum = DigestUtils.getHexDigest(mBackupInfo.checksumAlgo, infoFile)
            if (checksum != mChecksum[infoFile.getName()]) {
                throw BackupException(
                    "Couldn't verify metadata file." +
                            "\nFile: " + infoFile +
                            "\nFound: " + checksum +
                            "\nRequired: " + mChecksum[infoFile.getName()]
                )
            }
        }
        val metadataFile: Path = try {
            if (isV5AndUp) mBackupItem.getMetadataV5File(false) else mBackupItem.metadataV2File
        } catch (e: IOException) {
            throw BackupException("Could not get metadata file.", e)
        }
        val checksum = DigestUtils.getHexDigest(mBackupInfo.checksumAlgo, metadataFile)
        if (checksum != mChecksum[metadataFile.getName()]) {
            throw BackupException(
                "Couldn't verify metadata file." +
                        "\nFile: " + metadataFile +
                        "\nFound: " + checksum +
                        "\nRequired: " + mChecksum[metadataFile.getName()]
            )
        }
    }

    @Throws(BackupException::class)
    private fun verifyApkFiles() {
        val backupSourceFiles = mBackupItem.sourceFiles
        if (backupSourceFiles.isEmpty()) {
            // No APK files found
            throw BackupException("Backup does not contain any APK files.")
        }
        var checksum: String
        for (file in backupSourceFiles) {
            checksum = DigestUtils.getHexDigest(mBackupInfo.checksumAlgo, file)
            if (checksum != mChecksum[file.getName()]) {
                throw BackupException(
                    "Could not verify APK files." +
                            "\nFile: " + file.getName() +
                            "\nFound: " + checksum +
                            "\nRequired: " + mChecksum[file.getName()]
                )
            }
        }
    }

    @Throws(BackupException::class)
    private fun verifyKeyStore() {
        val keyStoreFiles = mBackupItem.keyStoreFiles
        if (keyStoreFiles.isEmpty()) {
            // Not having KeyStore backups is fine.
            return
        }
        var checksum: String
        for (file in keyStoreFiles) {
            checksum = DigestUtils.getHexDigest(mBackupInfo.checksumAlgo, file)
            if (checksum != mChecksum[file.getName()]) {
                throw BackupException(
                    "Could not verify KeyStore files." +
                            "\nFile: " + file.getName() +
                            "\nFound: " + checksum +
                            "\nRequired: " + mChecksum[file.getName()]
                )
            }
        }
    }

    @Throws(BackupException::class)
    private fun verifyData() {
        var dataFiles: Array<Path>
        var checksum: String
        for (i in mBackupMetadata.dataDirs!!.indices) {
            dataFiles = mBackupItem.getDataFiles(i)
            if (dataFiles.isEmpty()) {
                throw BackupException("No data files at index $i.")
            }
            for (file in dataFiles) {
                checksum = DigestUtils.getHexDigest(mBackupInfo.checksumAlgo, file)
                if (checksum != mChecksum[file.getName()]) {
                    throw BackupException(
                        "Could not verify data files at index $i." +
                                "\nFile: " + file.getName() +
                                "\nFound: " + checksum +
                                "\nRequired: " + mChecksum[file.getName()]
                    )
                }
            }
        }
    }

    @Throws(BackupException::class)
    private fun verifyExtras() {
        val miscFile: Path = try {
            mBackupItem.miscFile
        } catch (ignore: IOException) {
            // There are no permissions, just skip
            return
        }
        val checksum = DigestUtils.getHexDigest(mBackupInfo.checksumAlgo, miscFile)
        if (checksum != mChecksum[miscFile.getName()]) {
            throw BackupException(
                "Could not verify extras." +
                        "\nFile: " + miscFile.getName() +
                        "\nFound: " + checksum +
                        "\nRequired: " + mChecksum[miscFile.getName()]
                )
        }
    }

    @Throws(BackupException::class)
    private fun verifyRules() {
        val rulesFile: Path = try {
            mBackupItem.rulesFile
        } catch (e: IOException) {
            if (mBackupMetadata.hasRules) {
                throw BackupException("Rules file is missing.", e)
            } else {
                // There are no rules, just skip
                return
            }
        }
        val checksum = DigestUtils.getHexDigest(mBackupInfo.checksumAlgo, rulesFile)
        if (checksum != mChecksum[rulesFile.getName()]) {
            throw BackupException(
                "Could not verify rules file." +
                        "\nFile: " + rulesFile.getName() +
                        "\nFound: " + checksum +
                        "\nRequired: " + mChecksum[rulesFile.getName()]
            )
        }
    }

    companion object {
        val TAG: String = VerifyOp::class.java.simpleName
    }
}
