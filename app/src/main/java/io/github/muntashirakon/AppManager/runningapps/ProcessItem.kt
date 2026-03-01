// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.runningapps

import android.os.Parcel
import android.os.Parcelable
import androidx.core.os.ParcelCompat
import io.github.muntashirakon.AppManager.ipc.ps.ProcessEntry
import io.github.muntashirakon.AppManager.users.Owners
import java.util.*

open class ProcessItem : Parcelable {
    val pid: Int
    val ppid: Int
    val rss: Long
    val uid: Int
    val user: String
    val context: String?

    var state: String? = null
    var state_extra: String? = null
    var name: String? = null

    private val mProcessEntry: ProcessEntry

    constructor(processEntry: ProcessEntry) {
        mProcessEntry = processEntry
        pid = processEntry.pid
        ppid = processEntry.ppid
        rss = processEntry.residentSetSize
        uid = processEntry.users.fsUid
        context = processEntry.seLinuxPolicy
        user = Owners.getOwnerName(processEntry.users.fsUid)
    }

    /**
     * @see <a href="https://stackoverflow.com/a/16736599">How do I get the total CPU usage of an application from /proc/pid/stat?</a>
     */
    val cpuTimeInPercent: Double
        get() = mProcessEntry.cpuTimeConsumed * 100.0 / mProcessEntry.elapsedTime

    val cpuTimeInMillis: Long
        get() = mProcessEntry.cpuTimeConsumed * 1000

    val commandlineArgsAsString: String
        get() = mProcessEntry.name.replace('\u0000', ' ')

    val commandlineArgs: Array<String>
        get() = mProcessEntry.name.split("\u0000").toTypedArray()

    val memory: Long
        get() = mProcessEntry.residentSetSize shl 12

    val virtualMemory: Long
        get() = mProcessEntry.virtualMemorySize

    val sharedMemory: Long
        get() = mProcessEntry.sharedMemory shl 12

    val priority: Int
        get() = mProcessEntry.priority

    val threadCount: Int
        get() = mProcessEntry.threadCount

    protected constructor(`in`: Parcel) {
        mProcessEntry = ParcelCompat.readParcelable(`in`, ProcessEntry::class.java.classLoader, ProcessEntry::class.java)!!
        pid = mProcessEntry.pid
        ppid = mProcessEntry.ppid
        rss = mProcessEntry.residentSetSize
        uid = mProcessEntry.users.fsUid
        context = mProcessEntry.seLinuxPolicy
        user = `in`.readString()!!
        state = `in`.readString()
        state_extra = `in`.readString()
        name = `in`.readString()
    }

    override fun toString(): String {
        return "ProcessItem{pid=$pid, ppid=$ppid, rss=$rss, user='$user', uid=$uid, state='$state', state_extra='$state_extra', name='$name', context='$context'}"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProcessItem) return false
        return pid == other.pid
    }

    override fun hashCode(): Int = Objects.hash(pid)

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeParcelable(mProcessEntry, flags)
        dest.writeString(user)
        dest.writeString(state)
        dest.writeString(state_extra)
        dest.writeString(name)
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<ProcessItem> = object : Parcelable.Creator<ProcessItem> {
            override fun createFromParcel(`in`: Parcel): ProcessItem = ProcessItem(`in`)
            override fun newArray(size: Int): Array<ProcessItem?> = arrayOfNulls(size)
        }
    }
}
