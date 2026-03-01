// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.behavior

import android.annotation.UserIdInt
import android.content.Context
import android.content.Intent
import android.os.Parcel
import android.os.Parcelable
import io.github.muntashirakon.AppManager.shortcut.ShortcutInfo
import java.util.*

class FreezeUnfreezeShortcutInfo : ShortcutInfo {
    val packageName: String
    @UserIdInt val userId: Int
    @FreezeUnfreeze.FreezeFlags val flags: Int

    var privateFlags: Int = 0

    constructor(packageName: String, userId: Int, flags: Int) : super() {
        id = "freeze:u=$userId,p=$packageName"
        this.packageName = packageName
        this.userId = userId
        this.flags = flags
    }

    protected constructor(`in`: Parcel) : super(`in`) {
        packageName = `in`.readString()!!
        userId = `in`.readInt()
        flags = `in`.readInt()
        privateFlags = `in`.readInt()
    }

    fun addPrivateFlags(privateFlags: Int) {
        this.privateFlags = this.privateFlags or privateFlags
    }

    fun removePrivateFlags(privateFlags: Int) {
        this.privateFlags = this.privateFlags and privateFlags.inv()
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        super.writeToParcel(dest, flags)
        dest.writeString(packageName)
        dest.writeInt(userId)
        dest.writeInt(this.flags)
        dest.writeInt(privateFlags)
    }

    override fun hashCode(): Int = Objects.hash(packageName, userId)

    override fun toShortcutIntent(context: Context): Intent = FreezeUnfreeze.getShortcutIntent(context, this)

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<FreezeUnfreezeShortcutInfo> = object : Parcelable.Creator<FreezeUnfreezeShortcutInfo> {
            override fun createFromParcel(source: Parcel): FreezeUnfreezeShortcutInfo = FreezeUnfreezeShortcutInfo(source)
            override fun newArray(size: Int): Array<FreezeUnfreezeShortcutInfo?> = arrayOfNulls(size)
        }
    }
}
