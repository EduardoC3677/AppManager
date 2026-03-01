// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ipc.ps

import android.text.TextUtils
import android.util.Log
import androidx.annotation.AnyThread
import androidx.annotation.GuardedBy
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import io.github.muntashirakon.AppManager.utils.CpuUtils
import io.github.muntashirakon.AppManager.utils.Utils
import io.github.muntashirakon.io.Path
import io.github.muntashirakon.io.Paths
import io.github.muntashirakon.proc.*

/**
 * This is a generic Java-way of parsing processes from /proc. This is a work in progress and by no means perfect. To
 * create this class, I extensively followed the documentation located at https://www.kernel.org/doc/Documentation/filesystems/proc.txt.
 */
class Ps @VisibleForTesting constructor(private val mProcFs: ProcFs) {
    companion object {
        @JvmField
        val TAG: String = Ps::class.java.simpleName
    }

    @GuardedBy("mProcessEntries")
    private val mProcessEntries = ArrayList<ProcessEntry>(256)
    private var mUptime: Long = 0
    private var mClockTicks: Long = 0

    constructor() : this(Paths.get("/proc"))

    constructor(procPath: Path) : this(ProcFs(procPath))

    @get:AnyThread
    @get:GuardedBy("mProcessEntries")
    val processes: ArrayList<ProcessEntry>
        get() {
            synchronized(mProcessEntries) {
                return ArrayList(mProcessEntries)
            }
        }

    @WorkerThread
    @GuardedBy("mProcessEntries")
    fun loadProcesses() {
        synchronized(mProcessEntries) {
            mProcessEntries.clear()
            mUptime = mProcFs.uptime / 1000
            mClockTicks = if (!Utils.isRoboUnitTest()) {
                CpuUtils.getClockTicksPerSecond()
            } else {
                100 // To prevent error due to native library
            }
            // Get process info for each PID
            for (pid in mProcFs.pids) {
                val procItem = ProcItem()
                val procStat = mProcFs.getStat(pid)
                val procMemStat = mProcFs.getMemStat(pid)
                val procStatus = mProcFs.getStatus(pid)
                if (procStat == null) {
                    Log.w(TAG, "Could not read /proc/$pid/stat")
                    continue
                }
                if (procMemStat == null) {
                    Log.w(TAG, "Could not read /proc/$pid/statm")
                    continue
                }
                if (procStatus == null) {
                    Log.w(TAG, "Could not read /proc/$pid/status")
                    continue
                }
                procItem.stat = procStat
                procItem.memStat = procMemStat
                procItem.status = procStatus
                procItem.name = mProcFs.getCmdline(pid)
                procItem.sepol = mProcFs.getCurrentContext(pid)
                procItem.wchan = mProcFs.getWchan(pid)
                mProcessEntries.add(newProcess(procItem))
            }
        }
    }

    private fun newProcess(procItem: ProcItem): ProcessEntry {
        val processEntry = ProcessEntry()
        processEntry.pid = procItem.stat!!.getInteger(ProcStat.STAT_PID)
        processEntry.ppid = procItem.stat!!.getInteger(ProcStat.STAT_PPID)
        processEntry.priority = procItem.stat!!.getInteger(ProcStat.STAT_PRIORITY)
        processEntry.niceness = procItem.stat!!.getInteger(ProcStat.STAT_NICE)
        processEntry.instructionPointer = procItem.stat!!.getLong(ProcStat.STAT_EIP)
        processEntry.virtualMemorySize = procItem.stat!!.getLong(ProcStat.STAT_VSIZE)
        processEntry.residentSetSize = procItem.stat!!.getLong(ProcStat.STAT_RSS)
        processEntry.sharedMemory = procItem.memStat!!.getLong(ProcMemStat.MEM_STAT_SHARED)
        processEntry.processGroupId = procItem.stat!!.getInteger(ProcStat.STAT_PGRP)
        processEntry.majorPageFaults = procItem.stat!!.getInteger(ProcStat.STAT_MAJ_FLT)
        processEntry.minorPageFaults = procItem.stat!!.getInteger(ProcStat.STAT_MIN_FLT)
        processEntry.realTimePriority = procItem.stat!!.getInteger(ProcStat.STAT_RT_PRIORITY)
        processEntry.schedulingPolicy = procItem.stat!!.getInteger(ProcStat.STAT_POLICY)
        processEntry.cpu = procItem.stat!!.getInteger(ProcStat.STAT_TASK_CPU)
        processEntry.threadCount = procItem.stat!!.getInteger(ProcStat.STAT_NUM_THREADS)
        processEntry.tty = procItem.stat!!.getInteger(ProcStat.STAT_TTY_NR)
        processEntry.seLinuxPolicy = procItem.sepol
        processEntry.name = if (TextUtils.isEmpty(procItem.name)) procItem.status!!.getString(ProcStatus.STATUS_NAME) else procItem.name
        processEntry.users = ProcessUsers(
            procItem.status!!.getString(ProcStatus.STATUS_UID),
            procItem.status!!.getString(ProcStatus.STATUS_GID)
        )
        processEntry.cpuTimeConsumed = (procItem.stat!!.getInteger(ProcStat.STAT_UTIME)
                + procItem.stat!!.getInteger(ProcStat.STAT_STIME)).toLong() / mClockTicks
        processEntry.cCpuTimeConsumed = (procItem.stat!!.getInteger(ProcStat.STAT_CUTIME)
                + procItem.stat!!.getInteger(ProcStat.STAT_CSTIME)).toLong() / mClockTicks
        processEntry.elapsedTime = mUptime - (procItem.stat!!.getInteger(ProcStat.STAT_START_TIME).toLong() / mClockTicks)
        val state = procItem.status!!.getString(ProcStatus.STATUS_STATE)
            ?: throw RuntimeException("Process state cannot be empty!")
        processEntry.processState = state.substring(0, 1)
        val stateExtra = StringBuilder()
        if (procItem.stat!!.getInteger(ProcStat.STAT_NICE) < 0) {
            stateExtra.append("<")
        } else if (procItem.stat!!.getInteger(ProcStat.STAT_NICE) > 0) {
            stateExtra.append("N")
        }
        if (procItem.stat!!.getInteger(ProcStat.STAT_SID) == processEntry.pid) {
            stateExtra.append("s")
        }
        val vmLck = procItem.status!!.getString(ProcStatus.STATUS_VM_LCK)
        if (vmLck != null && Integer.decode(vmLck.substring(0, 1)) > 0) {
            stateExtra.append("L")
        }
        if (procItem.stat!!.getInteger(ProcStat.STAT_TTY_PGRP) == processEntry.pid) {
            stateExtra.append("+")
        }
        processEntry.processStatePlus = stateExtra.toString()
        return processEntry
    }

    private class ProcItem {
        var stat: ProcStat? = null
        var memStat: ProcMemStat? = null
        var status: ProcStatus? = null
        var name: String? = null
        var sepol: String? = null
        var wchan: String? = null
    }
}
