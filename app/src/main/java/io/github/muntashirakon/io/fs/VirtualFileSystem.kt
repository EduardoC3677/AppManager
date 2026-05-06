// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io.fs

import android.net.Uri
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import androidx.annotation.IntDef
import androidx.annotation.WorkerThread
import androidx.collection.SparseArrayCompat
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.self.filecache.FileCache
import io.github.muntashirakon.io.FileSystemManager
import io.github.muntashirakon.io.FileSystemManager.MODE_READ_ONLY
import io.github.muntashirakon.io.FileSystemManager.MODE_READ_WRITE
import io.github.muntashirakon.io.FileSystemManager.MODE_WRITE_ONLY
import io.github.muntashirakon.io.Path
import io.github.muntashirakon.io.Paths
import io.github.muntashirakon.io.UidGidPair
import java.io.*
import java.nio.channels.FileChannel
import java.util.*
import java.util.concurrent.ThreadLocalRandom

@Suppress("unused")
abstract class VirtualFileSystem(private val mFile: Path) {
    class MountOptions private constructor(
        val remount: Boolean,
        val readWrite: Boolean,
        val mode: Int,
        val uidGidPair: UidGidPair?,
        val onFileSystemUnmounted: OnFileSystemUnmounted?
    ) {
        class Builder {
            private var mRemount = false
            private var mReadWrite = false
            private var mMode = 0
            private var mUidGidPair: UidGidPair? = null
            private var mOnFileSystemUnmounted: OnFileSystemUnmounted? = null

            fun setRemount(remount: Boolean): Builder {
                mRemount = remount
                return this
            }

            fun setReadWrite(readWrite: Boolean): Builder {
                mReadWrite = readWrite
                return this
            }

            fun setMode(mode: Int): Builder {
                mMode = mode
                return this
            }

            fun setUidGidPair(uidGidPair: UidGidPair?): Builder {
                mUidGidPair = uidGidPair
                return this
            }

            fun setOnFileSystemUnmounted(fileSystemUnmounted: OnFileSystemUnmounted?): Builder {
                mOnFileSystemUnmounted = fileSystemUnmounted
                return this
            }

            fun build(): MountOptions {
                return MountOptions(mRemount, mReadWrite, mMode, mUidGidPair, mOnFileSystemUnmounted)
            }
        }
    }

