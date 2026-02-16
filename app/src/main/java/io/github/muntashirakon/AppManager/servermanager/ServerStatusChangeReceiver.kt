// SPDX-License-Identifier: MIT

package io.github.muntashirakon.AppManager.servermanager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Process
import android.os.RemoteException
import android.os.SystemClock
import androidx.annotation.AnyThread
import io.github.muntashirakon.AppManager.ipc.LocalServices
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.server.common.ConfigParams
import io.github.muntashirakon.AppManager.server.common.ServerActions
import io.github.muntashirakon.AppManager.settings.Ops
import io.github.muntashirakon.AppManager.utils.ThreadUtils
import io.github.muntashirakon.adb.AdbPairingRequiredException
import java.io.IOException

// Copyright 2016 Zheng Li
class ServerStatusChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        // Verify token before doing action
        val token = intent.getStringExtra(ConfigParams.PARAM_TOKEN)
        if (ServerConfig.getLocalToken() != token) {
            Log.d(TAG, "Mismatch token. Expected: %s, Received: %s", ServerConfig.getLocalToken(), token)
            return
        }
        val uidString = intent.getStringExtra(ConfigParams.PARAM_UID)
        if (uidString == null) {
            Log.w(TAG, "No UID received from the server.")
            return
        }
        Log.d(TAG, "onReceive --> %s %s", action, uidString)
        val uid = uidString.toInt()

        when (action) {
            ServerActions.ACTION_SERVER_STARTED -> {
                // Server was started for the first time
                Ops.setWorkingUid(uid)
                startServerIfNotAlready(context)
                // TODO: 8/4/24 Need to broadcast this message to update UI and/or trigger development
            }
            ServerActions.ACTION_SERVER_STOPPED -> {
                // Server was stopped
                LocalServer.die()
                Ops.setWorkingUid(Process.myUid())
            }
            ServerActions.ACTION_SERVER_CONNECTED -> {
                // Server was connected with App Manager
                Ops.setWorkingUid(uid)
            }
            ServerActions.ACTION_SERVER_DISCONNECTED -> {
                // Exited from App Manager
                Ops.setWorkingUid(Process.myUid())
            }
        }
    }

    @AnyThread
    private fun startServerIfNotAlready(context: Context) {
        ThreadUtils.postOnBackgroundThread {
            try {
                while (!LocalServer.alive(context)) {
                    // Server isn't yet in listening mode
                    Log.w(TAG, "Waiting for server...")
                    SystemClock.sleep(100)
                }
                LocalServer.getInstance()
                LocalServices.bindServicesIfNotAlready()
            } catch (e: IOException) {
                Log.w(TAG, "Failed to start server", e)
            } catch (e: AdbPairingRequiredException) {
                Log.w(TAG, "Failed to start server", e)
            } catch (e: RemoteException) {
                Log.w(TAG, "Failed to start services", e)
            }
        }
    }

    companion object {
        private val TAG = ServerStatusChangeReceiver::class.java.simpleName
    }
}
