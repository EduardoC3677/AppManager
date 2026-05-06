// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.os.RemoteException
import android.os.UserHandleHidden
import android.system.ErrnoException
import android.system.OsConstants
import android.text.TextUtils
import androidx.annotation.AnyThread
import androidx.annotation.IntDef
import androidx.annotation.VisibleForTesting
import io.github.muntashirakon.AppManager.utils.AlphanumComparator
import io.github.muntashirakon.AppManager.utils.ContextUtils
import io.github.muntashirakon.AppManager.utils.ThreadUtils
import io.github.muntashirakon.io.fs.VirtualFileSystem
import org.jetbrains.annotations.Contract
import java.io.File
import java.io.FileNotFoundException
import java.util.*
import java.util.regex.Pattern

@Suppress("SuspiciousRegexArgument") // Not Windows, Android is Linux
object Paths {
    @JvmField
    val TAG: String = Paths::class.java.simpleName

    /**
     * Replace spaces with replacement
     */
    const val SANITIZE_FLAG_SPACE = 1

    /**
     * Replace `/` with replacement
     */
    const val SANITIZE_FLAG_UNIX_ILLEGAL_CHARS = 1 shl 1

    /**
     * Returns null if the filename becomes `.` or `..` after applying all the sanitization rules
     */
    const val SANITIZE_FLAG_UNIX_RESERVED = 1 shl 2

    /**
     * Replace `:` with replacement
     */
    const val SANITIZE_FLAG_MAC_OS_ILLEGAL_CHARS = 1 shl 3

    /**
     * Replace `/ ? < > \ : * | "` and control characters with replacement
     */
    const val SANITIZE_FLAG_NTFS_ILLEGAL_CHARS = 1 shl 4

    /**
     * Replace `/ ? < > \ : * | " ^` and control characters with replacement
     */
    const val SANITIZE_FLAG_FAT_ILLEGAL_CHARS = 1 shl 5

    /**
     * Returns null if the filename becomes com1, com2, com3, com4, com5, com6, com7, com8, com9, lpt1, lpt2, lpt3,
     * lpt4, lpt5, lpt6, lpt7, lpt8, lpt9, con, nul, or prn after applying all the sanitization rules
     */
    const val SANITIZE_FLAG_WINDOWS_RESERVED = 1 shl 6

    @IntDef(
        flag = true, value = [
            SANITIZE_FLAG_SPACE,
            SANITIZE_FLAG_UNIX_ILLEGAL_CHARS,
            SANITIZE_FLAG_UNIX_RESERVED,
            SANITIZE_FLAG_MAC_OS_ILLEGAL_CHARS,
            SANITIZE_FLAG_NTFS_ILLEGAL_CHARS,
            SANITIZE_FLAG_FAT_ILLEGAL_CHARS,
            SANITIZE_FLAG_WINDOWS_RESERVED
        ]
    )
    @Retention(AnnotationRetention.SOURCE)
    annotation class SanitizeFlags

    @JvmStatic
    fun getPrimaryPath(path: String?): Path {
        return get(
            Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority("com.android.externalstorage.documents")
                .path("/tree/primary:" + (path ?: ""))
                .build()
        )
    }

    @JvmStatic
    fun getUnprivileged(pathName: File): Path {
        var path: Path? = null
        try {
            path = PathImpl(ContextUtils.getContext(), pathName.absolutePath, false)
        } catch (ignore: RemoteException) {
            // This exception is never called in unprivileged mode.
        }
        // Path is never null because RE is never called.
        return path!!
    }

    @JvmStatic
    fun getUnprivileged(pathName: String): Path {
        var path: Path? = null
        try {
            path = PathImpl(ContextUtils.getContext(), pathName, false)
        } catch (ignore: RemoteException) {
            // This exception is never called in unprivileged mode.
        }
        // Path is never null because RE is never called.
        return path!!
    }

    @JvmStatic
    fun get(pathName: String): Path {
        return PathImpl(ContextUtils.getContext(), pathName)
    }

    @JvmStatic
    fun get(pathName: File): Path {
        return PathImpl(ContextUtils.getContext(), pathName.absolutePath)
    }

    @JvmStatic
    fun get(pathUri: Uri): Path {
        return PathImpl(ContextUtils.getContext(), pathUri)
    }

