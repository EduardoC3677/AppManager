// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.usage

import android.annotation.UserIdInt
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Parcel
import android.os.Parcelable
import androidx.core.os.ParcelCompat
import java.util.*

class PackageUsageInfo : Parcelable {
    val packageName: String
    @UserIdInt
    val userId: Int
    val applicationInfo: ApplicationInfo?
    val appLabel: String

    var screenTime: Long = 0
    var lastUsageTime: Long = 0
    var timesOpened: Int = 0
    var mobileData: AppUsageStatsManager.DataUsage? = null
    var wifiData: AppUsageStatsManager.DataUsage? = null
    var entries: MutableList<Entry>? = null

    constructor(context: Context, packageName: String, @UserIdInt userId: Int, applicationInfo: ApplicationInfo?) {
        this.packageName = packageName
        this.userId = userId
        this.applicationInfo = applicationInfo
        this.appLabel = applicationInfo?.loadLabel(context.packageManager)?.toString() ?: packageName
    }

    protected constructor(`in`: Parcel) {
        packageName = `in`.readString()!!
        userId = `in`.readInt()
        applicationInfo = ParcelCompat.readParcelable(`in`, ApplicationInfo::class.java.classLoader, ApplicationInfo::class.java)
        appLabel = `in`.readString()!!
        screenTime = `in`.readLong()
        lastUsageTime = `in`.readLong()
        timesOpened = `in`.readInt()
        mobileData = ParcelCompat.readParcelable(`in`, AppUsageStatsManager.DataUsage::class.java.classLoader, AppUsageStatsManager.DataUsage::class.java)
        wifiData = ParcelCompat.readParcelable(`in`, AppUsageStatsManager.DataUsage::class.java.classLoader, AppUsageStatsManager.DataUsage::class.java)
        val size = `in`.readInt()
        if (size != 0) {
            entries = ArrayList(size)
            ParcelCompat.readList(`in`, entries!!, Entry::class.java.classLoader, Entry::class.java)
        }
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(packageName)
        dest.writeInt(userId)
        dest.writeParcelable(applicationInfo, flags)
        dest.writeString(appLabel)
        dest.writeLong(screenTime)
        dest.writeLong(lastUsageTime)
        dest.writeInt(timesOpened)
        dest.writeParcelable(mobileData, flags)
        dest.writeParcelable(wifiData, flags)
        dest.writeInt(entries?.size ?: 0)
        entries?.let { dest.writeList(it) }
    }

    override fun describeContents(): Int = 0

    fun copyOthers(packageUS: PackageUsageInfo) {
        screenTime = packageUS.screenTime
        lastUsageTime = packageUS.lastUsageTime
        timesOpened = packageUS.timesOpened
        mobileData = packageUS.mobileData
        wifiData = packageUS.wifiData
    }

    override fun toString(): String {
        return "PackageUS{packageName='$packageName', appLabel='$appLabel', screenTime=$screenTime, lastUsageTime=$lastUsageTime, timesOpened=$timesOpened, txData=$mobileData, rxData=$wifiData, entries=$entries}"\n}

    class Entry : Parcelable {
        val startTime: Long
        val endTime: Long

        constructor(startTime: Long, endTime: Long) {
            this.startTime = startTime
            this.endTime = endTime
        }

        protected constructor(`in`: Parcel) {
            startTime = `in`.readLong()
            endTime = `in`.readLong()
        }

        val duration: Long
            get() = endTime - startTime

        override fun toString(): String {
            return "USEntry{startTime=$startTime, endTime=$endTime}"
        }

        override fun describeContents(): Int = 0

        override fun writeToParcel(dest: Parcel, flags: Int) {
            dest.writeLong(startTime)
            dest.writeLong(endTime)
        }

        companion object {
            @JvmField
            val CREATOR: Parcelable.Creator<Entry> = object : Parcelable.Creator<Entry> {
                override fun createFromParcel(`in`: Parcel): Entry = Entry(`in`)
                override fun newArray(size: Int): Array<Entry?> = arrayOfNulls(size)
            }
        }
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<PackageUsageInfo> = object : Parcelable.Creator<PackageUsageInfo> {
            override fun createFromParcel(`in`: Parcel): PackageUsageInfo = PackageUsageInfo(`in`)
            override fun newArray(size: Int): Array<PackageUsageInfo?> = arrayOfNulls(size)
        }
    }
}
