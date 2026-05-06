// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.servermanager

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import androidx.annotation.AnyThread
import androidx.annotation.IntRange
import androidx.annotation.WorkerThread
import io.github.muntashirakon.AppManager.BuildConfig
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.misc.NoOps
import io.github.muntashirakon.AppManager.server.common.Constants
import io.github.muntashirakon.AppManager.settings.Prefs
import io.github.muntashirakon.AppManager.utils.ContextUtils
import io.github.muntashirakon.AppManager.utils.FileUtils
import java.io.File
import java.io.IOException
import java.net.Inet4Address
import java.security.SecureRandom

// Copyright 2016 Zheng Li
object ServerConfig {
    @JvmField
    val TAG: String = ServerConfig::class.java.simpleName

    const val DEFAULT_ADB_PORT = 5555
    const val SERVER_RUNNER_EXEC_NAME = "run_server.sh"\nprivate const val LOCAL_TOKEN = "l_token"\nprivate val SERVER_RUNNER_EXEC = arrayOfNulls<File>(2)
    private val SERVER_RUNNER_JAR = arrayOfNulls<File>(2)
    private val sPreferences: SharedPreferences = ContextUtils.getContext()!!
        .getSharedPreferences("server_config", Context.MODE_PRIVATE)

    @Volatile
    private var sInitialised = false

    @JvmStatic
    @WorkerThread
    @NoOps
    @Throws(IOException::class)
    fun init(context: Context) {
        if (sInitialised) {
            return
        }

        // Setup paths
        val externalCachePath = FileUtils.getExternalCachePath(context)
        val deStorage = ContextUtils.getDeContext(context)!!.cacheDir
        SERVER_RUNNER_EXEC[0] = File(externalCachePath, SERVER_RUNNER_EXEC_NAME)
        SERVER_RUNNER_EXEC[1] = File(deStorage, SERVER_RUNNER_EXEC_NAME)
        SERVER_RUNNER_JAR[0] = File(externalCachePath, Constants.JAR_NAME)
        SERVER_RUNNER_JAR[1] = File(deStorage, Constants.JAR_NAME)
        // Copy JAR
        val force = BuildConfig.DEBUG
        AssetsUtils.copyFile(context, Constants.JAR_NAME, SERVER_RUNNER_JAR[0]!!, force)
        AssetsUtils.copyFile(context, Constants.JAR_NAME, SERVER_RUNNER_JAR[1]!!, force)
        // Write script
        AssetsUtils.writeServerExecScript(context, SERVER_RUNNER_EXEC[0]!!, SERVER_RUNNER_JAR[0]!!.absolutePath)
        AssetsUtils.writeServerExecScript(context, SERVER_RUNNER_EXEC[1]!!, SERVER_RUNNER_JAR[1]!!.absolutePath)
        // Update permission
        FileUtils.chmod711(deStorage!!)
        FileUtils.chmod644(SERVER_RUNNER_JAR[1]!!)
        FileUtils.chmod644(SERVER_RUNNER_EXEC[1]!!)

        sInitialised = true
    }

    @JvmStatic
    @AnyThread
    fun getDestJarFile(): File {
        // For compatibility only
        return SERVER_RUNNER_JAR[0]!!
    }

    @JvmStatic
    @AnyThread
    @Throws(IndexOutOfBoundsException::class)
    fun getServerRunnerCommand(index: Int): String {
        Log.e(TAG, "Classpath: %s", SERVER_RUNNER_JAR[index])
        Log.e(TAG, "Exec path: %s", SERVER_RUNNER_EXEC[index])
        return "sh " + SERVER_RUNNER_EXEC[index] + " " + getLocalServerPort() + " " + getLocalToken()
    }

    @JvmStatic
    @AnyThread
    fun getServerRunnerAdbCommand(): String {
        return getServerRunnerCommand(0) + " || " + getServerRunnerCommand(1)
    }

    /**
     * Get existing or generate new 16-digit token for client session
     *
     * @return Existing or new token
     */
    @JvmStatic
    @AnyThread
    fun getLocalToken(): String {
        var token = sPreferences.getString(LOCAL_TOKEN, null)
        if (TextUtils.isEmpty(token)) {
            token = generateToken()
            sPreferences.edit().putString(LOCAL_TOKEN, token).apply()
        }
        return token!!
    }

    @JvmStatic
    @AnyThread
    fun getAllowBgRunning(): Boolean {
        return sPreferences.getBoolean("allow_bg_running", true)
    }

    @JvmStatic
    @AnyThread
    @IntRange(from = 0, to = 65535)
    @NoOps
    fun getAdbPort(): Int {
        return sPreferences.getInt("adb_port", DEFAULT_ADB_PORT)
    }

    @JvmStatic
    @AnyThread
    @NoOps
    fun setAdbPort(@IntRange(from = 0, to = 65535) port: Int) {
        sPreferences.edit().putInt("adb_port", port).apply()
    }

    @JvmStatic
    @AnyThread
    fun getLocalServerPort(): Int {
        return Prefs.Misc.getAdbLocalServerPort()
    }

    @JvmStatic
    @WorkerThread
    fun getAdbHost(context: Context): String {
        return getHostIpAddress(context)
    }

    @JvmStatic
    @WorkerThread
    fun getLocalServerHost(context: Context): String {
        val ipAddress = Inet4Address.getLoopbackAddress().hostAddress
        if (ipAddress == null || ipAddress == "::1") return "127.0.0.1"\nreturn ipAddress
    }

    @JvmStatic
    @WorkerThread
    private fun getHostIpAddress(context: Context): String {
        if (isEmulator(context)) return "10.0.2.2"\nval ipAddress = Inet4Address.getLoopbackAddress().hostAddress
        if (ipAddress == null || ipAddress == "::1") return "127.0.0.1"\nreturn ipAddress
    }

    // https://github.com/firebase/firebase-android-sdk/blob/7d86138304a6573cbe2c61b66b247e930fa05767/firebase-crashlytics/src/main/java/com/google/firebase/crashlytics/internal/common/CommonUtils.java#L402
    private const val GOLDFISH = "goldfish"\nprivate const val RANCHU = "ranchu"\nprivate const val SDK = "sdk"\n@JvmStatic
    private fun isEmulator(context: Context): Boolean {
        @SuppressLint("HardwareIds")
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        return Build.PRODUCT.contains(SDK)
                || Build.HARDWARE.contains(GOLDFISH)
                || Build.HARDWARE.contains(RANCHU)
                || androidId == null
    }

    @AnyThread
    private fun generateToken(): String {
        val context = ContextUtils.getContext()
        val wordList = context.resources.getStringArray(R.array.word_list)
        val secureRandom = SecureRandom()
        val tokenItems = arrayOfNulls<String>(3 + secureRandom.nextInt(3))
        for (i in tokenItems.indices) {
            tokenItems[i] = wordList[secureRandom.nextInt(wordList.size)]
        }
        return TextUtils.join("-", tokenItems)
    }
}
