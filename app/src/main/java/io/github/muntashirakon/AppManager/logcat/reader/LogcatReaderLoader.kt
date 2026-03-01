// SPDX-License-Identifier: WTFPL AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat.reader

import android.os.Parcel
import android.os.Parcelable
import io.github.muntashirakon.AppManager.logcat.helper.LogcatHelper
import io.github.muntashirakon.AppManager.logcat.helper.PreferenceHelper
import io.github.muntashirakon.util.ParcelUtils
import java.io.IOException
import java.util.*

/**
 * Copyright 2012 Nolan Lawson
 * Copyright 2021 Muntashir Al-Islam
 */
class LogcatReaderLoader : Parcelable {
    private val mLastLines: Map<Int, String?>
    private val mRecordingMode: Boolean
    private val mMultipleBuffers: Boolean

    private constructor(buffers: List<Int>, recordingMode: Boolean) {
        mRecordingMode = recordingMode
        mMultipleBuffers = buffers.size > 1
        mLastLines = HashMap<Int, String?>()
        for (buffer in buffers) {
            // No need to grab the last line if this isn't recording mode
            val lastLine = if (recordingMode) LogcatHelper.getLastLogLine(buffer) else null
            (mLastLines as HashMap)[buffer] = lastLine
        }
    }

    @Throws(IOException::class)
    fun loadReader(): LogcatReader {
        return if (!mMultipleBuffers) {
            // single reader
            val buffer = mLastLines.keys.iterator().next()
            val lastLine = mLastLines.values.iterator().next()
            SingleLogcatReader(mRecordingMode, buffer, lastLine)
        } else {
            // multiple reader
            MultipleLogcatReader(mRecordingMode, mLastLines)
        }
    }

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(if (mRecordingMode) 1 else 0)
        dest.writeInt(if (mMultipleBuffers) 1 else 0)
        ParcelUtils.writeMap(mLastLines, dest)
    }

    private constructor(`in`: Parcel) {
        mRecordingMode = `in`.readInt() == 1
        mMultipleBuffers = `in`.readInt() == 1
        mLastLines = ParcelUtils.readMap(`in`, Int::class.java.classLoader, String::class.java.classLoader)!!
    }

    companion object {
        @JvmStatic
        fun create(recordingMode: Boolean): LogcatReaderLoader {
            val buffers = PreferenceHelper.getBuffers()
            return LogcatReaderLoader(buffers, recordingMode)
        }

        @JvmField
        val CREATOR: Parcelable.Creator<LogcatReaderLoader> = object : Parcelable.Creator<LogcatReaderLoader> {
            override fun createFromParcel(`in`: Parcel): LogcatReaderLoader = LogcatReaderLoader(`in`)
            override fun newArray(size: Int): Array<LogcatReaderLoader?> = arrayOfNulls(size)
        }
    }
}
