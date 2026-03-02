// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server.common

import android.os.Parcel
import android.os.Parcelable

// Copyright 2017 Zheng Li
class CallerResult : Parcelable {
    var reply: ByteArray? = null
    var throwable: Throwable? = null
    private var mReplyObj: Any? = null

    fun getReplyObj(): Any? {
        if (mReplyObj == null && reply != null) {
            mReplyObj = ParcelableUtil.readValue(reply)
        }
        return mReplyObj
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeByteArray(this.reply)
        dest.writeSerializable(this.throwable)
    }

    constructor()

    protected constructor(`in`: Parcel) {
        this.reply = `in`.createByteArray()
        this.throwable = `in`.readSerializable() as Throwable?
    }

    override fun toString(): String {
        return "CallerResult{" +
                "reply=" + getReplyObj() +
                ", throwable=" + throwable +
                '}'
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<CallerResult> = object : Parcelable.Creator<CallerResult> {
            override fun createFromParcel(source: Parcel): CallerResult {
                return CallerResult(source)
            }

            override fun newArray(size: Int): Array<CallerResult?> {
                return arrayOfNulls(size)
            }
        }
    }
}
