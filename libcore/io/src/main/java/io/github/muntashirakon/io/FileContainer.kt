// SPDX-License-Identifier: Apache-2.0

package io.github.muntashirakon.io

import android.os.Binder
import android.util.SparseArray
import java.io.IOException

// Copyright 2022 John "topjohnwu" Wu
internal class FileContainer {
    private var nextHandle = 0

    // pid -> handle -> holder
    private val files = SparseArray<SparseArray<OpenFile>>()

    @Synchronized
    @Throws(IOException::class)
    operator fun get(handle: Int): OpenFile {
        val pid = Binder.getCallingPid()
        val pidFiles = files[pid] ?: throw IOException(ERROR_MSG)
        return pidFiles[handle] ?: throw IOException(ERROR_MSG)
    }

    @Synchronized
    fun put(h: OpenFile): Int {
        val pid = Binder.getCallingPid()
        var pidFiles = files[pid]
        if (pidFiles == null) {
            pidFiles = SparseArray()
            files.put(pid, pidFiles)
        }
        val handle = nextHandle++
        pidFiles.append(handle, h)
        return handle
    }

    @Synchronized
    fun remove(handle: Int) {
        val pid = Binder.getCallingPid()
        val pidFiles = files[pid] ?: return
        val h = pidFiles[handle] ?: return
        pidFiles.remove(handle)
        h.close()
    }

    @Synchronized
    fun pidDied(pid: Int) {
        val pidFiles = files[pid] ?: return
        files.remove(pid)
        for (i in 0 until pidFiles.size()) {
            pidFiles.valueAt(i).close()
        }
    }

    companion object {
        private const val ERROR_MSG = "Requested file was not opened!"
    }
}
