// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.servermanager

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import io.github.muntashirakon.AppManager.adb.AdbConnectionManager
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.misc.NoOps
import io.github.muntashirakon.AppManager.runner.Runner
import io.github.muntashirakon.AppManager.server.common.BaseCaller
import io.github.muntashirakon.AppManager.server.common.Caller
import io.github.muntashirakon.AppManager.server.common.CallerResult
import io.github.muntashirakon.AppManager.server.common.Constants
import io.github.muntashirakon.AppManager.server.common.DataTransmission
import io.github.muntashirakon.AppManager.server.common.ParcelableUtil
import io.github.muntashirakon.AppManager.settings.Ops
import io.github.muntashirakon.adb.AdbPairingRequiredException
import io.github.muntashirakon.adb.AdbStream
import io.github.muntashirakon.io.IoUtils
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// Copyright 2016 Zheng Li
internal class LocalServerManager private constructor(private val mContext: Context) {
    private val mLock = Any()

    @Volatile
    private var mSession: ClientSession? = null

    val isRunning: Boolean
        get() = mSession?.isRunning == true

    /**
     * Get current session. If no session is running, create a new one. If no server is running,
     * create one first.
     *
     * @return Currently running session
     * @throws IOException When creating session fails or server couldn't be started
     */
    @get:Throws(IOException::class, AdbPairingRequiredException::class)
    @get:NoOps(used = true)
    @get:WorkerThread
    private val session: ClientSession
        get() {
            synchronized(mLock) {
                if (mSession == null || mSession?.isRunning == false) {
                    try {
                        mSession = createSession()
                    } catch (e: Exception) {
                        if (!Ops.isDirectRoot && !Ops.isAdb) {
                            // Do not bother attempting to create a new session
                            throw IOException("Could not create session", e)
                        }
                    }
                    if (mSession == null) {
                        try {
                            startServer()
                        } catch (e: AdbPairingRequiredException) {
                            throw e
                        } catch (e: Exception) {
                            throw IOException("Could not start server", e)
                        }
                        mSession = createSession()
                    }
                }
                return mSession!!
            }
        }

    /**
     * Close client session
     */
    @AnyThread
    fun closeSession() {
        IoUtils.closeQuietly(mSession)
        mSession = null
    }

    /**
     * Stop ADB and then close client session
     */
    fun stop() {
        IoUtils.closeQuietly(mAdbStream)
        IoUtils.closeQuietly(mSession)
        mAdbStream = null
        mSession = null
    }

    @WorkerThread
    @NoOps(used = true)
    @Throws(IOException::class, AdbPairingRequiredException::class)
    fun start() {
        session
    }

    @get:Throws(IOException::class)
    @get:WorkerThread
    private val sessionDataTransmission: DataTransmission
        get() = try {
            session.dataTransmission
        } catch (e: AdbPairingRequiredException) {
            throw IOException(e)
        }

    @WorkerThread
    @Throws(IOException::class)
    private fun execPre(params: ByteArray): ByteArray {
        return try {
            sessionDataTransmission.sendAndReceiveMessage(params)
        } catch (e: IOException) {
            if (e.message?.contains("pipe") == true) {
                closeSession()
                return sessionDataTransmission.sendAndReceiveMessage(params)
            }
            throw e
        }
    }

    @WorkerThread
    @Throws(IOException::class)
    fun execNew(caller: Caller): CallerResult {
        val result = execPre(ParcelableUtil.marshall(BaseCaller(caller.wrapParameters())))
        return ParcelableUtil.unmarshall(result, CallerResult.CREATOR)
    }

    @WorkerThread
    @Throws(IOException::class)
    fun closeBgServer() {
        try {
            val baseCaller = BaseCaller(BaseCaller.TYPE_CLOSE)
            sessionDataTransmission.sendAndReceiveMessage(ParcelableUtil.marshall(baseCaller))
        } catch (e: Exception) {
            // Since the server is closed abruptly, this should always produce error
            Log.w(TAG, "closeBgServer: Error", e)
        }
        // Check if the server is still active
        if (LocalServer.alive(mContext)) {
            // Server still active, need to run killall am_local_server
            try {
                stopServer()
            } catch (e: Exception) {
                throw IOException(e)
            }
        }
    }

