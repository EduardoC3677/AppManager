// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.adb

import io.github.muntashirakon.AppManager.backup.BackupUtils
import io.github.muntashirakon.AppManager.backup.adb.BackupCategories.CAT_EXT
import io.github.muntashirakon.AppManager.backup.adb.BackupCategories.CAT_INT_CE
import io.github.muntashirakon.AppManager.backup.adb.BackupCategories.CAT_INT_DE
import io.github.muntashirakon.AppManager.backup.adb.BackupCategories.CAT_OBB
import io.github.muntashirakon.AppManager.backup.adb.BackupCategories.CAT_SRC
import io.github.muntashirakon.AppManager.backup.adb.BackupCategories.CAT_UNK
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.utils.ExUtils
import io.github.muntashirakon.AppManager.utils.TarUtils
import io.github.muntashirakon.AppManager.utils.TarUtils.DEFAULT_SPLIT_SIZE
import io.github.muntashirakon.io.IoUtils
import io.github.muntashirakon.io.Path
import io.github.muntashirakon.io.Paths
import io.github.muntashirakon.io.SplitOutputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarConstants
import java.io.*

class AndroidBackupExtractor @Throws(IOException::class) constructor(
    abFile: Path,
    private val mWorkingDir: Path,
    packageName: String
) : AutoCloseable {
    private val mCategoryTargetEntriesMap = HashMap<Int, MutableList<TargetTarEntry>>()
    private val mFilesToBeDeleted = mutableListOf<Path>()

    init {
        val relativeDirInAb = Constants.APPS_PREFIX + packageName + File.separator
        val abFilename = Paths.trimPathExtension(abFile.getName())
        val tarFile = mWorkingDir.createNewFile("$abFilename.tar", null)
        mFilesToBeDeleted.add(tarFile)
        val dest = mWorkingDir.createNewDirectory(abFilename)
        mFilesToBeDeleted.add(dest)
        toTar(abFile, tarFile, null)
        tarFile.openInputStream().use { fis ->
            TarArchiveInputStream(fis).use { tis ->
                val realDestPath = dest.getRealFilePath()
                val relDirSize = relativeDirInAb.length
                var entry: TarArchiveEntry?
                while (tis.nextTarEntry.also { entry = it } != null) {
                    val filename = Paths.normalize(entry!!.name)
                    // Early zip slip vulnerability check to avoid creating any files at all
                    if (filename == null || filename.startsWith("../")) {
                        throw IOException(
                            "Zip slip vulnerability detected!" +
                                    "
Expected dest: " + File(realDestPath, entry!!.name) +
                                    "
Actual path: " + (if (filename != null) File(realDestPath, filename) else realDestPath)
                        )
                    }
                    if (!filename.startsWith(relativeDirInAb)) {
                        throw IOException("Unsupported file in AB: $filename")
                    }
                    // Remove apps/{packageName}/ part
                    val shortFilename = filename.substring(relDirSize)
                    val file: Path = if (entry!!.isDirectory) {
                        dest.createDirectoriesIfRequired(shortFilename)
                    } else {
                        dest.createNewArbitraryFile(shortFilename, null)
                    }
                    // Check if the given entry is a link.
                    if (entry!!.isSymbolicLink && file.getFilePath() != null) {
                        val linkName = entry!!.linkName
                        file.delete()
                        file.createNewSymbolicLink(linkName)
                    } else {
                        // Zip slip vulnerability might still be present
                        val realFilePath = file.getRealFilePath()
                        if (realDestPath != null && realFilePath != null && !realFilePath.startsWith(realDestPath)) {
                            throw IOException(
                                "Zip slip vulnerability detected!" +
                                        "
Expected dest: " + File(realDestPath, entry!!.name) +
                                        "
Actual path: $realFilePath"
                            )
                        }
                        if (!entry!!.isDirectory) {
                            file.openOutputStream().use { os ->
                                IoUtils.copy(tis, os)
                            }
                        }
                    }

                    // Categorize and build TarArchiveEntry
                    val category = getCategory(shortFilename)
                    if (category == CAT_UNK && shortFilename == Constants.BACKUP_MANIFEST_FILENAME) {
                        // Ignore manifest file
                        continue
                    }
                    val targetEntry = getTargetArchiveEntry(entry!!, shortFilename)
                    var targetTarEntries = mCategoryTargetEntriesMap[category]
                    if (targetTarEntries == null) {
                        targetTarEntries = mutableListOf()
                        mCategoryTargetEntriesMap[category] = targetTarEntries
                    }
                    targetTarEntries.add(TargetTarEntry(file, shortFilename, category, targetEntry))
                }
            }
        }
        // Validate UNK entries
        if (mCategoryTargetEntriesMap[CAT_UNK] != null) {
            Log.w(TAG, "Unknown entries: " + mCategoryTargetEntriesMap[CAT_UNK])
            throw IOException("Unknown/unsupported entries detected.")
        }
    }

    override fun close() {
        for (file in mFilesToBeDeleted) {
            file.delete()
        }
    }

    @Throws(IOException::class)
    fun getSourceFiles(extension: String, @TarUtils.TarType tarType: String): Array<Path>? {
        return getFiles(CAT_SRC, 0, extension, tarType)
    }

    @Throws(IOException::class)
    fun getInternalCeDataFiles(dataIndex: Int, extension: String, @TarUtils.TarType tarType: String): Array<Path>? {
        return getFiles(CAT_INT_CE, dataIndex, extension, tarType)
    }

    @Throws(IOException::class)
    fun getInternalDeDataFiles(dataIndex: Int, extension: String, @TarUtils.TarType tarType: String): Array<Path>? {
        return getFiles(CAT_INT_DE, dataIndex, extension, tarType)
    }

    @Throws(IOException::class)
    fun getExternalDataFiles(dataIndex: Int, extension: String, @TarUtils.TarType tarType: String): Array<Path>? {
        return getFiles(CAT_EXT, dataIndex, extension, tarType)
    }

    @Throws(IOException::class)
    fun getObbFiles(dataIndex: Int, extension: String, @TarUtils.TarType tarType: String): Array<Path>? {
        return getFiles(CAT_OBB, dataIndex, extension, tarType)
    }

    @Throws(IOException::class)
    fun getFiles(category: Int, dataIndex: Int, extension: String, @TarUtils.TarType tarType: String): Array<Path>? {
        if (category >= CAT_UNK) {
            throw IllegalArgumentException("Invalid category: $category")
        }
        val targetTarEntries = mCategoryTargetEntriesMap[category] ?: return null
        val filePrefix = if (category == CAT_SRC) BackupUtils.getSourceFilePrefix(extension) else
            BackupUtils.getDataFilePrefix(dataIndex, extension)
        SplitOutputStream(mWorkingDir, filePrefix, DEFAULT_SPLIT_SIZE).use { sos ->
            BufferedOutputStream(sos).use { bos ->
                TarUtils.createCompressedStream(bos, tarType).use { os ->
                    TarArchiveOutputStream(os).use { tos ->
                        tos.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                        tos.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)
                        for (entry in targetTarEntries) {
                            if (entry.targetEntry.isSymbolicLink) {
                                // Add the link as is
                                tos.putArchiveEntry(entry.targetEntry)
                            } else {
                                tos.putArchiveEntry(entry.targetEntry)
                                if (!entry.targetEntry.isDirectory) {
                                    entry.sourceFile.openInputStream().use { isStream ->
                                        IoUtils.copy(isStream, tos)
                                    }
                                }
                            }
                            tos.closeArchiveEntry()
                        }
                        tos.finish()
                    }
                }
            }
            return Paths.getSortedPaths(sos.files.toTypedArray())
        }
    }

    private fun getCategory(filename: String): Int {
        val firstPart = filename.split(File.separator.toRegex(), 2).toTypedArray()[0]
        return when (firstPart) {
            Constants.APK_TREE_TOKEN -> CAT_SRC
            Constants.OBB_TREE_TOKEN -> CAT_OBB
            Constants.MANAGED_EXTERNAL_TREE_TOKEN -> CAT_EXT
            Constants.ROOT_TREE_TOKEN, Constants.FILES_TREE_TOKEN, Constants.NO_BACKUP_TREE_TOKEN, Constants.DATABASE_TREE_TOKEN, Constants.SHAREDPREFS_TREE_TOKEN, Constants.CACHE_TREE_TOKEN -> CAT_INT_CE
            Constants.DEVICE_ROOT_TREE_TOKEN, Constants.DEVICE_FILES_TREE_TOKEN, Constants.DEVICE_NO_BACKUP_TREE_TOKEN, Constants.DEVICE_DATABASE_TREE_TOKEN, Constants.DEVICE_SHAREDPREFS_TREE_TOKEN, Constants.DEVICE_CACHE_TREE_TOKEN -> CAT_INT_DE
            else -> CAT_UNK
        }
    }

    private fun getTargetArchiveEntry(src: TarArchiveEntry, filename: String): TarArchiveEntry {
        val realFilename = getRealFilename(filename)
        if (src.isSymbolicLink) {
            val dst = TarArchiveEntry(realFilename, TarConstants.LF_SYMLINK)
            dst.linkName = src.linkName
            return dst
        }
        // Regular file/folder
        val flag = if (src.isDirectory) TarConstants.LF_DIR else TarConstants.LF_NORMAL
        val dst = TarArchiveEntry(realFilename, flag)
        dst.size = src.size
        dst.mode = src.mode
        dst.modTime = src.modTime
        dst.userId = src.userId
        dst.groupId = src.groupId
        dst.userName = src.userName
        dst.groupName = src.groupName
        return dst
    }

    private fun getRealFilename(filename: String): String {
        val parts = filename.split(File.separator.toRegex(), 2).toTypedArray()
        val firstPart = parts[0]
        val secondPart = parts[1]
        return when (firstPart) {
            Constants.FILES_TREE_TOKEN, Constants.DEVICE_FILES_TREE_TOKEN -> "files/$secondPart"
            Constants.NO_BACKUP_TREE_TOKEN, Constants.DEVICE_NO_BACKUP_TREE_TOKEN -> "no_backup/$secondPart"
            Constants.DATABASE_TREE_TOKEN, Constants.DEVICE_DATABASE_TREE_TOKEN -> "databases/$secondPart"
            Constants.SHAREDPREFS_TREE_TOKEN, Constants.DEVICE_SHAREDPREFS_TREE_TOKEN -> "shared_prefs/$secondPart"
            Constants.CACHE_TREE_TOKEN, Constants.DEVICE_CACHE_TREE_TOKEN -> "caches/$secondPart"
            else -> secondPart
        }
    }

    private class TargetTarEntry(
        val sourceFile: Path,
        val sourceFilename: String,
        val category: Int,
        val targetEntry: TarArchiveEntry
    ) {
        override fun toString(): String {
            return sourceFilename
        }
    }

    companion object {
        @JvmField
        val TAG: String = AndroidBackupExtractor::class.java.simpleName

        @JvmStatic
        @Throws(IOException::class)
        fun toTar(abSource: Path, tarDest: Path, password: CharArray?) {
            val header = AndroidBackupHeader(password)
            try {
                tarDest.openOutputStream().use { os ->
                    header.read(abSource.openInputStream()).use { realIs ->
                        IoUtils.copy(realIs, os)
                    }
                }
            } catch (e: Exception) {
                ExUtils.rethrowAsIOException(e)
            }
        }
    }
}
