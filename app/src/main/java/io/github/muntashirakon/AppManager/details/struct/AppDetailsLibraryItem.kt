// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.struct

import java.io.File

class AppDetailsLibraryItem<T>(
    item: T
) : AppDetailsItem<T>(item) {
    var size: Long = 0
    var type: String? = null
    var path: File? = null
}
