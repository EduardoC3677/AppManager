// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server.common

import android.os.Parcel
import android.os.Parcelable

// Copyright 2017 Zheng Li
class BaseCaller : Parcelable {
    @JvmField
    val type: Int
    @JvmField
    var rawBytes: ByteArray? = null

    constructor(method: Caller) {
        type = method.getType()
        rawBytes = ParcelableUtil.marshall(method)
    }

    constructor(type: Int) {
        this.type = type
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(this.type)
        dest.writeByteArray(this.rawBytes)
    }

    protected constructor(`in`: Parcel) {
        this.type = `in`.readInt()
        this.rawBytes = `in`.createByteArray()
    }

    companion object {
        const val TYPE_CLOSE: Int = -10
        const val TYPE_SHELL: Int = 5

        @JvmField
        val CREATOR: Parcelable.Creator<BaseCaller> = object : Parcelable.Creator<BaseCaller> {
            override fun createFromParcel(source: Parcel): BaseCaller {
                return BaseCaller(source)
            }

            override fun newArray(size: Int): Array<BaseCaller?> {
                return arrayOfNulls(size)
            }
        }
    }
}
