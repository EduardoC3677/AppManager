// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer

object SupportedAppStores {
    val SUPPORTED_APP_STORES = mapOf(
        "com.aurora.store" to "Aurora Store",
        "com.looker.droidify" to "Droid-ify",
        "org.fdroid.fdroid" to "F-Droid",
        "org.fdroid.basic" to "F-Droid Basic",
        "eu.bubu1.fdroidclassic" to "F-Droid Classic",
        "com.machiav3lli.fdroid" to "Neo Store"
    )

    @JvmStatic
    fun isAppStoreSupported(packageName: String): Boolean {
        return SUPPORTED_APP_STORES.containsKey(packageName)
    }
}
