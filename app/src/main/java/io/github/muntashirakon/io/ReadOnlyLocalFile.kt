// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io

import android.system.ErrnoException
import java.io.File

internal open class ReadOnlyLocalFile : LocalFile {
    companion object {
        @JvmStatic
        fun getAliasInstance(pathname: String, alias: String): ReadOnlyLocalFile {
            val file = ReadOnlyLocalFile(alias)
            file.mActualPath = pathname
            return file
        }
    }

    private var mActualPath: String? = null

    constructor(pathname: String) : super(pathname)

    protected constructor(parent: String?, child: String) : super(parent, child)

    override fun getName(): String {
        return mActualPath?.let { File(it).name } ?: super.getName()
    }

    override fun isSymlink(): Boolean {
        return mActualPath != null || super.isSymlink()
    }

    override fun create(path: String): LocalFile {
        val children = LocalFileOverlay.listChildren(File(path))
        if (children != null) {
            // Emulated directory
            // Check for potential alias
            for (child in children) {
                if (child.startsWith(File.separator)) {
                    // Alias
                    return getAliasInstance(path, child)
                }
            }
            // No alias found
            return ReadOnlyLocalFile(path)
        }
        return super.create(path)
    }

    override fun getChildFile(name: String): LocalFile {
        val children = LocalFileOverlay.listChildren(this)
        if (children != null) {
            for (child in children) {
                if (child == name) {
                    return ReadOnlyLocalFile(path, child)
                }
            }
        }
        return super.getChildFile(name)
    }

    @Suppress("OctalInteger")
    override fun getMode(): Int {
        return try {
            super.getMode()
        } catch (e: ErrnoException) {
            // Folder + read-only
            0x4124 // 0040444 in octal is 16676 but S_IFDIR is 0x4000 and 0444 is 0x124
        }
    }

    override fun getUidGid(): UidGidPair {
        return try {
            super.getUidGid()
        } catch (e: ErrnoException) {
            UidGidPair(0, 0)
        }
    }

    override fun list(): Array<String>? {
        val children = LocalFileOverlay.listChildren(this) ?: return super.list()
        val childList = ArrayList<String>(children.size)
        for (child in children) {
            if (!child.startsWith(File.separator)) {
                // Check if the path actually exist
                if (File(this, child).exists()) {
                    childList.add(child)
                }
            }
        }
        return childList.toTypedArray()
    }
}
