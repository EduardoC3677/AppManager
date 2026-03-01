// SPDX-License-Identifier: WTFPL AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat.reader

import io.github.muntashirakon.AppManager.logcat.helper.LogcatHelper
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.utils.ThreadUtils
import java.io.IOException
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue

/**
 * Combines multiple buffered readers into a single reader that merges all input synchronously.
 */
// Copyright 2012 Nolan Lawson
class MultipleLogcatReader(recordingMode: Boolean, lastLines: Map<Int, String?>) : AbsLogcatReader(recordingMode) {
    private val mReaderThreads: MutableList<ReaderThread> = LinkedList()
    private val mQueue: BlockingQueue<String> = ArrayBlockingQueue(1)

    init {
        // Read from all three buffers all at once
        for ((buffers, lastLine) in lastLines) {
            val readerThread = ReaderThread(buffers, lastLine)
            readerThread.start()
            mReaderThreads.add(readerThread)
        }
    }

    @Throws(IOException::class)
    override fun readLine(): String? {
        try {
            val value = mQueue.take()
            if (value != DUMMY_NULL) {
                return value
            }
        } catch (e: InterruptedException) {
            Log.e(TAG, e)
        }
        return null
    }

    override fun readyToRecord(): Boolean {
        for (thread in mReaderThreads) {
            if (!thread.mReader.readyToRecord()) {
                return false
            }
        }
        return true
    }

    override fun killQuietly() {
        for (thread in mReaderThreads) {
            thread.mKilled = true
        }
        // Kill all threads in the background
        ThreadUtils.postOnBackgroundThread {
            for (thread in mReaderThreads) {
                thread.mReader.killQuietly()
            }
            mQueue.offer(DUMMY_NULL)
        }
    }

    override fun getProcesses(): List<Process> {
        val result: MutableList<Process> = ArrayList()
        for (thread in mReaderThreads) {
            result.addAll(thread.mReader.getProcesses())
        }
        return result
    }

    private inner class ReaderThread(@LogcatHelper.LogBufferId logBuffer: Int, lastLine: String?) : Thread() {
        val mReader: SingleLogcatReader
        @Volatile
        var mKilled: Boolean = false

        init {
            mReader = SingleLogcatReader(recordingMode, logBuffer, lastLine)
        }

        override fun run() {
            var line: String?
            try {
                while (!mKilled && mReader.readLine().also { line = it } != null && !mKilled) {
                    mQueue.put(line)
                }
            } catch (e: IOException) {
                Log.e(TAG, e)
            } catch (e: InterruptedException) {
                Log.e(TAG, e)
            }
            Log.w(TAG, "Thread died")
        }
    }

    companion object {
        val TAG: String = MultipleLogcatReader::class.java.simpleName
        private const val DUMMY_NULL = "" // Stop marker
    }
}