    companion object {
        @JvmField
        val TAG: String = VirtualFileSystem::class.java.simpleName
        const val SCHEME = "vfs"\n@JvmStatic
        fun getUri(fsId: Int, path: String?): Uri {
            return Uri.Builder()
                .scheme(SCHEME)
                .authority(fsId.toString())
                .path(path ?: File.separator)
                .build()
        }

        private fun getNewInstance(file: Path, type: String): VirtualFileSystem {
            return when (type) {
                ZipFileSystem.TYPE -> ZipFileSystem(file)
                ApkFileSystem.TYPE -> ApkFileSystem(file)
                DexFileSystem.TYPE -> DexFileSystem(file)
                else -> throw IllegalArgumentException("Invalid type $type")
            }
        }

        private val sFileSystems = SparseArrayCompat<VirtualFileSystem>(3)
        private val sUriVfsIdsMap = HashMap<Uri, Int>(3)
        private val sParentUriVfsIdsMap = HashMap<Uri, MutableList<Int>>(3)

        @JvmStatic
        @WorkerThread
        @Throws(IOException::class)
        fun mount(mountPoint: Uri, file: Path, type: String): Int {
            return mount(mountPoint, file, type, MountOptions.Builder().build())
        }

        @JvmStatic
        @WorkerThread
        @Throws(IOException::class)
        fun mount(
            mountPoint: Uri,
            file: Path,
            type: String,
            options: MountOptions
        ): Int {
            return mount(mountPoint, getNewInstance(file, type), options)
        }

        @JvmStatic
        @WorkerThread
        @Throws(IOException::class)
        private fun mount(mountPoint: Uri, fs: VirtualFileSystem, options: MountOptions): Int {
            var vfsId: Int
            synchronized(sFileSystems) {
                synchronized(sUriVfsIdsMap) {
                    if (!options.remount && sUriVfsIdsMap[mountPoint] != null) {
                        throw IOException("Mount point ($mountPoint) is already in use.")
                    }
                }
                do {
                    vfsId = ThreadLocalRandom.current().nextInt(1, Int.MAX_VALUE)
                } while (vfsId == 0 || sFileSystems[vfsId] != null)
                fs.mount(mountPoint, vfsId, options)
                sFileSystems.put(vfsId, fs)
            }
            synchronized(sUriVfsIdsMap) {
                sUriVfsIdsMap[mountPoint] = vfsId
            }
            synchronized(sParentUriVfsIdsMap) {
                val uri = Paths.removeLastPathSegment(mountPoint)
                var vfsIds = sParentUriVfsIdsMap[uri]
                if (vfsIds == null) {
                    vfsIds = ArrayList(1)
                    sParentUriVfsIdsMap[uri] = vfsIds
                }
                vfsIds.add(vfsId)
            }
            Log.d(TAG, "Mounted %d at %s", vfsId, mountPoint)
            return vfsId
        }

        @JvmStatic
        @WorkerThread
        @Throws(IOException::class)
        fun unmount(vfsId: Int) {
            val fs = getFileSystem(vfsId) ?: return
            val mountPoint = fs.getMountPoint()
            synchronized(sFileSystems) {
                sFileSystems.remove(vfsId)
            }
            synchronized(sUriVfsIdsMap) {
                sUriVfsIdsMap.remove(mountPoint)
            }
            synchronized(sParentUriVfsIdsMap) {
                if (mountPoint != null) {
                    val uri = Paths.removeLastPathSegment(mountPoint)
                    val vfsIds = sParentUriVfsIdsMap[uri]
                    if (vfsIds != null && vfsIds.contains(vfsId)) {
                        if (vfsIds.size == 1) sParentUriVfsIdsMap.remove(uri)
                        else vfsIds.remove(Integer.valueOf(vfsId))
                    }
                }
            }
            fs.unmount()
            Log.d(TAG, "%d unmounted at %s", vfsId, mountPoint)
        }

        @JvmStatic
        fun alterMountPoint(oldMountPoint: Uri, newMountPoint: Uri) {
            val fs = getFileSystem(oldMountPoint) ?: return
            synchronized(sUriVfsIdsMap) {
                sUriVfsIdsMap.remove(oldMountPoint)
                sUriVfsIdsMap[newMountPoint] = fs.getFsId()
            }
            synchronized(sParentUriVfsIdsMap) {
                // Remove old mount point
                val oldParent = Paths.removeLastPathSegment(oldMountPoint)
                val oldFsIds = sParentUriVfsIdsMap[oldParent]
                oldFsIds?.remove(Integer.valueOf(fs.getFsId()))
                // Add new mount point
                val newParent = Paths.removeLastPathSegment(newMountPoint)
                var newFsIds = sParentUriVfsIdsMap[newParent]
                if (newFsIds == null) {
                    newFsIds = ArrayList(1)
                    sParentUriVfsIdsMap[newParent] = newFsIds
                }
                newFsIds.add(fs.getFsId())
            }
            fs.mMountPoint = newMountPoint
            Log.d(TAG, "Mount point of %d altered from %s to %s", fs.getFsId(), oldMountPoint, newMountPoint)
        }

        @JvmStatic
        fun isMountPoint(uri: Uri): Boolean {
            return getFileSystem(uri) != null
        }

        /**
         * @see .getRootPath
         */
        @JvmStatic
        fun getFsRoot(vfsId: Int): Path? {
            val fs = getFileSystem(vfsId)
            return fs?.getRootPath()
        }

        /**
         * @see .getRootPath
         */
        @JvmStatic
        fun getFsRoot(mountPoint: Uri?): Path? {
            val vfsId = synchronized(sUriVfsIdsMap) {
                sUriVfsIdsMap[mountPoint]
            } ?: return null
            return getFsRoot(vfsId)
        }

        @JvmStatic
        fun getFileSystem(mountPoint: Uri): VirtualFileSystem? {
            val vfsId = synchronized(sUriVfsIdsMap) {
                sUriVfsIdsMap[mountPoint]
            } ?: return null
            return getFileSystem(vfsId)
        }

        @JvmStatic
        fun getFileSystem(vfsId: Int): VirtualFileSystem? {
            return synchronized(sFileSystems) {
                sFileSystems[vfsId]
            }
        }

        @JvmStatic
        fun getFileSystemsAtUri(parentUri: Uri): Array<VirtualFileSystem> {
            val vfsIds = synchronized(sParentUriVfsIdsMap) {
                sParentUriVfsIdsMap[parentUri]
            } ?: return emptyArray()
            val fs = arrayOfNulls<VirtualFileSystem>(vfsIds.size)
            synchronized(sFileSystems) {
                for (i in fs.indices) {
                    fs[i] = sFileSystems[vfsIds[i]]
                }
            }
            @Suppress("UNCHECKED_CAST")
            return fs as Array<VirtualFileSystem>
        }

        protected const val ACTION_CREATE = 1
        protected const val ACTION_UPDATE = 2
        protected const val ACTION_DELETE = 3
        protected const val ACTION_MOVE = 4
    }

