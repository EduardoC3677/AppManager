// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.dialog

import androidx.annotation.IntDef

@IntDef(
    BackupInfoState.NONE,
    BackupInfoState.BACKUP_MULTIPLE,
    BackupInfoState.RESTORE_MULTIPLE,
    BackupInfoState.BOTH_MULTIPLE,
    BackupInfoState.BACKUP_SINGLE,
    BackupInfoState.RESTORE_SINGLE,
    BackupInfoState.BOTH_SINGLE
)
@Retention(AnnotationRetention.SOURCE)
annotation class BackupInfoState {
    companion object {
        /**
         * None of the selected apps have backups nor any of them is installed.
         */
        const val NONE = 0

        /**
         * None of the selected apps have backups but some of them are installed.
         */
        const val BACKUP_MULTIPLE = 1

        /**
         * None of the apps are installed but a few have (base) backups.
         */
        const val RESTORE_MULTIPLE = 2

        /**
         * Some apps are installed and some apps have (base) backups.
         */
        const val BOTH_MULTIPLE = 3

        /**
         * The app is installed but has no backups
         */
        const val BACKUP_SINGLE = 4

        /**
         * The apps is uninstalled but has backups
         */
        const val RESTORE_SINGLE = 5

        /**
         * Some apps are installed and some apps have (base) backups.
         */
        const val BOTH_SINGLE = 6
    }
}
