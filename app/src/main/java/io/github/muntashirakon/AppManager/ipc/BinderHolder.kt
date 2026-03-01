// SPDX-License-Identifier: Apache-2.0

package io.github.muntashirakon.AppManager.ipc

import android.os.IBinder
import android.os.RemoteException
import com.topjohnwu.superuser.internal.UiThreadHandler

/**
 * Copyright 2022 John "topjohnwu" Wu
 */
internal abstract class BinderHolder @Throws(RemoteException::class) constructor(private val mBinder: IBinder) :
    IBinder.DeathRecipient {
    init {
        mBinder.linkToDeath(this, 0)
    }

    override fun binderDied() {
        mBinder.unlinkToDeath(this, 0)
        UiThreadHandler.run { onBinderDied() }
    }

    protected abstract fun onBinderDied()
}