    interface OnFileSystemUnmounted {
        /**
         * Run after the file is unmounted.
         *
         * @param fs         The file system, now unusable
         * @param cachedFile The cached file created by the file system if it supports writing
         * @return `true` if the cached file is handled manually, `false` otherwise. The file should be
         * handled manually in most cases.
         */
        fun onUnmounted(fs: VirtualFileSystem, cachedFile: File?): Boolean
    }

    @IntDef(ACTION_CREATE, ACTION_UPDATE, ACTION_DELETE, ACTION_MOVE)
    @Retention(AnnotationRetention.SOURCE)
    protected annotation class CrudAction

    /**
     * Create a new path in the system. [Action.targetNode] for this action may not exist.
     */
    protected open class Action(@CrudAction val action: Int, val targetNode: Node<*>) {
        /**
         * Cached path is located outside the file system, in a physical location. Contents of this path is later used
         * to modify the virtual file system.
         */
        var cachedPath: File? = null

        // Only applicable for ACTION_MOVE
        var sourcePath: String? = null

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Action) return false
            return action == other.action
        }

        override fun hashCode(): Int {
            return Objects.hash(action)
        }
    }

    private class FileCacheItem(val cachedFile: File) {
        var isModified = false

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is FileCacheItem) return false
            return cachedFile == other.cachedFile
        }

        override fun hashCode(): Int {
            return Objects.hash(cachedFile)
        }
    }

    private val sLock = Any()
    private val mActions = HashMap<String, MutableList<Action>>()
    private val mFileCacheMap = HashMap<String, FileCacheItem>()
    private val mFileCache = FileCache()

    private var mMountPoint: Uri? = null
    private var mOptions: MountOptions? = null
    private var mFsId = 0
    private var mRootPath: Path? = null

    fun getFile(): Path {
        return mFile
    }

    abstract fun getType(): String

    fun getFsId(): Int {
        return mFsId
    }

    /**
     * Return the abstract location where the file system is mounted. This is similar to the mount point returned by the
     * `mount` command.
     */
    fun getMountPoint(): Uri? {
        return mMountPoint
    }

    fun getOptions(): MountOptions? {
        return mOptions
    }

    /**
     * Return the abstract location where the file system is located. This is not the same as [getMountPoint]
     * which returns the abstract location where the file system is mounted. In other words, this is the real location
     * to the file system, and should never be used other than the Path APIs.
     */
    fun getRootPath(): Path {
        return mRootPath!!
    }

    /**
     * Mount the file system.
     *
     * @param fsId Unique file system ID to locate it.
     * @throws IOException If the file system cannot be mounted.
     */
    @Throws(IOException::class)
    private fun mount(mountPoint: Uri, fsId: Int, options: MountOptions) {
        synchronized(sLock) {
            mMountPoint = mountPoint
            mFsId = fsId
            mOptions = options
            onPreMount()
            mRootPath = onMount()
            onMounted()
        }
    }

    /**
     * Instructions to execute before mounting the file system.
     *
     * Note that the root path haven't been initialised at this point and calling [getRootPath] would throw
     * an NPE.
     */
    protected open fun onPreMount() {}

    /**
     * Mount the file system. Operations related to mounting the file system should be done here.
     *
     * @return The real path of the file system AKA root path.
     * @see .getRootPath
     */
    @Throws(IOException::class)
    protected abstract fun onMount(): Path

    /**
     * Instructions to execute after mounting the file system. Any initialisations such as building file trees should
     * be done where.
     */
    protected open fun onMounted() {}

    @Throws(IOException::class)
    private fun unmount() {
        checkMounted()
        val options = mOptions!!
        synchronized(sLock) {
            // Update actions
            for (path in mFileCacheMap.keys) {
                val fileCacheItem = mFileCacheMap[path]!!
                if (fileCacheItem.isModified) {
                    val node = getNode(path)
                    if (node != null) {
                        val action = Action(ACTION_UPDATE, node)
                        action.cachedPath = fileCacheItem.cachedFile
                        addAction(node.getFullPath(), action)
                    }
                }
            }
            val cachedFile = onUnmount(mActions)
            mFsId = 0
            mMountPoint = null
            // Cleanup
            mFileCacheMap.clear()
            mFileCache.deleteAll()
            mActions.clear()
            // Handle cached file
            if (cachedFile != null) {
                // Run interface if available
                val handled = options.onFileSystemUnmounted != null
                        && options.onFileSystemUnmounted.onUnmounted(this, cachedFile)
                if (!handled) {
                    // Cached file isn't handled above, try the default ignoring all issues
                    val dest = getFile()
                    val source = Paths.get(cachedFile)
                    dest.delete()
                    source.moveTo(dest)
                }
            }
            mOptions = null
            onUnmounted()
        }
    }

    protected fun finalize() {
        mFileCache.close()
    }

    /**
     * Instructions to execute to unmount the file system. Example operations include cleaning up trees, saving
     * changes, closing resources.
     *
     * @return The final cached file if the file system support modification, `null` otherwise.
     */
    @Throws(IOException::class)
    protected abstract fun onUnmount(actions: Map<String, List<Action>>): File?

    /**
     * Instructions to execute after unmounting the file system.
     */
    protected open fun onUnmounted() {}

    protected fun checkMounted() {
        synchronized(sLock) {
            if (mFsId == 0) {
                throw NotMountedException("Not mounted")
            }
        }
    }

    /* File APIs */
    private fun addAction(path: String, action: Action) {
        synchronized(mActions) {
            var actionSet = mActions[path]
            if (actionSet == null) {
                actionSet = ArrayList()
                mActions[path] = actionSet
            }
            actionSet.add(action)
        }
    }

    protected abstract fun getNode(path: String): Node<*>?

    protected abstract fun invalidate(path: String)

    open fun getCanonicalPath(path: String): String? {
        checkMounted()
        return null
    }

    fun isDirectory(path: String): Boolean {
        val targetNode = getNode(path) ?: return false
        return targetNode.isDirectory()
    }

    fun isFile(path: String): Boolean {
        val targetNode = getNode(path) ?: return false
        return targetNode.isFile()
    }

    fun isHidden(path: String): Boolean {
        val targetNode = getNode(path) ?: return false
        return targetNode.getName().startsWith(".")
    }

    protected abstract fun lastModified(path: Node<*>): Long

    fun lastModified(path: String): Long {
        val targetNode = getNode(path) ?: return getFile().lastModified()
        val cachedFile = findCachedFile(targetNode)
        if (cachedFile != null) {
            return cachedFile.lastModified()
        }
        return lastModified(targetNode)
    }

    fun lastAccess(path: String): Long {
        return lastModified(path)
    }

    fun creationTime(path: String): Long {
        return lastModified(path)
    }

    abstract fun length(path: String): Long

    fun createNewFile(path: String): Boolean {
        if (checkAccess(path, OsConstants.F_OK)) {
            return false
        }
        val filename = Paths.getLastPathSegment(path)
        val parent = Paths.removeLastPathSegment(path)
        if (!checkAccess(parent, OsConstants.W_OK)) {
            return false
        }
        val parentNode = getNode(parent)
        if (parentNode == null || !parentNode.isDirectory()) {
            return false
        }
        val newFileNode = Node<Any?>(parentNode, filename, false)
        newFileNode.setFile(true)
        parentNode.addChild(newFileNode)
        addAction(newFileNode.getFullPath(), Action(ACTION_CREATE, newFileNode))
        invalidate(path)
        return true
    }

    fun delete(path: String): Boolean {
        if (!checkAccess(path, OsConstants.W_OK)) {
            return false
        }
        val targetNode = getNode(path) ?: return false
        addAction(targetNode.getFullPath(), Action(ACTION_DELETE, targetNode))
        val parentNode = targetNode.getParent()
        parentNode?.removeChild(targetNode)
        invalidate(path)
        return true
    }

    fun list(path: String): Array<String>? {
        val targetNode = getNode(path) ?: return null
        val children = targetNode.listChildren() ?: return null
        val childNames = Array(children.size) { i -> children[i].getName() }
        return childNames
    }

    fun mkdir(path: String): Boolean {
        if (checkAccess(path, OsConstants.F_OK)) {
            // File/folder exists
            return false
        }
        val filename = Paths.getLastPathSegment(path)
        val parent = Paths.removeLastPathSegment(path)
        if (!checkAccess(parent, OsConstants.W_OK)) {
            return false
        }
        val parentNode = getNode(parent)
        if (parentNode == null || !parentNode.isDirectory()) {
            return false
        }
        val newFileNode = Node<Any?>(parentNode, filename, false)
        newFileNode.setDirectory(true)
        parentNode.addChild(newFileNode)
        addAction(newFileNode.getFullPath(), Action(ACTION_CREATE, newFileNode))
        invalidate(path)
        return true
    }

    fun mkdirs(path: String): Boolean {
        if (checkAccess(path, OsConstants.F_OK)) {
            // File/folder exists
            return false
        }
        val parts = ArrayList<String>()
        var parent = path
        var parentNode: Node<*>?
        do {
            val filename = Paths.getLastPathSegment(parent)
            parent = Paths.removeLastPathSegment(parent)
            parts.add(filename)
            parentNode = getNode(parent)
        } while (parentNode == null && parent != File.separator)
        // Found a parent node
        if (!checkAccess(parent, OsConstants.W_OK) || parentNode == null || !parentNode.isDirectory()) {
            // Parent node is inaccessible
            return false
        }
        for (i in parts.size - 1 downTo 0) {
            parentNode = Node<Any?>(parentNode as Node<Any?>?, parts[i], false)
            parentNode.setDirectory(true)
            parentNode.getParent()!!.addChild(parentNode)
            val fullPath = parentNode.getFullPath()
            addAction(fullPath, Action(ACTION_CREATE, parentNode))
            invalidate(fullPath)
        }
        return true
    }

    /**
     * Similar to Java File API in Unix.
     *
     *
     *  * If destination is a file, it overrides it.
     *  * If destination is a directory, it overrides it only if it has no children.
     *  * If destination is does not exist, it creates it.
     *
     */
    fun renameTo(source: String, dest: String): Boolean {
        // path and dest must belong to the same file system and are relative as usual.
        if (dest.startsWith(source)) {
            // Destination cannot be the same or a subdirectory of source
            return false
        }
        if (!checkAccess(source, OsConstants.W_OK)) {
            // Directory not modifiable
            return false
        }
        val destExists = checkAccess(dest, OsConstants.F_OK)
        val sourceNode = getNode(source) ?: return false
        if (sourceNode.isDirectory()) {
            // Source node is a directory, so create some directories and move whatever this directory has.
            val filename = Paths.getLastPathSegment(dest)
            val parent = Paths.removeLastPathSegment(dest)
            mkdirs(parent)
            val targetNode = getNode(parent) ?: return false
            if (destExists) {
                // Override existing node
                val node = getNode(filename)
                if (node != null) {
                    if (node.isFile()) {
                        // Existing node is a file
                        return false
                    } else if (node.listChildren() != null) {
                        // Is a directory with children
                        return false
                    } else {
                        // A directory with no children
                        addAction(dest, Action(ACTION_DELETE, node))
                        targetNode.removeChild(node)
                        invalidate(dest)
                    }
                }
            }
            // Rename sourceNode to filename
            moveChildren(sourceNode, source, dest)
            val parentNode = sourceNode.getParent()
            parentNode?.removeChild(sourceNode)
            @Suppress("UNCHECKED_CAST")
            val renamedNode = Node(targetNode as Node<Any?>?, sourceNode as Node<Any?>, filename)
            val action = Action(ACTION_MOVE, renamedNode)
            action.sourcePath = source
            if (dest != renamedNode.getFullPath()) {
                throw IllegalStateException(
                    String.format(
                        Locale.ROOT,
                        "Invalid destination for the renamed node. Required: %s, was: %s",
                        dest,
                        renamedNode.getFullPath()
                    )
                )
            }
            addAction(dest, action)
            targetNode.addChild(renamedNode)
            invalidate(source)
        } else if (sourceNode.isFile()) {
            // Source node is a file, create some directories up to this point and move this file to there
            // Overriding the existing one if necessary.
            val filename = Paths.getLastPathSegment(dest)
            val parent = Paths.removeLastPathSegment(dest)
            // Output of mkdirs is not relevant
            mkdirs(parent)
            val targetNode = getNode(parent) ?: return false
            if (destExists) {
                // Override existing node
                val destNode = getNode(filename)
                if (destNode != null) {
                    if (destNode.isDirectory()) {
                        // Existing node is a directory
                        return false
                    }
                    addAction(dest, Action(ACTION_DELETE, destNode))
                    targetNode.removeChild(destNode)
                    invalidate(dest)
                }
            }
            // Rename sourceNode to filename
            val parentNode = sourceNode.getParent()
            parentNode?.removeChild(sourceNode)
            @Suppress("UNCHECKED_CAST")
            val renamedNode = Node(targetNode as Node<Any?>?, sourceNode as Node<Any?>, filename)
            val action = Action(ACTION_MOVE, renamedNode)
            action.sourcePath = source
            if (dest != renamedNode.getFullPath()) {
                throw IllegalStateException(
                    String.format(
                        Locale.ROOT,
                        "Invalid destination for the renamed node. Required: %s, was: %s",
                        dest,
                        renamedNode.getFullPath()
                    )
                )
            }
            addAction(dest, action)
            // Check if it's cached
            val cache = mFileCacheMap.remove(source)
            if (cache != null) {
                // Cache exists, alter it
                mFileCacheMap[dest] = cache
            }
            targetNode.addChild(renamedNode)
            invalidate(source)
        }
        return true
    }

    private fun moveChildren(parentNode: Node<*>, sourceBase: String, destBase: String) {
        val children = parentNode.listChildren() ?: return
        for (node in children) {
            moveChildren(node, sourceBase, destBase)
            // Move this node
            val source = node.getFullPath()
            val dest = File(destBase, Paths.relativePath(sourceBase, source)).absolutePath
            val action = Action(ACTION_MOVE, node)
            action.sourcePath = source
            addAction(dest, action)
            // Check if it's cached
            val cache = mFileCacheMap.remove(source)
            if (cache != null) {
                // Cache exists, alter it
                mFileCacheMap[dest] = cache
            }
            invalidate(source)
            invalidate(dest)
        }
    }

    open fun setLastModified(path: String, time: Long): Boolean {
        checkMounted()
        return false
    }

    open fun setReadOnly(path: String): Boolean {
        checkMounted()
        return false
    }

    open fun setWritable(path: String, writable: Boolean, ownerOnly: Boolean): Boolean {
        checkMounted()
        return false
    }

    open fun setReadable(path: String, readable: Boolean, ownerOnly: Boolean): Boolean {
        checkMounted()
        return false
    }

    open fun setExecutable(path: String, executable: Boolean, ownerOnly: Boolean): Boolean {
        checkMounted()
        return false
    }

    abstract fun checkAccess(path: String, access: Int): Boolean

    //    public abstract long getTotalSpace(String path);
    //
    //    public abstract long getFreeSpace(String path);
    //
    //    public abstract long getUsableSpace(String path);
    @Suppress("OctalInteger")
    fun getMode(path: String): Int {
        val targetNode = getNode(path) ?: return 0
        var mode = getOptions()!!.mode and 0x1ff // 0777 in octal
        if (mode == 0) {
            mode = if (getOptions()!!.readWrite) 0x1b6 else 0x124 // 0666 or 0444
        }
        if (targetNode.isDirectory()) {
            return mode or OsConstants.S_IFDIR
        }
        if (targetNode.isFile()) {
            return mode or OsConstants.S_IFREG
        }
        return 0
    }

    open fun setMode(path: String, mode: Int) {
        // TODO: 7/12/22 This should either throw ErrnoException or a boolean value
        checkMounted()
    }

    open fun getUidGid(path: String): UidGidPair? {
        checkMounted()
        return getOptions()!!.uidGidPair
    }

    open fun setUidGid(path: String, uid: Int, gid: Int) {
        // TODO: 7/12/22 This should either throw ErrnoException or a boolean value
        checkMounted()
    }

    open fun createLink(link: String, target: String, soft: Boolean): Boolean {
        checkMounted()
        return false
    }

    /* I/O APIs */
    @Throws(IOException::class)
    fun newInputStream(path: String): FileInputStream {
        if (!checkAccess(path, OsConstants.R_OK)) {
            throw IOException("$path is inaccessible.")
        }
        val targetNode = getNode(path) ?: throw FileNotFoundException("$path does not exist.")
        if (!targetNode.isFile()) {
            throw IOException("$path is not a file.")
        }
        return FileInputStream(getCachedFile(targetNode, false))
    }

    @Throws(IOException::class)
    fun newOutputStream(path: String, append: Boolean): FileOutputStream {
        if (!checkAccess(path, OsConstants.W_OK)) {
            throw IOException("$path is inaccessible.")
        }
        val targetNode = getNode(path) ?: throw FileNotFoundException("$path does not exist.")
        if (!targetNode.isFile()) {
            throw IOException("$path is not a file.")
        }
        return FileOutputStream(getCachedFile(targetNode, true), append)
    }

    @Throws(IOException::class)
    fun openChannel(path: String, mode: Int): FileChannel {
        var read = false
        var write = false
        if (mode and MODE_READ_WRITE != 0) {
            read = true
            write = true
        } else if (mode and MODE_READ_ONLY != 0) {
            read = true
        } else if (mode and MODE_WRITE_ONLY != 0) {
            write = true
        } else {
            throw IllegalArgumentException("Bad mode: $mode")
        }

        if (read && write && !checkAccess(path, OsConstants.R_OK or OsConstants.W_OK)) {
            throw IOException("$path cannot be opened for both reading and writing.")
        } else if (read && !checkAccess(path, OsConstants.R_OK)) {
            throw IOException("$path cannot be opened for both reading.")
        } else if (write && !checkAccess(path, OsConstants.W_OK)) {
            throw IOException("$path cannot be opened for both writing.")
        }
        val targetNode = getNode(path) ?: throw FileNotFoundException("$path does not exist.")
        if (!targetNode.isFile()) {
            throw IOException("$path is not a file.")
        }
        return FileSystemManager.getLocal().openChannel(getCachedFile(targetNode, write), mode)
    }

    @Throws(IOException::class)
    fun openFileDescriptor(path: String, mode: Int): ParcelFileDescriptor {
        var read = false
        var write = false
        if (mode and MODE_READ_WRITE != 0) {
            read = true
            write = true
        } else if (mode and MODE_READ_ONLY != 0) {
            read = true
        } else if (mode and MODE_WRITE_ONLY != 0) {
            write = true
        } else {
            throw IllegalArgumentException("Bad mode: $mode")
        }

        if (read && write && !checkAccess(path, OsConstants.R_OK or OsConstants.W_OK)) {
            write = false
            // The below does not work with ContentProvider, only disable writing
            // throw new IOException(path + " cannot be opened for both reading and writing.");
        }
        if (read && !checkAccess(path, OsConstants.R_OK)) {
            throw IOException("$path cannot be opened for both reading.")
        }
        if (write && !checkAccess(path, OsConstants.W_OK)) {
            throw IOException("$path cannot be opened for both writing.")
        }
        val targetNode = getNode(path) ?: throw FileNotFoundException("$path does not exist.")
        if (!targetNode.isFile()) {
            throw IOException("$path is not a file.")
        }
        return ParcelFileDescriptor.open(getCachedFile(targetNode, write), mode)
    }

    @Throws(IOException::class)
    protected abstract fun getInputStream(node: Node<*>): InputStream

    @Throws(IOException::class)
    protected abstract fun cacheFile(src: Node<*>, sink: File)

    protected fun findCachedFile(node: Node<*>): File? {
        val fileCacheItem = mFileCacheMap[node.getFullPath()]
        return fileCacheItem?.cachedFile
    }

    @Throws(IOException::class)
    protected fun getCachedFile(node: Node<*>, write: Boolean): File {
        var fileCacheItem = mFileCacheMap[node.getFullPath()]
        if (fileCacheItem != null) {
            if (write) {
                fileCacheItem.isModified = true
            }
            return fileCacheItem.cachedFile
        }
        // File hasn't been cached.
        val cachedFile = mFileCache.createCachedFile(Paths.getPathExtension(node.getName()))
        if (node.isPhysical()) {
            // The file exists physically. It has to be cached first.
            cacheFile(node, cachedFile)
        }
        fileCacheItem = FileCacheItem(cachedFile)
        if (write) {
            fileCacheItem.isModified = true
        }
        mFileCacheMap[node.getFullPath()] = fileCacheItem
        return fileCacheItem.cachedFile
    }

    /* File tree */
    protected open class Node<T> {
        // TODO: 23/11/22 Should include modification, creation, access times
        private val mName: String
        private val mObject: T?
        private val mPhysical: Boolean
        private val mPhysicalFullPath: String?

        private var mParent: Node<T>? = null
        private var mChildren: HashMap<String, Node<T>>? = null
        private var mDirectory: Boolean
        private var mExtra: HashMap<String, Any?>? = null

        constructor(parent: Node<T>?, name: String) : this(parent, name, null)

        constructor(parent: Node<T>?, name: String, physical: Boolean) : this(parent, name, null, physical)

        constructor(parent: Node<T>?, name: String, `object`: T?) : this(parent, name, `object`, true)

        constructor(parent: Node<T>?, node: Node<T>, newName: String) : this(
            parent,
            newName,
            node.mObject,
            node.mPhysical
        ) {
            mChildren = node.mChildren
            mDirectory = node.mDirectory
            if (mChildren != null) {
                for (child in mChildren!!.values) {
                    child.mParent = this
                }
            }
        }

        constructor(parent: Node<T>?, name: String, `object`: T?, physical: Boolean) {
            mParent = parent
            mName = name
            mObject = `object`
            mPhysical = physical
            mDirectory = `object` == null
            mPhysicalFullPath = if (physical) calculateFullPath(parent, this) else null
        }

        fun getName(): String {
            return mName
        }

        fun getParent(): Node<T>? {
            return mParent
        }

        fun getFullPath(): String {
            return calculateFullPath(mParent, this)
        }

        fun getPhysicalFullPath(): String? {
            return mPhysicalFullPath
        }

        fun getObject(): T? {
            return mObject
        }

        fun isPhysical(): Boolean {
            return mPhysical
        }

        fun setDirectory(directory: Boolean) {
            mDirectory = directory
        }

        fun setFile(file: Boolean) {
            mDirectory = !file
        }

        fun isDirectory(): Boolean {
            return mDirectory
        }

        fun isFile(): Boolean {
            return !mDirectory
        }

        fun getChild(name: String): Node<T>? {
            return mChildren?.get(name)
        }

        fun getLastChild(name: String?): Node<T>? {
            return if (mChildren == null) null else getLastNode(this, name)
        }

        @Suppress("UNCHECKED_CAST")
        fun listChildren(): Array<Node<T>>? {
            if (mChildren == null || mChildren!!.isEmpty()) return null
            return mChildren!!.values.toTypedArray()
        }

        @Suppress("UNCHECKED_CAST")
        fun addChild(child: Node<*>?) {
            if (child == null) return
            if (mChildren == null) mChildren = HashMap()
            val node = child as Node<T>
            node.mParent = this
            mChildren!![node.mName] = node
        }

        fun removeChild(child: Node<*>?) {
            if (child == null || mChildren == null) return
            mChildren!!.remove(child.mName)
            child.mParent = null
        }

        fun <U> addExtra(fieldName: String, `object`: U?) {
            if (mExtra == null) {
                mExtra = HashMap()
            }
            mExtra!![fieldName] = `object`
        }

        @Suppress("UNCHECKED_CAST")
        fun <U> getExtra(fieldName: String): U? {
            return if (mExtra != null) mExtra!![fieldName] as U? else null
        }

        @Suppress("UNCHECKED_CAST")
        fun <U> removeExtra(fieldName: String): U? {
            if (mExtra != null) {
                val v = mExtra!!.remove(fieldName)
                if (v != null) {
                    return v as U?
                }
            }
            return null
        }

        companion object {
            private fun calculateFullPath(parent: Node<*>?, child: Node<*>): String {
                val basePath = parent?.getFullPath() ?: File.separator
                return (if (basePath == File.separator) (if (child.mName == File.separator) "" else File.separator) else basePath + File.separatorChar) + child.mName
            }

            @Suppress("SuspiciousRegexArgument") // Not on Windows
            private fun <T> getLastNode(baseNode: Node<T>, dirtyPath: String?): Node<T>? {
                if (dirtyPath == null) return baseNode
                val path = Paths.sanitize(dirtyPath, true) ?: return baseNode
                val components = path.split(File.separator.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                var lastNode: Node<T>? = baseNode
                for (component in components) {
                    lastNode = lastNode!!.getChild(component)
                    if (lastNode == null) {
                        // File do not exist
                        return null
                    }
                }
                return lastNode
            }
        }
    }

    class NotMountedException : RuntimeException {
        constructor() : super()
        constructor(message: String?) : super(message)
        constructor(message: String?, th: Throwable?) : super(message, th)
    }
}
