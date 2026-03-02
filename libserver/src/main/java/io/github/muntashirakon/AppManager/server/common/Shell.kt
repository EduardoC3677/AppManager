// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server.common

import android.os.Parcel
import android.os.Parcelable
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicInteger

// Copyright 2017 Zheng Li
class Shell @Throws(IOException::class) private constructor(cmd: String) {
    private val mProcess: Process = ProcessBuilder(cmd).redirectErrorStream(true).start()
    private val mIn: BufferedReader = BufferedReader(InputStreamReader(mProcess.inputStream))
    private val mOut: OutputStream = mProcess.outputStream
    private val mCommandQueue = LinkedBlockingQueue<Command>()
    private val mNextCmdID = AtomicInteger(0)

    @Volatile
    private var mClosed = false

    init {
        val shellRunnable = Runnable {
            while (!mClosed) {
                try {
                    val command = mCommandQueue.take()
                    if (command != null && !mClosed) {
                        writeCommand(command)
                        readCommand(command)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            if (mClosed) {
                destroyShell()
            }
        }
        Thread(shellRunnable, "shell").start()
    }

    @Throws(IOException::class)
    private fun writeCommand(command: Command) {
        val out = this.mOut
        command.writeCommand(out)
        val line = "\necho $TOKEN ${command.getID()} $?\n"
        out.write(line.toByteArray())
        out.flush()
    }

    @Throws(IOException::class)
    private fun readCommand(command: Command?) {
        if (command != null) {
            while (!mClosed) {
                var line = mIn.readLine()
                if (line == null || mClosed) {
                    break
                }
                val pos = line.indexOf(TOKEN)
                if (pos > 0) {
                    command.onUpdate(command.getID(), line.substring(0, pos))
                }
                if (pos >= 0) {
                    line = line.substring(pos)
                    val fields = line.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    if (fields.size >= 2 && fields[1] != null) {
                        var id = 0
                        try {
                            id = fields[1].toInt()
                        } catch (ignored: NumberFormatException) {
                        }
                        var exitCode = -1
                        try {
                            exitCode = fields[2].toInt()
                        } catch (ignored: NumberFormatException) {
                        }
                        if (id == command.getID()) {
                            command.setExitCode(exitCode)
                            break
                        }
                    }
                }
                command.onUpdate(command.getID(), line)
            }
        }
    }

    fun destroyShell() {
        try {
            writeCommand(object : Command("exit 33\n") {
                override fun onUpdate(id: Int, message: String) {}
                override fun onFinished(id: Int) {}
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            mIn.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        try {
            mOut.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        if (!mCommandQueue.isEmpty()) {
            var command: Command?
            while (mCommandQueue.poll().also { command = it } != null) {
                command?.terminate("Unexpected Termination.")
            }
        }
        mProcess.destroy()
    }

    fun isClosed(): Boolean {
        return mClosed
    }

    fun close() {
        this.mClosed = true
    }

    fun allCommandsOver(): Boolean {
        return mCommandQueue.isEmpty()
    }

    private fun generateCommandID(): Int {
        var id = mNextCmdID.getAndIncrement()
        if (id > 0x00FFFFFF) {
            mNextCmdID.set(1)
            id = generateCommandID()
        }
        return id
    }

    private fun add(command: Command): Command {
        if (mClosed) {
            throw IllegalStateException("Unable to add commands to a closed shell.")
        }
        command.setId(generateCommandID())
        mCommandQueue.offer(command)
        return command
    }

    fun exec(cmd: String): Result {
        val result = Result()
        FLog.log("Command:  $cmd")
        val outLine = StringBuilder()
        try {
            result.statusCode = add(object : Command(cmd) {
                override fun onUpdate(id: Int, message: String) {
                    outLine.append(message).append('\n')
                }

                override fun onFinished(id: Int) {}
            }).waitForFinish()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
        if (result.statusCode == -1) {
            try {
                outLine.setLength(0)
                result.statusCode = add(object : Command(cmd) {
                    override fun onUpdate(id: Int, message: String) {
                        outLine.append(message).append('\n')
                    }

                    override fun onFinished(id: Int) {}
                }).waitForFinish()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }
        result.message = outLine.toString()
        return result
    }

    fun countCommands(): Int {
        return mCommandQueue.size
    }

    abstract inner class Command @JvmOverloads constructor(timeout: Int, vararg commands: String) {
        private val mCommands: Array<out String> = commands
        private val mTimeout: Long = timeout.toLong()
        private var mIsFinished = false
        private var mExitCode = 0
        private var mId = 0

        abstract fun onUpdate(id: Int, message: String)
        abstract fun onFinished(id: Int)

        constructor(vararg commands: String) : this(1000 * 30, *commands)

        fun setId(id: Int) {
            mId = id
        }

        fun getID(): Int {
            return mId
        }

        fun setExitCode(code: Int) {
            synchronized(this) {
                mExitCode = code
                mIsFinished = true
                onFinished(mId)
                (this as Object).notifyAll()
            }
        }

        fun isFinished(): Boolean {
            synchronized(this) { return mIsFinished }
        }

        fun terminate(reason: String?) {
            close()
            setExitCode(-1)
        }

        @Throws(InterruptedException::class)
        fun waitForFinish(timeout: Long): Int {
            synchronized(this) {
                while (!mIsFinished) {
                    (this as Object).wait(timeout)
                    if (!mIsFinished) {
                        mIsFinished = true
                        terminate("Timeout Exception")
                    }
                }
            }
            return mExitCode
        }

        @Throws(InterruptedException::class)
        fun waitForFinish(): Int {
            synchronized(this) { return waitForFinish(mTimeout) }
        }

        val command: String
            get() {
                if (mCommands.isEmpty()) {
                    return ""
                }
                val sb = StringBuilder()
                for (s in mCommands) {
                    sb.append(s)
                    sb.append('\n')
                }
                return sb.toString()
            }

        @Throws(IOException::class)
        fun writeCommand(out: OutputStream) {
            out.write(command.toByteArray())
            out.flush()
        }
    }

    class Result : Parcelable {
        @JvmField
        var message: String? = null
        @JvmField
        var statusCode: Int = -1

        constructor()
        protected constructor(`in`: Parcel) {
            message = `in`.readString()
            statusCode = `in`.readInt()
        }

        override fun describeContents(): Int {
            return 0
        }

        override fun writeToParcel(dest: Parcel, flags: Int) {
            dest.writeString(message)
            dest.writeInt(statusCode)
        }

        companion object {
            @JvmField
            val CREATOR: Parcelable.Creator<Result> = object : Parcelable.Creator<Result> {
                override fun createFromParcel(`in`: Parcel): Result {
                    return Result(`in`)
                }

                override fun newArray(size: Int): Array<Result?> {
                    return arrayOfNulls(size)
                }
            }
        }
    }

    companion object {
        private val TOKEN = UUID.randomUUID().toString()

        @Volatile
        private var sShell: Shell? = null

        @JvmStatic
        @Throws(IOException::class)
        fun getShell(path: String): Shell {
            return sShell ?: synchronized(Shell::class.java) {
                sShell ?: Shell("sh").also {
                    it.exec("export PATH=$path:\$PATH")
                    sShell = it
                }
            }
        }
    }
}
