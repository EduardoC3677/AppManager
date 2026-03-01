// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io

import android.os.Parcel
import android.os.Parcelable
import java.util.Objects

class UidGidPair : Parcelable {
    @JvmField
    val uid: Int
    @JvmField
    val gid: Int

    constructor(uid: Int, gid: Int) {
        this.uid = uid
        this.gid = gid
    }

    protected constructor(`in`: Parcel) {
        uid = `in`.readInt()
        gid = `in`.readInt()
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(uid)
        dest.writeInt(gid)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UidGidPair) return false
        val that = other
        return uid == that.uid && gid == that.gid
    }

    override fun hashCode(): Int {
        return Objects.hash(uid, gid)
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<UidGidPair> = object : Parcelable.Creator<UidGidPair> {
            override fun createFromParcel(`in`: Parcel): UidGidPair {
                return UidGidPair(`in`)
            }

            override fun newArray(size: Int): Array<UidGidPair?> {
                return arrayOfNulls(size)
            }
        }
    }
}
