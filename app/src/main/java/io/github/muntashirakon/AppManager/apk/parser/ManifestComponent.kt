// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.parser

import android.content.ComponentName

class ManifestComponent(val cn: ComponentName) {
    val intentFilters: MutableList<ManifestIntentFilter> = mutableListOf()
}
