// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import java.util.Objects

// Copyright 2020 John "topjohnwu" Wu
object ContextUtils {
    const val TAG = "ContextUtils"

    @SuppressLint("StaticFieldLeak")
    @JvmField
    var rootContext: Context? = null

    @SuppressLint("StaticFieldLeak")
    private var sContext: Context? = null

    @SuppressLint("PrivateApi", "RestrictedApi")
    @JvmStatic
    fun getContext(): Context {
        if (sContext == null) {
            // Fetching ActivityThread on the main thread is no longer required on API 18+
            // See: https://cs.android.com/android/platform/frameworks/base/+/66a017b63461a22842b3678c9520f803d5ddadfc
            try {
                val c = Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication")
                    .invoke(null) as Context
                sContext = getContextImpl(Objects.requireNonNull(c))
            } catch (e: Exception) {
                // Shall never happen
                throw RuntimeException(e)
            }
        }
        return sContext!!
    }

    @JvmStatic
    fun getDeContext(context: Context?): Context? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context?.createDeviceProtectedStorageContext()
        } else {
            context
        }
    }

    @JvmStatic
    fun getContextImpl(context: Context?): Context? {
        var ctx = context
        while (ctx is ContextWrapper) {
            ctx = ctx.baseContext
        }
        return ctx
    }

    @JvmStatic
    fun unregisterReceiver(context: Context, receiver: BroadcastReceiver) {
        ExUtils.exceptionAsIgnored { context.unregisterReceiver(receiver) }
    }
}
