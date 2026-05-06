// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.lifecycle

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import java.lang.ref.WeakReference

class SoftInputLifeCycleObserver(private val mViewRef: WeakReference<View>) : DefaultLifecycleObserver {

    override fun onResume(owner: LifecycleOwner) {
        if (mViewRef.get() == null) {
            return
        }
        mViewRef.get()!!.postDelayed({
            val v = mViewRef.get() ?: return@postDelayed
            v.requestFocus()
            val imm = v.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT)
        }, 100)
    }

    override fun onPause(owner: LifecycleOwner) {
        if (mViewRef.get() == null) {
            return
        }
        mViewRef.get()!!.postDelayed({
            val v = mViewRef.get() ?: return@postDelayed
            val imm = v.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(v.windowToken, 0)
        }, 100)
    }
}
