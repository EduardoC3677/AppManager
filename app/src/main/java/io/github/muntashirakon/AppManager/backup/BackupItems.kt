// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup

import android.annotation.UserIdInt
import io.github.muntashirakon.AppManager.backup.struct.BackupMetadataV5
import io.github.muntashirakon.AppManager.crypto.Crypto
import io.github.muntashirakon.AppManager.crypto.DummyCrypto
import io.github.muntashirakon.AppManager.db.entity.Backup
import io.github.muntashirakon.AppManager.logcat.helper.SaveLogHelper
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.settings.Prefs
import io.github.muntashirakon.io.Path
import io.github.muntashirakon.io.PathReader
import io.github.muntashirakon.io.PathWriter
import io.github.muntashirakon.io.Paths
import java.io.*
import java.util.*
import java.util.stream.Collectors

object BackupItems {
    const val BACKUP_DIRECTORY = "backups"\nprivate const val APK_SAVING_DIRECTORY = "apks"\nprivate const val ICON_FILE = "icon.png"\nprivate const val RULES_TSV = "rules.am.tsv"\nprivate const val MISC_TSV = "misc.am.tsv"\nprivate const val CHECKSUMS_TXT = "checksums.txt"\nprivate const val FREEZE = ".freeze"\nprivate const val NO_MEDIA = ".nomedia"\nprivate val baseDirectory: Path
        get() = Prefs.Storage.getAppManagerDirectory()

    @JvmStatic
    @Throws(FileNotFoundException::class)
    fun findBackupItem(relativeDir: String): BackupItem {
        return BackupItem(baseDirectory.findFile(relativeDir))
    }

    @JvmStatic
    @Throws(IOException::class)
    fun findOrCreateBackupItem(@UserIdInt userId: Int, backupName: String?, packageName: String): BackupItem {
        val backupPath: Path
        var previousBackupItems: MutableList<BackupItem>? = null
        if (MetadataManager.getCurrentBackupMetaVersion() >= 5) {
            val previousBackups = BackupUtils.retrieveBackupFromDb(userId, backupName, packageName)
            if (previousBackups.isNotEmpty()) {
                previousBackupItems = ArrayList(previousBackups.size)
                for (backup in previousBackups) {
                    previousBackupItems.add(backup.item)
                }
            }
            val backupUuid = UUID.randomUUID().toString()
            backupPath = baseDirectory
                .findOrCreateDirectory(BACKUP_DIRECTORY)
                .findOrCreateDirectory(backupUuid)
        } else {
            backupPath = baseDirectory
                .findOrCreateDirectory(packageName)
                .findOrCreateDirectory(BackupUtils.getV4BackupName(userId, backupName))
        }
        val backupItem = BackupItem(backupPath, true)
        backupItem.setBackupName(BackupUtils.getCompatBackupName(backupName))
        backupItem.setPreviousBackups(previousBackupItems)
        return backupItem
    }

    @JvmStatic
    @Throws(IOException::class)
    fun createBackupItemGracefully(@UserIdInt userId: Int, backupName: String?, packageName: String): BackupItem {
        val backupPath: Path
        if (MetadataManager.getCurrentBackupMetaVersion() >= 5) {
            val backupUuid = UUID.randomUUID().toString()
            backupPath = baseDirectory
                .findOrCreateDirectory(BACKUP_DIRECTORY)
                .findOrCreateDirectory(backupUuid)
        } else {
            val baseDir = baseDirectory.findOrCreateDirectory(packageName)
            val backupItemName = BackupUtils.getV4BackupName(userId, backupName)
            var newBackupName = backupItemName
            var i = 0
            while (baseDir.hasFile(newBackupName)) {
                newBackupName = backupItemName + "_" + ++i
            }
            backupPath = baseDir.createNewDirectory(newBackupName)
        }
        val backupItem = BackupItem(backupPath, true)
        backupItem.setBackupName(BackupUtils.getCompatBackupName(backupName))
        return backupItem
    }

