// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.splitapk

import android.content.pm.PackageInfo
import android.graphics.Bitmap
import io.github.muntashirakon.AppManager.apk.ApkUtils
import io.github.muntashirakon.AppManager.compat.UserHandleHidden
import io.github.muntashirakon.AppManager.utils.ContextUtils
import io.github.muntashirakon.AppManager.utils.DigestUtils
import io.github.muntashirakon.AppManager.utils.UIUtils
import io.github.muntashirakon.io.IoUtils
import io.github.muntashirakon.io.Path
import io.github.muntashirakon.io.Paths
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Used to generate app bundle with .apks extension.
 */
object SplitApkExporter {
    @JvmStatic
    @Throws(IOException::class)
    fun saveApks(packageInfo: PackageInfo, apksFile: Path) {
        apksFile.openOutputStream().use { os ->
            ZipOutputStream(os).use { zos ->
                zos.setMethod(ZipOutputStream.DEFLATED)
                zos.setLevel(Deflater.BEST_COMPRESSION)
                saveApkInternal(zos, packageInfo)
            }
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun saveApkInternal(zipOutputStream: ZipOutputStream, packageInfo: PackageInfo) {
        val applicationInfo = packageInfo.applicationInfo!!
        val apkFiles = mutableListOf<Path>().apply {
            add(Paths.get(applicationInfo.publicSourceDir))
            applicationInfo.splitPublicSourceDirs?.forEach { add(Paths.get(it)) }
        }.sorted()

        val apksMetadata = ApksMetadata(packageInfo)
        apksMetadata.writeMetadata(zipOutputStream)

        val bitmap = UIUtils.getBitmapFromDrawable(applicationInfo.loadIcon(ContextUtils.getContext().packageManager))
        ByteArrayOutputStream().use { bos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, bos)
            addBytes(zipOutputStream, bos.toByteArray(), ApksMetadata.ICON_FILE, apksMetadata.exportTimestamp)
        }

        apkFiles.forEach { addFile(zipOutputStream, it, it.name, apksMetadata.exportTimestamp) }

        try {
            ApkUtils.getObbDir(packageInfo.packageName, UserHandleHidden.getUserId(applicationInfo.uid))?.let { dir ->
                dir.listFiles().forEach { addFile(zipOutputStream, it, it.name, apksMetadata.exportTimestamp) }
            }
        } catch (ignore: IOException) {}
    }

    @JvmStatic
    @Throws(IOException::class)
    fun addFile(zipOutputStream: ZipOutputStream, filePath: Path, name: String, timestamp: Long) {
        val zipEntry = ZipEntry(name).apply {
            method = ZipEntry.DEFLATED
            size = filePath.length()
            crc = DigestUtils.calculateCrc32(filePath)
            time = timestamp
        }
        zipOutputStream.putNextEntry(zipEntry)
        filePath.openInputStream().use { IoUtils.copy(it, zipOutputStream) }
        zipOutputStream.closeEntry()
    }

    @JvmStatic
    @Throws(IOException::class)
    fun addBytes(zipOutputStream: ZipOutputStream, bytes: ByteArray, name: String, timestamp: Long) {
        val zipEntry = ZipEntry(name).apply {
            method = ZipEntry.DEFLATED
            size = bytes.size.toLong()
            crc = DigestUtils.calculateCrc32(bytes)
            time = timestamp
        }
        zipOutputStream.putNextEntry(zipEntry)
        zipOutputStream.write(bytes)
        zipOutputStream.closeEntry()
    }
}
