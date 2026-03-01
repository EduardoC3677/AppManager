// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io

import java.io.File
import java.io.FileFilter
import java.io.FilenameFilter
import java.io.IOException
import java.util.*

// Copyright 2022 John "topjohnwu" Wu
internal abstract class FileImpl<T : ExtendedFile> : ExtendedFile {
    protected constructor(pathname: String) : super(pathname)

    protected constructor(parent: String?, child: String) : super(parent, child)

    protected abstract fun create(path: String): T
    protected abstract fun createArray(n: Int): Array<T>

    override abstract fun getChildFile(child: String): T

    override fun getAbsoluteFile(): T {
        return create(absolutePath)
    }

    @Throws(IOException::class)
    override fun getCanonicalFile(): T {
        return create(canonicalPath)
    }

    override fun getParentFile(): T? {
        val parent = parent
        return if (parent != null) create(parent) else null
    }

    override fun listFiles(): Array<T>? {
        val ss = list() ?: return null
        val n = ss.size
        val fs = createArray(n)
        for (i in 0 until n) {
            fs[i] = getChildFile(ss[i])
        }
        return fs
    }

    override fun listFiles(filter: FilenameFilter?): Array<T>? {
        val ss = list() ?: return null
        val files = ArrayList<T>()
        for (s in ss) {
            if (filter == null || filter.accept(this, s))
                files.add(getChildFile(s))
        }
        return files.toArray(createArray(0))
    }

    override fun listFiles(filter: FileFilter?): Array<T>? {
        val ss = list() ?: return null
        val files = ArrayList<T>()
        for (s in ss) {
            val f = getChildFile(s)
            if (filter == null || filter.accept(f))
                files.add(f)
        }
        return files.toArray(createArray(0))
    }
}
