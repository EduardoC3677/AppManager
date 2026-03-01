// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat

import android.content.pm.IPackageDataObserver

class ClearDataObserver : IPackageDataObserver.Stub() {
    private var mCompleted = false
    private var mSuccessful = false

    override fun onRemoveCompleted(packageName: String?, succeeded: Boolean) {
        synchronized(this) {
            mCompleted = true
            mSuccessful = succeeded
            (this as Object).notifyAll()
        }
    }

    val isCompleted: Boolean
        get() = mCompleted

    val isSuccessful: Boolean
        get() = mSuccessful
}
