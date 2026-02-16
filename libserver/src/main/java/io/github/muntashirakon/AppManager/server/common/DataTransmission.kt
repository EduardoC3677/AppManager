// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server.common

import android.util.Log
import java.io.*
import java.util.*

/**
 * `DataTransmission` class handles the data sent and received by server or client.
 */
// Copyright 2017 Zheng Li
class DataTransmission @JvmOverloads constructor(
    outputStream: OutputStream,
    inputStream: InputStream,
    private var mOnReceiveCallback: OnReceiveCallback? = null,
    private val mAsync: Boolean = true
) : Closeable {
    private val mOutputStream: DataOutputStream = DataOutputStream(outputStream)
    private val mInputStream: DataInputStream = DataInputStream(inputStream)

    @Volatile
    private var mRunning = true

    /**
     * Protocol version. Specification: `protocol-version,token`
     */
    enum class Role {
        Server,
        Client
    }

    /**
     * Create a new asynchronous data transfer object
     *
     * @param outputStream Stream where new messages will be written
     * @param inputStream  Stream where new messages will be read from
     */
    constructor(outputStream: OutputStream, inputStream: InputStream) : this(outputStream, inputStream, null, true)

    /**
     * Create a new data transfer object
     *
     * @param outputStream Stream where new messages will be written
     * @param inputStream  Stream where new messages will be read from
     * @param async        Whether the transfer should be asynchronous or synchronous
     */
    constructor(outputStream: OutputStream, inputStream: InputStream, async: Boolean) : this(
        outputStream,
        inputStream,
        null,
        async
    )

    /**
     * Set custom callback for receiving message.
     *
     * @param onReceiveCallback Callback that wants to receive message.
     */
    fun setOnReceiveCallback(onReceiveCallback: OnReceiveCallback?) {
        mOnReceiveCallback = onReceiveCallback
    }

    /**
     * Send text message
     *
     * @param text Text to be sent
     * @throws IOException When it fails to send the message
     * @see .sendMessage
     * @see .sendAndReceiveMessage
     */
    @Throws(IOException::class)
    fun sendMessage(text: String?) {
        if (text != null) {
            sendMessage(text.toByteArray())
        }
    }

    /**
     * Send message as bytes
     *
     * @param messageBytes Bytes to be sent
     * @throws IOException When it fails to send the message
     * @see .sendMessage
     * @see .sendAndReceiveMessage
     */
    @Throws(IOException::class)
    fun sendMessage(messageBytes: ByteArray?) {
        if (messageBytes != null) {
            mOutputStream.writeInt(messageBytes.size)
            mOutputStream.write(messageBytes)
            mOutputStream.flush()
        }
    }

    /**
     * Read response as bytes after sending a message
     *
     * @return The bytes to be read
     * @throws IOException When it fails to read the message
     */
    @Throws(IOException::class)
    private fun readMessage(): ByteArray {
        val len = mInputStream.readInt()
        val bytes = ByteArray(len)
        mInputStream.readFully(bytes, 0, len)
        return bytes
    }

    /**
     * Send and receive messages at the same time (half-duplex)
     *
     * @param messageBytes Bytes to be sent
     * @return Bytes to be read
     * @throws IOException When it fails to send or read the message
     * @see .sendMessage
     * @see .sendMessage
     */
    @Synchronized
    @Throws(IOException::class)
    fun sendAndReceiveMessage(messageBytes: ByteArray): ByteArray {
        sendMessage(messageBytes)
        return readMessage()
    }

    /**
     * Handshake: verify tokens
     *
     * @param token Token supplied by server or client based
     * @param role  Whether the supplied token is from server or client
     * @throws IOException              When it fails to verify the token
     * @throws ProtocolVersionException When the [PROTOCOL_VERSION] mismatch occurs
     */
    @Throws(IOException::class)
    fun shakeHands(token: String, role: Role) {
        Objects.requireNonNull(token)
        if (role == Role.Server) {
            FLog.log("DataTransmission#shakeHands: Server protocol: $PROTOCOL_VERSION")
            val auth = String(readMessage()) // <protocol-version>,<token>
            FLog.log("Received authentication: $auth")
            val split = auth.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val clientToken = split[1]
            // Match tokens
            if (token == clientToken) {
                // Connection is authorised
                FLog.log("DataTransmission#shakeHands: Authentication successful.")
            } else {
                FLog.log("DataTransmission#shakeHands: Authentication failed.")
                throw IOException("Unauthorized client, token: $token")
            }
            // Check protocol version
            val protocolVersion = split[0]
            if (PROTOCOL_VERSION != protocolVersion) {
                throw ProtocolVersionException(
                    "Client protocol version: $protocolVersion, Server protocol version: $PROTOCOL_VERSION"
                )
            }
        } else if (role == Role.Client) {
            Log.e("DataTransmission", "shakeHands: Client protocol: $PROTOCOL_VERSION")
            sendMessage("$PROTOCOL_VERSION,$token")
        }
    }

    /**
     * Handle for messages received. For asynchronous operations or when the socket is not active,
     * nothing is done. But when server is running [onReceiveMessage] is called.
     *
     * @throws IOException When it fails to read the message received
     */
    @Throws(IOException::class)
    fun handleReceive() {
        if (!mAsync) return
        while (mRunning) {
            onReceiveMessage(readMessage())
        }
    }

    /**
     * Calls the callback function [OnReceiveCallback.onMessage].
     *
     * @param bytes Bytes that was received earlier
     */
    private fun onReceiveMessage(bytes: ByteArray) {
        mOnReceiveCallback?.onMessage(bytes)
    }

    /**
     * Stop data transmission, called when socket connection is being closed
     */
    override fun close() {
        mRunning = false
        try {
            mOutputStream.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        try {
            mInputStream.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    /**
     * The callback that executes when a new message is received
     */
    interface OnReceiveCallback {
        /**
         * Implement this method to handle the received message
         *
         * @param bytes The message that was received
         */
        fun onMessage(bytes: ByteArray)
    }

    /**
     * Indicates that a protocol version mismatch has been occurred
     */
    class ProtocolVersionException(message: String?) : IOException(message)

    companion object {
        const val PROTOCOL_VERSION: String = "1.2.4"
    }
}
