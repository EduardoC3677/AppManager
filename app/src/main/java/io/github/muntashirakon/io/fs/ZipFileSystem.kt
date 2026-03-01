// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io.fs

import android.os.Build
import android.system.OsConstants
import android.util.LruCache
import com.j256.simplemagic.ContentType
import io.github.muntashirakon.AppManager.self.filecache.FileCache
import io.github.muntashirakon.AppManager.utils.DigestUtils
import io.github.muntashirakon.io.IoUtils
import io.github.muntashirakon.io.Path
import io.github.muntashirakon.io.Paths
import java.io.*
import java.nio.file.attribute.FileTime
import java.util.*
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

internal class ZipFileSystem(zipFile: Path) : VirtualFileSystem(zipFile) {
    companion object {
        @JvmField
        val TYPE: String = ContentType.ZIP.mimeType

        @JvmStatic
        private fun buildTree(zipFile: ZipFile): Node<ZipEntry> {
            val rootNode = Node<ZipEntry>(null, File.separator)
            val zipEntries = zipFile.entries()
            while (zipEntries.hasMoreElements()) {
                val zipEntry = zipEntries.nextElement()
                buildTree(rootNode, zipEntry)
            }
            return rootNode
        }

        // Build nodes as needed by the entry, entry itself is the last node in the tree if it is not a directory
        private fun buildTree(rootNode: Node<ZipEntry>, zipEntry: ZipEntry) {
            val filename = Paths.sanitize(zipEntry.name, true) ?: return
            val components = filename.split(File.separator.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (components.isEmpty()) return
            var lastNode = rootNode
            for (i in 0 until components.size - 1 /* last one will be set manually */) {
                var newNode = lastNode.getChild(components[i])
                if (newNode == null) {
                    // Add children
                    newNode = Node(lastNode, components[i])
                    lastNode.addChild(newNode)
                }
                lastNode = newNode
            }
            lastNode.addChild(
                Node(
                    lastNode, components[components.size - 1],
                    if (zipEntry.isDirectory) null else zipEntry
                )
            )
        }
    }

    private class VirtualZipEntry(name: String) : ZipEntry(name) {
        var cachedFile: File? = null
    }

    private val mCache = LruCache<String, Node<ZipEntry>>(100)
    private var mZipFile: ZipFile? = null
    private var mRootNode: Node<ZipEntry>? = null

    override fun getType(): String {
        return TYPE
    }

    @Throws(IOException::class)
    override fun onMount(): Path {
        if (getOptions()!!.remount && mZipFile != null && mRootNode != null) {
            // Remount requested, no need to generate anything if they're already generated.
            return Paths.get(this)
        }
        mZipFile = ZipFile(getFile().getFile()!!)
        mRootNode = buildTree(mZipFile!!)
        return Paths.get(this)
    }

    @Throws(IOException::class)
    override fun onUnmount(actions: Map<String, List<Action>>): File? {
        val cachedFile = getUpdatedZipFile(actions)
        mRootNode = null
        mCache.evictAll()
        if (mZipFile != null) {
            mZipFile!!.close()
            mZipFile = null
        }
        return cachedFile
    }

    @Throws(IOException::class)
    private fun getUpdatedZipFile(actionList: Map<String, List<Action>>): File? {
        if (!getOptions()!!.readWrite || actionList.isEmpty()) {
            return null
        }
        val extension = getFile().getExtension()
        val file = FileCache.getGlobalFileCache().createCachedFile(extension)
        val zipEntries = HashMap<String, ZipEntry>()
        for (zipEntry in Collections.list(mZipFile!!.entries())) {
            zipEntries[Paths.sanitize(File.separator + zipEntry.name, false)!!] = zipEntry
        }
        for (path in actionList.keys) {
            // Perform action for each path
            val actions = actionList[path] ?: continue
            for (action in actions) {
                val targetNode = action.targetNode
                // Actions are linear
                when (action.action) {
                    ACTION_CREATE ->                         // This must be a new file/folder. So, override the existing one.
                        zipEntries[targetNode.getFullPath()] = getNewZipEntry(targetNode)
                    ACTION_DELETE ->                         // Delete the entry
                        zipEntries.remove(targetNode.getFullPath())
                    ACTION_UPDATE -> {
                        // It's a file and it's updated. So, cached file must exist.
                        val cachedFile = action.cachedPath!!
                        zipEntries[targetNode.getFullPath()] = getZipEntry(targetNode, cachedFile)
                    }
                    ACTION_MOVE -> {
                        // File/directory move
                        val sourcePath = action.sourcePath!!
                        val zipEntry = zipEntries[sourcePath]
                        if (zipEntry != null) {
                            zipEntries[targetNode.getFullPath()] = zipEntry
                        } else {
                            zipEntries[targetNode.getFullPath()] = getNewZipEntry(targetNode)
                        }
                        zipEntries.remove(sourcePath)
                    }
                }
            }
        }
        FileOutputStream(file).use { os ->
            ZipOutputStream(os).use { zos ->
                zos.setMethod(ZipOutputStream.DEFLATED)
                zos.setLevel(Deflater.BEST_COMPRESSION)
                val paths = ArrayList(zipEntries.keys)
                Collections.sort(paths)
                for (path in paths) {
                    val zipEntry = zipEntries[path] ?: continue
                    if (zipEntry is VirtualZipEntry) {
                        // Our custom zip files
                        zos.putNextEntry(zipEntry)
                        if (zipEntry.isDirectory) {
                            zos.closeEntry()
                            continue
                        }
                        // Entry is a file
                        val cachedFile = zipEntry.cachedFile
                        if (cachedFile != null) {
                            FileInputStream(cachedFile).use { `is` -> IoUtils.copy(`is`, zos) }
                        } // else cached file was not created because the file was only created and never written to
                        zos.closeEntry()
                    } else {
                        // Not our custom files, need to copy from zipEntry everything except the name
                        val newZipEntry = getZipEntry(path, zipEntry)
                        zos.putNextEntry(newZipEntry)
                        if (zipEntry.isDirectory) {
                            zos.closeEntry()
                            continue
                        }
                        // Entry is a file
                        mZipFile!!.getInputStream(zipEntry).use { `is` -> IoUtils.copy(`is`, zos) }
                        zos.closeEntry()
                    }
                }
            }
        }
        return file
    }

    private fun getNewZipEntry(node: Node<*>): ZipEntry {
        var name = Paths.sanitize(node.getFullPath(), false)!!
        if (node.isDirectory()) {
            name += File.separator
        }
        val zipEntry = VirtualZipEntry(name)
        zipEntry.method = ZipEntry.DEFLATED
        if (node.isFile()) {
            zipEntry.size = 0L
        }
        zipEntry.time = System.currentTimeMillis()
        return zipEntry
    }

    @Throws(IOException::class)
    private fun getZipEntry(node: Node<*>, cachedFile: File): ZipEntry {
        var name = Paths.sanitize(node.getFullPath(), false)!!
        if (node.isDirectory()) {
            name += File.separator
        }
        val zipEntry = VirtualZipEntry(name)
        zipEntry.method = ZipEntry.DEFLATED
        zipEntry.cachedFile = cachedFile
        zipEntry.size = cachedFile.length()
        zipEntry.crc = DigestUtils.calculateCrc32(Paths.get(cachedFile))
        zipEntry.time = cachedFile.lastModified()
        return zipEntry
    }

    private fun getZipEntry(path: String, zipEntry: ZipEntry): ZipEntry {
        var name = Paths.sanitize(File.separator + path, false)!!
        if (zipEntry.isDirectory) {
            name += File.separator
        }
        val zipEntry1 = VirtualZipEntry(name)
        zipEntry1.method = ZipEntry.DEFLATED
        zipEntry1.size = zipEntry.size
        zipEntry1.crc = zipEntry.crc
        zipEntry1.time = zipEntry.time
        zipEntry1.comment = zipEntry.comment
        zipEntry1.extra = zipEntry.extra
        return zipEntry1
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
        @Suppress("UNCHECKED_CAST")
        val zipEntry = path.getObject() as ZipEntry? ?: return getFile().lastModified()
        return zipEntry.time
    }

    override fun lastAccess(path: String): Long {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return lastModified(path)
        }
        val targetNode = getNode(path)
        @Suppress("UNCHECKED_CAST")
        val zipEntry = (targetNode?.getObject() as ZipEntry?) ?: return getFile().lastModified()
        val ft = zipEntry.lastAccessTime
        return ft?.toMillis() ?: zipEntry.time
    }

