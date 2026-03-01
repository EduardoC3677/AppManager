// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.adb

import android.content.pm.PackageInfo
import android.content.pm.Signature
import android.os.Build
import android.util.StringBuilderPrinter
import io.github.muntashirakon.AppManager.backup.BackupUtils
import io.github.muntashirakon.AppManager.backup.adb.BackupCategories.CAT_SRC
import io.github.muntashirakon.AppManager.utils.ExUtils
import io.github.muntashirakon.AppManager.utils.PackageUtils
import io.github.muntashirakon.AppManager.utils.TarUtils
import io.github.muntashirakon.io.IoUtils
import io.github.muntashirakon.io.Path
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import java.io.*
import java.nio.charset.StandardCharsets
import java.security.cert.CertificateEncodingException
import java.util.*

class AndroidBackupCreator(
    private val mCategoryFilesMap: Map<Int, List<Path>>,
    private val mWorkingDir: Path,
    private val mPackageInfo: PackageInfo,
    private val mInstallerPackage: String?,
    @TarUtils.TarType private val mTarType: String
) : AutoCloseable {
    private val mPackageName: String = mPackageInfo.packageName
    private val mFilesToBeDeleted = mutableListOf<Path>()

    override fun close() {
        for (file in mFilesToBeDeleted) {
            file.delete()
        }
    }

    @Throws(IOException::class)
    fun getBackupFile(dataIndex: Int): Path {
        // Create temporary merged TAR file
        val backupFilename = BackupUtils.getDataFilePrefix(dataIndex, null)
        val tempTarFile = mWorkingDir.createNewFile("$backupFilename.tar", null)
        mFilesToBeDeleted.add(tempTarFile)
        val backupFile = mWorkingDir.createNewFile("$backupFilename.ab", null)

        // Merge all category files into a single TAR
        mergeCategoryFilesIntoTar(tempTarFile)

        // Convert to AB file
        fromTar(tempTarFile, backupFile, null, Build.VERSION.SDK_INT, true)
        return backupFile
    }

    @Throws(IOException::class)
    private fun mergeCategoryFilesIntoTar(outputTarFile: Path) {
        outputTarFile.openOutputStream().use { fos ->
            BufferedOutputStream(fos).use { bos ->
                TarArchiveOutputStream(bos).use { taos ->
                    taos.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                    taos.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)

                    // Add manifest file first
                    addManifestEntry(taos)

                    // Process each category
                    for ((category, files) in mCategoryFilesMap) {
                        if (files.isNotEmpty()) {
                            processCategoryFiles(taos, category, files)
                        }
                    }

                    taos.finish()
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun addManifestEntry(taos: TarArchiveOutputStream) {
        val manifestPath = Constants.APPS_PREFIX + mPackageName + File.separator + Constants.BACKUP_MANIFEST_FILENAME
        val manifestContent = getManifestBytes(mCategoryFilesMap[CAT_SRC] != null)

        val manifestEntry = TarArchiveEntry(manifestPath)
        manifestEntry.size = manifestContent.size.toLong()
        manifestEntry.mode = 0x180 // 0600 in octal
        manifestEntry.modTime = 0 // See AppMetadataBackupWriter.java

        taos.putArchiveEntry(manifestEntry)
        taos.write(manifestContent)
        taos.closeArchiveEntry()
    }

    private fun getManifestBytes(withApk: Boolean): ByteArray {
        val builder = StringBuilder(4096)
        val printer = StringBuilderPrinter(builder)
        printer.println(Constants.BACKUP_MANIFEST_VERSION.toString())
        printer.println(mPackageName)
        printer.println(io.github.muntashirakon.AppManager.utils.PackageUtils.getLongVersionCode(mPackageInfo).toString())
        printer.println(Build.VERSION.SDK_INT.toString())
        printer.println(mInstallerPackage ?: "")
        printer.println(if (withApk) "1" else "0")

        // Write the signature block.
        val signerInfo = PackageUtils.getSignerInfo(mPackageInfo, true)
        if (signerInfo?.currentSignerCerts == null) {
            printer.println("0")
        } else {
            // Retrieve the newest signatures to write.
            try {
                val signerCerts = signerInfo.currentSignerCerts!!
                val signatures = Array(signerCerts.size) { i -> Signature(signerCerts[i].encoded) }
                printer.println(signerCerts.size.toString())
                for (sig in signatures) {
                    printer.println(sig.toCharsString())
                }
            } catch (e: CertificateEncodingException) {
                // Fall back to 0
                printer.println("0")
            }
        }
        return builder.toString().toByteArray()
    }

    @Throws(IOException::class)
    private fun processCategoryFiles(taos: TarArchiveOutputStream, category: Int, files: List<Path>) {
        val basePath = Constants.APPS_PREFIX + mPackageName + File.separator
        for (file in files) {
            val fileName = file.getName()
            val relativePath = getRelativePathForCategory(category, fileName)
            val entry = TarArchiveEntry(basePath + relativePath)
            entry.size = file.length()
            // Set basic attributes
            entry.mode = 0x1b6 // 0666 in octal
            entry.modTime = file.lastModified() / 1000

            taos.putArchiveEntry(entry)
            file.openInputStream().use { isStream ->
                IoUtils.copy(isStream, taos)
            }
            taos.closeArchiveEntry()
        }
    }

    private fun getRelativePathForCategory(category: Int, fileName: String): String {
        return when (category) {
            CAT_SRC -> Constants.APK_TREE_TOKEN + File.separator + fileName
            BackupCategories.CAT_INT_CE -> Constants.FILES_TREE_TOKEN + File.separator + fileName
            BackupCategories.CAT_INT_DE -> Constants.DEVICE_FILES_TREE_TOKEN + File.separator + fileName
            BackupCategories.CAT_EXT -> Constants.MANAGED_EXTERNAL_TREE_TOKEN + File.separator + fileName
            BackupCategories.CAT_OBB -> Constants.OBB_TREE_TOKEN + File.separator + fileName
            else -> fileName
        }
    }

    companion object {
        @JvmField
        val TAG: String = AndroidBackupCreator::class.java.simpleName

        @JvmStatic
        @Throws(IOException::class)
        fun fromTar(tarSource: Path, abDest: Path, password: CharArray?, api: Int, compress: Boolean) {
            val backupFileVersion = Constants.getBackupFileVersionFromApi(api)
            val header = AndroidBackupHeader(backupFileVersion, compress, password)
            try {
                tarSource.openInputStream().use { isStream ->
                    header.write(abDest.openOutputStream()).use { realOs ->
                        IoUtils.copy(isStream, realOs)
                    }
                }
            } catch (e: IOException) {
                throw e
            } catch (e: Exception) {
                ExUtils.rethrowAsIOException(e)
            }
        }
    }
}
