// SPDX-License-Identifier: WTFPL AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat.helper

import android.content.Context
import android.text.SpannableStringBuilder
import io.github.muntashirakon.AppManager.logcat.struct.SavedLog
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.self.filecache.FileCache
import io.github.muntashirakon.AppManager.settings.Prefs
import io.github.muntashirakon.AppManager.utils.UIUtils
import io.github.muntashirakon.io.Path
import io.github.muntashirakon.io.Paths
import java.io.*
import java.text.DateFormat
import java.util.*

/**
 * Copyright 2012 Nolan Lawson
 */
object SaveLogHelper {
    @JvmField
    val TAG: String = SaveLogHelper::class.java.simpleName

    const val DEVICE_INFO_FILENAME = "device_info.txt"
    const val LOG_FILENAME = "logcat.am.log"
    const val DMESG_FILENAME = "dmesg.txt"
    const val SAVED_LOGS_DIR = "saved_logs"
    private const val BUFFER = 0x1000 // 4K

    @JvmStatic
    fun saveTemporaryFile(extension: String, text: CharSequence?, lines: Collection<String>?): Path? {
        return try {
            val tempFile = Paths.get(FileCache.getGlobalFileCache().createCachedFile(extension))
            PrintStream(BufferedOutputStream(tempFile.openOutputStream(), BUFFER)).use { out ->
                if (text != null) {
                    out.print(text)
                } else if (lines != null) {
                    for (line in lines) {
                        out.println(line)
                    }
                }
                Log.d(TAG, "Saved temp file: $tempFile")
                tempFile
            }
        } catch (e: IOException) {
            Log.e(TAG, e)
            null
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun getFile(filename: String): Path {
        return savedLogsDirectory.findFile(filename)
    }

    @JvmStatic
    fun deleteLogIfExists(filename: String?) {
        if (filename == null) return
        try {
            getFile(filename).delete()
        } catch (ignore: IOException) {
        }
    }

    @JvmStatic
    fun getFormattedFilenames(context: Context, files: List<Path>): Array<CharSequence> {
        val fileNames = arrayOfNulls<CharSequence>(files.size)
        val dateFormat = DateFormat.getDateTimeInstance()
        for (i in files.indices) {
            fileNames[i] = SpannableStringBuilder(files[i].getName())
                .append("
").append(
                    UIUtils.getSmallerText(
                        UIUtils.getSecondaryText(
                            context,
                            dateFormat.format(Date(files[i].lastModified()))
                        )
                    )
                )
        }
        return fileNames.requireNoNulls()
    }

    @JvmStatic
    fun getLogFiles(): List<Path> {
        return try {
            val filesArray = savedLogsDirectory.listFiles()
            val files = mutableListOf(*filesArray)
            files.sortWith { o1, o2 -> o2.lastModified().compareTo(o1.lastModified()) }
            files
        } catch (e: IOException) {
            emptyList()
        }
    }

    @JvmStatic
    @get:Throws(IOException::class)
    private val savedLogsDirectory: Path
        get() = Prefs.Storage.getAppManagerDirectory().findOrCreateDirectory(SAVED_LOGS_DIR)

    @JvmStatic
    @Throws(IOException::class)
    fun openSavedLog(filename: String): SavedLog {
        val logDir = getFile(filename)
        val logFile = logDir.findFile(LOG_FILENAME)
        var deviceInfo: String? = null
        try {
            deviceInfo = logDir.findFile(DEVICE_INFO_FILENAME).getContentAsString()
        } catch (ignore: IOException) {
        }
        return SavedLog(logFile, deviceInfo)
    }
}
