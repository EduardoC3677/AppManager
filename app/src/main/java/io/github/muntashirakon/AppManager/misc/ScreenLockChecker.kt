// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import io.github.muntashirakon.AppManager.logs.Log
import java.util.*

class ScreenLockChecker(context: Context, private val mRunnable: Runnable?) {
    private val mContext: Context = context.applicationContext
    private val mTimer = Timer()
    private var mCheckLockTask: CheckLockTask? = null

    fun checkLock() {
        checkLockInternal(-1)
    }

    private fun checkLockInternal(delayIndex: Int) {
        val keyguardManager = mContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val powerManager = mContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        val isProtected = keyguardManager.isKeyguardSecure
        val isLocked = keyguardManager.isKeyguardLocked
        val isInteractive = powerManager.isInteractive
        val safeDelayIndex = getSafeCheckLockDelay(delayIndex)
        Log.i(TAG, "checkLock: isProtected=$isProtected, isLocked=$isLocked, isInteractive=$isInteractive, delay=${sCheckLockDelays[safeDelayIndex]}")

        mCheckLockTask?.let {
            Log.i(TAG, "checkLock: cancelling CheckLockTask[${Integer.toHexString(System.identityHashCode(it))}]")
            it.cancel()
        }

        if (isProtected && !isLocked && !isInteractive) {
            mCheckLockTask = CheckLockTask(safeDelayIndex)
            Log.i(TAG, "checkLock: scheduling CheckLockTask[${Integer.toHexString(System.identityHashCode(mCheckLockTask))}] for ${sCheckLockDelays[safeDelayIndex]} ms")
            mTimer.schedule(mCheckLockTask, sCheckLockDelays[safeDelayIndex].toLong())
        } else {
            Log.d(TAG, "checkLock: no need to schedule CheckLockTask")
            if (isProtected && isLocked) {
                mRunnable?.run()
            }
        }
    }

    private fun getSafeCheckLockDelay(delayIndex: Int): Int {
        return if (delayIndex >= sCheckLockDelays.size) sCheckLockDelays.size - 1
        else Math.max(delayIndex, 0)
    }

    private inner class CheckLockTask(val delayIndex: Int) : TimerTask() {
        override fun run() {
            Log.i(TAG, "CLT.run [${Integer.toHexString(System.identityHashCode(this))}]: redirect intent to LockMonitor")
            checkLockInternal(getSafeCheckLockDelay(delayIndex + 1))
        }
    }

    companion object {
        val TAG: String = ScreenLockChecker::class.java.simpleName
        private const val SECOND = 1000
        private const val MINUTE = 60 * SECOND
        private val sCheckLockDelays = intArrayOf(SECOND, 5 * SECOND, 10 * SECOND, 20 * SECOND, 30 * SECOND, MINUTE, 3 * MINUTE, 5 * MINUTE, 10 * MINUTE, 30 * MINUTE)
    }
}
