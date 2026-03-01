// SPDX-License-Identifier: WTFPL AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat.reader

import io.github.muntashirakon.AppManager.compat.ProcessCompat
import io.github.muntashirakon.AppManager.logcat.helper.LogcatHelper
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.utils.ThreadUtils
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

/**
 * Copyright 2012 Nolan Lawson
 */
internal class SingleLogcatReader(recordingMode: Boolean,
                                    private val mBuffers: Int,
                                    lastLine: String?) : AbsLogcatReader(recordingMode) {
    private var mLogcatProcess: Process? = null
    private var mBufferedReader: BufferedReader? = null
    private var mReadyToRecord: Boolean = false
    private var mLogBufferCensored: Boolean = false

    init {
        // Drop all previous lines if this is not recording mode
        if (!recordingMode) {
            lastLine = null
        }

        try {
            mLogcatProcess = LogcatHelper.getLogcatProcess(mBuffers)
        } catch (e: IOException) {
            Log.e(TAG, e)
        }

        if (mLogcatProcess != null) {
            val filterInputStream = ProcessCompat.getWrappedInputStream(mLogcatProcess!!)
            mBufferedReader = BufferedReader(InputStreamReader(filterInputStream), 8192)
            try {
                if (lastLine == null) {
                    mReadyToRecord = true
                    return
                }

                // Skip all lines until the last one we read
                var line: String?
                while (mBufferedReader!!.readLine().also { line = it } != null) {
                    if (ThreadUtils.isInterrupted()) {
                        break
                    }
                    if (line == lastLine) {
                        mReadyToRecord = true
                        return
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, e)
            } finally {
                if (!mReadyToRecord) {
                    Log.w(TAG, "Not ready to record, killing current process")
                    killQuietly()

                    try {
                        mLogcatProcess = LogcatHelper.getLogcatProcess(mBuffers)
                    } catch (e: IOException) {
                        Log.e(TAG, e)
                    }

                    if (mLogcatProcess != null) {
                        val filterInputStream = ProcessCompat.getWrappedInputStream(mLogcatProcess!!)
                        mBufferedReader = BufferedReader(InputStreamReader(filterInputStream), 8192)
                        mReadyToRecord = true
                    }
                }
            }
        }
    }

    @Throws(IOException::class)
    override fun readLine(): String? {
        // logcat might have been killed due to some error
        if (mBufferedReader == null) {
            return null
        }
        val line = mBufferedReader!!.readLine()
        if (!mLogBufferCensored && line != null && line.startsWith(BEGIN)) {
            Log.d(TAG, "Log buffer is censored: $line")
            mLogBufferCensored = true
        }
        return line
    }

    override fun killQuietly() {
        ProcessCompat.destroy(mLogcatProcess)
        mLogcatProcess = null
        try {
            mBufferedReader?.close()
        } catch (e: IOException) {
            Log.e(TAG, e)
        }
        mBufferedReader = null
    }

    override fun readyToRecord(): Boolean = mReadyToRecord

    override fun getProcesses(): List<Process> {
        return mLogcatProcess?.let { listOf(it) } ?: emptyList()
    }

    companion object {
        val TAG: String = SingleLogcatReader::class.java.simpleName
        private const val BEGIN = "--------- beginning of "
    }
}
