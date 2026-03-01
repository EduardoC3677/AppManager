// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.progress

import android.annotation.SuppressLint
import android.app.Service
import androidx.annotation.AnyThread
import androidx.annotation.CallSuper
import androidx.annotation.MainThread
import io.github.muntashirakon.AppManager.utils.ThreadUtils
import java.util.*

/**
 * A generic class to handle any kind of progress. Progress can be handled in various ways such as using notifications
 * or progress indicator or both.
 */
abstract class ProgressHandler {
    fun interface ProgressTextInterface {
        fun getProgressText(progressHandler: ProgressHandler): CharSequence?
    }

    var progressTextInterface: ProgressTextInterface = PROGRESS_DEFAULT

    /**
     * Call this function if the progress handler is backed by a foreground service and a progressbar is needed to be
     * initiated right away. After finished working with it, call [onDetach]
     */
    @MainThread
    abstract fun onAttach(service: Service?, message: Any)

    /**
     * Initialise progress. Arguments here can be modified by calling [onProgressUpdate].
     *
     * @param max     Maximum progress value. Use `-1` to switch to non-determinate mode.
     * @param current Current progress value. Should be `0`. Irrelevant in non-determinate mode.
     * @param message Additional arguments to pass on. Depends on implementation.
     */
    @MainThread
    abstract fun onProgressStart(max: Int, current: Float, message: Any?)

    /**
     * Update progress
     *
     * @param max     Maximum progress value. Use `-1` to switch to non-determinate mode.
     * @param current Current progress value. Irrelevant in non-determinate mode.
     * @param message Additional arguments to pass on. Depends on implementation.
     */
    @MainThread
    abstract fun onProgressUpdate(max: Int, current: Float, message: Any?)

    /**
     * Call when the progress is finished. If this is not attached to a foreground service, the progress also stops.
     */
    @MainThread
    abstract fun onResult(message: Any?)

    /**
     * Call this function to stop progress when this is attached to a foreground service.
     */
    @MainThread
    abstract fun onDetach(service: Service?)

    /**
     * Get a new progress handler from this handler. The handler will never have a queue handler.
     */
    abstract fun newSubProgressHandler(): ProgressHandler

    abstract fun getLastMessage(): Any?

    abstract fun getLastMax(): Int

    abstract fun getLastProgress(): Float

    fun setProgressTextInterface(progressTextInterface: ProgressTextInterface?) {
        this.progressTextInterface = progressTextInterface ?: PROGRESS_DEFAULT
    }

    /**
     * Update progress from any thread. Arguments from the last time are used.
     *
     * @param current Current progress value. Irrelevant in non-determinate mode.
     */
    @AnyThread
    fun postUpdate(current: Float) {
        postUpdate(getLastMax(), current, getLastMessage())
    }

    /**
     * Update progress from any thread. Arguments from the last time are used.
     *
     * @param max     Max progress values. Use `-1` to switch to non-determinate mode.
     * @param current Current progress value. Irrelevant in non-determinate mode.
     */
    @AnyThread
    fun postUpdate(max: Int, current: Float) {
        postUpdate(max, current, getLastMessage())
    }

    /**
     * Update progress from any thread.
     *
     * @param max     Max progress values. Use `-1` to switch to non-determinate mode.
     * @param current Current progress value. Irrelevant in non-determinate mode.
     * @param message Additional arguments to pass on. Depends on implementation.
     */
    @SuppressLint("WrongThread")
    @AnyThread
    @CallSuper
    open fun postUpdate(max: Int, current: Float, message: Any?) {
        if (ThreadUtils.isMainThread()) {
            onProgressUpdate(max, current, message)
        } else {
            ThreadUtils.postOnMainThread { onProgressUpdate(max, current, message) }
        }
    }

    companion object {
        @JvmField
        val PROGRESS_PERCENT = ProgressTextInterface { progressHandler ->
            val current = progressHandler.getLastProgress() / progressHandler.getLastMax() * 100
            String.format(Locale.getDefault(), "%d%%", current.toInt())
        }

        @JvmField
        val PROGRESS_REGULAR = ProgressTextInterface { progressHandler ->
            String.format(Locale.getDefault(), "%d/%d", progressHandler.getLastProgress().toInt(), progressHandler.getLastMax())
        }

        @JvmField
        protected val PROGRESS_DEFAULT = ProgressTextInterface { null }

        const val MAX_INDETERMINATE = -1
        const val MAX_FINISHED = -2
    }
}