    @Volatile
    private var mAdbStream: AdbStream? = null

    @Volatile
    private var mAdbConnectionWatcher = CountDownLatch(1)

    @Volatile
    private var mAdbServerStarted = false
    private val mAdbOutputThread = Runnable {
        try {
            BufferedReader(InputStreamReader(mAdbStream!!.openInputStream())).use { reader ->
                var s: String?
                while (reader.readLine().also { s = it } != null) {
                    Log.d(TAG, "RESPONSE: %s", s)
                    if (s!!.startsWith("Success!")) {
                        mAdbServerStarted = true
                        mAdbConnectionWatcher.countDown()
                        break
                    } else if (s!!.startsWith("Error!")) {
                        mAdbServerStarted = false
                        mAdbConnectionWatcher.countDown()
                        break
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "useAdbStartServer: unable to read from shell.", e)
        }
    }

    @WorkerThread
    @Throws(Exception::class)
    private fun useAdbStartServer() {
        if (mAdbStream == null || mAdbStream!!.isClosed) {
            // ADB shell not running
            val adbHost = ServerConfig.getAdbHost(mContext)
            val adbPort = ServerConfig.getAdbPort()
            val manager = AdbConnectionManager.getInstance()
            Log.d(TAG, "useAdbStartServer: Connecting using host=%s, port=%d", adbHost, adbPort)
            manager.setTimeout(10, TimeUnit.SECONDS)
            if (!manager.isConnected && !manager.connect(adbHost, adbPort)) {
                throw IOException("Could not connect to ADB.")
            }

            Log.d(TAG, "useAdbStartServer: Opening shell...")
            mAdbStream = manager.openStream("shell:")
            mAdbConnectionWatcher = CountDownLatch(1)
            mAdbServerStarted = false
            Thread(mAdbOutputThread).start()
        }
        Log.d(TAG, "useAdbStartServer: Shell opened.")

        mAdbStream!!.openOutputStream().use { os ->
            os.write("id\n".toByteArray())
            // ADB may require a fallback method
            val command = ServerConfig.getServerRunnerAdbCommand()
            Log.d(TAG, "useAdbStartServer: %s", command)
            os.write("$command\n".toByteArray())
        }

        if (!mAdbConnectionWatcher.await(1, TimeUnit.MINUTES) || !mAdbServerStarted) {
            throw Exception("Server wasn't started.")
        }
        Log.d(TAG, "useAdbStartServer: Server has started.")
    }

    @WorkerThread
    @Throws(Exception::class)
    private fun useRootStartServer() {
        if (!Ops.hasRoot()) {
            throw Exception("Root access denied")
        }
        val command = ServerConfig.getServerRunnerCommand(0)
        // + "\n" + "supolicy --live 'allow qti_init_shell zygote_exec file execute'";
        Log.d(TAG, "useRootStartServer: %s", command)
        val result = Runner.runCommand(command)

        Log.d(TAG, "useRootStartServer: %s", result.output)
        if (!result.isSuccessful) {
            throw Exception("Could not start server.")
        }
        SystemClock.sleep(3000)
        Log.e(TAG, "useRootStartServer: Server has started.")
    }

    /**
     * Start root or ADB server based on config
     */
    @WorkerThread
    @NoOps(used = true)
    @Throws(Exception::class)
    private fun startServer() {
        if (Ops.isAdb) {
            useAdbStartServer()
        } else if (Ops.isDirectRoot) {
            useRootStartServer()
        } else throw Exception("Neither root nor ADB mode is enabled.")
    }

    /**
     * Stop root or ADB server based on config
     */
    @WorkerThread
    @NoOps(used = true)
    @Throws(Exception::class)
    private fun stopServer() {
        val command = "killall " + Constants.SERVER_NAME
        if (Ops.isAdb) {
            if (mAdbStream == null || mAdbStream!!.isClosed) {
                // ADB shell not running
                val adbHost = ServerConfig.getAdbHost(mContext)
                val adbPort = ServerConfig.getAdbPort()
                val manager = AdbConnectionManager.getInstance()
                Log.d(TAG, "stopServer (ADB): Connecting using host=%s, port=%d", adbHost, adbPort)
                manager.setTimeout(10, TimeUnit.SECONDS)
                if (!manager.isConnected && !manager.connect(adbHost, adbPort)) {
                    throw IOException("Could not connect to ADB.")
                }

                Log.d(TAG, "stopServer (ADB): Opening shell...")
                mAdbStream = manager.openStream("shell:")
                mAdbConnectionWatcher = CountDownLatch(1)
                mAdbServerStarted = false
                Thread(mAdbOutputThread).start()
            }
            Log.d(TAG, "stopServer (ADB): Shell opened.")

            mAdbStream!!.openOutputStream().use { os ->
                os.write("id\n".toByteArray())
                Log.d(TAG, "stopServer (ADB): %s", command)
                os.write("$command\n".toByteArray())
            }

            if (!mAdbConnectionWatcher.await(1, TimeUnit.MINUTES) || !mAdbServerStarted) {
                throw Exception("Server wasn't stopped.")
            }
            Log.d(TAG, "useAdbStartServer: Server has stopped.")
        } else if (Ops.isDirectRoot) {
            if (!Ops.hasRoot()) {
                throw Exception("Root access denied")
            }
            Log.d(TAG, "stopServer (root): %s", command)
            val result = Runner.runCommand(command)
            Log.d(TAG, "stopServer (root): %s", result.output)
            if (!result.isSuccessful) {
                throw Exception("Could not start server.")
            }
            SystemClock.sleep(3000)
            Log.e(TAG, "useRootStartServer: Server has started.")
        } else throw Exception("Neither root nor ADB mode is enabled.")
    }

    /**
     * Create a client session
     *
     * @return New session if not running, running session otherwise
     * @throws IOException If session creation failed
     */
    @WorkerThread
    @Throws(IOException::class)
    @NoOps(used = true)
    private fun createSession(): ClientSession {
        if (isRunning) {
            // Non-null check has already been done
            return mSession!!
        }
        val host = ServerConfig.getLocalServerHost(mContext)
        val port = ServerConfig.getLocalServerPort()
        val socket = Socket(host, port)
        socket.soTimeout = 30_000
        // NOTE: (CWE-319) No need for SSL since it only runs on a random port in localhost with specific authorization.
        // TODO: 5/8/23 We could use an SSL server with a randomly generated certificate per session without requiring
        //  any other authorization methods. This session is independent of the application.
        val os = socket.getOutputStream()
        val isInput = socket.getInputStream()
        val transfer = DataTransmission(os, isInput, false)
        transfer.shakeHands(ServerConfig.getLocalToken(), DataTransmission.Role.Client)
        return ClientSession(socket, transfer)
    }

    /**
     * The client session handler
     */
    private class ClientSession @AnyThread constructor(
        private val mSocket: Socket,
        val dataTransmission: DataTransmission
    ) : AutoCloseable {
        @Volatile
        private var mIsRunning = true

        /**
         * Close the session, stop any active transmission
         */
        @AnyThread
        @Throws(IOException::class)
        override fun close() {
            if (mIsRunning) {
                mIsRunning = false
                dataTransmission.close()
                mSocket.close()
            }
        }

        /**
         * Whether the client session is running
         */
        val isRunning: Boolean
            @AnyThread
            get() = mIsRunning
    }

    companion object {
        private const val TAG = "LocalServerManager"

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var sLocalServerManager: LocalServerManager? = null

        @AnyThread
        @NoOps
        @JvmStatic
        fun getInstance(context: Context): LocalServerManager {
            return sLocalServerManager ?: synchronized(this) {
                sLocalServerManager ?: LocalServerManager(context).also { sLocalServerManager = it }
            }
        }
    }
}
