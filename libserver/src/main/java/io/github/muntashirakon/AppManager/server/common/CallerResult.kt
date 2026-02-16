// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server.common

import android.os.Parcel
import android.os.Parcelable

// Copyright 2017 Zheng Li
class CallerResult : Parcelable {
    private var mReply: ByteArray? = null
    private var mThrowable: Throwable? = null
    private var mReplyObj: Any? = null

    fun getReply(): ByteArray? {
        return mReply
    }

    fun getThrowable(): Throwable? {
        return mThrowable
    }

    fun getReplyObj(): Any? {
        if (mReplyObj == null && mReply != null) {
            mReplyObj = ParcelableUtil.readValue(mReply)
        }
        return mReplyObj
    }

    fun setReply(reply: ByteArray?) {
        this.mReply = reply
    }

    fun setThrowable(throwable: Throwable?) {
        this.mThrowable = throwable
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeByteArray(this.mReply)
        dest.writeSerializable(this.mThrowable)
    }

    constructor()

    protected constructor(`in`: Parcel) {
        this.mReply = `in`.createByteArray()
        this.mThrowable = `in`.readSerializable() as Throwable?
    }

    override fun toString(): String {
        return "CallerResult{" +
                "reply=" + getReplyObj() +
                ", throwable=" + mThrowable +
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

    // Property accessors for Java compatibility (if needed)
    val reply: ByteArray?
        get() = getReply()

    val throwable: Throwable?
        get() = getThrowable()

    val replyObj: Any?
        get() = getReplyObj()
}
