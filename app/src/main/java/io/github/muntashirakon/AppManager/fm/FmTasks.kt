// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm

import androidx.annotation.IntDef
import io.github.muntashirakon.io.Path
import java.util.*

class FmTasks private constructor() {
    private val taskList: Queue<FmTask> = LinkedList()

    fun enqueue(fmTask: FmTask) {
        // Currently, we only allow a single task. So, clear the queue first.
        taskList.clear()
        taskList.add(fmTask)
    }

    fun peek(): FmTask? {
        return taskList.peek()
    }

    fun dequeue(): FmTask? {
        val task = peek()
        return if (task != null && task.type == FmTask.TYPE_COPY) {
            // Copy is allowed multiple times but others aren't
            task
        } else {
            taskList.poll()
        }
    }

    fun isEmpty(): Boolean {
        return taskList.isEmpty()
    }

    class FmTask(@TaskType val type: Int, files: List<Path>) {
        @IntDef(TYPE_COPY, TYPE_CUT)
        @Retention(AnnotationRetention.SOURCE)
        annotation class TaskType

        val timestamp: Long = System.currentTimeMillis()
        val files: List<Path> = ArrayList(files)
        private var mFlags = 0

        fun canPaste(): Boolean {
            return type == TYPE_COPY || type == TYPE_CUT
        }

        fun addFlag(flag: Int) {
            mFlags = mFlags or flag
        }

        fun removeFlag(flag: Int) {
            mFlags = mFlags and flag.inv()
        }

        fun hasFlag(flag: Int): Boolean {
            return (mFlags and flag) != 0
        }

        companion object {
            const val TYPE_COPY = 0
            const val TYPE_CUT = 1
        }
    }

    companion object {
        @JvmStatic
        val instance = FmTasks()
    }
}
