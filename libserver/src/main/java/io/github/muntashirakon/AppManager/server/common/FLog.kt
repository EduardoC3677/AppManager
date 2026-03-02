// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server.common

import android.system.ErrnoException
import android.system.Os
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger

// Copyright 2017 Zheng Li
object FLog {
    @JvmField
    var writeLog: Boolean = false
    private var fos: FileOutputStream? = null
    private val sBufferSize = AtomicInteger()
    private val sErrorCount = AtomicInteger()

    private fun openFile() {
        try {
            if (writeLog && fos == null && sErrorCount.get() < 5) {
                val file = File("/data/local/tmp/am.txt")
                fos = FileOutputStream(file)
                fos?.write("\n\n\n--------------------\n".toByteArray())
                fos?.write(Date().toString().toByteArray())
                fos?.write("\n\n".toByteArray())
                chown(file.absolutePath, 2000, 2000)
                chmod(file.absolutePath, 493) // 0755 in octal is 493 in decimal
            }
        } catch (e: Exception) {
            e.printStackTrace()
            sErrorCount.incrementAndGet()
            fos = null
        }
    }

    private fun chown(path: String, uid: Int, gid: Int) {
        try {
            Os.chown(path, uid, gid)
        } catch (e: ErrnoException) {
            e.printStackTrace()
        }
    }

    private fun chmod(path: String, mode: Int) {
        try {
            Os.chmod(path, mode)
        } catch (e: ErrnoException) {
            e.printStackTrace()
        }
    }

    @JvmStatic
    fun log(log: String) {
        if (writeLog) {
            println(log)
        } else {
            Log.e("am", "Flog --> $log")
        }
        try {
            if (writeLog) {
                openFile()
                fos?.let {
                    it.write(log.toByteArray())
                    it.write("\n".toByteArray())
                    if (sBufferSize.incrementAndGet() > 10) {
                        it.fd.sync()
                        it.flush()
                        sBufferSize.set(0)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @JvmStatic
    fun log(e: Throwable) {
        log(Log.getStackTraceString(e))
    }

    @JvmStatic
    fun close() {
        try {
            if (writeLog && fos != null) {
                fos?.fd?.sync()
                fos?.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