    @JvmStatic
    fun findAllBackupItems(): List<BackupItem> {
        val paths = baseDirectory.listFiles { pathname -> pathname.isDirectory() }
        val backupItems: MutableList<BackupItem> = ArrayList(paths.size)
        for (path in paths) {
            if (SaveLogHelper.SAVED_LOGS_DIR == path.getName()) continue
            if (APK_SAVING_DIRECTORY == path.getName()) continue
            if (".tmp" == path.getName()) continue
            // Other backups can store multiple backups per folder
            backupItems.addAll(
                Arrays.stream(path.listFiles { pathname -> pathname.isDirectory() })
                    .map { backupPath -> BackupItem(backupPath) }
                    .collect(Collectors.toList())
            )
        }
        return backupItems
    }

    @Synchronized
    @Throws(IOException::class)
    private fun getTemporaryUnencryptedPath(backupName: String): Path {
        val tmpDir = Prefs.Storage.getTempPath()
        var newFilename = backupName
        var i = 0
        while (tmpDir.hasFile(newFilename)) {
            newFilename = backupName + "_" + ++i
        }
        return tmpDir.findOrCreateDirectory(newFilename)
    }

    @Synchronized
    @Throws(IOException::class)
    private fun getTemporaryBackupPath(originalBackupPath: Path): Path {
        val tmpDir = originalBackupPath.requireParent()
        val tmpFilename = "." + originalBackupPath.getName()
        var newFilename = tmpFilename
        var i = 0
        while (tmpDir.hasFile(newFilename)) {
            newFilename = tmpFilename + "_" + ++i
        }
        return tmpDir.findOrCreateDirectory(newFilename)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun getApkBackupDirectory(): Path {
        return baseDirectory.findOrCreateDirectory(APK_SAVING_DIRECTORY)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun createNoMediaIfNotExists() {
        if (!baseDirectory.hasFile(NO_MEDIA)) {
            baseDirectory.createNewFile(NO_MEDIA, null)
        }
    }

    class BackupItem {
        private val mBackupPath: Path
        private val mTempBackupPath: Path
        private val mCryptoGuard = Any()
        private var mCrypto: Crypto? = null
        private var mCryptoMode: String = CryptoUtils.MODE_NO_ENCRYPTION
        private var mBackupName: String? = null
        private var mBackupNameSet = false
        var isBackupMode: Boolean = false
            private set
        private var mBackupSuccess = false
        private val mTemporaryFiles: MutableList<Path> = ArrayList()
        private var mTempUnencyptedPath: Path? = null
        private var mPreviousBackups: List<BackupItem>? = null

        @Throws(IOException::class)
        internal constructor(backupPath: Path, backupMode: Boolean) {
            mBackupPath = backupPath
            isBackupMode = backupMode
            if (isBackupMode) {
                mBackupPath.mkdirs() // Create backup path if not exists
                mTempBackupPath = getTemporaryBackupPath(mBackupPath)
            } else {
                mTempBackupPath = mBackupPath
            }
        }

        // Read-only instance: the point is not to throw IOException
        internal constructor(backupPath: Path) {
            mBackupPath = backupPath
            isBackupMode = false
            mTempBackupPath = mBackupPath
        }

        fun setCrypto(crypto: Crypto?) {
            if (crypto == null || crypto is DummyCrypto) {
                mCrypto = null
                mCryptoMode = CryptoUtils.MODE_NO_ENCRYPTION
            } else {
                mCrypto = crypto
                mCryptoMode = crypto.modeName
            }
        }

        fun setBackupName(backupName: String?) {
            mBackupName = backupName
            mBackupNameSet = true
        }

        val backupName: String?
            get() {
                if (mBackupNameSet) {
                    return mBackupName
                }
                if (isBackupMode) {
                    throw IllegalStateException("mBackupName must be set in backup mode.")
                }
                if (isV5AndUp()) {
                    throw IllegalStateException("getBackupName() is unavailable in backup v5 and up unless set manually.")
                }
                // For v4 or earlier backups, fallback to filename
                return BackupUtils.getRealBackupName(4, mBackupPath.getName())
            }

        fun setPreviousBackups(previousBackups: List<BackupItem>?) {
            mPreviousBackups = previousBackups
        }

        val relativeDir: String
            get() = if (isV5AndUp()) {
                // {AppManagerDir}/backups/{UUID}/
                BackupUtils.getV5RelativeDir(mBackupPath.getName())
            } else {
                // {AppManagerDir}/{packagename}/{userid}[_{backup_name}]
                val userIdBackupName = mBackupPath.getName()
                val packageName = mBackupPath.requireParent().getName()
                BackupUtils.getV4RelativeDir(userIdBackupName, packageName)
            }

        val backupPath: Path
            get() = if (isBackupMode) mTempBackupPath else mBackupPath

        @Throws(IOException::class)
        fun getUnencryptedBackupPath(): Path {
            return if (mCrypto == null) {
                // Use real path for unencrypted backups
                backupPath
            } else {
                requireUnencryptedBackupPath()
            }
        }

        @Throws(IOException::class)
        fun requireUnencryptedBackupPath(): Path {
            if (mTempUnencyptedPath == null) {
                // We can only do this once for each BackupItem
                mTempUnencyptedPath = getTemporaryUnencryptedPath(backupPath.getName())
            }
            return mTempUnencyptedPath!!
        }

        @Throws(IOException::class)
        fun encrypt(files: Array<Path>): Array<Path> {
            // Encrypt the files and delete the originals
            synchronized(mCryptoGuard) {
                if (mCrypto == null) {
                    // No encryption enabled
                    return files
                }
                val newFileList: MutableList<Path> = ArrayList()
                // Get desired extension
                val ext = CryptoUtils.getExtension(mCryptoMode)
                // Create necessary files (1-1 correspondence)
                for (inputFile in files) {
                    val parent = backupPath
                    val outputFilename = inputFile.getName() + ext
                    val outputPath = parent.createNewFile(outputFilename, null)
                    newFileList.add(outputPath)
                    Log.i(TAG, "Input: $inputFile
Output: $outputPath")
                }
                val newFiles = newFileList.toTypedArray()
                // Perform actual encryption
                mCrypto!!.encrypt(files, newFiles)
                // Delete unencrypted files
                for (inputFile in files) {
                    if (!inputFile.delete()) {
                        throw IOException("Couldn't delete old file $inputFile")
                    }
                }
                return newFiles
            }
        }

        @Throws(IOException::class)
        fun decrypt(files: Array<Path>): Array<Path> {
            // Decrypt the files but do NOT delete the originals
            synchronized(mCryptoGuard) {
                if (mCrypto == null) {
                    // No encryption enabled
                    return files
                }
                val newFileList: MutableList<Path> = ArrayList()
                // Get desired extension
                val ext = CryptoUtils.getExtension(mCryptoMode)
                // Create necessary files (1-1 correspondence)
                for (inputFile in files) {
                    val parent = requireUnencryptedBackupPath()
                    val filename = inputFile.getName()
                    val outputFilename = filename.substring(0, filename.lastIndexOf(ext))
                    val outputPath = parent.createNewFile(outputFilename, null)
                    newFileList.add(outputPath)
                    Log.i(TAG, "Input: $inputFile
Output: $outputPath")
                }
                val newFiles = newFileList.toTypedArray()
                // Perform actual decryption
                mCrypto!!.decrypt(files, newFiles)
                mTemporaryFiles.addAll(newFileList)
                return newFiles
            }
        }

        @get:Throws(IOException::class)
        val iconFile: Path
            get() = if (isBackupMode) {
                backupPath.findOrCreateFile(ICON_FILE, null)
            } else backupPath.findFile(ICON_FILE)

        fun isV5AndUp(): Boolean {
            return try {
                backupPath.hasFile(MetadataManager.INFO_V5_FILE)
            } catch (e: Exception) {
                false
            }
        }

        @get:Throws(IOException::class)
        val infoFile: Path
            get() = if (isBackupMode) {
                backupPath.findOrCreateFile(MetadataManager.INFO_V5_FILE, null)
            } else backupPath.findFile(MetadataManager.INFO_V5_FILE)

        @Throws(IOException::class)
        fun getMetadataV5File(decryptIfRequired: Boolean): Path {
            return if (isBackupMode) {
                // Needs to be encrypted in backup mode
                backupPath.findOrCreateFile(MetadataManager.META_V5_FILE, null)
            } else {
                // Needs to be decrypted in restore mode
                val file = backupPath.findFile(MetadataManager.META_V5_FILE + CryptoUtils.getExtension(mCryptoMode))
                if (decryptIfRequired) decrypt(arrayOf(file))[0] else file
            }
        }

        @get:Throws(IOException::class)
        val metadataV2File: Path
            get() = if (isBackupMode) {
                backupPath.findOrCreateFile(MetadataManager.META_V2_FILE, null)
            } else backupPath.findFile(MetadataManager.META_V2_FILE)

        @get:Throws(IOException::class)
        val info: BackupMetadataV5.Info
            get() = MetadataManager.readInfo(this)

        @get:Throws(IOException::class)
        val metadata: BackupMetadataV5
            get() = MetadataManager.readMetadata(this)

        @Throws(IOException::class)
        fun getMetadata(backupInfo: BackupMetadataV5.Info): BackupMetadataV5 {
            return MetadataManager.readMetadata(this, backupInfo)
        }

        @get:Throws(IOException::class)
        private val checksumFile: Path
            get() = if (isBackupMode) {
                // Needs to be encrypted in backup mode
                getUnencryptedBackupPath().findOrCreateFile(CHECKSUMS_TXT, null)
            } else {
                // Needs to be decrypted in restore mode
                val file = backupPath.findFile(CHECKSUMS_TXT + CryptoUtils.getExtension(mCryptoMode))
                decrypt(arrayOf(file))[0]
            }

        @get:Throws(IOException::class)
        val checksum: Checksum
            get() = Checksum(checksumFile, if (isBackupMode) "w" else "r")

        @get:Throws(IOException::class)
        val miscFile: Path
            get() = if (isBackupMode) {
                // Needs to be encrypted in backup mode
                getUnencryptedBackupPath().findOrCreateFile(MISC_TSV, null)
            } else {
                // Needs to be decrypted in restore mode
                backupPath.findFile(MISC_TSV + CryptoUtils.getExtension(mCryptoMode))
            }

        @get:Throws(IOException::class)
        val rulesFile: Path
            get() = if (isBackupMode) {
                // Needs to be encrypted in backup mode
                getUnencryptedBackupPath().findOrCreateFile(RULES_TSV, null)
            } else {
                // Needs to be decrypted in restore mode
                backupPath.findFile(RULES_TSV + CryptoUtils.getExtension(mCryptoMode))
            }

        val sourceFiles: Array<Path>
            get() {
                val ext = CryptoUtils.getExtension(mCryptoMode)
                val sourcePrefix = BackupUtils.getSourceFilePrefix(null)
                val paths = backupPath.listFiles { _, name -> name.startsWith(sourcePrefix) && name.endsWith(ext) }
                return Paths.getSortedPaths(paths)
            }

        fun getDataFiles(index: Int): Array<Path> {
            val ext = CryptoUtils.getExtension(mCryptoMode)
            val dataPrefix = BackupUtils.getDataFilePrefix(index, null) // extension can be anything
            val paths = backupPath.listFiles { _, name -> name.startsWith(dataPrefix) && name.endsWith(ext) }
            return Paths.getSortedPaths(paths)
        }

        val keyStoreFiles: Array<Path>
            get() {
                val ext = CryptoUtils.getExtension(mCryptoMode)
                val paths = backupPath.listFiles { _, name -> name.startsWith(BackupManager.KEYSTORE_PREFIX) && name.endsWith(ext) }
                return Paths.getSortedPaths(paths)
            }

        @Throws(IOException::class)
        fun freeze() {
            backupPath.createNewFile(FREEZE, null)
        }

        @Throws(FileNotFoundException::class)
        fun unfreeze() {
            freezeFile.delete()
        }

        fun isFrozen(): Boolean {
            return try {
                freezeFile.exists()
            } catch (e: IOException) {
                false
            }
        }

        @Throws(IOException::class)
        fun commit() {
            if (isBackupMode) {
                if (mBackupSuccess) {
                    // Backup already done
                    return
                }
                if (!delete()) {
                    throw IOException("Could not delete $mBackupPath")
                }
                if (!mTempBackupPath.moveTo(mBackupPath)) {
                    throw IOException("Could not move $mTempBackupPath to $mBackupPath")
                }
                mPreviousBackups?.forEach { previousBackup ->
                    if (!previousBackup.delete()) {
                        Log.w(TAG, "Could not delete ${previousBackup.mBackupPath}")
                    }
                }
                mBackupSuccess = true
                // Set backup mode to false to make it read-only
                isBackupMode = false
            }
        }

        fun cleanup() {
            if (isBackupMode) {
                if (!mBackupSuccess) {
                    // Backup wasn't successful, delete the directory
                    mTempBackupPath.delete()
                }
            }
            for (file in mTemporaryFiles) {
                Log.d(TAG, "Deleting $file")
                file.delete()
            }
            mTempUnencyptedPath?.delete()
            mCrypto?.close()
        }

        fun exists(): Boolean {
            return mBackupPath.exists()
        }

        fun delete(): Boolean {
            if (mBackupPath.exists()) {
                if (!isV5AndUp()) {
                    // For v4 and earlier, delete parent if it's the last one.
                    val parent = mBackupPath.requireParent()
                    if (parent.listFiles().size == 1) {
                        // Also deletes children
                        return parent.delete()
                    }
                }
                return mBackupPath.delete()
            }
            return true // The backup path doesn't exist anyway
        }

        @get:Throws(FileNotFoundException::class)
        private val freezeFile: Path
            get() = backupPath.findFile(FREEZE)

        companion object {
            val TAG: String = BackupItem::class.java.simpleName
        }
    }

    class Checksum @Throws(IOException::class) internal constructor(
        private val mFile: Path,
        private val mMode: String
    ) : Closeable {
        private var mWriter: PrintWriter? = null
        private val mChecksums = HashMap<String, String>()

        init {
            if ("w" == mMode) {
                mWriter = PrintWriter(BufferedWriter(PathWriter(mFile)))
            } else if ("r" == mMode) {
                synchronized(mChecksums) {
                    BufferedReader(PathReader(mFile)).use { reader ->
                        // Get checksums
                        var line: String?
                        var lineSplits: Array<String>
                        while (reader.readLine().also { line = it } != null) {
                            lineSplits = line!!.split("	".toRegex(), 2).toTypedArray()
                            if (lineSplits.size != 2) {
                                throw RuntimeException("Illegal lines found in the checksum file.")
                            }
                            mChecksums[lineSplits[1]] = lineSplits[0]
                        }
                    }
                }
            } else throw IOException("Unknown mode: $mMode")
        }

        val file: Path
            get() = mFile

        fun add(fileName: String, checksum: String) {
            synchronized(mChecksums) {
                if ("w" != mMode) {
                    throw IllegalStateException("add is inaccessible in mode $mMode")
                }
                mWriter!!.println(String.format("%s	%s", checksum, fileName))
                mChecksums[fileName] = checksum
                mWriter!!.flush()
            }
        }

        operator fun get(fileName: String): String? {
            synchronized(mChecksums) {
                return mChecksums[fileName]
            }
        }

        override fun close() {
            synchronized(mChecksums) {
                mWriter?.close()
                mWriter = null
            }
        }

        companion object {
            @JvmStatic
            fun getCertChecksums(checksum: Checksum): Array<String> {
                val certChecksums: MutableList<String> = ArrayList()
                synchronized(checksum.mChecksums) {
                    for (name in checksum.mChecksums.keys) {
                        if (name.startsWith(BackupManager.CERT_PREFIX)) {
                            certChecksums.add(checksum.mChecksums[name]!!)
                        }
                    }
                }
                return certChecksums.toTypedArray()
            }
        }
    }
}
