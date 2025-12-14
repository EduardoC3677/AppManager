// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.struct

import android.annotation.UserIdInt
import android.content.om.IOverlayManager
import android.content.om.OverlayInfo
import android.content.om.OverlayInfoHidden
import android.os.Build
import android.os.RemoteException
import androidx.annotation.RequiresApi
import dev.rikka.tools.refine.Refine

@RequiresApi(Build.VERSION_CODES.O)
class AppDetailsOverlayItem(
    overlayInfo: OverlayInfo
) : AppDetailsItem<OverlayInfoHidden>(Refine.unsafeCast(overlayInfo)) {

    init {
        name = if (overlayInfo.overlayName != null) {
            overlayInfo.overlayName
        } else {
            overlayInfo.overlayIdentifier?.toString() ?: overlayInfo.packageName
        }
    }

    val packageName: String
        get() = item.packageName

    val category: String?
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            item.category
        } else null

    val isEnabled: Boolean
        get() = item.isEnabled()

    @Suppress("DEPRECATION")
    val isMutable: Boolean
        get() = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> item.isMutable
            Build.VERSION.SDK_INT == Build.VERSION_CODES.P ->
                state != OverlayInfoHidden.STATE_ENABLED_IMMUTABLE
            else -> true
        }

    val readableState: String
        get() = stateToString(item.state)

    val state: Int
        get() = item.state

    @get:RequiresApi(Build.VERSION_CODES.P)
    val priority: Int
        get() = item.priority

    val overlayName: String?
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            item.overlayName
        } else null

    val baseCodePath: String
        get() = item.baseCodePath

    @get:UserIdInt
    val userId: Int
        get() = item.userId

    val isFabricated: Boolean
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            item.isFabricated
        } else false

    @Throws(RemoteException::class)
    fun setEnabled(mgr: IOverlayManager, enabled: Boolean): Boolean {
        return mgr.setEnabled(packageName, enabled, item.userId)
    }

    @Throws(RemoteException::class)
    fun setPriority(mgr: IOverlayManager, newParentPackageName: String): Boolean {
        return mgr.setPriority(packageName, newParentPackageName, item.userId)
    }

    @Throws(RemoteException::class)
    fun setHighestPriority(mgr: IOverlayManager): Boolean {
        return mgr.setHighestPriority(item.packageName, item.userId)
    }

    @Throws(RemoteException::class)
    fun setLowestPriority(mgr: IOverlayManager): Boolean {
        return mgr.setLowestPriority(item.packageName, item.userId)
    }

    override fun toString(): String {
        return "AppDetailsOverlayItem: { $item }"
    }

    companion object {
        @JvmStatic
        fun stateToString(@OverlayInfoHidden.State state: Int): String {
            return OverlayInfoHidden.stateToString(state)
        }
    }
}
