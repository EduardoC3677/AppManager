// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ipc.ps

import android.os.Parcel
import android.os.Parcelable

class ProcessUsers : Parcelable {
    val realUid: Int
    val realGid: Int
    val effectiveUid: Int
    val effectiveGid: Int
    val savedSetUid: Int
    val savedSetGid: Int
    val fsUid: Int
    val fsGid: Int

    internal constructor(uidLine: String?, gidLine: String?) {
        if (uidLine == null || gidLine == null) {
            throw IllegalArgumentException("UID/GID must be non null")
        }
        val uids = uidLine.split("\s+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val gids = gidLine.split("\s+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        if (uids.size != gids.size && uids.size >= 4) {
            throw IllegalArgumentException("Invalid UID/GID.
Uid: $uidLine
Gid: $gidLine")
        }
        // Set uids
        realUid = Integer.decode(uids[0].trim())
        effectiveUid = Integer.decode(uids[1].trim())
        savedSetUid = Integer.decode(uids[2].trim())
        fsUid = Integer.decode(uids[3].trim())
        // Set gids
        realGid = Integer.decode(gids[0].trim())
        effectiveGid = Integer.decode(gids[1].trim())
        savedSetGid = Integer.decode(gids[2].trim())
        fsGid = Integer.decode(gids[3].trim())
    }

    protected constructor(`in`: Parcel) {
        realUid = `in`.readInt()
        realGid = `in`.readInt()
        effectiveUid = `in`.readInt()
        effectiveGid = `in`.readInt()
        savedSetUid = `in`.readInt()
        savedSetGid = `in`.readInt()
        fsUid = `in`.readInt()
        fsGid = `in`.readInt()
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(realUid)
        dest.writeInt(realGid)
        dest.writeInt(effectiveUid)
        dest.writeInt(effectiveGid)
        dest.writeInt(savedSetUid)
        dest.writeInt(savedSetGid)
        dest.writeInt(fsUid)
        dest.writeInt(fsGid)
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<ProcessUsers> = object : Parcelable.Creator<ProcessUsers> {
            override fun createFromParcel(`in`: Parcel): ProcessUsers {
                return ProcessUsers(`in`)
            }

            override fun newArray(size: Int): Array<ProcessUsers?> {
                return arrayOfNulls(size)
            }
        }
    }
}