    override fun creationTime(path: String): Long {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return lastModified(path)
        }
        val targetNode = getNode(path)
        @Suppress("UNCHECKED_CAST")
        val zipEntry = (targetNode?.getObject() as ZipEntry?) ?: return getFile().lastModified()
        val ft = zipEntry.creationTime
        return ft?.toMillis() ?: zipEntry.time
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
        val zipEntry = targetNode.getObject() as ZipEntry?
        if (zipEntry == null) {
            // Check for cache
            val cachedFile = findCachedFile(targetNode)
            if (cachedFile != null) {
                return cachedFile.length()
            }
            return if (targetNode.isPhysical()) -1 else 0
        }
        return zipEntry.size
    }

    override fun checkAccess(path: String, access: Int): Boolean {
        val targetNode = getNode(path)
        if (access == OsConstants.F_OK) {
            return targetNode != null
        }
        if (access == OsConstants.R_OK) {
            return true
        }
        if (access == OsConstants.W_OK) {
            return getOptions()!!.readWrite
        }
        if (access == (OsConstants.R_OK or OsConstants.W_OK)) {
            return getOptions()!!.readWrite
        }
        // X_OK, R_OK|X_OK, R_OK|W_OK|X_OK are false
        return false
    }

    @Throws(IOException::class)
    override fun getInputStream(node: Node<*>): InputStream {
        @Suppress("UNCHECKED_CAST")
        val zipEntry = node.getObject() as ZipEntry? ?: throw FileNotFoundException("Class definition for ${node.getFullPath()} is not found.")
        return mZipFile!!.getInputStream(zipEntry)
    }

    @Throws(IOException::class)
    override fun cacheFile(src: Node<*>, sink: File) {
        getInputStream(src).use { `is` ->
            FileOutputStream(sink).use { os ->
                IoUtils.copy(`is`, os)
            }
        }
    }
}
