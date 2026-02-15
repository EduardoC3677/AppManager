// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils

import androidx.annotation.StringDef
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import com.github.luben.zstd.ZstdInputStream
import com.github.luben.zstd.ZstdOutputStream
import io.github.muntashirakon.io.IoUtils
import io.github.muntashirakon.io.Path
import io.github.muntashirakon.io.Paths
import io.github.muntashirakon.io.SplitInputStream
import io.github.muntashirakon.io.SplitOutputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarConstants
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.jetbrains.annotations.Contract
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.regex.Pattern

object TarUtils {
    @JvmField
    val DEFAULT_SPLIT_SIZE: Long = 1024 * 1024 * 1024

    @StringDef(
        value = [
            TAR_GZIP,
            TAR_BZIP2,
            TAR_ZSTD,
        ]
    )
    @Retention(AnnotationRetention.SOURCE)
    annotation class TarType

    const val TAR_GZIP = "z"
    const val TAR_BZIP2 = "j"
    const val TAR_ZSTD = "s"

    /**
     * Create a tar file using the given compression method and split it into multiple files based
     * on the supplied split size.
     *
     * @param type           Compression type
     * @param source         Source directory/file
     * @param dest           Destination directory
     * @param destFilePrefix filename as a prefix (.0, .1, etc. are added at the end)
     * @param filters        A list of mutually exclusive regex filters
     * @param splitSize      Size of the split, [DEFAULT_SPLIT_SIZE] will be used if null is supplied
     * @param exclude        A list of mutually exclusive regex patterns to be excluded
     * @param followLinks    Whether to follow the links
     * @return List of added files
     */
    @JvmStatic
    @WorkerThread
    @Throws(IOException::class)
    fun create(
        @TarType type: String,
        source: Path,
        dest: Path,
        destFilePrefix: String,
        filters: Array<String>?,
        splitSize: Long?,
        exclude: Array<String>?,
        followLinks: Boolean
    ): List<Path> {
        SplitOutputStream(dest, destFilePrefix, splitSize ?: DEFAULT_SPLIT_SIZE).use { sos ->
            BufferedOutputStream(sos).use { bos ->
                createCompressedStream(bos, type).use { os ->
                    TarArchiveOutputStream(os).use { tos ->
                        tos.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                        tos.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)
                        var basePath = if (source.isDirectory) source else source.parent
                        if (basePath == null) {
                            basePath = Paths.get("/")
                        }
                        val files = Paths.getAll(basePath, source, filters, exclude, followLinks)
                        for (file in files) {
                            val relativePath = Paths.relativePath(file, basePath)
                            if (relativePath.isEmpty() || relativePath == "/") continue
                            // For links, check if followLinks is enabled
                            if (!followLinks && file.isSymbolicLink) {
                                // A path can be symbolic link only if it's a file
                                // Add the link as is
                                val tarEntry = TarArchiveEntry(relativePath, TarConstants.LF_SYMLINK)
                                tarEntry.linkName = file.realFilePath
                                tos.putArchiveEntry(tarEntry)
                            } else {
                                val tarEntry = TarArchiveEntry(file, relativePath)
                                tos.putArchiveEntry(tarEntry)
                                if (!file.isDirectory) {
                                    file.openInputStream().use { `is` ->
                                        IoUtils.copy(`is`, tos)
                                    }
                                }
                            }
                            tos.closeArchiveEntry()
                        }
                        tos.finish()
                    }
                }
            }
            return sos.files
        }
    }

    /**
     * Create a tar file using the given compression method and split it into multiple files based
     * on the supplied split size.
     *
     * @param type       Compression type
     * @param sources    Source files, sorted properly if there are multiple files.
     * @param dest       Destination directory
     * @param filters    A list of mutually exclusive regex filters
     * @param exclusions A list of mutually exclusive regex patterns to be excluded
     */
    @JvmStatic
    @WorkerThread
    @Throws(IOException::class)
    fun extract(
        @TarType type: String,
        sources: Array<Path>,
        dest: Path,
        filters: Array<String>?,
        exclusions: Array<String>?,
        realDataAppPath: String?
    ) {
        // Convert filters into patterns to reduce overheads
        val filterPatterns: Array<Pattern>? = filters?.let { f ->
            Array(f.size) { i ->
                Pattern.compile(f[i])
            }
        }
        val exclusionPatterns: Array<Pattern>? = exclusions?.let { e ->
            Array(e.size) { i ->
                Pattern.compile(e[i])
            }
        }
        // Run extraction
        SplitInputStream(sources).use { sis ->
            BufferedInputStream(sis).use { bis ->
                createDecompressedStream(bis, type).use { `is` ->
                    TarArchiveInputStream(`is`).use { tis ->
                        val realDestPath = dest.realFilePath
                        var entry: TarArchiveEntry?
                        while (tis.nextEntry.also { entry = it } != null) {
                            val filename = Paths.normalize(entry!!.name)
                            // Early zip slip vulnerability check to avoid creating any files at all
                            if (filename == null || filename.startsWith("../")) {
                                throw IOException(
                                    "Zip slip vulnerability detected!" +
                                            "\nExpected dest: " + File(realDestPath, entry!!.name) +
                                            "\nActual path: " + (filename?.let { File(realDestPath, it) }
                                        ?: realDestPath)
                                )
                            }
                            val file: Path = if (entry!!.isDirectory) {
                                dest.createDirectoriesIfRequired(filename)
                            } else {
                                dest.createNewArbitraryFile(filename, null)
                            }
                            if (!entry!!.isDirectory && (!Paths.isUnderFilter(file, dest, filterPatterns)
                                        || Paths.willExclude(file, dest, exclusionPatterns))
                            ) {
                                // Unlike create, there's no efficient way to detect if a directory contains any filters.
                                // Therefore, directory can't be filtered during extraction
                                file.delete()
                                continue
                            }
                            // Check if the given entry is a link.
                            if (entry!!.isSymbolicLink && file.filePath != null) {
                                if ((!Paths.isUnderFilter(file, dest, filterPatterns) || Paths.willExclude(
                                        file,
                                        dest,
                                        exclusionPatterns
                                    ))
                                ) {
                                    // Do not create this link even if it is a directory
                                    continue
                                }
                                var linkName = entry!!.linkName
                                // There's no need to check if the linkName exists as it may be extracted
                                // after the link has been created
                                // Special check for /data/app
                                if (linkName.startsWith("/data/app/")) {
                                    linkName = getAbsolutePathToDataApp(linkName, realDataAppPath)
                                }
                                file.delete()
                                if (!file.createNewSymbolicLink(linkName)) {
                                    throw IOException("Couldn't create symbolic link $file pointing to $linkName")
                                }
                                continue  // links do not need permission fixes
                            } else {
                                // Zip slip vulnerability might still be present
                                val realFilePath = file.realFilePath
                                if (realDestPath != null && realFilePath != null && !realFilePath.startsWith(
                                        realDestPath
                                    )
                                ) {
                                    throw IOException(
                                        "Zip slip vulnerability detected!" +
                                                "\nExpected dest: " + File(realDestPath, entry!!.name) +
                                                "\nActual path: " + realFilePath
                                    )
                                }
                                if (!entry!!.isDirectory) {
                                    file.openOutputStream().use { os ->
                                        IoUtils.copy(tis, os)
                                    }
                                }
                            }
                            // Fix permissions
                            val finalEntry = entry
                            ExUtils.exceptionAsIgnored {
                                Paths.setPermissions(
                                    file, finalEntry!!.mode,
                                    finalEntry.longUserId.toInt(), finalEntry.longGroupId.toInt()
                                )
                            }
                            // Restore timestamp
                            val modificationTime = entry!!.modTime.time
                            if (modificationTime > 0) { // Backward-compatibility
                                file.setLastModified(entry!!.modTime.time)
                            }
                        }
                    }
                }
            }
        }
    }

    @JvmStatic
    @Contract("_, _ -> new")
    @Throws(IOException::class)
    fun createDecompressedStream(
        compressedStream: InputStream,
        @TarType tarType: String
    ): InputStream {
        return when (tarType) {
            TAR_GZIP -> GzipCompressorInputStream(compressedStream, true)
            TAR_BZIP2 -> BZip2CompressorInputStream(compressedStream, true)
            TAR_ZSTD -> ZstdInputStream(compressedStream)
            else -> throw IllegalArgumentException("Invalid compression type: $tarType")
        }
    }

    @JvmStatic
    @Contract("_, _ -> new")
    @Throws(IOException::class)
    fun createCompressedStream(
        regularStream: OutputStream,
        @TarType tarType: String
    ): OutputStream {
        return when (tarType) {
            TAR_GZIP -> GzipCompressorOutputStream(regularStream)
            TAR_BZIP2 -> BZip2CompressorOutputStream(regularStream)
            TAR_ZSTD -> ZstdOutputStream(regularStream)
            else -> throw IllegalArgumentException("Invalid compression type: $tarType")
        }
    }

    @JvmStatic
    @VisibleForTesting
    fun getAbsolutePathToDataApp(brokenPath: String, realPath: String?): String {
        val normalizedPath = if (brokenPath.endsWith(File.separator)) {
            brokenPath.substring(0, brokenPath.length - 1)
        } else {
            brokenPath
        }
        if (realPath == null) return normalizedPath
        if ("/data/app" == normalizedPath) {
            return normalizedPath
        }
        @Suppress("SuspiciousRegexArgument")
        val brokenPathParts = normalizedPath.split(File.separator).toTypedArray()
        // The initial number of File.separator is 4, and the rests could be either part of the app path or
        // point to lib, oat or apk files
        // Index 4-1 = 3 is always a link to app
        if (brokenPathParts.size <= 4) {
            return realPath
        }
        if ("lib" == brokenPathParts[4] || "oat" == brokenPathParts[4] || brokenPathParts[4].endsWith(".apk")) {
            val sb = StringBuilder(realPath)
            for (i in 4 until brokenPathParts.size) {
                sb.append(File.separator).append(brokenPathParts[i])
            }
            return sb.toString()
        }
        // Index 5-1 = 4 is also a part of the app
        if (brokenPathParts.size == 5) {
            return realPath
        }
        // Index 6-1 = 5 and later are currently not a part of the app
        val sb = StringBuilder(realPath)
        for (i in 5 until brokenPathParts.size) {
            sb.append(File.separator).append(brokenPathParts[i])
        }
        return sb.toString()
    }
}
