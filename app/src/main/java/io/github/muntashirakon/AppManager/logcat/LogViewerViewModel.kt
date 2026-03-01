// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat

import android.app.Application
import android.net.Uri
import androidx.annotation.AnyThread
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.db.AppsDb
import io.github.muntashirakon.AppManager.db.entity.LogFilter
import io.github.muntashirakon.AppManager.logcat.helper.BuildHelper
import io.github.muntashirakon.AppManager.logcat.helper.SaveLogHelper
import io.github.muntashirakon.AppManager.logcat.reader.LogcatReader
import io.github.muntashirakon.AppManager.logcat.reader.LogcatReaderLoader
import io.github.muntashirakon.AppManager.logcat.struct.LogLine
import io.github.muntashirakon.AppManager.logcat.struct.SavedLog
import io.github.muntashirakon.AppManager.logcat.struct.SendLogDetails
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.runner.Runner
import io.github.muntashirakon.AppManager.self.filecache.FileCache
import io.github.muntashirakon.AppManager.settings.Prefs
import io.github.muntashirakon.AppManager.utils.AppExecutor
import io.github.muntashirakon.AppManager.utils.ThreadUtils
import io.github.muntashirakon.io.IoUtils
import io.github.muntashirakon.io.Path
import io.github.muntashirakon.io.Paths
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.lang.ref.WeakReference
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.regex.Pattern
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// Copyright 2022 Muntashir Al-Islam
class LogViewerViewModel(application: Application) : AndroidViewModel(application) {
    interface LogLinesAvailableInterface {
        @UiThread
        fun onNewLogsAvailable(logLines: List<LogLine>)
    }

    private val mLock = Any()

    @Volatile
    private var mPaused: Boolean = false
    @Volatile
    private var mKilled = true
    @Volatile
    var isCollapsedMode: Boolean = false
        private set
    @Volatile
    var logLevel: Int = 0
        private set
    @Volatile
    private var mReader: LogcatReader? = null

    private val mFilterPattern: Pattern
    private val mExpandLogsLiveData = MutableLiveData<Boolean>()
    private val mLoggingFinishedLiveData = MutableLiveData<Boolean>()
    private val mLoadingProgressLiveData = MutableLiveData<Int>()
    private val mTruncatedLinesLiveData = MutableLiveData<Int>()
    private val mLogLevelLiveData = MutableLiveData<Int>()
    private val mLogFiltersLiveData = MutableLiveData<List<LogFilter>>()
    private val mLogSavedLiveData = MutableLiveData<Path>()
    private val mLogToBeSentLiveData = MutableLiveData<SendLogDetails>()
    private val mExecutor: ExecutorService = AppExecutor.getExecutor()

    init {
        mFilterPattern = Pattern.compile(Prefs.LogViewer.getFilterPattern())
    }

    override fun onCleared() {
        killLogcatReaderInternal()
        super.onCleared()
    }

    fun observeLoggingFinished(): LiveData<Boolean> = mLoggingFinishedLiveData
    fun observeLoadingProgress(): LiveData<Int> = mLoadingProgressLiveData
    fun observeTruncatedLines(): LiveData<Int> = mTruncatedLinesLiveData
    fun getLogFilters(): LiveData<List<LogFilter>> = mLogFiltersLiveData
    fun observeLogSaved(): LiveData<Path> = mLogSavedLiveData
    fun observeLogLevelLiveData(): LiveData<Int> = mLogLevelLiveData
    fun getLogsToBeSent(): LiveData<SendLogDetails> = mLogToBeSentLiveData
    fun getExpandLogsLiveData(): LiveData<Boolean> = mExpandLogsLiveData

