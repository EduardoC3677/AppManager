// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.convert

import androidx.annotation.IntDef

@IntDef(ImportType.OAndBackup, ImportType.TitaniumBackup, ImportType.SwiftBackup, ImportType.SmartLauncher)
@Retention(AnnotationRetention.SOURCE)
annotation class ImportType {
    companion object {
        const val OAndBackup = 0
        const val TitaniumBackup = 1
        const val SwiftBackup = 2
        const val SmartLauncher = 3
    }
}
