// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.adb

import androidx.annotation.IntDef

@IntDef(
    BackupCategories.CAT_SRC,
    BackupCategories.CAT_INT_CE,
    BackupCategories.CAT_INT_DE,
    BackupCategories.CAT_EXT,
    BackupCategories.CAT_OBB,
    BackupCategories.CAT_UNK
)
@Retention(AnnotationRetention.SOURCE)
annotation class BackupCategories {
    companion object {
        const val CAT_SRC = 0
        const val CAT_INT_CE = 1
        const val CAT_INT_DE = 2
        const val CAT_EXT = 3
        const val CAT_OBB = 4
        const val CAT_UNK = 5
    }
}
