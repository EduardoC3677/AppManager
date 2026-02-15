// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.struct

import java.io.File

class AppDetailsLibraryItem<T> @JvmOverloads constructor(
    item: T,
    name: String = ""
) : AppDetailsItem<T>(item, name) {
    @JvmField var size: Long = 0
    @JvmField var type: String? = null
    @JvmField var path: File? = null
}
