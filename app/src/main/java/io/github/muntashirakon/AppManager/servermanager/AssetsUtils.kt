// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.servermanager

import android.content.Context
import androidx.annotation.WorkerThread
import io.github.muntashirakon.AppManager.BuildConfig
import io.github.muntashirakon.AppManager.server.common.ConfigParams
import io.github.muntashirakon.AppManager.server.common.Constants
import io.github.muntashirakon.io.IoUtils
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.IOException
import java.io.InputStreamReader

// Copyright 2016 Zheng Li
@Suppress("ResultOfMethodCallIgnored")
internal object AssetsUtils {
    @JvmStatic
    @WorkerThread
    @Throws(IOException::class)
    fun copyFile(context: Context, fileName: String, destFile: File, force: Boolean) {
        context.assets.openFd(fileName).use { openFd ->
            if (force) {
                destFile.delete()
            } else {
                if (destFile.exists()) {
                    if (destFile.length() != openFd.length) {
                        destFile.delete()
                    } else {
                        return
                    }
                }
            }
            openFd.createInputStream().use { open ->
                FileOutputStream(destFile).use { fos ->
                    val buff = ByteArray(IoUtils.DEFAULT_BUFFER_SIZE)
                    var len: Int
                    while (open.read(buff).also { len = it } != -1) {
                        fos.write(buff, 0, len)
                    }
                    fos.flush()
                    fos.fd.sync()
                }
            }
        }
    }

    @JvmStatic
    @WorkerThread
    @Throws(IOException::class)
    fun writeServerExecScript(context: Context, destFile: File, classPath: String) {
        context.assets.openFd(ServerConfig.SERVER_RUNNER_EXEC_NAME).use { openFd ->
            BufferedReader(InputStreamReader(openFd.createInputStream())).use { bufferedReader ->
                if (destFile.exists()) {
                    destFile.delete()
                }
                BufferedWriter(FileWriter(destFile, false)).use { bw ->
                    // Set variables
                    val script = StringBuilder()
                    script.append("SERVER_NAME=").append(Constants.SERVER_NAME).append("
")
                        .append("JAR_NAME=").append(Constants.JAR_NAME).append("
")
                        .append("JAR_PATH=").append(classPath).append("
")
                        .append("ARGS=").append(serverArgs).append("
")
                    var line: String?
                    while (bufferedReader.readLine().also { line = it } != null) {
                        val wl: String = if ("%ENV_VARS%" == line?.trim()) {
                            script.toString()
                        } else {
                            line!!
                        }
                        bw.write(wl)
                        bw.newLine()
                    }
                    bw.flush()
                }
            }
        }
    }

    private val serverArgs: String
        get() {
            val argsBuilder = StringBuilder()
            argsBuilder.append(',').append(ConfigParams.PARAM_APP).append(':').append(BuildConfig.APPLICATION_ID)
            if (ServerConfig.getAllowBgRunning()) {
                argsBuilder.append(',').append(ConfigParams.PARAM_RUN_IN_BACKGROUND).append(':').append(1)
            }
            if (BuildConfig.DEBUG) {
                argsBuilder.append(',').append(ConfigParams.PARAM_DEBUG).append(':').append(1)
            }
            return argsBuilder.toString()
        }
}
