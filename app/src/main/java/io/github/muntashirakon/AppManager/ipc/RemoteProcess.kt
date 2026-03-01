// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ipc

import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.Parcelable
import android.os.RemoteException
import io.github.muntashirakon.AppManager.IRemoteProcess
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

/**
 * Copyright 2020 Rikka
 * Copyright 2023 Muntashir Al-Islam
 */
class RemoteProcess : Process, Parcelable {
    private val mRemote: IRemoteProcess
    private var mOs: OutputStream? = null
    private var mIs: InputStream? = null

    constructor(remote: IRemoteProcess) {
        mRemote = remote
    }

    override fun getOutputStream(): OutputStream {
        if (mOs == null) {
            mOs = RemoteOutputStream(mRemote)
        }
        return mOs!!
    }

    override fun getInputStream(): InputStream {
        if (mIs == null) {
            try {
                mIs = ParcelFileDescriptor.AutoCloseInputStream(mRemote.getInputStream())
            } catch (e: RemoteException) {
                throw RuntimeException(e)
            }
        }
        return mIs!!
    }

    override fun getErrorStream(): InputStream {
        return try {
            ParcelFileDescriptor.AutoCloseInputStream(mRemote.getErrorStream())
        } catch (e: RemoteException) {
            throw RuntimeException(e)
        }
    }

    @Throws(InterruptedException::class)
    override fun waitFor(): Int {
        return try {
            mRemote.waitFor()
        } catch (e: RemoteException) {
            throw RuntimeException(e)
        }
    }

    override fun exitValue(): Int {
        return try {
            mRemote.exitValue()
        } catch (e: RemoteException) {
            throw RuntimeException(e)
        }
    }

    override fun destroy() {
        try {
            mRemote.destroy()
        } catch (e: RemoteException) {
            throw RuntimeException(e)
        }
    }

    fun alive(): Boolean {
        return try {
            mRemote.alive()
        } catch (e: RemoteException) {
            throw RuntimeException(e)
        }
    }

    @Throws(InterruptedException::class)
    fun waitForTimeout(timeout: Long, unit: TimeUnit): Boolean {
        return try {
            mRemote.waitForTimeout(timeout, unit.toString())
        } catch (e: RemoteException) {
            throw RuntimeException(e)
        }
    }

    fun asBinder(): IBinder {
        return mRemote.asBinder()
    }

    private constructor(`in`: Parcel) {
        mRemote = IRemoteProcess.Stub.asInterface(`in`.readStrongBinder())
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeStrongBinder(mRemote.asBinder())
    }

    private class RemoteOutputStream(private val mRemoteProcess: IRemoteProcess) : OutputStream() {
        private var mOutputStream: OutputStream? = null
        private var mIsClosed = false

        @Throws(IOException::class)
        override fun write(b: Int) {
            if (mIsClosed) {
                throw IOException("Remote is closed.")
            }
            if (mOutputStream == null) {
                try {
                    mOutputStream = ParcelFileDescriptor.AutoCloseOutputStream(mRemoteProcess.getOutputStream())
                } catch (e: RemoteException) {
                    throw IOException(e)
                }
            }
            mOutputStream!!.write(b)
        }

        @Throws(IOException::class)
        override fun flush() {
            if (mIsClosed) {
                throw IOException("Remote is closed.")
            }
            if (mOutputStream != null) {
                mOutputStream!!.close()
            }
            mOutputStream = null
        }

        @Throws(IOException::class)
        override fun close() {
            mIsClosed = true
            if (mOutputStream != null) {
                mOutputStream!!.close()
            }
            try {
                mRemoteProcess.closeOutputStream()
            } catch (e: RemoteException) {
                throw IOException(e)
            }
        }
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<RemoteProcess> = object : Parcelable.Creator<RemoteProcess> {
            override fun createFromParcel(`in`: Parcel): RemoteProcess {
                return RemoteProcess(`in`)
            }

            override fun newArray(size: Int): Array<RemoteProcess?> {
                return arrayOfNulls(size)
            }
        }
    }
}
