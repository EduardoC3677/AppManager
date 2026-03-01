// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ipc.ps

import android.os.Parcel
import android.os.Parcelable
import androidx.core.os.ParcelCompat

class ProcessEntry : Parcelable {
    var pid: Int = 0
    var ppid: Int = 0
    var priority: Int = 0
    var niceness: Int = 0
    var instructionPointer: Long = 0
    var virtualMemorySize: Long = 0
    var residentSetSize: Long = 0
    var sharedMemory: Long = 0
    var processGroupId: Int = 0
    var majorPageFaults: Int = 0
    var minorPageFaults: Int = 0
    var realTimePriority: Int = 0
    var schedulingPolicy: Int = 0
    var cpu: Int = 0
    var threadCount: Int = 0
    var tty: Int = 0
    var seLinuxPolicy: String? = null
    var name: String? = null
    var users: ProcessUsers? = null
    var cpuTimeConsumed: Long = 0
    var cCpuTimeConsumed: Long = 0
    var elapsedTime: Long = 0
    var processState: String? = null
    var processStatePlus: String? = null

    internal constructor()

    protected constructor(`in`: Parcel) {
        pid = `in`.readInt()
        ppid = `in`.readInt()
        priority = `in`.readInt()
        niceness = `in`.readInt()
        instructionPointer = `in`.readLong()
        virtualMemorySize = `in`.readLong()
        residentSetSize = `in`.readLong()
        sharedMemory = `in`.readLong()
        processGroupId = `in`.readInt()
        majorPageFaults = `in`.readInt()
        minorPageFaults = `in`.readInt()
        realTimePriority = `in`.readInt()
        schedulingPolicy = `in`.readInt()
        cpu = `in`.readInt()
        threadCount = `in`.readInt()
        tty = `in`.readInt()
        seLinuxPolicy = `in`.readString()
        name = `in`.readString()
        users = ParcelCompat.readParcelable(`in`, ProcessUsers::class.java.classLoader, ProcessUsers::class.java)!!
        cpuTimeConsumed = `in`.readLong()
        cCpuTimeConsumed = `in`.readLong()
        elapsedTime = `in`.readLong()
        processState = `in`.readString()
        processStatePlus = `in`.readString()
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(pid)
        dest.writeInt(ppid)
        dest.writeInt(priority)
        dest.writeInt(niceness)
        dest.writeLong(instructionPointer)
        dest.writeLong(virtualMemorySize)
        dest.writeLong(residentSetSize)
        dest.writeLong(sharedMemory)
        dest.writeInt(processGroupId)
        dest.writeInt(majorPageFaults)
        dest.writeInt(minorPageFaults)
        dest.writeInt(realTimePriority)
        dest.writeInt(schedulingPolicy)
        dest.writeInt(cpu)
        dest.writeInt(threadCount)
        dest.writeInt(tty)
        dest.writeString(seLinuxPolicy)
        dest.writeString(name)
        dest.writeParcelable(users, flags)
        dest.writeLong(cpuTimeConsumed)
        dest.writeLong(cCpuTimeConsumed)
        dest.writeLong(elapsedTime)
        dest.writeString(processState)
        dest.writeString(processStatePlus)
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<ProcessEntry> = object : Parcelable.Creator<ProcessEntry> {
            override fun createFromParcel(`in`: Parcel): ProcessEntry {
                return ProcessEntry(`in`)
            }

            override fun newArray(size: Int): Array<ProcessEntry?> {
                return arrayOfNulls(size)
            }
        }
    }
}
