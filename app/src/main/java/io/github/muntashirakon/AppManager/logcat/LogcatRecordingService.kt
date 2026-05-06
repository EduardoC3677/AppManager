// SPDX-License-Identifier: WTFPL AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.muntashirakon.AppManager.BuildConfig
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.intercept.IntentCompat
import io.github.muntashirakon.AppManager.logcat.helper.SaveLogHelper
import io.github.muntashirakon.AppManager.logcat.helper.ServiceHelper
import io.github.muntashirakon.AppManager.logcat.helper.WidgetHelper
import io.github.muntashirakon.AppManager.logcat.reader.LogcatReaderLoader
import io.github.muntashirakon.AppManager.logcat.struct.LogLine
import io.github.muntashirakon.AppManager.logcat.struct.SearchCriteria
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.progress.NotificationProgressHandler
import io.github.muntashirakon.AppManager.progress.QueuedProgressHandler
import io.github.muntashirakon.AppManager.settings.Prefs
import io.github.muntashirakon.AppManager.types.ForegroundService
import io.github.muntashirakon.AppManager.utils.CpuUtils
import io.github.muntashirakon.AppManager.utils.NotificationUtils
import io.github.muntashirakon.AppManager.utils.ThreadUtils
import io.github.muntashirakon.AppManager.utils.UIUtils
import java.io.IOException
import java.util.*
import java.util.regex.Pattern

/**
 * Reads logs.
 */
// Copyright 2012 Nolan Lawson
// Copyright 2021 Muntashir Al-Islam
class LogcatRecordingService : ForegroundService(TAG) {
    private var mLogcatReaderLoader: LogcatReaderLoader? = null
    private var mLogcatReader: LogcatReader? = null
    private var mFilterPattern: Pattern? = null
    private var mPowerManagerWakeLock: PowerManager.WakeLock? = null
    private var mWakeLockReleased = false
    private var mWriteLogToFile: Boolean = false
    private var mLogFilename: String? = null
    private var mServiceKilled: Boolean = false
    private var mQueuedProgressHandler: QueuedProgressHandler? = null

