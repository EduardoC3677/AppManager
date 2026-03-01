// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.runningapps

import android.content.pm.PackageInfo
import android.os.Parcel
import android.os.Parcelable
import androidx.core.os.ParcelCompat
import io.github.muntashirakon.AppManager.ipc.ps.ProcessEntry

class AppProcessItem : ProcessItem {
    val packageInfo: PackageInfo

    constructor(processEntry: ProcessEntry, packageInfo: PackageInfo) : super(processEntry) {
        this.packageInfo = packageInfo
    }

    protected constructor(`in`: Parcel) : super(`in`) {
        packageInfo = ParcelCompat.readParcelable(`in`, PackageInfo::class.java.classLoader, PackageInfo::class.java)!!
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        super.writeToParcel(dest, flags)
        dest.writeParcelable(packageInfo, flags)
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<AppProcessItem> = object : Parcelable.Creator<AppProcessItem> {
            override fun createFromParcel(`in`: Parcel): AppProcessItem = AppProcessItem(`in`)
            override fun newArray(size: Int): Array<AppProcessItem?> = arrayOfNulls(size)
        }
    }
}
