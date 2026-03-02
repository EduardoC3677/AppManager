// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server.common

import android.os.Parcel
import android.os.Parcelable
import org.jetbrains.annotations.Contract

// Copyright 2017 Zheng Li
object ParcelableUtil {
    @JvmStatic
    fun marshall(parcelable: Parcelable): ByteArray {
        val parcel = Parcel.obtain()
        return try {
            parcelable.writeToParcel(parcel, 0)
            parcel.marshall()
        } finally {
            parcel.recycle()
        }
    }

    @JvmStatic
    @Contract("!null,_ -> !null")
    fun <T : Parcelable> unmarshall(bytes: ByteArray?, creator: Parcelable.Creator<T>): T? {
        if (bytes == null) {
            return null
        }
        val parcel = unmarshall(bytes)
        return try {
            creator.createFromParcel(parcel)
        } finally {
            parcel?.recycle()
        }
    }

    @JvmStatic
    @Contract("!null -> !null")
    fun unmarshall(bytes: ByteArray?): Parcel? {
        if (bytes == null) {
            return null
        }
        val parcel = Parcel.obtain()
        parcel.unmarshall(bytes, 0, bytes.size)
        parcel.setDataPosition(0)
        return parcel
    }

    @JvmStatic
    fun readValue(bytes: ByteArray?): Any? {
        if (bytes == null) {
            return null
        }
        val unmarshall = unmarshall(bytes)
        return try {
            unmarshall?.readValue(ParcelableUtil::class.java.classLoader)
        } finally {
            unmarshall?.recycle()
        }
    }
}
