// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.*
import android.provider.DocumentsContract
import android.system.ErrnoException
import android.system.OsConstants
import android.text.TextUtils
import android.util.Log
import android.util.Pair
import android.webkit.MimeTypeMap
import androidx.annotation.CheckResult
import androidx.core.provider.DocumentsContractCompat
import androidx.documentfile.provider.*
import io.github.muntashirakon.AppManager.compat.StorageManagerCompat
import io.github.muntashirakon.AppManager.ipc.LocalServices
import io.github.muntashirakon.AppManager.self.SelfPermissions
import io.github.muntashirakon.AppManager.utils.ContextUtils
import io.github.muntashirakon.AppManager.utils.FileUtils
import io.github.muntashirakon.io.fs.VirtualFileSystem
import java.io.*
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * Provide an interface to [File] and [DocumentFile] with basic functionalities.
 */
internal class PathImpl : Path {
    companion object {
        @JvmField
        val TAG: String = PathImpl::class.java.simpleName

        private val EXCLUSIVE_ACCESS_GRANTED = mutableListOf<Boolean>()
        private val EXCLUSIVE_ACCESS_PATHS = mutableListOf<String>()

        init {
            setAccessPaths()
        }

        private fun setAccessPaths() {
            if (Process.myUid() == Process.ROOT_UID || Process.myUid() == Process.SYSTEM_UID || Process.myUid() == Process.SHELL_UID) {
                // Root/ADB
                return
            }
            // We cannot use Path API here
            // Read-write
            val context = ContextUtils.getContext()
            EXCLUSIVE_ACCESS_PATHS.add(context.filesDir.parentFile!!.absolutePath)
            EXCLUSIVE_ACCESS_GRANTED.add(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                EXCLUSIVE_ACCESS_PATHS.add(context.createDeviceProtectedStorageContext().dataDir.absolutePath)
                EXCLUSIVE_ACCESS_GRANTED.add(true)
            }
            val extDirs = context.externalCacheDirs
            if (extDirs != null) {
                for (dir in extDirs) {
                    if (dir == null) continue
                    EXCLUSIVE_ACCESS_PATHS.add(dir.parentFile!!.absolutePath)
                    EXCLUSIVE_ACCESS_GRANTED.add(true)
                }
            }
            if (SelfPermissions.checkSelfStoragePermission()) {
                val userId = UserHandleHidden.myUserId()
                val cards: Array<String> = if (userId == 0) {
                    arrayOf(
                        "/sdcard",
                        "/storage/emulated/$userId",
                        "/storage/self/primary"
                    )
                } else {
                    arrayOf("/storage/emulated/$userId")
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // Add Android/data and Android/obb to the exemption list
                    val canInstallApps = SelfPermissions.checkSelfPermission(Manifest.permission.REQUEST_INSTALL_PACKAGES)
                            || SelfPermissions.checkSelfPermission(Manifest.permission.INSTALL_PACKAGES)
                    for (card in cards) {
                        EXCLUSIVE_ACCESS_PATHS.add("$card/Android/data")
                        EXCLUSIVE_ACCESS_GRANTED.add(false)
                        if (!canInstallApps) {
                            EXCLUSIVE_ACCESS_PATHS.add("$card/Android/obb")
                            EXCLUSIVE_ACCESS_GRANTED.add(false)
                        }
                    }
                }
                // Lowest priority
                for (card in cards) {
                    EXCLUSIVE_ACCESS_PATHS.add(card)
                    EXCLUSIVE_ACCESS_GRANTED.add(true)
                }
            }
            // Assert sizes
            if (EXCLUSIVE_ACCESS_PATHS.size != EXCLUSIVE_ACCESS_GRANTED.size) {
                throw RuntimeException()
            }
        }

        private fun needPrivilegedAccess(path: String): Boolean {
            if (Process.myUid() == Process.ROOT_UID || Process.myUid() == Process.SYSTEM_UID || Process.myUid() == Process.SHELL_UID) {
                // Root/shell
                return false
            }
            for (i in EXCLUSIVE_ACCESS_PATHS.indices) {
                if (path.startsWith(EXCLUSIVE_ACCESS_PATHS[i])) {
                    // May need no privileged access
                    return !EXCLUSIVE_ACCESS_GRANTED[i]
                }
            }
            return true
        }

        private fun getRequiredRawDocument(path: String): DocumentFile {
            if (needPrivilegedAccess(path) && LocalServices.alive()) {
                try {
                    val fs = LocalServices.getFileSystemManager()
                    return ExtendedRawDocumentFile(fs.getFile(path))
                } catch (e: RemoteException) {
                    Log.w(TAG, "Could not get privileged access to path $path due to "${e.message}"")
                    // Fall-back to unprivileged access
                }
            }
            val file = FileSystemManager.getLocal().getFile(path)
            return ExtendedRawDocumentFile(LocalFileOverlay.getOverlayFile(file))
        }

        // An invalid MIME so that it doesn't match any extension
        private const val DEFAULT_MIME = "application/x-invalid-mime-type"

        @Throws(IOException::class)
        private fun copyFile(context: Context, src: DocumentFile, dst: DocumentFile) {
            copyFile(PathImpl(context, src), PathImpl(context, dst))
        }

        @Throws(IOException::class)
        private fun copyFile(src: Path, dst: Path) {
            if (src.isMountPoint() || dst.isMountPoint()) {
                throw IOException("Either source or destination are a mount point.")
            }
            IoUtils.copy(src, dst)
        }

        // Copy directory content
        @Throws(IOException::class)
        private fun copyDirectory(
            context: Context, src: DocumentFile, dst: DocumentFile,
            override: Boolean
        ) {
            copyDirectory(PathImpl(context, src), PathImpl(context, dst), override)
        }

        @Throws(IOException::class)
        private fun copyDirectory(context: Context, src: DocumentFile, dst: DocumentFile) {
            copyDirectory(PathImpl(context, src), PathImpl(context, dst), true)
        }

        @Throws(IOException::class)
        private fun copyDirectory(src: Path, dst: Path, override: Boolean) {
            for (file in src.listFiles()) {
                val name = file.getName()
                if (file.isDirectory()) {
                    val newDir = dst.createNewDirectory(name)
                    val fsRoot = VirtualFileSystem.getFsRoot(file.getUri())
                    if (fsRoot != null) {
                        VirtualFileSystem.alterMountPoint(file.getUri(), newDir.getUri())
                    }
                    copyDirectory(file, newDir, override)
                } else if (file.isFile()) {
                    if (dst.hasFile(name) && !override) {
                        // Override disabled
                        continue
                    }
                    val newFile = dst.createNewFile(name, null)
                    copyFile(file, newFile)
                }
            }
        }

        @Throws(IOException::class)
        private fun createFileAsDirectChild(
            context: Context,
            documentFile: DocumentFile,
            displayName: String,
            mimeType: String?
        ): Path {
            var mDisplayName = displayName
            if (mDisplayName.indexOf(File.separatorChar) != -1) {
                throw IllegalArgumentException("Display name contains file separator.")
            }
            var mDocumentFile = getRealDocumentFile(documentFile)
            if (!mDocumentFile.isDirectory) {
                throw IOException("Current file is not a directory.")
            }
            var mMimeType = mimeType
            val extension = if (mMimeType != null) {
                MimeTypeMap.getSingleton().getExtensionFromMimeType(mMimeType)
            } else {
                mMimeType = DEFAULT_MIME
                null
            }
            val nameWithExtension = mDisplayName + if (extension != null) ".$extension" else ""
            checkVfs(Paths.appendPathSegment(mDocumentFile.uri, nameWithExtension))
            val f = mDocumentFile.findFile(mDisplayName)
            if (f != null) {
                if (f.isDirectory) {
                    throw IOException("Directory cannot be converted to file")
                }
                // Delete the file if exists
                f.delete()
            }
            val file = mDocumentFile.createFile(mMimeType!!, mDisplayName) ?: throw IOException(
                "Could not create ${mDocumentFile.uri}${File.separatorChar}$nameWithExtension with type $mMimeType"
            )
            return PathImpl(context, file)
        }

        private fun findFileInternal(documentFile: DocumentFile, dirtyDisplayName: String): DocumentFile? {
            var mDocumentFile = documentFile
            val displayName = Paths.sanitize(dirtyDisplayName, true) ?: return null
            // Empty display name
            val parts = displayName.split(File.separator.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            mDocumentFile = getRealDocumentFile(mDocumentFile)
            for (part in parts) {
                // Check for mount point
                val newUri = Paths.appendPathSegment(mDocumentFile.uri, part)
                val fsRoot = VirtualFileSystem.getFsRoot(newUri)
                // Mount point has the higher priority
                mDocumentFile = fsRoot?.documentFile ?: mDocumentFile.findFile(part) ?: return null
            }
            return mDocumentFile
        }

        private fun getParentFile(context: Context, vfs: VirtualFileSystem): DocumentFile? {
            val mountPoint = vfs.mountPoint ?: return null
            // FIXME: 9/9/23 This doesn't actually work for content URIs
            val parentUri = Paths.removeLastPathSegment(mountPoint)
            return PathImpl(context, parentUri).documentFile
        }

        @Throws(IOException::class)
        private fun createArbitraryDirectories(
            documentFile: DocumentFile,
            names: Array<String>,
            length: Int
        ): DocumentFile {
            var mDocumentFile = getRealDocumentFile(documentFile)
            for (i in 0 until length) {
                val fsRoot = VirtualFileSystem.getFsRoot(Paths.appendPathSegment(mDocumentFile.uri, names[i]))
                var t = fsRoot?.documentFile ?: mDocumentFile.findFile(names[i])
                if (t == null) {
                    t = mDocumentFile.createDirectory(names[i])
                } else if (!t.isDirectory) {
                    throw IOException("${t.uri} exists and it is not a directory.")
                }
                if (t == null) {
                    throw IOException("Could not create directory ${mDocumentFile.uri}${File.separatorChar}${names[i]}")
                }
                mDocumentFile = t
            }
            return mDocumentFile
        }

        private fun getRealDocumentFile(documentFile: DocumentFile): DocumentFile {
            val fsRoot = VirtualFileSystem.getFsRoot(documentFile.uri)
            if (fsRoot != null) {
                return fsRoot.documentFile
            }
            return documentFile
        }

        private fun resolveFileOrNull(documentFile: DocumentFile): DocumentFile? {
            val realDocumentFile = getRealDocumentFile(documentFile)
            if (!realDocumentFile.isDirectory) {
                return realDocumentFile
            }
            // Try original
            if (!documentFile.isDirectory) {
                return documentFile
            }
            return null
        }

        @Throws(IOException::class)
        private fun checkVfs(uri: Uri) {
            if (VirtualFileSystem.getFileSystem(uri) != null) {
                throw IOException("Destination is a mount point.")
            }
        }

        private fun isDocumentsProvider(context: Context, authority: String?): Boolean {
            val intent = Intent(DocumentsContract.PROVIDER_INTERFACE)
            val infos = context.packageManager.queryIntentContentProviders(intent, 0)
            for (info in infos) {
                if (authority == info.providerInfo.authority) {
                    return true
                }
            }
            return false
        }
    }

    /* package */ constructor(context: Context, fileLocation: String) : super(
        context,
        getRequiredRawDocument(fileLocation)
    )

    /* package */ constructor(context: Context, fs: VirtualFileSystem) : super(
        context,
        VirtualDocumentFile(getParentFile(context, fs), fs)
    )

    @Throws(RemoteException::class)
    /* package */ constructor(context: Context, fileLocation: String, privileged: Boolean) : super(context, null) {
        if (privileged) {
            val fs = LocalServices.getFileSystemManager()
            documentFile = ExtendedRawDocumentFile(fs.getFile(fileLocation))
        } else {
            val file = FileSystemManager.getLocal().getFile(fileLocation)
            documentFile = ExtendedRawDocumentFile(LocalFileOverlay.getOverlayFile(file))
        }
    }

    /* package */ constructor(context: Context, uri: Uri) : super(context, null) {
        // At first check if the Uri is in VFS since it gets higher priority.
        val fsRoot = VirtualFileSystem.getFsRoot(uri)
        if (fsRoot != null) {
            documentFile = fsRoot.documentFile
            return
        }
        if (uri.scheme == null) {
            throw IllegalArgumentException("Uri has no scheme: $uri")
        }
        val documentFile: DocumentFile = when (uri.scheme) {
            ContentResolver.SCHEME_CONTENT -> {
                if (isDocumentsProvider(context, uri.authority)) { // We can't use DocumentsContract.isDocumentUri() because it expects something that isn't always correct
                    val isTreeUri = DocumentsContractCompat.isTreeUri(uri)
                    if (isTreeUri) DocumentFile.fromTreeUri(context, uri)!! else DocumentFile.fromSingleUri(context, uri)!!
                } else {
                    // Content provider
                    MediaDocumentFile(null, context, uri)
                }
            }
            ContentResolver.SCHEME_FILE -> getRequiredRawDocument(uri.path!!)
            VirtualFileSystem.SCHEME -> {
                val parsedUri = VirtualDocumentFile.parseUri(uri)
                if (parsedUri != null) {
                    val rootPath = VirtualFileSystem.getFsRoot(parsedUri.first)
                    if (rootPath != null) {
                        val path = Paths.sanitize(parsedUri.second, true)
                        if (TextUtils.isEmpty(path) || path == File.separator) {
                            // Root requested
                            rootPath.documentFile
                        } else {
                            // Find file is acceptable here since the file always exists
                            val pathComponents = path!!.split(File.separator.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                            var finalDocumentFile = rootPath.documentFile
                            for (pathComponent in pathComponents) {
                                finalDocumentFile = finalDocumentFile.findFile(pathComponent)!!
                            }
                            finalDocumentFile
                        }
                    } else {
                        throw IllegalArgumentException("Unsupported uri $uri")
                    }
                } else {
                    throw IllegalArgumentException("Unsupported uri $uri")
                }
            }
            else -> throw IllegalArgumentException("Unsupported uri $uri")
        }
        // Setting mDocumentFile at the end ensures that it is never null
        this.documentFile = documentFile
    }

    /**
     * NOTE: This construct is only applicable for tree Uri
     */
    /* package */ constructor(parent: Path?, context: Context, documentUri: Uri) : super(context, null) {
        val parentDocumentFile = parent?.documentFile
        documentFile = DocumentFileUtils.newTreeDocumentFile(parentDocumentFile, context, documentUri)
    }

    private constructor(context: Context, documentFile: DocumentFile) : super(context, null) {
        var mDocumentFile = documentFile
        if (mDocumentFile is ExtendedRawDocumentFile) {
            val file = (mDocumentFile as ExtendedRawDocumentFile).getFile()
            if (file is LocalFile) {
                val newFile = LocalFileOverlay.getOverlayFileOrNull(file)
                if (newFile != null) {
                    mDocumentFile = ExtendedRawDocumentFile(newFile)
                }
            }
        }
        this.documentFile = mDocumentFile
    }

    override fun getName(): String {
        // Last path segment is required.
        val name = documentFile.name
        if (name != null) {
            return name
        }
        return DocumentFileUtils.resolveAltNameForSaf(documentFile)
    }

    /**
     * Return the underlying [ExtendedFile] if the path is backed by a real file,
     * `null` otherwise.
     */
    override fun getFile(): ExtendedFile? {
        if (documentFile is ExtendedRawDocumentFile) {
            return (documentFile as ExtendedRawDocumentFile).getFile()
        }
        return null
    }

    /**
     * Same as [getFile] except it return a raw string.
     */
    override fun getFilePath(): String? {
        if (documentFile is ExtendedRawDocumentFile) {
            return documentFile.uri.path
        }
        return null
    }

    /**
     * Same as [getFile] except it returns the real path if the
     * current path is a symbolic link.
     */
    @Throws(IOException::class)
    override fun getRealFilePath(): String? {
        if (documentFile is ExtendedRawDocumentFile) {
            return getFile()!!.canonicalPath
        }
        return null
    }

    /**
     * Same as [getFile] except it returns the real path if the
     * current path is a symbolic link.
     */
    @Throws(IOException::class)
    override fun getRealPath(): Path? {
        if (documentFile is ExtendedRawDocumentFile) {
            return Paths.get(getFile()!!.canonicalFile)
        }
        return null
    }

    override fun getType(): String {
        var type = getRealDocumentFile(documentFile).type
        if (type == null) {
            type = PathContentInfoImpl.fromExtension(this).mimeType
        }
        if (type == null) {
            type = "application/octet-stream"
        }
        return type
    }

    override fun getPathContentInfo(): PathContentInfo {
        return PathContentInfoImpl.fromPath(this)
    }

    @CheckResult
    override fun length(): Long {
        return getRealDocumentFile(documentFile).length()
    }

    @CheckResult
    override fun recreate(): Boolean {
        val documentFile = getRealDocumentFile(this.documentFile)
        if (documentFile.isDirectory) {
            // Directory does not need to be created again.
            return true
        }
        if (documentFile.exists() && !documentFile.isFile) return false
        // For Linux documents, recreate using file APIs
        if (documentFile is ExtendedRawDocumentFile) {
            return try {
                val f = documentFile.getFile()!!
                if (f.exists()) f.delete()
                f.createNewFile()
            } catch (e: IOException) {
                false
            } catch (e: SecurityException) {
                false
            }
        }
        // In other cases, open OutputStream to make the file empty.
        // We can directly use openOutputStream because if it were a mount point, it would be a directory.
        return try {
            openOutputStream(false).use { _ -> true }
        } catch (e: IOException) {
            false
        }
    }

    @Throws(IOException::class)
    override fun createNewFile(displayName: String, mimeType: String?): Path {
        val sanitizedDisplayName = Paths.sanitize(displayName, true) ?: throw IOException("Empty display name.")
        return createFileAsDirectChild(context, documentFile, sanitizedDisplayName, mimeType)
    }

    @Throws(IOException::class)
    override fun createNewDirectory(displayName: String): Path {
        val sanitizedDisplayName = Paths.sanitize(displayName, true) ?: throw IOException("Empty display name.")
        if (sanitizedDisplayName.indexOf(File.separatorChar) != -1) {
            throw IllegalArgumentException("Display name contains file separator.")
        }
        val documentFile = getRealDocumentFile(this.documentFile)
        if (!documentFile.isDirectory) {
            throw IOException("Current file is not a directory.")
        }
        checkVfs(Paths.appendPathSegment(documentFile.uri, sanitizedDisplayName))
        val file = documentFile.createDirectory(sanitizedDisplayName) ?: throw IOException("Could not create directory named $sanitizedDisplayName")
        return PathImpl(context, file)
    }

    @Throws(IOException::class)
    override fun createNewArbitraryFile(displayName: String, mimeType: String?): Path {
        val sanitizedDisplayName = Paths.sanitize(displayName, true) ?: throw IOException("Empty display name.")
        val names = sanitizedDisplayName.split(File.separator.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        if (names.isEmpty()) {
            throw IllegalArgumentException("Display name is empty.")
        }
        for (name in names) {
            if (name == "..") {
                throw IOException("Could not create directories in the parent directory.")
            }
        }
        val file = createArbitraryDirectories(documentFile, names, names.size - 1)
        return createFileAsDirectChild(context, file, names[names.size - 1], mimeType)
    }

    @Throws(IOException::class)
    override fun createDirectoriesIfRequired(displayName: String): Path {
        val sanitizedDisplayName = Paths.sanitize(displayName, true) ?: throw IOException("Empty display name.")
        val dirNames = sanitizedDisplayName.split(File.separator.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        if (dirNames.isEmpty()) {
            throw IllegalArgumentException("Display name is empty")
        }
        for (name in dirNames) {
            if (name == "..") {
                throw IOException("Could not create directories in the parent directory.")
            }
        }
        val file = createArbitraryDirectories(documentFile, dirNames, dirNames.size)
        return PathImpl(context, file)
    }

    @Throws(IOException::class)
    override fun createDirectories(displayName: String): Path {
        val sanitizedDisplayName = Paths.sanitize(displayName, true) ?: throw IOException("Empty display name.")
        val dirNames = sanitizedDisplayName.split(File.separator.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        if (dirNames.isEmpty()) {
            throw IllegalArgumentException("Display name is empty")
        }
        for (name in dirNames) {
            if (name == "..") {
                throw IOException("Could not create directories in the parent directory.")
            }
        }
        val file = createArbitraryDirectories(documentFile, dirNames, dirNames.size - 1)
        // Special case for the last segment
        val lastSegment = dirNames[dirNames.size - 1]
        val fsRoot = VirtualFileSystem.getFsRoot(Paths.appendPathSegment(file.uri, lastSegment))
        val t = fsRoot?.documentFile ?: file.findFile(lastSegment)
        if (t != null) {
            throw IOException("${t.uri} already exists.")
        }
        val created = file.createDirectory(lastSegment) ?: throw IOException("Directory ${file.uri}${File.separatorChar}$lastSegment could not be created.")
        return PathImpl(context, created)
    }

    override fun getParent(): Path? {
        val file = getRealDocumentFile(documentFile).parentFile
        return if (file == null) null else PathImpl(context, file)
    }

    override fun hasFile(displayName: String): Boolean {
        return findFileInternal(documentFile, displayName) != null
    }

    @Throws(FileNotFoundException::class)
    override fun findFile(displayName: String): Path {
        val nextPath = findFileInternal(documentFile, displayName) ?: throw FileNotFoundException("Cannot find $this${File.separatorChar}$displayName")
        return PathImpl(context, nextPath)
    }

    @Throws(IOException::class)
    override fun findOrCreateFile(displayName: String, mimeType: String?): Path {
        var mDisplayName = displayName
        var mMimeType = mimeType
        mDisplayName = Paths.sanitize(mDisplayName, true) ?: throw IOException("Empty display name.")
        if (mDisplayName.indexOf(File.separatorChar) != -1) {
            throw IllegalArgumentException("Display name contains file separator.")
        }
        val documentFile = getRealDocumentFile(this.documentFile)
        if (!documentFile.isDirectory) {
            throw IOException("Current file is not a directory.")
        }
        val extension = if (mMimeType != null) {
            MimeTypeMap.getSingleton().getExtensionFromMimeType(mMimeType)
        } else {
            mMimeType = DEFAULT_MIME
            null
        }
        val nameWithExtension = mDisplayName + if (extension != null) ".$extension" else ""
        checkVfs(Paths.appendPathSegment(documentFile.uri, nameWithExtension))
        var file = documentFile.findFile(mDisplayName)
        if (file != null) {
            if (file.isDirectory) {
                throw IOException("Directory cannot be converted to file")
            }
            return PathImpl(context, file)
        }
        file = documentFile.createFile(mMimeType!!, mDisplayName) ?: throw IOException("Could not create ${documentFile.uri}${File.separatorChar}$nameWithExtension with type $mMimeType")
        return PathImpl(context, file)
    }

    @Throws(IOException::class)
    override fun findOrCreateDirectory(displayName: String): Path {
        var mDisplayName = displayName
        mDisplayName = Paths.sanitize(mDisplayName, true) ?: throw IOException("Empty display name.")
        if (mDisplayName.indexOf(File.separatorChar) != -1) {
            throw IllegalArgumentException("Display name contains file separator.")
        }
        val documentFile = getRealDocumentFile(this.documentFile)
        if (!documentFile.isDirectory) {
            throw IOException("Current file is not a directory.")
        }
        val fsRoot = VirtualFileSystem.getFsRoot(Paths.appendPathSegment(documentFile.uri, mDisplayName))
        if (fsRoot != null) return fsRoot
        var file = documentFile.findFile(mDisplayName)
        if (file != null) {
            if (!file.isDirectory) {
                throw IOException("Existing file is not a directory")
            }
            return PathImpl(context, file)
        }
        file = documentFile.createDirectory(mDisplayName) ?: throw IOException("Could not create directory named $mDisplayName")
        return PathImpl(context, file)
    }

    @Throws(IOException::class)
    override fun getAttributes(): PathAttributes {
        if (documentFile is ExtendedRawDocumentFile) {
            return PathAttributesImpl.fromFile(documentFile as ExtendedRawDocumentFile)
        }
        if (documentFile is VirtualDocumentFile) {
            return PathAttributesImpl.fromVirtual(documentFile as VirtualDocumentFile)
        }
        return PathAttributesImpl.fromSaf(context, documentFile)
    }

    @CheckResult
    override fun exists(): Boolean {
        return getRealDocumentFile(documentFile).exists()
    }

    @CheckResult
    override fun isDirectory(): Boolean {
        return getRealDocumentFile(documentFile).isDirectory
    }

    @CheckResult
    override fun isFile(): Boolean {
        return getRealDocumentFile(documentFile).isFile
    }

    @CheckResult
    override fun isVirtual(): Boolean {
        return getRealDocumentFile(documentFile).isVirtual
    }

    @CheckResult
    override fun isSymbolicLink(): Boolean {
        if (getRealDocumentFile(documentFile) is ExtendedRawDocumentFile) {
            return getFile()!!.isSymlink
        }
        return false
    }

    override fun createNewSymbolicLink(target: String): Boolean {
        if (getRealDocumentFile(documentFile) is ExtendedRawDocumentFile) {
            return try {
                getFile()!!.createNewSymlink(target)
            } catch (e: IOException) {
                e.printStackTrace()
                false
            }
        }
        return false
    }

    override fun canRead(): Boolean {
        return getRealDocumentFile(documentFile).canRead()
    }

    override fun canWrite(): Boolean {
        return getRealDocumentFile(documentFile).canWrite()
    }

    override fun canExecute(): Boolean {
        if (getRealDocumentFile(documentFile) is ExtendedRawDocumentFile) {
            return getFile()!!.canExecute()
        }
        return false
    }

    override fun getMode(): Int {
        if (documentFile is ExtendedRawDocumentFile) {
            return try {
                getFile()!!.getMode()
            } catch (e: ErrnoException) {
                0
            }
        }
        if (documentFile is VirtualDocumentFile) {
            return (documentFile as VirtualDocumentFile).getMode()
        }
        return 0
    }

    override fun setMode(mode: Int): Boolean {
        if (documentFile is ExtendedRawDocumentFile) {
            return try {
                getFile()!!.setMode(mode)
                true
            } catch (e: ErrnoException) {
                false
            }
        }
        if (documentFile is VirtualDocumentFile) {
            return (documentFile as VirtualDocumentFile).setMode(mode)
        }
        return false
    }

    override fun getUidGid(): UidGidPair? {
        if (documentFile is ExtendedRawDocumentFile) {
            return try {
                getFile()!!.getUidGid()
            } catch (e: ErrnoException) {
                null
            }
        }
        if (documentFile is VirtualDocumentFile) {
            return (documentFile as VirtualDocumentFile).getUidGid()
        }
        return null
    }

    override fun setUidGid(uidGidPair: UidGidPair): Boolean {
        if (documentFile is ExtendedRawDocumentFile) {
            return try {
                getFile()!!.setUidGid(uidGidPair.uid, uidGidPair.gid)
            } catch (e: ErrnoException) {
                false
            }
        }
        if (documentFile is VirtualDocumentFile) {
            return (documentFile as VirtualDocumentFile).setUidGid(uidGidPair)
        }
        return false
    }

    override fun getSelinuxContext(): String? {
        if (documentFile is ExtendedRawDocumentFile) {
            return getFile()!!.selinuxContext
        }
        return null
    }

    override fun setSelinuxContext(context: String?): Boolean {
        if (documentFile is ExtendedRawDocumentFile) {
            return if (context == null) {
                getFile()!!.restoreSelinuxContext()
            } else {
                getFile()!!.setSelinuxContext(context)
            }
        }
        return false
    }

    override fun isMountPoint(): Boolean {
        return VirtualFileSystem.isMountPoint(uri)
    }

    override fun mkdir(): Boolean {
        var documentFile = getRealDocumentFile(this.documentFile)
        if (documentFile.exists()) {
            return false
        }
        if (documentFile is ExtendedRawDocumentFile) {
            return getFile()!!.mkdir()
        } else {
            val parent = documentFile.parentFile
            if (parent != null) {
                val thisFile = parent.createDirectory(getName())
                if (thisFile != null) {
                    this.documentFile = thisFile
                    return true
                }
            }
        }
        return false
    }

    override fun mkdirs(): Boolean {
        var documentFile = getRealDocumentFile(this.documentFile)
        if (documentFile.exists()) {
            return false
        }
        if (documentFile is ExtendedRawDocumentFile) {
            return getFile()!!.mkdirs()
        }
        // For others, directory can't be created recursively as parent must exist
        val parent = documentFile.parentFile
        if (parent != null) {
            val thisFile = parent.createDirectory(getName())
            if (thisFile != null) {
                this.documentFile = thisFile
                return true
            }
        }
        return false
    }

    override fun renameTo(displayName: String): Boolean {
        val sanitizedDisplayName = Paths.sanitize(displayName, true) ?: return false
        // Empty display name
        if (sanitizedDisplayName.contains(File.separator)) {
            // Display name must belong to the same directory.
            return false
        }
        val parent = documentFile.parentFile ?: return false
        val file = parent.findFile(sanitizedDisplayName)
        if (file != null) {
            // File exists
            return false
        }
        val fsRoot = VirtualFileSystem.getFsRoot(Paths.appendPathSegment(parent.uri, sanitizedDisplayName))
        if (fsRoot != null) {
            // Mount point exists
            return false
        }
        val oldMountPoint = documentFile.uri
        if (documentFile.renameTo(sanitizedDisplayName)) {
            if (VirtualFileSystem.getFileSystem(oldMountPoint) != null) {
                // Change mount point
                VirtualFileSystem.alterMountPoint(oldMountPoint, documentFile.uri)
            }
            return true
        }
        return false
    }

    override fun moveTo(path: Path, override: Boolean): Boolean {
        var source = getRealDocumentFile(documentFile)
        var dest = getRealDocumentFile(path.documentFile)
        if (!source.exists()) {
            // Source itself does not exist.
            return false
        }
        if (dest.exists() && !dest.canWrite()) {
            // There's no point is attempting to move if the destination is read-only.
            return false
        }
        if (dest.uri.toString().startsWith(source.uri.toString())) {
            // Destination cannot be the same or a subdirectory of source
            return false
        }
        if (source is ExtendedRawDocumentFile && dest is ExtendedRawDocumentFile) {
            // Try Linux-default file move
            val srcFile = source.getFile()!!
            var dstFile = dest.getFile()!!
            // Java rename cannot infer anything about the source and destination. Therefore, hacks are needed
            if (srcFile.isFile) { // Source is a file
                if (dstFile.isDirectory) {
                    // Move source file inside this directory
                    dstFile = File(dstFile, srcFile.name)
                } else if (dstFile.isFile) {
                    // If destination is a file, it overrides it
                    if (!override) {
                        // Overriding is disabled
                        return false
                    }
                }
                // else destination does not exist and Java is able to create it
            } else if (srcFile.isDirectory) { // Source is a directory
                if (dstFile.isDirectory) {
                    // Move source directory inside this directory
                    dstFile = File(dstFile, srcFile.name)
                } else if (dstFile.isFile) {
                    // Destination cannot be a file
                    return false
                }
                // else destination does not exist and Java is able to create it
            } else {
                // Unsupported file
                return false
            }
            if (srcFile.renameTo(dstFile)) {
                documentFile = getRequiredRawDocument(dstFile.absolutePath)
                if (VirtualFileSystem.getFileSystem(Uri.fromFile(srcFile)) != null) {
                    // Move mount point
                    VirtualFileSystem.alterMountPoint(Uri.fromFile(srcFile), Uri.fromFile(dstFile))
                }
                return true
            }
        }
        // Try Path#renameTo if both are located in the same directory. Mount point is already handled here.
        val sourceParent = source.parentFile
        val destParent = dest.parentFile
        if (sourceParent != null && sourceParent == destParent) {
            // If both path are located in the same directory, rename them
            if (renameTo(path.getName())) {
                return true
            }
        }
        // Try copy and delete approach
        if (source.isDirectory) { // Source is a directory
            if (dest.isDirectory) {
                // Destination is a directory: Apply copy-and-delete inside the dest
                val newPath = dest.createDirectory(source.name!!)
                if (newPath == null || newPath.listFiles().isEmpty()) {
                    // Couldn't create directory or the directory is not empty
                    return false
                }
                try {
                    // Copy all the directory items to the new path and delete source
                    copyDirectory(context, source, newPath)
                    source.delete()
                    documentFile = newPath
                    return true
                } catch (e: IOException) {
                    return false
                }
            }
            if (!dest.exists()) {
                // Destination does not exist, simply create and copy
                // Make sure that parent exists, and it is a directory
                if (destParent == null || !destParent.isDirectory) {
                    return false
                }
                val newPath = destParent.createDirectory(dest.name!!)
                if (newPath == null || newPath.listFiles().isEmpty()) {
                    // Couldn't create directory or the directory is not empty
                    return false
                }
                try {
                    // Copy all the directory items to the new path and delete source
                    copyDirectory(context, source, newPath)
                    source.delete()
                    documentFile = newPath
                    return true
                } catch (e: IOException) {
                    return false
                }
            }
            // Current path is a directory but target is not a directory
            return false
        }
        if (source.isFile) { // Source is a file
            var newPath: DocumentFile?
            if (dest.isDirectory) {
                // Move the file inside the directory
                newPath = dest.createFile(DEFAULT_MIME, name)
            } else if (dest.isFile) {
                // Rename the file and override the existing dest
                if (!override) {
                    // overriding is disabled
                    return false
                }
                newPath = dest
            } else if (destParent != null) {
                // File does not exist, create a new one
                newPath = destParent.createFile(DEFAULT_MIME, dest.name!!)
            } else {
                // File does not exist, but nothing could be done about it
                return false
            }
            if (newPath == null) {
                // For some reason, newPath could not be created
                return false
            }
            try {
                // Copy the contents of the source file and delete it
                copyFile(context, source, newPath)
                source.delete()
                documentFile = newPath
                return true
            } catch (e: IOException) {
                return false
            }
        }
        return false
    }

    override fun copyTo(path: Path): Path? {
        return copyTo(path, true)
    }

    override fun copyTo(path: Path, override: Boolean): Path? {
        val source = getRealDocumentFile(documentFile)
        val dest = getRealDocumentFile(path.documentFile)
        if (!source.exists()) {
            // Source itself does not exist.
            Log.d(TAG, "Source does not exist.")
            return null
        }
        if (dest.exists() && !dest.canWrite()) {
            // There's no point is attempting to copy if the destination is read-only.
            Log.d(TAG, "Read-only destination.")
            return null
        }
        // Add separator to avoid matching wrong files
        val destStr = dest.uri.toString() + File.separator
        val srcStr = source.uri.toString() + File.separator
        if (destStr.startsWith(srcStr)) {
            // Destination cannot be the same or a subdirectory of source
            Log.d(TAG, "Destination is a subdirectory of source.")
            return null
        }
        val destParent = dest.parentFile
        if (source.isDirectory) { // Source is a directory
            if (dest.isDirectory) {
                // Destination is a directory: Apply copy source inside the dest
                val name = source.name!!
                var newPath = dest.findFile(name)
                if (newPath != null) {
                    // Desired directory exists
                    if (!override) {
                        Log.d(TAG, "Overwriting isn't enabled.")
                        return null
                    }
                    // Check if this is the source
                    if (source.uri == newPath.uri) {
                        Log.d(TAG, "Source and destination are the same.")
                        return null
                    }
                }
                newPath = dest.createDirectory(name)
                if (newPath == null) {
                    // Couldn't create directory
                    Log.d(TAG, "Could not create directory in the destination.")
                    return null
                }
                try {
                    // Copy all the directory items to the new path
                    copyDirectory(context, source, newPath, override)
                    return PathImpl(context, newPath)
                } catch (e: IOException) {
                    Log.d(TAG, "Could not copy files.", e)
                    return null
                }
            }
            if (!dest.exists()) {
                // Destination does not exist, simply create and copy
                // Make sure that parent exists, and it is a directory
                if (destParent == null || !destParent.isDirectory) {
                    Log.d(TAG, "Parent of destination must exist.")
                    return null
                }
                val newPath = destParent.createDirectory(dest.name!!)
                if (newPath == null) {
                    // Couldn't create directory or the directory is not empty
                    Log.d(TAG, "Could not create directory or non-empty directory.")
                    return null
                }
                try {
                    // Copy all the directory items to the new path
                    copyDirectory(context, source, newPath, override)
                    return PathImpl(context, newPath)
                } catch (e: IOException) {
                    Log.d(TAG, "Could not copy files.", e)
                    return null
                }
            }
            // Current path is a directory but target is not a directory
            Log.d(TAG, "Source is a directory while destination is not.")
            return null
        }
        if (source.isFile) { // Source is a file
            var newPath: DocumentFile?
            if (dest.isDirectory) {
                // Move the file inside the directory
                newPath = dest.findFile(name)
                if (newPath != null) {
                    // File exists
                    if (!override) {
                        Log.d(TAG, "Overwriting isn't enabled.")
                        return null
                    }
                    // Check if this is the source
                    if (source.uri == newPath.uri) {
                        Log.d(TAG, "Source and destination are the same.")
                        return null
                    }
                }
                newPath = dest.createFile(DEFAULT_MIME, name)
            } else if (dest.isFile) {
                // Override the existing dest
                if (!override) {
                    // overriding is disabled
                    Log.d(TAG, "Overwriting isn't enabled.")
                    return null
                }
                newPath = dest
            } else if (destParent != null) {
                // File does not exist, create a new one
                newPath = destParent.createFile(DEFAULT_MIME, dest.name!!)
            } else {
                // File does not exist, but nothing could be done about it
                Log.d(TAG, "Could not copy file.")
                return null
            }
            if (newPath == null) {
                // For some reason, newPath could not be created
                Log.d(TAG, "Could not create file in the destination.")
                return null
            }
            try {
                // Copy the contents of the source file
                copyFile(context, source, newPath)
                return PathImpl(context, newPath)
            } catch (e: IOException) {
                Log.d(TAG, "Could not copy files.", e)
                return null
            }
        }
        Log.d(TAG, "Unknown error during copying.")
        return null
    }

    override fun lastModified(): Long {
        return documentFile.lastModified()
    }

    override fun setLastModified(time: Long): Boolean {
        if (documentFile is ExtendedRawDocumentFile) {
            return getFile()!!.setLastModified(time)
        }
        if (documentFile is VirtualDocumentFile) {
            return (documentFile as VirtualDocumentFile).setLastModified(time)
        }
        return false
    }

    override fun lastAccess(): Long {
        if (documentFile is ExtendedRawDocumentFile) {
            return getFile()!!.lastAccess()
        }
        if (documentFile is VirtualDocumentFile) {
            return (documentFile as VirtualDocumentFile).lastAccess()
        }
        return 0
    }

    override fun setLastAccess(millis: Long): Boolean {
        if (documentFile is ExtendedRawDocumentFile) {
            return getFile()!!.setLastAccess(millis)
        }
        return false
    }

    override fun creationTime(): Long {
        if (documentFile is ExtendedRawDocumentFile) {
            return getFile()!!.creationTime()
        }
        if (documentFile is VirtualDocumentFile) {
            return (documentFile as VirtualDocumentFile).creationTime()
        }
        return 0
    }

    override fun listFiles(): Array<Path> {
        // Get all file systems mounted at this Uri
        val documentFile = getRealDocumentFile(this.documentFile)
        val fileSystems = VirtualFileSystem.getFileSystemsAtUri(documentFile.uri)
        val nameMountPointMap = HashMap<String, Uri>(fileSystems.size)
        for (fs in fileSystems) {
            val mountPoint = fs.mountPoint!!
            nameMountPointMap[mountPoint.lastPathSegment!!] = mountPoint
        }
        // List documents at this folder
        val ss = documentFile.listFiles()
        if (nameMountPointMap.isEmpty()) {
            // No need to go further
            return Array(ss.size) { i -> PathImpl(context, ss[i]) }
        }
        val paths = ArrayList<Path>(ss.size + fileSystems.size)
        // Remove mount points
        for (s in ss) {
            if (nameMountPointMap[s.name] != null) {
                // Mount point exists, remove it from map
                nameMountPointMap.remove(s.name)
            }
            paths.add(PathImpl(context, s))
        }
        // Add all the other mount points
        for (mountPoint in nameMountPointMap.values) {
            paths.add(PathImpl(context, mountPoint))
        }
        return paths.toTypedArray()
    }

    override fun listFileNames(filter: FilenameFilter?): Array<String> {
        val ss = listFiles()
        val files = ArrayList<String>()
        for (s in ss) {
            val name = s.getName()
            if (filter == null || filter.accept(s, name)) {
                files.add(name)
            }
        }
        return files.toTypedArray()
    }

    @Throws(FileNotFoundException::class)
    override fun openFileDescriptor(mode: String, callbackHandler: Handler): ParcelFileDescriptor {
        val documentFile = getRealDocumentFile(this.documentFile)
        if (documentFile is ExtendedRawDocumentFile) {
            val file = getFile()!!
            if (file is RemoteFile) {
                val modeBits = ParcelFileDescriptor.parseMode(mode)
                return try {
                    StorageManagerCompat.openProxyFileDescriptor(
                        modeBits, ProxyStorageCallback(
                            file.absolutePath, modeBits, callbackHandler
                        )
                    )
                } catch (e: IOException) {
                    throw FileNotFoundException("Could not open file $file").initCause(e) as FileNotFoundException
                }
            } // else use the default content provider
        } else if (documentFile is VirtualDocumentFile) {
            val modeBits = ParcelFileDescriptor.parseMode(mode)
            return try {
                (documentFile as VirtualDocumentFile).openFileDescriptor(modeBits)
            } catch (e: IOException) {
                throw FileNotFoundException(e.message).initCause(e) as FileNotFoundException
            }
        }
        return FileUtils.getFdFromUri(context, documentFile.uri, mode)
    }

    @Throws(IOException::class)
    override fun openOutputStream(append: Boolean): OutputStream {
        val documentFile = resolveFileOrNull(this.documentFile) ?: throw IOException("${this.documentFile.uri} is a directory")
        if (documentFile is ExtendedRawDocumentFile) {
            return try {
                getFile()!!.newOutputStream(append)
            } catch (e: IOException) {
                throw IOException("Could not open file for writing: ${documentFile.uri}", e)
            }
        } else if (documentFile is VirtualDocumentFile) {
            return (documentFile as VirtualDocumentFile).openOutputStream(append)
        }
        val mode = "w" + (if (append) "a" else "t")
        return context.contentResolver.openOutputStream(documentFile.uri, mode) ?: throw IOException("Could not resolve Uri: ${documentFile.uri}")
    }

    @Throws(IOException::class)
    override fun openInputStream(): InputStream {
        val documentFile = resolveFileOrNull(this.documentFile) ?: throw IOException("${this.documentFile.uri} is a directory")
        if (documentFile is ExtendedRawDocumentFile) {
            return try {
                getFile()!!.newInputStream()
            } catch (e: IOException) {
                throw IOException("Could not open file for reading: ${documentFile.uri}", e)
            }
        } else if (documentFile is VirtualDocumentFile) {
            return (documentFile as VirtualDocumentFile).openInputStream()
        }
        return try {
            context.contentResolver.openInputStream(documentFile.uri) ?: throw IOException("Could not resolve Uri: ${documentFile.uri}")
        } catch (e: SecurityException) {
            throw IOException(e)
        }
    }

    @Throws(IOException::class)
    override fun openFileChannel(mode: Int): FileChannel {
        val documentFile = resolveFileOrNull(this.documentFile) ?: throw IOException("${this.documentFile.uri} is a directory")
        if (documentFile is ExtendedRawDocumentFile) {
            val file = getFile()!!
            if (file is RemoteFile) {
                return try {
                    LocalServices.getFileSystemManager().openChannel(file, mode)
                } catch (e: RemoteException) {
                    throw IOException(e)
                }
            }
            return FileSystemManager.getLocal().openChannel(file, mode)
        } else if (documentFile is VirtualDocumentFile) {
            return (documentFile as VirtualDocumentFile).openChannel(mode)
        }
        throw IOException("Target is not backed by a real file")
    }

    private class ProxyStorageCallback @Throws(IOException::class) constructor(
        path: String,
        modeBits: Int,
        handler: Handler
    ) : StorageManagerCompat.ProxyFileDescriptorCallbackCompat(handler) {
        private val mChannel: FileChannel

        init {
            try {
                val fs = LocalServices.getFileSystemManager()
                mChannel = fs.openChannel(path, modeBits)
            } catch (throwable: Throwable) {
                throw IOException(throwable)
            }
        }

        @Throws(ErrnoException::class)
        override fun onGetSize(): Long {
            return try {
                mChannel.size()
            } catch (e: IOException) {
                throw ErrnoException(e.message, OsConstants.EBADF)
            }
        }

        @Throws(ErrnoException::class)
        override fun onRead(offset: Long, size: Int, data: ByteArray): Int {
            val bf = ByteBuffer.wrap(data)
            bf.limit(size)
            return try {
                mChannel.read(bf, offset)
            } catch (e: IOException) {
                throw ErrnoException(e.message, OsConstants.EBADF)
            }
        }

        @Throws(ErrnoException::class)
        override fun onWrite(offset: Long, size: Int, data: ByteArray): Int {
            val bf = ByteBuffer.wrap(data)
            bf.limit(size)
            return try {
                mChannel.write(bf, offset)
            } catch (e: IOException) {
                throw ErrnoException(e.message, OsConstants.EBADF)
            }
        }

        @Throws(ErrnoException::class)
        override fun onFsync() {
            try {
                mChannel.force(true)
            } catch (e: IOException) {
                throw ErrnoException(e.message, OsConstants.EBADF)
            }
        }

        override fun onRelease() {
            try {
                mChannel.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        @Throws(Throwable::class)
        protected fun finalize() {
            mChannel.close()
        }
    }
}
