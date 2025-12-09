// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants.O_ACCMODE
import android.system.OsConstants.O_APPEND
import android.system.OsConstants.O_RDONLY
import android.system.OsConstants.O_RDWR
import android.system.OsConstants.O_TRUNC
import android.system.OsConstants.O_WRONLY
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.progress.ProgressHandler
import io.github.muntashirakon.AppManager.self.filecache.FileCache
import io.github.muntashirakon.io.FileSystemManager
import io.github.muntashirakon.io.IoUtils
import io.github.muntashirakon.io.Path
import io.github.muntashirakon.io.Paths
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.math.BigInteger
import java.nio.channels.FileChannel
import java.util.zip.ZipEntry

object FileUtils {
    @JvmField
    val TAG: String = FileUtils::class.java.simpleName

    @AnyThread
    @JvmStatic
    @Throws(IOException::class)
    fun isZip(path: Path): Boolean {
        val header: Int
        path.openInputStream().use { inputStream ->
            val headerBytes = ByteArray(4)
            inputStream.read(headerBytes)
            header = BigInteger(headerBytes).toInt()
        }
        return header == 0x504B0304 || header == 0x504B0506 || header == 0x504B0708
    }

    @AnyThread
    @JvmStatic
    fun getFilenameFromZipEntry(zipEntry: ZipEntry): String {
        return Paths.getLastPathSegment(zipEntry.name)
    }

    @JvmStatic
    @Throws(FileNotFoundException::class)
    fun getFdFromUri(context: Context, uri: Uri, mode: String): ParcelFileDescriptor {
        val fd = context.contentResolver.openFileDescriptor(uri, mode)
            ?: throw FileNotFoundException("Uri inaccessible or empty.")
        return fd
    }

    @AnyThread
    @JvmStatic
    fun getFileFromFd(fd: ParcelFileDescriptor): File {
        return File("/proc/self/fd/${fd.fd}")
    }

    @AnyThread
    @JvmStatic
    fun deleteSilently(path: Path?) {
        if (path == null || !path.exists()) return
        if (!path.delete()) {
            Log.w(TAG, "Unable to delete %s", path)
        }
    }

    @AnyThread
    @JvmStatic
    fun deleteSilently(file: File?) {
        if (!Paths.exists(file)) return
        if (!file!!.delete()) {
            Log.w(TAG, "Unable to delete %s", file)
        }
    }

