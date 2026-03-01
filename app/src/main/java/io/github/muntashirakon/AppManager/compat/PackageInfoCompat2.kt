// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat

import android.content.pm.PackageInfo
import android.content.pm.PackageInfoHidden
import android.os.Build
import dev.rikka.tools.refine.Refine
import io.github.muntashirakon.AppManager.utils.ExUtils
import java.util.*

object PackageInfoCompat2 {
    @JvmStatic
    fun getOverlayTarget(packageInfo: PackageInfo): String? {
        return Refine.unsafeCast<PackageInfoHidden>(packageInfo).overlayTarget
    }

    @JvmStatic
    fun getTargetOverlayableName(packageInfo: PackageInfo): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return Refine.unsafeCast<PackageInfoHidden>(packageInfo).targetOverlayableName
        }
        return null
    }

    @JvmStatic
    fun getOverlayCategory(packageInfo: PackageInfo): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Refine.unsafeCast<PackageInfoHidden>(packageInfo).overlayCategory
        }
        return null
    }

    @JvmStatic
    fun getOverlayPriority(packageInfo: PackageInfo): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return Refine.unsafeCast<PackageInfoHidden>(packageInfo).overlayPriority
        }
        return 0 // MAX priority
    }

    @JvmStatic
    fun isStaticOverlayPackage(packageInfo: PackageInfo): Boolean {
        val info = Refine.unsafeCast<PackageInfoHidden>(packageInfo)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return info.isStaticOverlayPackage
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return Optional.ofNullable(ExUtils.exceptionAsNull<Boolean> { info.isStaticOverlay })
                .orElse((info.overlayFlags and PackageInfoHidden.FLAG_OVERLAY_STATIC) != 0)
        }
        // Static is by default
        return true
    }
}
