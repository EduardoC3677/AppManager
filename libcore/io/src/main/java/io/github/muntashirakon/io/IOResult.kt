// SPDX-License-Identifier: Apache-2.0

package io.github.muntashirakon.io

import android.os.Parcel
import android.os.Parcelable
import android.os.RemoteException
import android.system.ErrnoException
import java.io.IOException

// Copyright 2023 John "topjohnwu" Wu
internal class IOResult : Parcelable {
    private val `val`: Any?

    constructor() {
        `val` = null
    }

    constructor(v: Any?) {
        `val` = v
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeValue(`val`)
    }

    @Throws(IOException::class)
    fun checkException() {
        if (`val` is Throwable) {
            throw IOException(REMOTE_ERR_MSG, `val`)
        }
    }

    @Throws(ErrnoException::class, RemoteException::class)
    fun checkErrnoException() {
        if (`val` is ErrnoException) {
            throw `val`
        } else if (`val` is Throwable) {
            throw RemoteException(`val`.message).initCause(`val`) as RemoteException
        }
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(IOException::class)
    fun <T> tryAndGet(): T {
        checkException()
        return `val` as T
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(ErrnoException::class, RemoteException::class)
    fun <T> tryAndGetErrnoException(): T {
        checkErrnoException()
        return `val` as T
    }

    override fun describeContents(): Int {
        return 0
    }

    private constructor(`in`: Parcel) {
        `val` = `in`.readValue(cl)
    }

    companion object {
        private const val REMOTE_ERR_MSG = "Exception thrown on remote process"
        private val cl = IOResult::class.java.classLoader

        @JvmField
        val CREATOR: Parcelable.Creator<IOResult> = object : Parcelable.Creator<IOResult> {
            override fun createFromParcel(`in`: Parcel): IOResult {
                return IOResult(`in`)
            }

            override fun newArray(size: Int): Array<IOResult?> {
                return arrayOfNulls(size)
            }
        }
    }
}
