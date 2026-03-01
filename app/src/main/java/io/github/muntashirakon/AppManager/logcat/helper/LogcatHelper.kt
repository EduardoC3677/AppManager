// SPDX-License-Identifier: WTFPL AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat.helper

import androidx.annotation.IntDef
import io.github.muntashirakon.AppManager.compat.ProcessCompat
import io.github.muntashirakon.AppManager.logs.Log
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

/**
 * Copyright 2012 Nolan Lawson
 */
object LogcatHelper {
    @JvmField
    val TAG: String = LogcatHelper::class.java.simpleName

    @IntDef(value = [LOG_ID_MAIN, LOG_ID_RADIO, LOG_ID_EVENTS, LOG_ID_SYSTEM, LOG_ID_CRASH], flag = true)
    @Retention(AnnotationRetention.SOURCE)
    annotation class LogBufferId

    const val LOG_ID_MAIN = 1
    const val LOG_ID_RADIO = 1 shl 1
    const val LOG_ID_EVENTS = 1 shl 2
    const val LOG_ID_SYSTEM = 1 shl 3
    const val LOG_ID_CRASH = 1 shl 4
    const val LOG_ID_ALL = LOG_ID_MAIN or LOG_ID_RADIO or LOG_ID_EVENTS or LOG_ID_SYSTEM or LOG_ID_CRASH
    const val LOG_ID_DEFAULT = LOG_ID_MAIN or LOG_ID_SYSTEM or LOG_ID_CRASH

    const val BUFFER_MAIN = "main"
    const val BUFFER_RADIO = "radio"
    const val BUFFER_EVENTS = "events"
    const val BUFFER_SYSTEM = "system"
    const val BUFFER_CRASH = "crash"
    const val BUFFER_ALL = "all"
    const val BUFFER_DEFAULT = "default"

    const val DEFAULT_DISPLAY_LIMIT = 10000
    const val DEFAULT_LOG_WRITE_INTERVAL = 200

    @JvmStatic
    @Throws(IOException::class)
    fun getLogcatProcess(@LogBufferId buffers: Int): Process {
        return ProcessCompat.exec(getLogcatArgs(buffers, false))
    }

    @JvmStatic
    fun getLastLogLine(@LogBufferId buffers: Int): String? {
        var dumpLogcatProcess: Process? = null
        var result: String? = null
        try {
            dumpLogcatProcess = ProcessCompat.exec(getLogcatArgs(buffers, true))
            BufferedReader(InputStreamReader(dumpLogcatProcess.inputStream), 8192).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    result = line
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, e)
        } finally {
            dumpLogcatProcess?.destroy()
            Log.d(TAG, "destroyed 1 dump logcat process")
        }
        return result
    }

    @JvmStatic
    fun getLogcatArgs(@LogBufferId buffers: Int, dumpAndExit: Boolean): Array<String> {
        val args = mutableListOf("logcat", "-v", "threadtime", "-v", "uid")
        if (buffers == LOG_ID_ALL) {
            args.add("-b")
            args.add(BUFFER_ALL)
        } else if (buffers == LOG_ID_DEFAULT) {
            args.add("-b")
            args.add(BUFFER_DEFAULT)
        } else {
            if (buffers and LOG_ID_MAIN != 0) {
                args.add("-b")
                args.add(BUFFER_MAIN)
            }
            if (buffers and LOG_ID_RADIO != 0) {
                args.add("-b")
                args.add(BUFFER_RADIO)
            }
            if (buffers and LOG_ID_EVENTS != 0) {
                args.add("-b")
                args.add(BUFFER_EVENTS)
            }
            if (buffers and LOG_ID_SYSTEM != 0) {
                args.add("-b")
                args.add(BUFFER_SYSTEM)
            }
            if (buffers and LOG_ID_CRASH != 0) {
                args.add("-b")
                args.add(BUFFER_CRASH)
            }
        }
        if (dumpAndExit) args.add("-d")
        return args.toTypedArray()
    }
}
