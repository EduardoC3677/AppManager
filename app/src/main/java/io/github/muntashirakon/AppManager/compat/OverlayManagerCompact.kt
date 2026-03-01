// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat

import android.content.Context
import android.content.om.IOverlayManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import io.github.muntashirakon.AppManager.ipc.ProxyBinder

@RequiresApi(Build.VERSION_CODES.O)
object OverlayManagerCompact {
    @JvmStatic
    @RequiresPermission(ManifestCompat.permission.CHANGE_OVERLAY_PACKAGES)
    fun getOverlayManager(): IOverlayManager {
        return IOverlayManager.Stub.asInterface(ProxyBinder.getService("overlay"))
    }
}
