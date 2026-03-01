// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.IntRange
import androidx.core.content.pm.PackageInfoCompat
import io.github.muntashirakon.AppManager.settings.Ops
import io.github.muntashirakon.AppManager.settings.Prefs
import java.util.*

class DeviceInfo(context: Context) {
    val abis: Array<String> = Build.SUPPORTED_ABIS
    val abis32Bits: Array<String> = Build.SUPPORTED_32_BIT_ABIS
    val abis64Bits: Array<String> = Build.SUPPORTED_64_BIT_ABIS
    val brand: String = Build.BRAND
    val buildID: String = Build.DISPLAY
    val buildVersion: String = Build.VERSION.INCREMENTAL
    val device: String = Build.DEVICE
    val hardware: String = Build.HARDWARE
    val manufacturer: String = Build.MANUFACTURER
    val model: String = Build.MODEL
    val product: String = Build.PRODUCT
    val releaseVersion: String = Build.VERSION.RELEASE
    @IntRange(from = 0)
    val sdkVersion: Int = Build.VERSION.SDK_INT
    val versionCode: Long
    val versionName: String?
    val inferredMode: CharSequence = Ops.getInferredMode(context)

    init {
        val packageInfo = try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
        if (packageInfo != null) {
            versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)
            versionName = packageInfo.versionName
        } else {
            versionCode = -1
            versionName = null
        }
    }

    override fun toString(): String {
        return "App version: $versionName
" +
                "App version code: $versionCode
" +
                "Android build version: $buildVersion
" +
                "Android release version: $releaseVersion
" +
                "Android SDK version: $sdkVersion
" +
                "Android build ID: $buildID
" +
                "Device brand: $brand
" +
                "Device manufacturer: $manufacturer
" +
                "Device name: $device
" +
                "Device model: $model
" +
                "Device product name: $product
" +
                "Device hardware name: $hardware
" +
                "ABIs: ${Arrays.toString(abis)}
" +
                "ABIs (32bit): ${Arrays.toString(abis32Bits)}
" +
                "ABIs (64bit): ${Arrays.toString(abis64Bits)}
" +
                "System language: ${Locale.getDefault().toLanguageTag()}
" +
                "In-App Language: ${Prefs.Appearance.getLanguage()}
" +
                "Mode: ${Ops.getMode()}
" +
                "Inferred Mode: $inferredMode"
    }
}