    @WorkerThread
    @JvmStatic
    fun getContentFromAssets(context: Context, fileName: String): String {
        try {
            context.resources.assets.open(fileName).use { inputStream ->
                return IoUtils.getInputStreamContent(inputStream)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return ""
    }

    @AnyThread
    @JvmStatic
    fun isAssetDirectory(context: Context, path: String): Boolean {
        val files: Array<String>?
        try {
            files = context.assets.list(path)
        } catch (e: IOException) {
            // Doesn't exist
            return false
        }
        return files != null && files.isNotEmpty()
    }

    @AnyThread
    @JvmStatic
    @Throws(IOException::class)
    fun copy(from: Path, to: Path, progressHandler: ProgressHandler?): Long {
        from.openInputStream().use { inputStream ->
            to.openOutputStream().use { outputStream ->
                return copy(inputStream, outputStream, from.length(), progressHandler)
            }
        }
    }

    /**
     * Copy the contents of one stream to another.
     *
     * @param totalSize Total size of the stream. Only used for handling progress. Set `-1` if unknown.
     */
    @AnyThread
    @JvmStatic
    @Throws(IOException::class)
    fun copy(
        inputStream: InputStream,
        out: OutputStream,
        totalSize: Long,
        progressHandler: ProgressHandler?
    ): Long {
        val lastProgress = progressHandler?.getLastProgress() ?: 0f
        return IoUtils.copy(inputStream, out, ThreadUtils.getBackgroundThreadExecutor()) { progress ->
            progressHandler?.postUpdate(100, lastProgress + (progress * 100f / totalSize))
        }
    }

    @WorkerThread
    @JvmStatic
    @Throws(IOException::class)
    fun copyFromAsset(context: Context, fileName: String, dest: Path) {
        context.assets.open(fileName).use { inputStream ->
            dest.openOutputStream().use { outputStream ->
                IoUtils.copy(inputStream, outputStream)
            }
        }
    }

    @AnyThread
    @JvmStatic
    fun getTempPath(relativeDir: String, filename: String): Path {
        val newDir = FileCache.getGlobalFileCache().createCachedDir(relativeDir)
        return Paths.get(File(newDir, filename))
    }

    @AnyThread
    @JvmStatic
    fun getCachePath(): File {
        val context = ContextUtils.getContext()
        return try {
            getExternalCachePath(context)
        } catch (e: FileNotFoundException) {
            context.cacheDir
        }
    }

    @AnyThread
    @JvmStatic
    @Throws(FileNotFoundException::class)
    fun getExternalCachePath(context: Context): File {
        return getBestExternalPath(context.externalCacheDirs)
    }

    @AnyThread
    @JvmStatic
    @Throws(FileNotFoundException::class)
    fun getExternalMediaPath(context: Context): File {
        return getBestExternalPath(context.externalMediaDirs)
    }

    @AnyThread
    @JvmStatic
    @Throws(FileNotFoundException::class)
    fun getBestExternalPath(extDirs: Array<File>?): File {
        if (extDirs == null) {
            throw FileNotFoundException("Shared storage unavailable.")
        }
        var lastReason: String? = null
        for (extDir in extDirs) {
            // The priority is from top to bottom of the list as per Context#getExternalDir()
            if (extDir == null) {
                // Other external directory might exist
                continue
            }
            if (!(extDir.exists() || extDir.mkdirs())) {
                lastReason = "$extDir: permission denied."
                Log.w(TAG, "Could not use %s.", extDir)
                continue
            }
            val storageState = Environment.getExternalStorageState(extDir)
            if (storageState != Environment.MEDIA_MOUNTED) {
                lastReason = "$extDir: not mounted ($storageState)"
                Log.w(TAG, "Path %s not mounted. State: %s", extDir, storageState)
                continue
            }
            return extDir
        }
        throw FileNotFoundException(lastReason ?: "No available shared storage found.")
    }

    @AnyThread
    @JvmStatic
    @Throws(IOException::class)
    fun chmod711(file: File) {
        try {
            Os.chmod(file.absolutePath, 457)
        } catch (e: ErrnoException) {
            Log.e("IOUtils", "Failed to apply mode 711 to $file")
            throw IOException(e)
        }
    }

    @AnyThread
    @JvmStatic
    @Throws(IOException::class)
    fun chmod644(file: File) {
        try {
            Os.chmod(file.absolutePath, 420)
        } catch (e: ErrnoException) {
            Log.e(TAG, "Failed to apply mode 644 to %s", file)
            throw IOException(e)
        }
    }

    @JvmStatic
    fun canReadUnprivileged(file: File): Boolean {
        if (file.canRead()) {
            try {
                FileSystemManager.getLocal().openChannel(file, FileSystemManager.MODE_READ_ONLY).use {
                    return true
                }
            } catch (e: IOException) {
                return false
            } catch (e: SecurityException) {
                return false
            }
        }
        return false
    }

    @JvmStatic
    fun translateModePosixToString(mode: Int): String {
        var res: String = when (mode and O_ACCMODE) {
            O_RDWR -> "rw"
            O_WRONLY -> "w"
            O_RDONLY -> "r"
            else -> throw IllegalArgumentException("Bad mode: $mode")
        }
        if ((mode and O_TRUNC) == O_TRUNC) {
            res += "t"
        }
        if ((mode and O_APPEND) == O_APPEND) {
            res += "a"
        }
        return res
    }
}
