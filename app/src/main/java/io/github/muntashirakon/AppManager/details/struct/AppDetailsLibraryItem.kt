// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.struct

import java.io.File

class AppDetailsLibraryItem<T>(
    item: T
) : AppDetailsItem<T>(item) {
    @JvmField var size: Long = 0
    @JvmField var type: String? = null
    @JvmField var path: File? = null
}