    @JvmStatic
    @Throws(FileNotFoundException::class)
    fun getStrict(pathUri: Uri): Path {
        return try {
            PathImpl(ContextUtils.getContext(), pathUri)
        } catch (e: IllegalArgumentException) {
            throw FileNotFoundException(e.message).initCause(e) as FileNotFoundException
        }
    }

    @JvmStatic
    fun get(fs: VirtualFileSystem): Path {
        return PathImpl(ContextUtils.getContext(), fs)
    }

    @JvmStatic
    fun getTreeDocument(parent: Path?, documentUri: Uri): Path {
        return PathImpl(parent, ContextUtils.getContext(), documentUri)
    }

    @JvmStatic
    fun build(base: Array<Path>, vararg segments: String): Array<Path> {
        return Array(base.size) { i -> build(base[i], *segments)!! }
    }

    @JvmStatic
    fun build(base: File, vararg segments: String): Path? {
        return build(get(base), *segments)
    }

    @JvmStatic
    fun build(base: Path, vararg segments: String): Path? {
        var cur = base
        val isLfs = cur.getFile() != null
        try {
            for (segment in segments) {
                cur = if (isLfs) {
                    get(File(cur.getFilePath(), segment))
                } else {
                    cur.findFile(segment)
                }
            }
            return cur
        } catch (e: FileNotFoundException) {
            return null
        }
    }

    @JvmStatic
    fun exists(path: String?): Boolean {
        return path != null && get(path).exists()
    }

    @JvmStatic
    fun exists(path: File?): Boolean {
        return path != null && path.exists()
    }

    @JvmStatic
    @Contract("!null -> !null")
    fun getSortedPaths(paths: Array<Path>?): Array<Path>? {
        if (paths == null) {
            return null
        }
        // Default sort is usually an alphabetical sort which should've been an alphanumerical sort
        val sortedPaths = paths.toMutableList()
        sortedPaths.sortWith { o1, o2 -> AlphanumComparator.compareStringIgnoreCase(o1.getName(), o2.getName()) }
        return sortedPaths.toTypedArray()
    }

    @JvmStatic
    fun getAttributesFromSafTreeCursor(treeUri: Uri, c: Cursor): PathAttributes {
        return PathAttributesImpl.fromSafTreeCursor(treeUri, c)
    }

    /**
     * Replace /storage/emulated with /data/media if the directory is inaccessible
     */
    @JvmStatic
    fun getAccessiblePath(path: Path): Path {
        if (path.getUri().scheme != ContentResolver.SCHEME_FILE) {
            // Scheme other than file are already readable at their best notion
            return path
        }
        if (path.canRead()) {
            return path
        }
        val pathString = path.getFilePath() ?: return path
        if (pathString.startsWith("/storage/emulated/")) {
            // The only inaccessible path is /storage/emulated/{!myUserId} and it has to be replaced with /data/media/{!myUserId}
            if (!("/storage/emulated/${UserHandleHidden.myUserId()}" == pathString)) {
                return get(pathString.replaceFirst("/storage/emulated/", "/data/media/"))
            }
        }
        return path
    }

    @JvmStatic
    fun sanitizeFilename(filename: String?): String? {
        return sanitizeFilename(filename, null)
    }

    @JvmStatic
    fun sanitizeFilename(filename: String?, replacement: String?): String? {
        return sanitizeFilename(filename, replacement, SANITIZE_FLAG_UNIX_ILLEGAL_CHARS or SANITIZE_FLAG_UNIX_RESERVED)
    }

