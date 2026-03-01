// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.self

import io.github.muntashirakon.AppManager.utils.ThreadUtils
import java.util.*
import java.util.stream.Collectors

internal class Migration {
    private val mMigrationTasks = mutableListOf<MigrationTask>()

    fun addTask(migrationTask: MigrationTask) {
        mMigrationTasks.add(migrationTask)
    }

    fun migrate(fromVersion: Long) {
        if (fromVersion == 0L) {
            // This is a new version, no migration needed
            return
        }
        val migrationTasks = mMigrationTasks.stream() // Any tasks with toVersion > fromVersion hasn't been run yet
            .filter { task -> task.toVersion > fromVersion } // Migration performed in ascending order
            .sorted(Comparator.comparingLong<MigrationTask> { it.fromVersion }.thenComparingLong { it.toVersion })
            .collect(Collectors.toList())
        for (migrationTask in migrationTasks) {
            if (migrationTask.mainThread) {
                ThreadUtils.postOnMainThread(migrationTask)
            } else {
                migrationTask.run()
            }
        }
    }
}
