// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io.fs

import android.system.OsConstants
import android.util.LruCache
import com.android.apksig.apk.ApkFormatException
import com.android.apksig.apk.ApkUtils
import com.android.apksig.internal.zip.CentralDirectoryRecord
import com.android.apksig.internal.zip.LocalFileRecord
import com.android.apksig.internal.zip.ZipUtils
import com.android.apksig.util.DataSinks
import com.android.apksig.util.DataSource
import com.android.apksig.util.DataSources
import com.android.apksig.zip.ZipFormatException
import com.j256.simplemagic.ContentType
import io.github.muntashirakon.AppManager.utils.ExUtils
import io.github.muntashirakon.io.Path
import io.github.muntashirakon.io.Paths
import java.io.*
import java.util.*

internal class ApkFileSystem(zipFile: Path) : VirtualFileSystem(zipFile) {
    companion object {
        @JvmField
        val TYPE: String = ContentType.APK.mimeType

        @JvmStatic
        private fun buildTree(cdRecords: List<CentralDirectoryRecord>): Node<CentralDirectoryRecord> {
            val rootNode = Node<CentralDirectoryRecord>(null, File.separator)
            rootNode.addExtra("mtime", System.currentTimeMillis())
            for (cdRecord in cdRecords) {
                buildTree(rootNode, cdRecord)
            }
            return rootNode
        }

        // Build nodes as needed by the entry, entry itself is the last node in the tree if it is not a directory
        private fun buildTree(rootNode: Node<CentralDirectoryRecord>, cdRecord: CentralDirectoryRecord) {
            val filename = Paths.sanitize(cdRecord.name, true) ?: return
            val components = filename.split(File.separator.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (components.isEmpty()) return
            val lastModTime = convertToUnixMillis(cdRecord.lastModificationDate, cdRecord.lastModificationTime)
            var lastNode = rootNode
            for (i in 0 until components.size - 1 /* last one will be set manually */) {
                var newNode = lastNode.getChild(components[i])
                if (newNode == null) {
                    // Add children
                    newNode = Node(lastNode, components[i])
                    newNode.addExtra("mtime", lastModTime)
                    lastNode.addChild(newNode)
                }
                lastNode = newNode
            }
            val finalNode = Node(
                lastNode, components[components.size - 1],
                if (cdRecord.name.endsWith(File.separator)) null else cdRecord
            )
            finalNode.addExtra("mtime", lastModTime)
            lastNode.addChild(finalNode)
        }

        @JvmStatic
        fun convertToUnixMillis(date: Int, time: Int): Long {
            // Extract date components
            val day = date and 0x1F
            val month = date shr 5 and 0xF
            val year = (date shr 9 and 0x7F) + 1980

            // Extract time components
            val second = (time and 0x1F) * 2 // Seconds are rounded to the nearest even second
            val minute = time shr 5 and 0x3F
            val hour = time shr 11 and 0x1F

            // Create a Calendar object
            val calendar = Calendar.getInstance()
            calendar[Calendar.YEAR] = year
            calendar[Calendar.MONTH] = month - 1 // Java months are 0-based
            calendar[Calendar.DAY_OF_MONTH] = day
            calendar[Calendar.HOUR_OF_DAY] = hour
            calendar[Calendar.MINUTE] = minute
            calendar[Calendar.SECOND] = second
            calendar[Calendar.MILLISECOND] = 0

            // Convert to Unix millis
            return calendar.timeInMillis
        }
    }

    private val mCache = LruCache<String, Node<CentralDirectoryRecord>>(100)
    private var mIn: RandomAccessFile? = null
    private var mApk: DataSource? = null
    private var mApkSections: ApkUtils.ZipSections? = null
    private var mCdRecords: List<CentralDirectoryRecord>? = null
    private var mRootNode: Node<CentralDirectoryRecord>? = null

    override fun getType(): String {
        return TYPE
    }

    @Throws(IOException::class)
    override fun onMount(): Path {
        if (getOptions()!!.remount && mIn != null && mRootNode != null) {
            // Remount requested, no need to generate anything if they're already generated.
            return Paths.get(this)
        }
        mIn = RandomAccessFile(getFile().getFile()!!, "r")
        mApk = DataSources.asDataSource(mIn)
        try {
            mApkSections = ApkUtils.findZipSections(mApk)
        } catch (e: ZipFormatException) {
            return ExUtils.rethrowAsIOException(e)
        }
        try {
            mCdRecords = ZipUtils.parseZipCentralDirectory(mApk, mApkSections)
        } catch (e: ApkFormatException) {
            return ExUtils.rethrowAsIOException(e)
        }
        mRootNode = buildTree(mCdRecords!!)
        return Paths.get(this)
    }

    @Throws(IOException::class)
    override fun onUnmount(actions: Map<String, List<Action>>): File? {
        mRootNode = null
        mCache.evictAll()
        mApk = null
        mApkSections = null
        mCdRecords = null
        if (mIn != null) {
            mIn!!.close()
            mIn = null
        }
        // Does not support modification
        return null
    }

    override fun getNode(path: String): Node<*>? {
        checkMounted()
        var targetNode = mCache[path]
        if (targetNode == null) {
            targetNode = if (path == File.separator) {
                mRootNode
            } else {
                mRootNode!!.getLastChild(Paths.sanitize(path, true))
            }
            if (targetNode != null) {
                mCache.put(path, targetNode)
            }
        }
        return targetNode
    }

    override fun invalidate(path: String) {
        mCache.remove(path)
    }

    override fun lastModified(path: Node<*>): Long {
        val time: Long? = path.getExtra("mtime")
        return time ?: getFile().lastModified()
    }

    override fun length(path: String): Long {
        val targetNode = getNode(path) ?: return -1
        if (targetNode.isDirectory()) {
            return 0
        }
        if (!targetNode.isFile()) {
            return -1
        }
        @Suppress("UNCHECKED_CAST")
        val cdRecord = targetNode.getObject() as CentralDirectoryRecord?
        if (cdRecord == null) {
            // Check for cache
            val cachedFile = findCachedFile(targetNode)
            if (cachedFile != null) {
                return cachedFile.length()
            }
            return if (targetNode.isPhysical()) -1 else 0
        }
        return cdRecord.size
    }

    override fun checkAccess(path: String, access: Int): Boolean {
        val targetNode = getNode(path)
        if (access == OsConstants.F_OK) {
            return targetNode != null
        }
        return if (access == OsConstants.R_OK) {
            true
        } else false
        // X_OK, R_OK|X_OK, W_OK, R_OK|W_OK, R_OK|W_OK|X_OK are false
    }

    @Throws(IOException::class)
    override fun getInputStream(node: Node<*>): InputStream {
        return FileInputStream(getCachedFile(node, false))
    }

    @Throws(IOException::class)
    override fun cacheFile(src: Node<*>, sink: File) {
        @Suppress("UNCHECKED_CAST")
        val cdRecord = src.getObject() as CentralDirectoryRecord?
        if (cdRecord == null || mApk == null || mApkSections == null) {
            throw FileNotFoundException("Class definition for ${src.getFullPath()} is not found.")
        }
        val lfhSection = mApk!!.slice(0, mApkSections!!.zipCentralDirectoryOffset)
        try {
            FileOutputStream(sink).use { os ->
                val out = DataSinks.asDataSink(os)
                LocalFileRecord.outputUncompressedData(lfhSection, cdRecord, lfhSection.size(), out)
            }
        } catch (e: ZipFormatException) {
            throw IOException(e)
        }
    }
}
