// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat

import android.content.Context
import android.hardware.input.IInputManager
import android.hardware.input.InputManagerHidden
import android.os.Build
import android.os.RemoteException
import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.ViewConfiguration
import androidx.annotation.RequiresPermission
import io.github.muntashirakon.AppManager.ipc.ProxyBinder

// Based on https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/services/core/java/com/android/server/input/InputShellCommand.java;l=350;drc=0b80090e02814093f2187c2ce7e64f87cb917edc
object InputManagerCompat {
    @JvmStatic
    @RequiresPermission(ManifestCompat.permission.INJECT_EVENTS)
    fun sendKeyEvent(keyCode: Int, longpress: Boolean): Boolean {
        val now = SystemClock.uptimeMillis()
        val event = KeyEvent(
            now, now, KeyEvent.ACTION_DOWN, keyCode, 0, 0,
            KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_UNKNOWN
        )
        var success = true

        success = success and injectKeyEvent(event)
        if (longpress) {
            sleep(ViewConfiguration.getLongPressTimeout().toLong())
            // Some long press behavior would check the event time, we set a new event time here.
            val nextEventTime = now + ViewConfiguration.getLongPressTimeout()
            success = success and injectKeyEvent(
                KeyEvent.changeTimeRepeat(
                    event, nextEventTime, 1,
                    KeyEvent.FLAG_LONG_PRESS
                )
            )
        }
        success = success and injectKeyEvent(KeyEvent.changeAction(event, KeyEvent.ACTION_UP))
        return success
    }

    @JvmStatic
    @RequiresPermission(ManifestCompat.permission.INJECT_EVENTS)
    fun injectKeyEvent(event: KeyEvent): Boolean {
        return injectInputEvent(event, InputManagerHidden.INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH, -1)
    }

    @JvmStatic
    @RequiresPermission(ManifestCompat.permission.INJECT_EVENTS)
    fun injectInputEvent(event: InputEvent, mode: Int, targetUid: Int): Boolean {
        val inputManager = inputManager
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                inputManager.injectInputEventToTarget(event, mode, targetUid)
            } else {
                inputManager.injectInputEvent(event, mode)
            }
        } catch (e: RemoteException) {
            false
        }
    }

    /**
     * Puts the thread to sleep for the provided time.
     *
     * @param milliseconds The time to sleep in milliseconds.
     */
    private fun sleep(milliseconds: Long) {
        try {
            Thread.sleep(milliseconds)
        } catch (e: InterruptedException) {
            throw RuntimeException(e)
        }
    }

    private val inputManager: IInputManager
        get() = IInputManager.Stub.asInterface(ProxyBinder.getService(Context.INPUT_SERVICE))
}
