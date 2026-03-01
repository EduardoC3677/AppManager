// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.signing

import com.reandroid.archive.ArchiveEntry
import com.reandroid.archive.ArchiveFile
import com.reandroid.archive.writer.ApkFileWriter
import com.reandroid.archive.writer.ZipAligner
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.io.Paths
import java.io.File
import java.io.IOException
import java.util.regex.Pattern
import java.util.zip.ZipEntry

object ZipAlign {
    val TAG: String = ZipAlign::class.java.simpleName
    const val ALIGNMENT_4 = 4
    private const val ALIGNMENT_PAGE = 4096

    @JvmStatic
    @Throws(IOException::class)
    fun align(input: File, output: File, alignment: Int, pageAlignSharedLibs: Boolean) {
        val dir = output.parentFile
        if (dir != null && !Paths.exists(dir)) {
            dir.mkdirs()
        }
        ArchiveFile(input).use { archive ->
            ApkFileWriter(output, archive.inputSources).use { apkWriter ->
                apkWriter.setZipAligner(getZipAligner(alignment, pageAlignSharedLibs))
                apkWriter.write()
            }
        }
        if (!verify(output, alignment, pageAlignSharedLibs)) {
            throw IOException("Could not verify aligned APK file.")
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun align(inFile: File, alignment: Int, pageAlignSharedLibs: Boolean) {
        val tmp = toTmpFile(inFile)
        tmp.delete()
        try {
            align(inFile, tmp, alignment, pageAlignSharedLibs)
            inFile.delete()
            tmp.renameTo(inFile)
        } catch (e: IOException) {
            tmp.delete()
            throw e
        }
    }

    @JvmStatic
    fun verify(file: File, alignment: Int, pageAlignSharedLibs: Boolean): Boolean {
        var foundBad = false
        Log.d(TAG, "Verifying alignment of %s...", file)
        try {
            ArchiveFile(file).use { zipFile ->
                val entryIterator = zipFile.iterator()
                while (entryIterator.hasNext()) {
                    val pEntry = entryIterator.next()
                    val name = pEntry.name
                    val fileOffset = pEntry.fileOffset
                    if (pEntry.method == ZipEntry.DEFLATED) {
                        Log.d(TAG, "%8d %s (OK - compressed)", fileOffset, name)
                    } else if (pEntry.isDirectory) {
                        Log.d(TAG, "%8d %s (OK - directory)", fileOffset, name)
                    } else {
                        val alignTo = getAlignment(pEntry, alignment, pageAlignSharedLibs)
                        if (fileOffset % alignTo != 0L) {
                            Log.w(TAG, "%8d %s (BAD - %d)
", fileOffset, name, fileOffset % alignTo)
                            foundBad = true
                            break
                        } else {
                            Log.d(TAG, "%8d %s (OK)
", fileOffset, name)
                        }
                    }
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Unable to open '%s' for verification", e, file)
            return false
        }
        Log.d(TAG, "Verification %s
", if (foundBad) "FAILED" else "successful")
        return !foundBad
    }

    private fun getAlignment(entry: ArchiveEntry, defaultAlignment: Int, pageAlignSharedLibs: Boolean): Int {
        if (!pageAlignSharedLibs) return defaultAlignment
        val name = entry.name
        return if (name.startsWith("lib/") && name.endsWith(".so")) ALIGNMENT_PAGE else defaultAlignment
    }

    @JvmStatic
    fun getZipAligner(defaultAlignment: Int, pageAlignSharedLibs: Boolean): ZipAligner {
        val zipAligner = ZipAligner()
        zipAligner.setDefaultAlignment(defaultAlignment)
        if (pageAlignSharedLibs) {
            val patternNativeLib = Pattern.compile("^lib/.+\.so$")
            zipAligner.setFileAlignment(patternNativeLib, ALIGNMENT_PAGE)
        }
        return zipAligner
    }

    private fun toTmpFile(file: File): File {
        val name = file.name + ".align.tmp"
        val dir = file.parentFile
        return if (dir == null) File(name) else File(dir, name)
    }
}
