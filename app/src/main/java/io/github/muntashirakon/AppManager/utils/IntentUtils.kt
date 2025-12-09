// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi

object IntentUtils {
    @JvmStatic
    fun getAppDetailsSettings(packageName: String): Intent {
        return getSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageName)
    }

    @JvmStatic
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM) // Added in r20
    fun getAppStorageSettings(packageName: String): Intent {
        return getSettings("com.android.settings.APP_STORAGE_SETTINGS", packageName)
    }

    @JvmStatic
    @RequiresApi(Build.VERSION_CODES.N)
    fun getNetPolicySettings(packageName: String): Intent {
        return getSettings(Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS, packageName)
    }

    @JvmStatic
    @SuppressLint("BatteryLife")
    @RequiresApi(Build.VERSION_CODES.M)
    fun getBatteryOptSettings(packageName: String): Intent {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return getSettings("android.settings.VIEW_ADVANCED_POWER_USAGE_DETAIL", packageName)
        }
        return getSettings(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS, null)
    }

    @JvmStatic
    fun getSettings(action: String, packageName: String?): Intent {
        val intent = Intent(action)
            .addCategory(Intent.CATEGORY_DEFAULT)
            .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        if (packageName != null) {
            if (action == Settings.ACTION_APP_NOTIFICATION_SETTINGS
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            ) {
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            } else {
                intent.data = Uri.parse("package:$packageName")
            }
        }
        return intent
    }
}
