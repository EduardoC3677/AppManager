// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.parser

import androidx.collection.ArraySet

class ManifestIntentFilter {
    val actions: MutableSet<String> = ArraySet()
    val categories: MutableSet<String> = ArraySet()
    val data: MutableList<ManifestData> = mutableListOf()
    var priority: Int = 0

    class ManifestData {
        var scheme: String? = null
        var host: String? = null
        var port: String? = null
        var path: String? = null
        var pathPattern: String? = null
        var pathPrefix: String? = null
        var pathSuffix: String? = null
        var pathAdvancedPattern: String? = null
        var mimeType: String? = null
    }
}
