// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils.appearance

import android.content.Context
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.google.android.material.color.MaterialColors
import com.google.android.material.elevation.SurfaceColors
import io.github.muntashirakon.AppManager.debloat.DebloatObject
import io.github.muntashirakon.ui.R

object ColorCodes {
    @JvmStatic
    @ColorInt
    fun getListItemColor0(context: Context): Int {
        return MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurface, ColorCodes::class.java.canonicalName)
    }

    @JvmStatic
    @ColorInt
    fun getListItemColor1(context: Context): Int {
        return SurfaceColors.SURFACE_1.getColor(context)
    }

    @JvmStatic
    @ColorInt
    fun getListItemDefaultIndicatorColor(context: Context): Int {
        return SurfaceColors.SURFACE_3.getColor(context)
    }

    @JvmStatic
    @ColorInt
    fun getQueryStringHighlightColor(context: Context): Int {
        return ContextCompat.getColor(context, R.color.highlight)
    }

    @JvmStatic
    fun getSuccessColor(context: Context): Int {
        return ContextCompat.getColor(context, R.color.salem_green)
    }

    @JvmStatic
    fun getFailureColor(context: Context): Int {
        return ContextCompat.getColor(context, R.color.electric_red)
    }

    @JvmStatic
    fun getAppDisabledIndicatorColor(context: Context): Int {
        return ContextCompat.getColor(context, R.color.disabled_user)
    }

    @JvmStatic
    fun getAppForceStoppedIndicatorColor(context: Context): Int {
        return ContextCompat.getColor(context, R.color.stopped)
    }

    @JvmStatic
    fun getAppKeystoreIndicatorColor(context: Context): Int {
        return ContextCompat.getColor(context, R.color.tracker)
    }

    @JvmStatic
    fun getAppNoBatteryOptimizationIndicatorColor(context: Context): Int {
        return ContextCompat.getColor(context, R.color.red_orange)
    }

    @JvmStatic
    fun getAppSsaidIndicatorColor(context: Context): Int {
        return ContextCompat.getColor(context, R.color.tracker)
    }

    @JvmStatic
    fun getAppPlayAppSigningIndicatorColor(context: Context): Int {
        return ContextCompat.getColor(context, R.color.disabled_user)
    }

    @JvmStatic
    fun getAppWriteAndExecuteIndicatorColor(context: Context): Int {
        return ContextCompat.getColor(context, R.color.red)
    }

    @JvmStatic
    fun getBloatwareIndicatorColor(context: Context, @DebloatObject.Removal removal: Int): Int {
        return when (removal) {
            DebloatObject.REMOVAL_REPLACE -> getRemovalReplaceIndicatorColor(context)
            DebloatObject.REMOVAL_SAFE -> getRemovalSafeIndicatorColor(context)
            DebloatObject.REMOVAL_CAUTION -> getRemovalCautionIndicatorColor(context)
            DebloatObject.REMOVAL_UNSAFE -> getRemovalUnsafeIndicatorColor(context)
            else -> getRemovalUnsafeIndicatorColor(context)
        }
    }

    @JvmStatic
    fun getAppSuspendedIndicatorColor(context: Context): Int {
        return ContextCompat.getColor(context, R.color.stopped)
    }

    @JvmStatic
    fun getAppHiddenIndicatorColor(context: Context): Int {
        return ContextCompat.getColor(context, R.color.disabled_user)
    }

    @JvmStatic
    fun getAppUninstalledIndicatorColor(context: Context): Int {
        return ContextCompat.getColor(context, R.color.red)
    }

    @JvmStatic
    fun getBackupLatestIndicatorColor(context: Context): Int {
        return getSuccessColor(context)
    }

    @JvmStatic
    fun getBackupOutdatedIndicatorColor(context: Context): Int {
        return ContextCompat.getColor(context, R.color.orange)
    }

    @JvmStatic
    fun getBackupUninstalledIndicatorColor(context: Context): Int {
        return ContextCompat.getColor(context, R.color.red)
    }

    @JvmStatic
    fun getComponentRunningIndicatorColor(context: Context): Int {
        return ContextCompat.getColor(context, R.color.running)
    }

    @JvmStatic
    fun getComponentTrackerIndicatorColor(context: Context): Int {
        return ContextCompat.getColor(context, R.color.tracker)
    }

    @JvmStatic
    fun getComponentTrackerBlockedIndicatorColor(context: Context): Int {
        return getSuccessColor(context)
    }

    @JvmStatic
    fun getComponentBlockedIndicatorColor(context: Context): Int {
        return ContextCompat.getColor(context, R.color.red)
    }

    @JvmStatic
    fun getComponentExternallyBlockedIndicatorColor(context: Context): Int {
        return ContextCompat.getColor(context, R.color.disabled_user)
    }

    @JvmStatic
    fun getPermissionDangerousIndicatorColor(context: Context): Int {
        return ContextCompat.getColor(context, R.color.red)
    }

    @JvmStatic
    fun getScannerTrackerIndicatorColor(context: Context): Int {
        return getFailureColor(context)
    }

    @JvmStatic
    fun getRemovalSafeIndicatorColor(context: Context): Int {
        return getSuccessColor(context)
    }

    @JvmStatic
    fun getRemovalReplaceIndicatorColor(context: Context): Int {
        return ContextCompat.getColor(context, R.color.lilac_bush_purple)
    }

    @JvmStatic
    fun getRemovalCautionIndicatorColor(context: Context): Int {
        return ContextCompat.getColor(context, R.color.pumpkin_orange)
    }

    @JvmStatic
    fun getRemovalUnsafeIndicatorColor(context: Context): Int {
        return getFailureColor(context)
    }

    @JvmStatic
    fun getScannerNoTrackerIndicatorColor(context: Context): Int {
        return getSuccessColor(context)
    }

    @JvmStatic
    fun getVirusTotalSafeIndicatorColor(context: Context): Int {
        return getSuccessColor(context)
    }

    @JvmStatic
    fun getVirusTotalUnsafeIndicatorColor(context: Context): Int {
        return ContextCompat.getColor(context, R.color.tracker)
    }

    @JvmStatic
    fun getVirusTotalExtremelyUnsafeIndicatorColor(context: Context): Int {
        return getFailureColor(context)
    }

    @JvmStatic
    fun getWhatsNewPlusIndicatorColor(context: Context): Int {
        return getSuccessColor(context)
    }

    @JvmStatic
    fun getWhatsNewMinusIndicatorColor(context: Context): Int {
        return getFailureColor(context)
    }
}
