// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later
package io.github.muntashirakon.AppManager.server.common

import android.content.Context
import android.os.Looper

// Copyright 2020 John "topjohnwu" Wu
// Must be accessed via reflection
object ServerUtils {
    const val CMDLINE_START_SERVICE: String = "start"
    const val CMDLINE_START_DAEMON: String = "daemon"
    const val CMDLINE_STOP_SERVICE: String = "stop"
    const val CMDLINE_STOP_SERVER: String = "stopServer"

    @JvmStatic
    fun getSystemContext(): Context {
        return try {
            synchronized(Looper::class.java) {
                if (Looper.getMainLooper() == null) Looper.prepareMainLooper()
            }
            val atClazz = Class.forName("android.app.ActivityThread")
            val systemMain = atClazz.getMethod("systemMain")
            val activityThread = systemMain.invoke(null)
            val getSystemContext = atClazz.getMethod("getSystemContext")
            getSystemContext.invoke(activityThread) as Context
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    // Put "app-manager-" in front of the service name to prevent possible conflicts
    @JvmStatic
    fun getServiceName(pkg: String): String {
        return "app-manager-$pkg"
    }
}
