// SPDX-License-Identifier: Apache-2.0

package io.github.muntashirakon.AppManager.ipc

import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.IBinder
import android.util.Log
import java.lang.reflect.Method

/**
 * All hidden Android framework APIs used here are very stable.
 *
 * These methods should only be accessed in the root process, since under normal circumstances
 * accessing these internal APIs through reflection will be blocked.
 *
 * Copyright 2020 John "topjohnwu" Wu
 */
@SuppressLint("PrivateApi,DiscouragedPrivateApi,SoonBlockedPrivateApi", "RestrictedApi")
internal object HiddenAPIs {
    @JvmField
    val TAG: String = HiddenAPIs::class.java.simpleName

    private var sAddService: Method? = null
    private var sAttachBaseContext: Method? = null
    private var sSetAppName: Method? = null

    // Set this flag to silence AMS's complaints. Only exist on Android 8.0+
    @JvmField
    val FLAG_RECEIVER_FROM_SHELL = if (Build.VERSION.SDK_INT >= 26) 0x00400000 else 0

    init {
        try {
            val sm = Class.forName("android.os.ServiceManager")
            if (Build.VERSION.SDK_INT >= 28) {
                try {
                    sAddService = sm.getDeclaredMethod(
                        "addService",
                        String::class.java, IBinder::class.java, Boolean::class.javaPrimitiveType, Int::class.javaPrimitiveType
                    )
                } catch (ignored: NoSuchMethodException) {
                    // Fallback to the 2 argument version
                }
            }
            if (sAddService == null) {
                sAddService = sm.getDeclaredMethod("addService", String::class.java, IBinder::class.java)
            }

            sAttachBaseContext = ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java)
            sAttachBaseContext!!.isAccessible = true

            val ddm = Class.forName("android.ddm.DdmHandleAppName")
            sSetAppName = ddm.getDeclaredMethod("setAppName", String::class.java, Int::class.javaPrimitiveType)
        } catch (e: ReflectiveOperationException) {
            Log.e(TAG, e.message, e)
        }
    }

    @JvmStatic
    fun setAppName(name: String) {
        try {
            sSetAppName!!.invoke(null, name, 0)
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    @JvmStatic
    fun addService(name: String, service: IBinder) {
        try {
            if (sAddService!!.parameterTypes.size == 4) {
                // Set dumpPriority to 0 so the service cannot be listed
                sAddService!!.invoke(null, name, service, false, 0)
            } else {
                sAddService!!.invoke(null, name, service)
            }
        } catch (e: Exception) {
            Log.e(TAG, e.message, e)
        }
    }

    @JvmStatic
    fun attachBaseContext(wrapper: Any, context: Context) {
        if (wrapper is ContextWrapper) {
            try {
                sAttachBaseContext!!.invoke(wrapper, context)
            } catch (ignored: Exception) { /* Impossible */ }
        }
    }
}