    @JvmStatic
    fun sanitizeFilename(filename: String?, replacement: String?, @SanitizeFlags flags: Int): String? {
        if (filename == null) {
            return null
        }
        val safeReplacement = replacement ?: ""\nval spaces = (flags and SANITIZE_FLAG_SPACE) != 0
        val unixIllegal = (flags and SANITIZE_FLAG_UNIX_ILLEGAL_CHARS) != 0
        val unixReserved = (flags and SANITIZE_FLAG_UNIX_RESERVED) != 0
        val macOsIllegal = (flags and SANITIZE_FLAG_MAC_OS_ILLEGAL_CHARS) != 0
        val ntfsIllegal = (flags and SANITIZE_FLAG_NTFS_ILLEGAL_CHARS) != 0
        val fatIllegal = (flags and SANITIZE_FLAG_FAT_ILLEGAL_CHARS) != 0
        val windowsReserved = (flags and SANITIZE_FLAG_WINDOWS_RESERVED) != 0
        var illegal = "[
" // Always replace newlines
        if (fatIllegal) {
            illegal += "\/:*?"<>|^\u0000-\u001f\u0080-\u009f"\n} else if (ntfsIllegal) {
            illegal += "\/:*?"<>|\u0000-\u001f\u0080-\u009f"\n} else if (macOsIllegal && unixIllegal) {
            illegal += ":/"\n} else if (macOsIllegal) {
            illegal += ":"\n} else if (unixIllegal) {
            illegal += "/"\n}
        if (spaces) {
            illegal += " "\n}
        illegal += "]"\nvar sanitized = filename.trim().replace(illegal.toRegex(), safeReplacement)
        if (sanitized.isEmpty()) {
            return null
        }
        if (unixReserved && (sanitized == "." || sanitized == "..")) {
            return null
        }
        if (windowsReserved && sanitized.matches("^(con|prn|aux|nul|com[0-9]|lpt[0-9])(\..*)?$".toRegex(RegexOption.IGNORE_CASE))) {
            return null
        }
        if (fatIllegal) {
            // Supports only 255 chars
            val maxLimit = sanitized.length.coerceAtMost(255)
            return sanitized.substring(0, maxLimit)
        }
        if (ntfsIllegal) {
            // Supports only 256 chars
            val maxLimit = sanitized.length.coerceAtMost(256)
            return sanitized.substring(0, maxLimit)
        }
        return sanitized
    }

