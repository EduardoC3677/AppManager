// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server

import android.net.LocalServerSocket
import android.net.LocalSocket
import io.github.muntashirakon.AppManager.server.common.DataTransmission
import io.github.muntashirakon.AppManager.server.common.FLog
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket

// Copyright 2017 Zheng Li
class Server : Closeable {
    private val mLifecycleAgent: LifecycleAgent
    private val mServer: IServer
    private val mToken: String
    private val mOnReceiveCallback: DataTransmission.OnReceiveCallback?

    private var mDataTransmission: DataTransmission? = null
    private var mRunning = true
    var mRunInBackground = false

    /**
     * Constructor for starting a local server
     *
     * @param name              Socket address
     * @param token             Token for handshaking
     * @param onReceiveCallback Callback for sending message (received by the calling class)
     * @throws IOException On failing to create a socket connection
     */
    constructor(
        name: String, token: String, lifecycleAgent: LifecycleAgent,
        onReceiveCallback: DataTransmission.OnReceiveCallback?
    ) : this(LocalServerImpl(name), token, lifecycleAgent, onReceiveCallback)

    /**
     * Constructor for starting a local server
     *
     * @param port              Port number
     * @param token             Token for handshaking
     * @param onReceiveCallback Callback for sending message (received by the calling class)
     * @throws IOException On failing to create a socket connection
     */
    constructor(
        port: Int, token: String, lifecycleAgent: LifecycleAgent,
        onReceiveCallback: DataTransmission.OnReceiveCallback?
    ) : this(NetSocketServerImpl(port), token, lifecycleAgent, onReceiveCallback)

    private constructor(
        serverImpl: IServer,
        token: String,
        lifecycleAgent: LifecycleAgent,
        onReceiveCallback: DataTransmission.OnReceiveCallback?
    ) {
        mToken = token
        mLifecycleAgent = lifecycleAgent
        mServer = serverImpl
        mOnReceiveCallback = onReceiveCallback
    }

    /**
     * Run the server
     *
     * @throws IOException On failing to shake hands or make connection
     * @throws RuntimeException If server crashes unexpectedly
     */
    @Throws(IOException::class, RuntimeException::class)
    fun run() {
        while (mRunning) {
            try {
                // Allow only one client
                mServer.accept()
                // Prepare input and output streams for data interchange
                mDataTransmission = DataTransmission(
                    mServer.getOutputStream(),
                    mServer.getInputStream(),
                    mOnReceiveCallback
                )
                // Handshake: check if tokens matched
                mDataTransmission?.shakeHands(mToken, DataTransmission.Role.Server)
                // Send broadcast message to the system that the server has connected
                mLifecycleAgent.onConnected()
                // Handle the data received initially from the client
                mDataTransmission?.handleReceive()
            } catch (e: DataTransmission.ProtocolVersionException) {
                FLog.log(e)
                throw e
            } catch (e: IOException) {
                FLog.log(e)
                FLog.log("Run in background: $mRunInBackground")
                // Send broadcast message to the system that the server has disconnected
                mLifecycleAgent.onDisconnected()
                // Throw exception only when run in background is not requested
                if (!mRunInBackground) {
                    mRunning = false
                    throw e
                }
            } catch (e: RuntimeException) {
                FLog.log(e)
                // Send broadcast message to the system that the server has disconnected
                mLifecycleAgent.onDisconnected()
                // Re-throw the exception
                mRunInBackground = false
                mRunning = false
                throw e
            }
        }
    }

    @Throws(IOException::class)
    fun sendResult(bytes: ByteArray) {
        if (mRunning && mDataTransmission != null) {
            LifecycleAgent.sServerInfo.txBytes += bytes.size.toLong()
            mDataTransmission?.sendMessage(bytes)
        }
    }

    @Throws(IOException::class)
    override fun close() {
        mRunning = false
        mDataTransmission?.close()
        mServer.close()
    }

    private interface IServer : Closeable {
        @Throws(IOException::class)
        fun getInputStream(): InputStream

        @Throws(IOException::class)
        fun getOutputStream(): OutputStream

        @Throws(IOException::class)
        fun accept()

        @Throws(IOException::class)
        override fun close()
    }

    private class LocalServerImpl(name: String) : IServer {
        private val mServerSocket: LocalServerSocket = LocalServerSocket(name)
        private var mLocalSocket: LocalSocket? = null

        @Throws(IOException::class)
        override fun getInputStream(): InputStream {
            return mLocalSocket!!.inputStream
        }

        @Throws(IOException::class)
        override fun getOutputStream(): OutputStream {
            return mLocalSocket!!.outputStream
        }

        @Throws(IOException::class)
        override fun accept() {
            mLocalSocket = mServerSocket.accept()
        }

        @Throws(IOException::class)
        override fun close() {
            mLocalSocket?.close()
            mServerSocket.close()
        }
    }

    private class NetSocketServerImpl(port: Int) : IServer {
        private val mServerSocket: ServerSocket = ServerSocket(port)
        private var mSocket: Socket? = null

        @Throws(IOException::class)
        override fun getInputStream(): InputStream {
            return mSocket!!.getInputStream()
        }

        @Throws(IOException::class)
        override fun getOutputStream(): OutputStream {
            return mSocket!!.outputStream
        }

        @Throws(IOException::class)
        override fun accept() {
            mSocket = mServerSocket.accept()
        }

        @Throws(IOException::class)
        override fun close() {
            mSocket?.close()
            mServerSocket.close()
        }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            // Not needed for this class conversion
        }
    }
}
