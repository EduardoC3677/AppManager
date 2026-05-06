// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.crypto

import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.AnyThread
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.core.app.NotificationCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.content.ContextCompat
import io.github.muntashirakon.AppManager.BuildConfig
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.backup.CryptoUtils
import io.github.muntashirakon.AppManager.intercept.IntentCompat
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.settings.Prefs
import io.github.muntashirakon.AppManager.utils.NotificationUtils
import io.github.muntashirakon.io.Path
import org.openintents.openpgp.IOpenPgpService2
import org.openintents.openpgp.OpenPgpError
import org.openintents.openpgp.util.OpenPgpApi
import org.openintents.openpgp.util.OpenPgpServiceConnection
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class OpenPGPCrypto @AnyThread constructor(private val mContext: Context, keyIdsStr: String) : Crypto {
    private var mService: OpenPgpServiceConnection? = null
    private var mSuccessFlag = false
    private var mErrorFlag = false
    private var mInputFiles: Array<Path> = emptyArray()
    private var mOutputFiles: Array<Path> = emptyArray()
    private var mIs: InputStream? = null
    private var mOs: OutputStream? = null
    private val mKeyIds: LongArray
    private val mProvider: String = Prefs.Encryption.getOpenPgpProvider()
    private var mLastIntent: Intent? = null
    private val mHandler: Handler = Handler(Looper.getMainLooper())
    private var mIsFileMode = false

    private val mReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action ?: return
            when (action) {
                ACTION_OPEN_PGP_INTERACTION_BEGIN -> {}
                ACTION_OPEN_PGP_INTERACTION_END -> {
                    Thread {
                        try {
                            doAction(mLastIntent!!, false)
                        } catch (e: IOException) {
                            e.printStackTrace()
                        }
                    }.start()
                }
            }
        }
    }

    init {
        try {
            val keyIds = keyIdsStr.split(",").toTypedArray()
            mKeyIds = LongArray(keyIds.size)
            for (i in keyIds.indices) mKeyIds[i] = keyIds[i].toLong()
        } catch (e: NumberFormatException) {
            throw CryptoException(e)
        }
        bind()
    }

    override val modeName: String
        get() = CryptoUtils.MODE_OPEN_PGP

    override fun close() {
        mService?.unbindFromService()
        mContext.unregisterReceiver(mReceiver)
    }

    @WorkerThread
    @Throws(IOException::class)
    override fun decrypt(inputFiles: Array<Path>, outputFiles: Array<Path>) {
        val intent = Intent(OpenPgpApi.ACTION_DECRYPT_VERIFY)
        handleFiles(intent, inputFiles, outputFiles)
    }

    @Throws(IOException::class)
    override fun decrypt(encryptedStream: InputStream, unencryptedStream: OutputStream) {
        val intent = Intent(OpenPgpApi.ACTION_DECRYPT_VERIFY)
        handleStreams(intent, encryptedStream, unencryptedStream)
    }

    @WorkerThread
    @Throws(IOException::class)
    override fun encrypt(inputFiles: Array<Path>, outputFiles: Array<Path>) {
        val intent = Intent(OpenPgpApi.ACTION_ENCRYPT).apply { putExtra(OpenPgpApi.EXTRA_KEY_IDS, mKeyIds) }
        handleFiles(intent, inputFiles, outputFiles)
    }

    @Throws(IOException::class)
    override fun encrypt(unencryptedStream: InputStream, encryptedStream: OutputStream) {
        val intent = Intent(OpenPgpApi.ACTION_ENCRYPT).apply { putExtra(OpenPgpApi.EXTRA_KEY_IDS, mKeyIds) }
        handleStreams(intent, unencryptedStream, encryptedStream)
    }

    @WorkerThread
    @Throws(IOException::class)
    private fun handleFiles(intent: Intent, inputFiles: Array<Path>, outputFiles: Array<Path>) {
        mIsFileMode = true
        waitForServiceBound()
        mIs = null
        mOs = null
        mInputFiles = inputFiles
        mOutputFiles = outputFiles
        mLastIntent = intent
        doAction(intent, true)
    }

    @WorkerThread
    @Throws(IOException::class)
    private fun handleStreams(intent: Intent, inputStream: InputStream, outputStream: OutputStream) {
        mIsFileMode = false
        waitForServiceBound()
        mIs = inputStream
        mOs = outputStream
        mInputFiles = emptyArray()
        mOutputFiles = emptyArray()
        mLastIntent = intent
        doAction(intent, true)
    }

    @WorkerThread
    @Throws(IOException::class)
    private fun doAction(intent: Intent, waitForResult: Boolean) {
        if (mIsFileMode) doActionForFiles(intent, waitForResult)
        else doActionForStream(intent, waitForResult)
    }

    @WorkerThread
    @Throws(IOException::class)
    private fun doActionForFiles(intent: Intent, waitForResult: Boolean) {
        mErrorFlag = false
        if (mInputFiles.isEmpty()) {
            Log.d(TAG, "No files to de/encrypt")
            return
        }
        if (mInputFiles.size != mOutputFiles.size) {
            throw IOException("The number of input and output files are not the same.")
        }
        for (i in mInputFiles.indices) {
            val inputPath = mInputFiles[i]
            val outputPath = mOutputFiles[i]
            Log.i(TAG, "Input: $inputPath
Output: $outputPath")
            val `is` = inputPath.openInputStream()
            val os = outputPath.openOutputStream()
            val api = OpenPgpApi(mContext, mService!!.service)
            val result = api.executeApi(intent, `is`, os)
            mHandler.post { handleResult(result) }
            if (waitForResult) waitForResultInternal()
            if (mErrorFlag) {
                outputPath.delete()
                throw IOException("Error occurred during en/decryption process")
            }
        }
    }

    @WorkerThread
    @Throws(IOException::class)
    private fun doActionForStream(intent: Intent, waitForResult: Boolean) {
        mErrorFlag = false
        val api = OpenPgpApi(mContext, mService!!.service)
        val result = api.executeApi(intent, mIs, mOs)
        mHandler.post { handleResult(result) }
        if (waitForResult) waitForResultInternal()
        if (mErrorFlag) throw IOException("Error occurred during en/decryption process")
    }

    private fun bind() {
        mService = OpenPgpServiceConnection(mContext, mProvider, object : OpenPgpServiceConnection.OnBound {
            override fun onBound(service: IOpenPgpService2) { Log.i(OpenPgpApi.TAG, "Service bound.") }
            override fun onError(e: Exception) { Log.e(OpenPgpApi.TAG, "Exception on binding.", e) }
        })
        mService!!.bindToService()
        val filter = IntentFilter(ACTION_OPEN_PGP_INTERACTION_BEGIN).apply { addAction(ACTION_OPEN_PGP_INTERACTION_END) }
        ContextCompat.registerReceiver(mContext, mReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    @WorkerThread
    @Throws(IOException::class)
    private fun waitForServiceBound() {
        var i = 0
        while (mService!!.service == null) {
            if (i % 20 == 0) Log.i(TAG, "Waiting for openpgp-api service to be bound")
            SystemClock.sleep(100)
            if (i > 1000) break
            i++
        }
        if (mService!!.service == null) throw IOException("OpenPGPService could not be bound.")
    }

    @WorkerThread
    private fun waitForResultInternal() {
        var i = 0
        while (!mSuccessFlag && !mErrorFlag) {
            if (i % 200 == 0) Log.i(TAG, "Waiting for user interaction")
            SystemClock.sleep(100)
            if (i > 1000) break
            i++
        }
    }

    @UiThread
    private fun handleResult(result: Intent) {
        mSuccessFlag = false
        when (result.getIntExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR)) {
            OpenPgpApi.RESULT_CODE_SUCCESS -> {
                Log.i(TAG, "en/decryption successful.")
                mSuccessFlag = true
            }
            OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED -> {
                Log.i(TAG, "User interaction required. Sending intent...")
                val broadcastIntent = Intent(ACTION_OPEN_PGP_INTERACTION_BEGIN).apply { `package` = mContext.packageName }
                mContext.sendBroadcast(broadcastIntent)
                val intent = Intent(mContext, OpenPGPCryptoActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    putExtra(OpenPgpApi.RESULT_INTENT, IntentCompat.getParcelableExtra(result, OpenPgpApi.RESULT_INTENT, PendingIntent::class.java))
                }
                val openPGP = "Open PGP"\nval builder = NotificationUtils.getHighPriorityNotificationBuilder(mContext)
                    .setAutoCancel(true)
                    .setDefaults(Notification.DEFAULT_ALL)
                    .setWhen(System.currentTimeMillis())
                    .setSmallIcon(R.drawable.ic_default_notification)
                    .setTicker(openPGP)
                    .setContentTitle(openPGP)
                    .setSubText(openPGP)
                    .setContentText(mContext.getString(R.string.allow_open_pgp_operation))
                    .setContentIntent(PendingIntentCompat.getActivity(mContext, 0, intent, PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_UPDATE_CURRENT, false))
                NotificationUtils.displayHighPriorityNotification(mContext, builder.build())
            }
            OpenPgpApi.RESULT_CODE_ERROR -> {
                mErrorFlag = true
                val error = IntentCompat.getParcelableExtra(result, OpenPgpApi.RESULT_ERROR, OpenPgpError::class.java)
                if (error != null) Log.e(TAG, "handleResult: (%d) %s", error.errorId, error.message)
                else Log.e(TAG, "handleResult: Error occurred during en/decryption process")
            }
        }
    }

    companion object {
        const val TAG = "OpenPGPCrypto"\nconst val ACTION_OPEN_PGP_INTERACTION_BEGIN = "${BuildConfig.APPLICATION_ID}.action.OPEN_PGP_INTERACTION_BEGIN"\nconst val ACTION_OPEN_PGP_INTERACTION_END = "${BuildConfig.APPLICATION_ID}.action.OPEN_PGP_INTERACTION_END"\nconst val GPG_EXT = ".gpg"
    }
}
