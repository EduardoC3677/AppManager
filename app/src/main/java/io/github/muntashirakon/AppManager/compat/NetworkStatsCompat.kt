// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat

import android.net.INetworkStatsService
import android.net.INetworkStatsSession
import android.net.NetworkStats
import android.net.NetworkTemplate
import android.os.Build
import android.os.RemoteException
import io.github.muntashirakon.AppManager.self.SelfPermissions
import io.github.muntashirakon.AppManager.settings.Ops
import io.github.muntashirakon.AppManager.users.Users

class NetworkStatsCompat @Throws(RemoteException::class, SecurityException::class) internal constructor(
    private val mTemplate: NetworkTemplate,
    flags: Int,
    private val mStartTime: Long,
    private val mEndTime: Long,
    statsService: INetworkStatsService
) : AutoCloseable {
    private var mSession: INetworkStatsSession? = null
    private var mIndex = 0
    private var mSummary: NetworkStats? = null
    private var mSummaryEntry: NetworkStats.Entry? = null

    init {
        val callingUid = Users.getSelfOrRemoteUid()
        val callingPackage = SelfPermissions.getCallingPackage(callingUid)
        mSession = if (callingUid == Ops.ROOT_UID) {
            statsService.openSession()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            statsService.openSessionForUsageStats(flags, callingPackage)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            statsService.openSessionForUsageStats(callingPackage)
        } else {
            statsService.openSession()
        }
    }

    fun hasNextEntry(): Boolean {
        return mSummary != null && mIndex < mSummary!!.size()
    }

    fun getNextEntry(recycle: Boolean): NetworkStats.Entry? {
        val summary = mSummary ?: return null
        mSummaryEntry = summary.getValues(mIndex, if (recycle) mSummaryEntry else null)
        ++mIndex
        return mSummaryEntry
    }

    @Throws(RemoteException::class)
    fun startSummaryEnumeration() {
        if (mSession != null) {
            mSummary = mSession!!.getSummaryForAllUid(mTemplate, mStartTime, mEndTime, false)
        }
        mIndex = 0
    }

    override fun close() {
        if (mSession != null) {
            try {
                mSession!!.close()
            } catch (e: RemoteException) {
                e.printStackTrace()
                // Otherwise, meh
            }
        }
        mSession = null
    }

    @Throws(Throwable::class)
    protected fun finalize() {
        try {
            close()
        } finally {
            // No super.finalize() in Kotlin
        }
    }
}
