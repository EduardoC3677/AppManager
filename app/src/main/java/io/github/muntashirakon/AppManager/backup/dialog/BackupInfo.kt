// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.dialog

import androidx.collection.ArraySet
import io.github.muntashirakon.AppManager.backup.struct.BackupMetadataV5

class BackupInfo(val packageName: String, userId: Int) {
    val userIds = ArraySet<Int>()

    var appLabel: CharSequence = packageName
    var backupMetadataList: List<BackupMetadataV5> = emptyList()
    var isInstalled: Boolean = false
    var hasBaseBackup: Boolean = false

    init {
        userIds.add(userId)
    }
}