    private val mStopRecordingBroadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_STOP_RECORDING == intent.action) {
                Log.i(TAG, "Received stop recording intent")
                stopSelf()
            }
        }
    }

    override fun onHandleIntent(intent: Intent?) {
        if (intent == null) return
        val filename = intent.getStringExtra(EXTRA_FILENAME)
        mLogcatReaderLoader = IntentCompat.getWrappedParcelableExtra(intent, EXTRA_LOADER, LogcatReaderLoader::class.java)
        val query = intent.getStringExtra(EXTRA_QUERY_FILTER)
        val level = intent.getIntExtra(EXTRA_LEVEL, Prefs.LogViewer.getLogLevel())

        mWriteLogToFile = filename != null
        mLogFilename = filename

        val maxLogs = Prefs.LogViewer.getDisplayLimit()
        val writeInterval = Prefs.LogViewer.getLogWriteInterval()

        val notificationManager = ContextCompat.getSystemService(this, NotificationManagerCompat::class.java)!!
        NotificationUtils.createNotificationChannel(this, CHANNEL_ID, getString(R.string.logcat_recorder), NotificationManagerCompat.IMPORTANCE_LOW)

        val notificationInfo = NotificationProgressHandler.NotificationInfo(
            NotificationProgressHandler.NotificationManagerInfo(this, CHANNEL_ID, NOTIFICATION_ID)
        )
            .setOperationName(getString(R.string.logcat_recorder))
            .setTitle(getString(R.string.recording))
            .setSmallIcon(R.drawable.ic_logcat_recorder)
            .setOngoing(true)
            .setAction(createStopPendingIntent(), getString(R.string.stop), R.drawable.ic_stop)

        mQueuedProgressHandler = QueuedProgressHandler(this, notificationInfo.managerInfo)
        mQueuedProgressHandler!!.onProgressStart(maxLogs, 0, notificationInfo)

        registerReceiver(mStopRecordingBroadcastReceiver, IntentFilter(ACTION_STOP_RECORDING))

        mPowerManagerWakeLock = CpuUtils.getWakeLock(this, TAG)
        mPowerManagerWakeLock!!.acquire()

        mFilterPattern = Pattern.compile(Prefs.LogViewer.getFilterPattern())
        var searchCriteria: SearchCriteria? = null
        if (query != null) searchCriteria = SearchCriteria(query)

        val logLines = LinkedList<String>()
        val stopWatch = Utils.StopWatch("LogcatRecordingService")
        var lastLineCount = 0

        try {
            mLogcatReader = mLogcatReaderLoader!!.loadReader()
        } catch (e: IOException) {
            stopSelf(e.message)
            return
        }

        WidgetHelper.updateWidgets(this)

        var line: String?
        try {
            while (mLogcatReader!!.readLine().also { line = it } != null) {
                if (ThreadUtils.isInterrupted() || mServiceKilled) break
                val logLine = LogLine.newLogLine(line!!, false, mFilterPattern)
                if (logLine == null || logLine.logLevel > level || (searchCriteria != null && !searchCriteria.matches(logLine))) continue

                logLines.add(line!!)

                if (mWriteLogToFile && logLines.size >= writeInterval) {
                    SaveLogHelper.appendToFile(logLines, filename)
                    mQueuedProgressHandler!!.postUpdate(mQueuedProgressHandler!!.lastProgress + logLines.size)
                    logLines.clear()
                    lastLineCount = 0
                } else if (!mWriteLogToFile) {
                    mQueuedProgressHandler!!.postUpdate(mQueuedProgressHandler!!.lastProgress + (logLines.size - lastLineCount))
                    lastLineCount = logLines.size
                }

                while (logLines.size > maxLogs) logLines.removeFirst()
            }
        } catch (e: IOException) {
            Log.e(TAG, e)
            stopSelf(e.message)
            return
        } finally {
            if (mWriteLogToFile && logLines.isNotEmpty()) SaveLogHelper.appendToFile(logLines, filename)
            stopSelf(null)
        }
    }

    private fun createStopPendingIntent(): PendingIntent {
        val stopRecording = Intent(ACTION_STOP_RECORDING)
        return PendingIntentCompat.getBroadcast(this, 0, stopRecording, PendingIntent.FLAG_UPDATE_CURRENT, false)
    }

    private fun stopSelf(errorMessage: String?) {
        mServiceKilled = true

        mLogcatReader?.killQuietly()
        if (!mWakeLockReleased) {
            mPowerManagerWakeLock?.release()
            mWakeLockReleased = true
        }
        unregisterReceiver(mStopRecordingBroadcastReceiver)

        SaveLogHelper.deleteLogIfExists(mLogFilename)

        mQueuedProgressHandler?.onResult(errorMessage)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        WidgetHelper.updateWidgets(this)

        stopSelf()
    }

    companion object {
        val TAG: String = LogcatRecordingService::class.java.simpleName
        const val URI_SCHEME = "am_logcat_recording_service"\nconst val EXTRA_FILENAME = "filename"\nconst val EXTRA_LOADER = "loader"\nconst val EXTRA_QUERY_FILTER = "filter"\nconst val EXTRA_LEVEL = "level"\nprivate const val ACTION_STOP_RECORDING = BuildConfig.APPLICATION_ID + ".action.STOP_RECORDING"\nconst val CHANNEL_ID = BuildConfig.APPLICATION_ID + ".channel.LOGCAT_RECORDER"
        const val NOTIFICATION_ID = 1

        @JvmStatic
        fun getServiceIntent(context: Context): Intent {
            return Intent(context, LogcatRecordingService::class.java)
        }

        @JvmStatic
        fun getServiceIntent(context: Context, filename: String?, loader: LogcatReaderLoader?, query: String?, level: Int): Intent {
            return getServiceIntent(context).apply {
                data = Uri.fromParts(URI_SCHEME, filename, null)
                putExtra(EXTRA_FILENAME, filename)
                IntentCompat.putWrappedParcelableExtra(this, EXTRA_LOADER, loader)
                putExtra(EXTRA_QUERY_FILTER, query)
                putExtra(EXTRA_LEVEL, level)
            }
        }

        @JvmStatic
        fun startService(context: Context, filename: String?, loader: LogcatReaderLoader?, query: String?, level: Int): Boolean {
            if (ServiceHelper.isServiceRunning(context)) {
                UIUtils.displayShortToast(R.string.logcat_recorder_already_running)
                return false
            }
            ContextCompat.startForegroundService(context, getServiceIntent(context, filename, loader, query, level))
            return true
        }
    }
}
