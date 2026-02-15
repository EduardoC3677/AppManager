// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.list

import android.graphics.Bitmap

class AppListItem(
    @JvmField val packageName: String
) {
    @JvmField var icon: Bitmap? = null
    @JvmField var packageLabel: String? = null
    @JvmField var versionCode: Long = 0
    @JvmField var versionName: String? = null
    @JvmField var minSdk: Int = 0
    @JvmField var targetSdk: Int = 0
    @JvmField var signatureSha256: String? = null
    @JvmField var firstInstallTime: Long = 0
    @JvmField var lastUpdateTime: Long = 0
    @JvmField var installerPackageName: String? = null
    @JvmField var installerPackageLabel: String? = null
}
