// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server

import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.system.Os
import androidx.annotation.NonNull
import io.github.muntashirakon.AppManager.server.common.ConfigParams
import io.github.muntashirakon.AppManager.server.common.Constants
import io.github.muntashirakon.AppManager.server.common.FLog
import java.io.File
import java.io.FileInputStream
import java.io.IOException

import static io.github.muntashirakon.AppManager.server.common.ConfigParams.PARAM_UID

/**
 * ServerRunner runs the server based on the parameters given. It takes two arguments:
 * <ol>
 *     <li>
 *         <b>Parameters.</b> Each parameter is a key-value pair separated by a comma and key-values
 *         are separated by a colon. See [ConfigParams] to see a list of parameters.
 *     </li>
 *     <li>
 *         <b>Process ID.</b> The old process ID that has to be killed. This is an optional argument
 *     </li>
 * </ol>
 */
// Copyright 2017 Zheng Li
object ServerRunner {
    /**
     * The main method
     *
     * @param args See [ServerRunner]
     */
    @JvmStatic
    fun main(args: Array<String>) {
        try {
            FLog.writeLog = true
            FLog.log("Arguments: ${args.contentToString()}")
            if (args.isNullOrEmpty()) {
                return
            }
            // Get arguments
            val paramsStr = args[0]
            var oldPid = -1
            if (args.size > 1) {
                try {
                    oldPid = args[1].toInt()
                } catch (ignore: Exception) {
                }
            }
            // Make it main looper
            Looper.prepareMainLooper()
            Class.forName("android.app.ActivityThread")
                .getMethod("systemMain")
                .invoke(null)
            // Parse arguments
            val split = paramsStr.split(",").toTypedArray()
            val configParams = ConfigParams()
            for (s in split) {
                val param = s.split(":").toTypedArray()
                configParams.put(param[0], param[1])
            }
            configParams.put(PARAM_UID, Process.myUid().toString())
            // Set server info
            LifecycleAgent.sServerInfo.startArgs = paramsStr
            LifecycleAgent.sServerInfo.startTime = System.currentTimeMillis()
            LifecycleAgent.sServerInfo.startRealTime = SystemClock.elapsedRealtime()
            // Print debug
            println("UID: ${configParams.uid}, PID: ${Process.myUid()}")
            println("Params: $configParams")
            // Kill old server if requested
            if (oldPid != -1) {
                killOldServer(oldPid)
                SystemClock.sleep(1000)
            }
            // Start server
            val thread = Thread({
                ServerRunner().runServer(configParams)
                // Exit current thread, regardless of whether the server started or not
                FLog.close()
                killSelfProcess()
            }, "AM-IPC")
            thread.start()
            Looper.loop()
        } catch (e: Throwable) {
            e.printStackTrace()
            FLog.log(e)
        } finally {
            // Exit current process, regardless of whether the server started or not
            FLog.log("Log closed.")
            FLog.close()
            killSelfProcess()
        }
    }

    /**
     * Kill old server by process ID, process name is verified before killed.
     *
     * @param oldPid Process ID of the old server
     */
    private fun killOldServer(oldPid: Int) {
        try {
            val processName = getProcessName(oldPid)
            if (Constants.SERVER_NAME == processName) {
                Process.killProcess(oldPid)
                FLog.log("Killed old server with pid $oldPid")
            }
        } catch (throwable: Throwable) {
            FLog.log(throwable)
        }
    }

    /**
     * Kill current process
     */
    private fun killSelfProcess() {
        val pid = Process.myPid()
        println("Killing self process with pid $pid")
        killProcess(pid)
    }

    /**
     * Kill a process by process ID
     *
     * @param pid Process ID to be killed
     */
    private fun killProcess(pid: Int) {
        try {
            Process.killProcess(pid)
        } catch (e: Throwable) {
            e.printStackTrace()
        } finally {
            try {
                // Kill using SIGNAL 9
                Os.execve("/system/bin/kill", arrayOf("-9", pid.toString()), null)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Get the process name from process ID
     *
     * @param pid Process ID
     * @return Process name or empty string
     */
    @NonNull
    private fun getProcessName(pid: Int): String {
        val cmdLine = File("/proc/$pid/cmdline")
        if (!cmdLine.exists()) return ""
        try {
            FileInputStream(cmdLine).use { fis ->
                val buff = ByteArray(512)
                val len = fis.read(buff)
                if (len > 0) {
                    var i = 0
                    while (i < len && buff[i] != 0.toByte()) {
                        i++
                    }
                    return String(buff, 0, i)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ""
    }

    /**
     * Run the local server. The server is actually run by [ServerHandler].
     *
     * @param configParams The parameters to be used during and after the server starts.
     */
    private fun runServer(configParams: ConfigParams) {
        val lifecycleAgent = LifecycleAgent(configParams)
        ServerHandler(lifecycleAgent).use { serverHandler ->
            println("Success! Server has started.")
            val pid = Process.myPid()
            println("Process: ${getProcessName(pid)}, PID: $pid")
            // Send broadcast message to the system that the server has started
            lifecycleAgent.onStarted()
            // Start server
            serverHandler.start()
        }
        // Send broadcast message to the system that the server has stopped
        lifecycleAgent.onStopped()
    }
}
