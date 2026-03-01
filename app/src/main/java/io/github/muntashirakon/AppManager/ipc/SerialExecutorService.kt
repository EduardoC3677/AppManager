// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ipc

import com.topjohnwu.superuser.Shell.EXECUTOR
import java.util.*
import java.util.concurrent.*

/**
 * Copyright 2020 John "topjohnwu" Wu
 */
class SerialExecutorService : AbstractExecutorService(), Callable<Void?> {
    private var mIsShutdown = false
    private val mTasks = ArrayDeque<Runnable>()
    private var mScheduleTask: FutureTask<Void?>? = null

    override fun call(): Void? {
        while (true) {
            val task = synchronized(this) {
                mTasks.poll() ?: run {
                    mScheduleTask = null
                    return null
                }
            }
            task.run()
        }
    }

    @Synchronized
    override fun execute(r: Runnable) {
        if (mIsShutdown) {
            throw RejectedExecutionException("Task $r rejected from $this")
        }
        mTasks.offer(r)
        if (mScheduleTask == null) {
            mScheduleTask = FutureTask(this)
            EXECUTOR.execute(mScheduleTask)
        }
    }

    @Synchronized
    override fun shutdown() {
        mIsShutdown = true
        mTasks.clear()
    }

    @Synchronized
    override fun shutdownNow(): List<Runnable> {
        mIsShutdown = true
        mScheduleTask?.cancel(true)
        return try {
            ArrayList(mTasks)
        } finally {
            mTasks.clear()
        }
    }

    @get:Synchronized
    override fun isShutdown(): Boolean {
        return mIsShutdown
    }

    @get:Synchronized
    override fun isTerminated(): Boolean {
        return mIsShutdown && mScheduleTask == null
    }

    @Synchronized
    @Throws(InterruptedException::class)
    override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean {
        val task = mScheduleTask ?: return true
        return try {
            task.get(timeout, unit)
            true
        } catch (e: TimeoutException) {
            false
        } catch (ignored: ExecutionException) {
            true
        }
    }
}
