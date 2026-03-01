// SPDX-License-Identifier: WTFPL AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat

import android.content.Intent
import android.os.SystemClock
import android.util.Log
import io.github.muntashirakon.AppManager.types.ForegroundService
import java.util.*

// Copyright 2012 Nolan Lawson
class CrazyLoggerService : ForegroundService(TAG) {
    private var mKill = false

    override fun onHandleIntent(intent: Intent?) {
        while (!mKill) {
            SystemClock.sleep(INTERVAL)
            if (Random().nextInt(100) % 5 == 0) {
                Log.println(LOG_LEVELS[Random().nextInt(6)], TAG, LOG_MESSAGES[Random().nextInt(5)])
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mKill = true
    }

    companion object {
        val TAG: String = CrazyLoggerService::class.java.simpleName

        val LOG_LEVELS = intArrayOf(Log.VERBOSE, Log.DEBUG, Log.INFO, Log.WARN, Log.ERROR, Log.ASSERT)
        val LOG_MESSAGES = arrayOf(
            "Email: email@me.com",
            "FTP: ftp://website.com:21",
            "HTTP: https://website.com",
            "A simple log",
            "Another log"
        )

        private const val INTERVAL = 300L
    }
}
