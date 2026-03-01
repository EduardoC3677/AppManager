// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm

import android.util.Base64
import io.github.muntashirakon.AppManager.utils.ThreadUtils
import io.github.muntashirakon.io.Path
import io.github.muntashirakon.io.PathAttributes
import io.github.muntashirakon.io.PathContentInfo
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.*

class FmItem : Comparable<FmItem> {
    val isDirectory: Boolean
    val path: Path

    private var mTag: String? = null
    var contentInfo: PathContentInfo? = null
    private var mAttributes: PathAttributes? = null
    private var mName: String? = null
    private var mChildCount = UNRESOLVED
    var isCached: Boolean = false
        private set

    constructor(path: Path) {
        this.path = path
        isDirectory = path.isDirectory()
    }

    internal constructor(path: Path, attributes: PathAttributes) {
        this.path = path
        mAttributes = attributes
        mName = mAttributes!!.name
        isDirectory = mAttributes!!.isDirectory
    }

    val tag: String
        get() {
            if (mTag == null) {
                mTag = "fm_" + Base64.encodeToString(path.toString().toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
            }
            return mTag!!
        }

    val name: String
        get() {
            if (mName != null) {
                return mName!!
            }
            if (mAttributes != null) {
                return mAttributes!!.name
            }
            return path.getName()
        }

    val lastModified: Long
        get() {
            if (mAttributes != null) {
                return mAttributes!!.lastModified
            }
            return path.lastModified()
        }

    val size: Long
        get() {
            if (mAttributes != null) {
                return mAttributes!!.size
            }
            return path.length()
        }

    val childCount: Int
        get() {
            if (!isDirectory) {
                return 0
            }
            if (mChildCount == UNRESOLVED) {
                mChildCount = path.listFiles().size
            }
            return mChildCount
        }

    fun cache() {
        try {
            tag
            fetchAttributes()
            if (ThreadUtils.isInterrupted()) {
                return
            }
            if (isDirectory) {
                childCount
            } else {
                mChildCount = 0
            }
        } finally {
            isCached = true
        }
    }

    private fun fetchAttributes() {
        try {
            // WARNING: The attributes can be changed in SAF anytime from anywhere.
            // But we don't care because speed matters more.
            mAttributes = path.getAttributes()
            mName = mAttributes!!.name
        } catch (e: IOException) {
            e.printStackTrace()
            mName = path.getName()
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FmItem) return false
        return path == other.path
    }

    override fun hashCode(): Int {
        return Objects.hash(path)
    }

    override fun compareTo(other: FmItem): Int {
        if (this == other) return 0
        val typeComp = -isDirectory.compareTo(other.isDirectory)
        return if (typeComp == 0) {
            path.getName().compareTo(other.path.getName(), ignoreCase = true)
        } else {
            typeComp
        }
    }

    companion object {
        const val UNRESOLVED = -2
    }
}