    @AnyThread
    fun startLogcat(logLinesAvailableInterface: WeakReference<LogLinesAvailableInterface>?) {
        mExecutor.submit {
            mKilled = false
            try {
                mReader = LogcatReaderLoader.create(true).loadReader()

                val maxLines = Prefs.LogViewer.getDisplayLimit()

                var line: String?
                val initialLines = LinkedList<LogLine>()
                while (mReader!!.readLine().also { line = it } != null && !ThreadUtils.isInterrupted()) {
                    if (mPaused) {
                        synchronized(mLock) {
                            if (mPaused) mLock.wait()
                        }
                    }
                    val logLine = LogLine.newLogLine(line!!, !isCollapsedMode, mFilterPattern)
                    if (logLine == null) {
                        if (mReader!!.readyToRecord()) {
                            // Logcat is ready
                        }
                    } else if (!mReader!!.readyToRecord()) {
                        initialLines.add(logLine)
                        if (initialLines.size > maxLines) {
                            initialLines.removeFirst()
                        }
                    } else if (initialLines.isNotEmpty()) {
                        initialLines.add(logLine)
                        sendNewLogs(initialLines, logLinesAvailableInterface)
                        initialLines.clear()
                    } else {
                        sendNewLogs(Collections.singletonList(logLine), logLinesAvailableInterface)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, e)
            } finally {
                logLinesAvailableInterface?.clear()
                killLogcatReaderInternal()
            }
            mLoggingFinishedLiveData.postValue(true)
        }
    }

    @AnyThread
    fun restartLogcat() {
        mExecutor.submit {
            synchronized(mLock) {
                mPaused = true
                try {
                    mReader = LogcatReaderLoader.create(true).loadReader()
                } catch (e: Exception) {
                    Log.e(TAG, e)
                } finally {
                    mPaused = false
                    mLock.notify()
                }
            }
        }
    }

    private fun sendNewLogs(logLines: List<LogLine>, logLinesAvailableInterface: WeakReference<LogLinesAvailableInterface>?) {
        logLinesAvailableInterface?.get()?.let { i ->
            val logLines1 = ArrayList(logLines)
            ThreadUtils.postOnMainThread { i.onNewLogsAvailable(logLines1) }
        }
    }

    @AnyThread
    fun pauseLogcat() {
        mExecutor.submit {
            synchronized(mLock) { mPaused = true }
        }
    }

    @AnyThread
    fun resumeLogcat() {
        mExecutor.submit {
            synchronized(mLock) {
                mPaused = false
                mLock.notify()
            }
        }
    }

    fun isLogcatPaused(): Boolean = mPaused
    fun isLogcatKilled(): Boolean = mKilled

    fun setCollapsedMode(collapsedMode: Boolean) {
        isCollapsedMode = collapsedMode
        mExpandLogsLiveData.postValue(collapsedMode)
    }

    fun setLogLevel(logLevel: Int) {
        this.logLevel = logLevel
        mLogLevelLiveData.postValue(this.logLevel)
    }

    @AnyThread
    fun killLogcatReader() {
        mExecutor.submit { killLogcatReaderInternal() }
    }

    @WorkerThread
    private fun killLogcatReaderInternal() {
        if (!mKilled) {
            synchronized(mLock) {
                if (!mKilled && mReader != null) {
                    mReader!!.killQuietly()
                    mKilled = true
                }
            }
        }
    }

    @AnyThread
    fun openLogFile(filename: String, logLinesAvailableInterface: WeakReference<LogLinesAvailableInterface>?) {
        mExecutor.submit {
            val maxLines = Prefs.LogViewer.getDisplayLimit()
            val savedLog = SaveLogHelper.openSavedLog(filename)
            val lines = savedLog.logLines
            val logLines = mutableListOf<LogLine>()
            lines.forEachIndexed { lineNumber, line ->
                LogLine.newLogLine(line, !isCollapsedMode, mFilterPattern)?.let { logLines.add(it) }
                mLoadingProgressLiveData.postValue(lineNumber * 100 / lines.size)
            }
            sendNewLogs(logLines, logLinesAvailableInterface)
            if (savedLog.isTruncated) {
                mTruncatedLinesLiveData.postValue(maxLines)
            }
        }
    }

    @AnyThread
    fun loadFilters() {
        mExecutor.submit {
            val filters = AppsDb().logFilterDao().all
            Collections.sort(filters)
            mLogFiltersLiveData.postValue(filters)
        }
    }

    @AnyThread
    fun saveLogs(filename: String, logLines: List<String>) {
        mExecutor.submit {
            SaveLogHelper.deleteLogIfExists(filename)
            mLogSavedLiveData.postValue(SaveLogHelper.saveLog(logLines, filename))
        }
    }

    @AnyThread
    fun saveLogs(path: Path, sendLogDetails: SendLogDetails) {
        mExecutor.submit {
            if (sendLogDetails.attachmentType == null || sendLogDetails.attachment == null) {
                mLogSavedLiveData.postValue(null)
                return@submit
            }
            try {
                path.openOutputStream().use { output ->
                    sendLogDetails.attachment.openInputStream().use { input ->
                        IoUtils.copy(input, output)
                    }
                }
                mLogSavedLiveData.postValue(path)
            } catch (e: IOException) {
                mLogSavedLiveData.postValue(null)
                e.printStackTrace()
            }
        }
    }

    @AnyThread
    fun prepareLogsToBeSent(includeDeviceInfo: Boolean, includeDmesg: Boolean, logLines: Collection<String>) {
        mExecutor.submit {
            val fileCache = FileCache.getGlobalFileCache()
            var tempDeviceInfo: Path? = null
            var tempDmesg: Path? = null
            var tempLog: Path? = null
            var tempZip: Path? = null
            val attachmentType = "application/zip"

            try {
                if (includeDeviceInfo) {
                    tempDeviceInfo = SaveLogHelper.saveTemporaryFile("txt", BuildHelper.getBuildInformationAsString(), null)
                }
                if (includeDmesg) {
                    val dmesg = Runner.runCommand(arrayOf("dmesg")).output
                    tempDmesg = SaveLogHelper.saveTemporaryFile("txt", dmesg, null)
                }
                tempLog = SaveLogHelper.saveTemporaryFile("log", null, logLines)
                tempZip = Paths.get(fileCache.createCachedFile("zip"))

                ZipOutputStream(BufferedOutputStream(tempZip.openOutputStream())).use { zos ->
                    val filesToZip = mutableListOf<Path>()
                    tempDeviceInfo?.let { filesToZip.add(it) }
                    tempDmesg?.let { filesToZip.add(it) }
                    tempLog?.let { filesToZip.add(it) }

                    for (file in filesToZip) {
                        zos.putNextEntry(ZipEntry(file.getName()))
                        file.openInputStream().use { inputStream ->
                            IoUtils.copy(inputStream, zos)
                        }
                        zos.closeEntry()
                    }
                }
                mLogToBeSentLiveData.postValue(SendLogDetails(tempZip, attachmentType))
            } catch (e: IOException) {
                mLogToBeSentLiveData.postValue(null)
                e.printStackTrace()
            } finally {
                tempDeviceInfo?.delete()
                tempDmesg?.delete()
                tempLog?.delete()
            }
        }
    }

    fun getLogLevelTitle(logLevel: Int): String {
        val logLevelNames = getApplication<Application>().resources.getStringArray(R.array.log_levels)
        return logLevelNames[LogLine.convertLogLevelToChar(logLevel).code - 'A'.code]
    }

    companion object {
        val TAG: String = LogViewerViewModel::class.java.simpleName
    }
}
