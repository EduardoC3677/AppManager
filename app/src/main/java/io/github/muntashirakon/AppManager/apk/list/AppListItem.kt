// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.list

import android.graphics.Bitmap

class AppListItem(
    val packageName: String
) {
    var icon: Bitmap? = null
    var packageLabel: String? = null
    var versionCode: Long = 0
    var versionName: String? = null
    var minSdk: Int = 0
    var targetSdk: Int = 0
    var signatureSha256: String? = null
    var firstInstallTime: Long = 0
    var lastUpdateTime: Long = 0
    var installerPackageName: String? = null
    var installerPackageLabel: String? = null
}
