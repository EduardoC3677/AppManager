// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops

import io.github.muntashirakon.AppManager.logs.Logger
import java.util.*

class BatchOpsLogger : Logger {
    override fun println(priority: Int, tag: String?, message: String?, th: Throwable?) {
        val log = (if (tag != null) "[$tag] " else "") + message + (if (th != null) "
" + android.util.Log.getStackTraceString(th) else "")
        synchronized(sAllLogs) {
            sAllLogs.add(log)
        }
    }

    override fun println(message: String?, th: Throwable?) {
        println(0, null, message, th)
    }

    override fun println(message: String?) {
        println(message, null)
    }

    companion object {
        private val sAllLogs = Collections.synchronizedList(mutableListOf<String>())

        @JvmStatic
        fun getAllLogs(): List<String> {
            return ArrayList(sAllLogs)
        }

        @JvmStatic
        fun clear() {
            sAllLogs.clear()
        }
    }
}
