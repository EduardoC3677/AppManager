// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server.common

import android.os.Parcel
import android.os.Parcelable

class ShellCaller : Caller {
    private val mCommand: String?

    constructor(command: String?) {
        mCommand = command
    }

    fun getCommand(): String? {
        return mCommand
    }

    override fun getType(): Int {
        return BaseCaller.TYPE_SHELL
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(mCommand)
    }

    protected constructor(`in`: Parcel) {
        mCommand = `in`.readString()
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<ShellCaller> = object : Parcelable.Creator<ShellCaller> {
            override fun createFromParcel(source: Parcel): ShellCaller {
                return ShellCaller(source)
            }

            override fun newArray(size: Int): Array<ShellCaller?> {
                return arrayOfNulls(size)
            }
        }
    }
}
