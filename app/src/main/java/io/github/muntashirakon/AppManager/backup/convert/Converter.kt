// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.convert

import io.github.muntashirakon.AppManager.backup.BackupException

abstract class Converter {
    abstract val packageName: String

    @Throws(BackupException::class)
    abstract fun convert()

    abstract fun cleanup()
}
