// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Message
import android.os.Parcel
import android.os.Parcelable
import io.github.muntashirakon.AppManager.server.common.*
import java.io.Closeable
import java.io.IOException

// Copyright 2017 Zheng Li
class ServerHandler(
    private val mLifecycleAgent: LifecycleAgent
) : DataTransmission.OnReceiveCallback, Closeable {

    private val mConfigParams: ConfigParams = mLifecycleAgent.configParams
    private val mServer: Server
    private val mRunInBackground: Boolean

    private var mHandler: Handler? = null
    private val mIsDead = false

    init {
        // Set params
        FLog.log("Config params: $mConfigParams")
        val path = mConfigParams.path
        var port = -1
        try {
            if (path != null) port = path.toInt()
        } catch (ignore: Exception) {
        }
        val token = mConfigParams.token ?: throw IOException("Token is not found.")
        mRunInBackground = mConfigParams.isRunInBackground

        // Set server
        mServer = if (port == -1) {
            Server(path!!, token, mLifecycleAgent, this)
        } else {
            Server(port, token, mLifecycleAgent, this)
        }
        mServer.mRunInBackground = mRunInBackground

        // If run in background not requested, stop server on timeout
        if (!mRunInBackground) {
            val handlerThread = HandlerThread("am_server_watcher")
            handlerThread.start()
            mHandler = object : Handler(handlerThread.looper) {
                override fun handleMessage(message: Message) {
                    super.handleMessage(message)
                    if (message.what == MSG_TIMEOUT) {
                        close()
                    }
                }
            }
            mHandler?.sendEmptyMessageDelayed(MSG_TIMEOUT, DEFAULT_TIMEOUT)
        }
    }

    @Throws(IOException::class, RuntimeException::class)
    fun start() {
        mServer.run()
    }

    override fun close() {
        FLog.log("ServerHandler: Destroying...")
        try {
            if (!mRunInBackground && mHandler != null) {
                mHandler?.removeCallbacksAndMessages(null)
                mHandler?.removeMessages(MSG_TIMEOUT)
                mHandler?.looper?.quit()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            FLog.log(e)
        }
        try {
            // mIsDead = true // This field is not used after setting it
            mServer.close()
        } catch (e: Exception) {
            e.printStackTrace()
            FLog.log(e)
        }
    }

    private fun sendOpResult(result: Parcelable) {
        try {
            mServer.sendResult(ParcelableUtil.marshall(result))
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun onMessage(bytes: ByteArray) {
        mHandler?.removeCallbacksAndMessages(null)
        mHandler?.removeMessages(MSG_TIMEOUT)

        if (!mIsDead) { // mIsDead is always false, maybe it's dead code or intended for later
            if (!mRunInBackground && mHandler != null) {
                mHandler?.sendEmptyMessageDelayed(MSG_TIMEOUT, BG_TIMEOUT)
            }
            LifecycleAgent.sServerInfo.rxBytes += bytes.size.toLong()
            var result: CallerResult? = null
            try {
                val baseCaller = ParcelableUtil.unmarshall(bytes, BaseCaller.CREATOR)
                when (baseCaller?.type) {
                    BaseCaller.TYPE_CLOSE -> {
                        close()
                        return
                    }
                    BaseCaller.TYPE_SHELL -> {
                        val shellCaller = ParcelableUtil.unmarshall(baseCaller.rawBytes, ShellCaller.CREATOR)
                        val shell = Shell.getShell("")
                        val shellResult = shell.exec(shellCaller?.getCommand())
                        result = CallerResult()
                        val parcel = Parcel.obtain()
                        try {
                            parcel.writeValue(shellResult)
                            result.reply = parcel.marshall()
                        } finally {
                            parcel.recycle()
                        }
                    }
                    else -> {
                        // Handle other types or unknown types if necessary
                        FLog.log("Unknown caller type received: ${baseCaller?.type}")
                    }
                }
                LifecycleAgent.sServerInfo.successCount++
            } catch (e: Throwable) {
                FLog.log(e)
                result = CallerResult()
                result.throwable = e
                LifecycleAgent.sServerInfo.errorCount++
            } finally {
                if (result == null) {
                    result = CallerResult()
                }
                sendOpResult(result)
            }
        }
    }

    companion object {
        private const val MSG_TIMEOUT = 1
        private const val DEFAULT_TIMEOUT = 1000 * 60L // 1 min
        private const val BG_TIMEOUT = DEFAULT_TIMEOUT * 10L // 10 min
    }
}