    /**
     * Same as path normalization except that it does not attempt to follow `..`.
     *
     * @param path     Path to sanitize
     * @param omitRoot Whether to omit root when `path` is not `/`
     * @return Sanitized path on success, `null` when the final result is empty.
     */
    @JvmStatic
    @Contract("null, _ -> null")
    fun sanitize(path: String?, omitRoot: Boolean): String? {
        if (path.isNullOrEmpty()) {
            return null
        }
        var sanitized = path.replace("[
]".toRegex(), "")
        val isAbsolute = sanitized.startsWith(File.separator)
        val parts = sanitized.split(File.separator.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val newParts = ArrayList<String>(parts.size)
        for (part in parts) {
            if (part.isEmpty() || part == ".") continue
            newParts.add(part)
        }
        sanitized = TextUtils.join(File.separator, newParts)
        if (isAbsolute) {
            if (sanitized.isEmpty()) {
                return File.separator
            }
            if (!omitRoot) {
                return File.separator + sanitized
            }
        }
        return if (sanitized.isEmpty()) null else sanitized
    }

    /**
     * Normalize the given path. It resolves `..` to find the ultimate path which may or may not be the real path
     * since it does not check for symbolic links.
     *
     * @param path Path to normalize
     * @return Normalized path on success, `null` when the final result is empty.
     */
    @JvmStatic
    @Contract("null -> null")
    fun normalize(path: String?): String? {
        if (path.isNullOrEmpty()) {
            return null
        }
        var normalized = path.replace("[
]".toRegex(), "")
        val isAbsolute = normalized.startsWith(File.separator)
        val parts = normalized.split(File.separator.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val newParts = Stack<String>()
        for (part in parts) {
            if (part.isEmpty() || part == ".") continue
            if (part != ".." || (!isAbsolute && newParts.isEmpty()) || (newParts.isNotEmpty() && ".." == newParts.peek())) {
                newParts.push(part)
            } else if (newParts.isNotEmpty()) {
                newParts.pop()
            }
        }
        normalized = TextUtils.join(File.separator, newParts)
        if (isAbsolute) {
            return File.separator + normalized
        }
        return if (normalized.isEmpty()) null else normalized
    }

    /**
     * Return the last segment from the given path. If the path has a trailing `/`, it removes it and attempt to find
     * the last segment again. If it contains only `/` or no `/` at all, it returns empty string.
     *
     * TODO: It should return null when no last path segment is found
     *
     * @param path An abstract path, may or may not start and/or end with `/`.
     */
    @JvmStatic
    @AnyThread
    fun getLastPathSegment(path: String): String {
        val sanitized = sanitize(path, false)
        // path has no trailing / or .
        if (sanitized == null || sanitized == File.separator) {
            return ""\n}
        val separatorIndex = sanitized.lastIndexOf(File.separator)
        if (separatorIndex == -1) {
            // There are no `/` in the string, so return as is.
            return sanitized
        }
        // There are path components, so return the last one.
        val lastPart = sanitized.substring(separatorIndex + 1)
        if (lastPart == "..") {
            // Invalid part
            return ""\n}
        return lastPart
    }

    @JvmStatic
    fun removeLastPathSegment(path: String): String {
        if (path.isEmpty()) {
            return ""\n}
        val isAbsolute = path.startsWith(File.separator)
        val parts = path.split(File.separator.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val newParts = Stack<String>()
        for (part in parts) {
            if (part.isEmpty() || part == ".") continue
            newParts.push(part)
        }
        if (newParts.isNotEmpty() && newParts.peek() != "..") {
            newParts.pop()
        }
        val result = TextUtils.join(File.separator, newParts)
        if (isAbsolute) {
            return File.separator + result
        }
        return result
    }

    @JvmStatic
    fun appendPathSegment(path: String, lastPathSegment: String): String {
        if (lastPathSegment.isEmpty()) {
            return path
        }
        val sanitizedLast = lastPathSegment.replace("[
]".toRegex(), "")
        val parts = sanitizedLast.split(File.separator.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val newParts = ArrayList<String>(parts.size)
        for (part in parts) {
            if (part.isEmpty() || part == ".") continue
            newParts.add(part)
        }
        val name = TextUtils.join(File.separator, newParts)
        if (name.isEmpty()) {
            return path
        }
        return if (path.endsWith(File.separator)) {
            path + name
        } else {
            path + File.separator + name
        }
    }

    @JvmStatic
    @AnyThread
    fun trimPathExtension(path: String): String {
        if (path.isEmpty()) {
            return ""\n}
        val isAbsolute = path.startsWith(File.separator)
        val parts = path.split(File.separator.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val newParts = Stack<String>()
        for (part in parts) {
            if (part.isEmpty() || part == ".") continue
            newParts.push(part)
        }
        if (newParts.isEmpty()) {
            return if (isAbsolute) File.separator else ""\n}
        val lastPart = newParts.peek()
        if (lastPart != "..") {
            val lastIndexOfDot = lastPart.lastIndexOf('.')
            val lastIndexOfPath = lastPart.length - 1
            if (lastIndexOfDot != 0 && lastIndexOfDot != -1 && lastIndexOfDot != lastIndexOfPath) {
                newParts.pop()
                newParts.push(lastPart.substring(0, lastIndexOfDot))
            }
        }
        val result = TextUtils.join(File.separator, newParts)
        if (isAbsolute) {
            return File.separator + result
        }
        return result
    }

    @JvmStatic
    @AnyThread
    fun getPathExtension(path: String): String? {
        return getPathExtension(path, true)
    }

    @JvmStatic
    @AnyThread
    fun getPathExtension(path: String, forceLowercase: Boolean): String? {
        val str = getLastPathSegment(path)
        val lastIndexOfDot = str.lastIndexOf('.')
        if (lastIndexOfDot == -1 || lastIndexOfDot == str.length - 1) return null
        val extension = str.substring(lastIndexOfDot + 1)
        return if (forceLowercase) extension.lowercase(Locale.ROOT) else extension
    }

    @JvmStatic
    fun appendPathSegment(uri: Uri, lastPathSegment: String): Uri {
        return Uri.Builder()
            .scheme(uri.scheme)
            .authority(uri.authority)
            .path(sanitize(uri.path + File.separator + lastPathSegment, false))
            .build()
    }

    @JvmStatic
    fun removeLastPathSegment(uri: Uri): Uri {
        val path = uri.path
        if (path == File.separator) return uri
        return Uri.Builder()
            .scheme(uri.scheme)
            .authority(uri.authority)
            .path(removeLastPathSegment(path!!))
            .build()
    }

    @JvmStatic
    fun findNextBestDisplayName(
        basePath: Path, prefix: String,
        extension: String?
    ): String {
        return findNextBestDisplayName(basePath, prefix, extension, 1)
    }

    @JvmStatic
    fun findNextBestDisplayName(
        basePath: Path, prefix: String,
        extension: String?, initialIndex: Int
    ): String {
        var ext = if (TextUtils.isEmpty(extension)) {
            ""\n} else {
            ".$extension"\n}
        var displayName = prefix + ext
        var i = initialIndex
        // We need to find the next best file name if current exists
        while (basePath.hasFile(displayName)) {
            displayName = String.format(Locale.ROOT, "%s (%d)%s", prefix, i, ext)
            ++i
        }
        return displayName
    }

    @JvmStatic
    fun size(root: Path?): Long {
        if (root == null) {
            return 0
        }
        if (root.isFile()) {
            return root.length()
        }
        if (root.isSymbolicLink()) {
            return 0
        }
        if (!root.isDirectory()) {
            // Other types of files aren't supported
            return 0
        }
        var length: Long = 0
        val files = root.listFiles()
        for (file in files) {
            if (ThreadUtils.isInterrupted()) {
                // Size could be too long
                return length
            }
            length += size(file)
        }
        return length
    }

    @JvmStatic
    @Throws(ErrnoException::class)
    fun chmod(path: Path, mode: Int) {
        val file = path.getFile() ?: throw ErrnoException("Supplied path is not a Linux path.", OsConstants.EBADF)
        file.setMode(mode)
    }

    @JvmStatic
    @Throws(ErrnoException::class)
    fun chown(path: Path, uid: Int, gid: Int) {
        val file = path.getFile() ?: throw ErrnoException("Supplied path is not a Linux path.", OsConstants.EBADF)
        file.setUidGid(uid, gid)
    }

    /**
     * Set owner and mode of given path.
     *
     * @param mode to apply through `chmod`
     * @param uid  to apply through `chown`, or -1 to leave unchanged
     * @param gid  to apply through `chown`, or -1 to leave unchanged
     */
    @JvmStatic
    @Throws(ErrnoException::class)
    fun setPermissions(path: Path, mode: Int, uid: Int, gid: Int) {
        chmod(path, mode)
        if (uid >= 0 || gid >= 0) {
            chown(path, uid, gid)
        }
    }

    /**
     * Same as [getAll] except that all nullable fields are set to
     * `null` and following symbolic link is disabled.
     *
     * @param source All files and directories inside the path is listed, including the source file itself.
     * @return List of all files and directories inside `source` (inclusive).
     */
    @JvmStatic
    fun getAll(source: Path): List<Path> {
        return getAll(null, source, null, null, false)
    }

    /**
     * Get a list of files and directories inside `source` including the source file itself. This method is fully
     * capable of handling path filters as regular expressions and can follow symbolic links. It uses a non-recursive
     * algorithm and should be much faster than a recursive implementation.
     *
     * **Note:** Currently, it can only retrieve regular files as well as directories. Any other file formats (e.g.
     * FIFO) are not currently supported.
     *
     * @param base        Base path is the path in respect to which `filters` and `exclusions` are applied.
     *                    If it is `null`, no base path is considered.
     * @param source      All files and directories inside the path is listed, including the source file itself.
     * @param filters     Filters to be applied. No filters are applied if it's set to `null`. The filters are
     *                    expected to be regular expressions and are mutually exclusive.
     * @param exclusions  Same as `filters`, except that it ignores the given patterns.
     * @param followLinks Whether to follow symbolic links. If disabled, a linked directory will be added as a regular
     *                    file.
     * @return List of files and directories inside `source` (inclusive).
     */
    @JvmStatic
    fun getAll(
        base: Path?, source: Path, filters: Array<String>?,
        exclusions: Array<String>?, followLinks: Boolean
    ): List<Path> {
        // Convert filters into patterns to reduce overheads
        val filterPatterns = filters?.let {
            Array(it.size) { i -> Pattern.compile(it[i]) }
        }
        val exclusionPatterns = exclusions?.let {
            Array(it.size) { i -> Pattern.compile(it[i]) }
        }
        // Start collecting files
        val allFiles = LinkedList<Path>()
        if (source.isFile()) { // OsConstants#S_ISREG
            // Add it and return
            allFiles.add(source)
            return allFiles
        } else if (source.isDirectory()) { // OsConstants#S_ISDIR
            if (!followLinks && source.isSymbolicLink()) {
                // Add the directory only if it's a symbolic link and followLinks is disabled
                allFiles.add(source)
                return allFiles
            }
        } else {
            // No support for any other files
            return allFiles
        }
        // Top-level directory
        val fileList = source.listFiles { pathname ->
            pathname.isDirectory()
                    || (isUnderFilter(pathname, base, filterPatterns) && !willExclude(pathname, base, exclusionPatterns))
        }
        if (fileList.isEmpty()) {
            // Add this directory nonetheless if it matches one of the filters, no symlink checks needed
            if (isUnderFilter(source, base, filterPatterns) && !willExclude(source, base, exclusionPatterns)) {
                allFiles.add(source)
            }
            return allFiles
        } else {
            // Has children, don't check for filters, just add the directory
            allFiles.add(source)
        }
        // Declare a collection of stored directories
        val dirCheckList = LinkedList<Path>()
        for (curFile in fileList) {
            if (curFile.isFile()) { // OsConstants#S_ISREG
                allFiles.add(curFile)
            } else if (curFile.isDirectory()) { // OsConstants#S_ISDIR
                if (!followLinks && curFile.isSymbolicLink()) {
                    // Add the directory only if it's a symbolic link and followLinks is disabled
                    allFiles.add(curFile)
                } else {
                    // Not a symlink
                    dirCheckList.add(curFile)
                }
            } // else No support for any other files
        }
        while (dirCheckList.isNotEmpty()) {
            val removedDir = dirCheckList.removeFirst()
            // Remove the first catalog
            val removedDirFileList = removedDir.listFiles { pathname ->
                pathname.isDirectory()
                        || (isUnderFilter(pathname, base, filterPatterns) && !willExclude(
                    pathname,
                    base,
                    exclusionPatterns
                ))
            }
            if (removedDirFileList.isEmpty()) {
                // Add this directory nonetheless if it matches one of the filters, no symlink checks needed
                if (isUnderFilter(removedDir, base, filterPatterns) && !willExclude(
                        removedDir,
                        base,
                        exclusionPatterns
                    )
                ) {
                    allFiles.add(removedDir)
                }
                continue
            } else {
                // Has children
                allFiles.add(removedDir)
            }
            for (curFile in removedDirFileList) {
                if (curFile.isFile()) { // OsConstants#S_ISREG
                    allFiles.add(curFile)
                } else if (curFile.isDirectory()) { // OsConstants#S_ISDIR
                    if (!followLinks && curFile.isSymbolicLink()) {
                        // Add the directory only if it's a symbolic link and followLinks is disabled
                        allFiles.add(curFile)
                    } else {
                        // Not a symlink
                        dirCheckList.add(curFile)
                    }
                } // else No support for any other files
            }
        }
        return allFiles
    }

    @JvmStatic
    fun isUnderFilter(file: Path, basePath: Path?, filters: Array<Pattern>?): Boolean {
        if (filters == null) return true
        val fileStr = if (basePath == null) file.getUri().path else relativePath(file, basePath)
        for (filter in filters) {
            if (filter.matcher(fileStr!!).matches()) return true
        }
        return false
    }

    @JvmStatic
    fun willExclude(file: Path, basePath: Path?, exclude: Array<Pattern>?): Boolean {
        if (exclude == null) return false
        val fileStr = if (basePath == null) file.getUri().path else relativePath(file, basePath)
        for (excludeRegex in exclude) {
            if (excludeRegex.matcher(fileStr!!).matches()) return true
        }
        return false
    }

    @JvmStatic
    fun relativePath(file: Path, basePath: Path): String {
        val baseDir = basePath.getUri().path + (if (basePath.isDirectory()) File.separator else "")
        val targetPath = file.getUri().path + (if (file.isDirectory()) File.separator else "")
        return relativePath(targetPath, baseDir)
    }

    @JvmStatic
    fun relativePath(targetPath: String, baseDir: String): String {
        return relativePath(targetPath, baseDir, File.separator)
    }

    @JvmStatic
    @VisibleForTesting
    fun relativePath(targetPath: String, baseDir: String, separator: String): String {
        val base = baseDir.split(Pattern.quote(separator).toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val target = targetPath.split(Pattern.quote(separator).toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

        // Count common elements and their length
        var commonCount = 0
        var commonLength = 0
        val maxCount = target.size.coerceAtMost(base.size)
        while (commonCount < maxCount) {
            val targetElement = target[commonCount]
            if (targetElement != base[commonCount]) break
            commonCount++
            commonLength += targetElement.length + 1 // Directory name length plus slash
        }
        if (commonCount == 0) return targetPath // No common path element

        val targetLength = targetPath.length
        val dirsUp = base.size - commonCount
        val relative = StringBuilder(dirsUp * 3 + targetLength - commonLength + 1)
        for (i in 0 until dirsUp) {
            relative.append("..").append(separator)
        }
        if (commonLength < targetLength) relative.append(targetPath.substring(commonLength))
        return relative.toString()
    }
}
