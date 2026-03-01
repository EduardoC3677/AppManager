// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.os.RemoteException
import android.provider.Settings
import android.text.TextUtils
import androidx.annotation.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Observer
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.adb.AdbConnectionManager
import io.github.muntashirakon.AppManager.adb.AdbPairingRequiredException
import io.github.muntashirakon.AppManager.adb.AdbPairingService
import io.github.muntashirakon.AppManager.adb.AdbUtils
import io.github.muntashirakon.AppManager.compat.ManifestCompat
import io.github.muntashirakon.AppManager.ipc.LocalServices
import io.github.muntashirakon.AppManager.logcat.helper.ServiceHelper
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.runner.RunnerUtils
import io.github.muntashirakon.AppManager.servermanager.LocalServer
import io.github.muntashirakon.AppManager.servermanager.ServerConfig
import io.github.muntashirakon.AppManager.session.SessionMonitoringService
import io.github.muntashirakon.AppManager.users.Owners
import io.github.muntashirakon.AppManager.users.Users
import io.github.muntashirakon.AppManager.utils.*
import io.github.muntashirakon.dialog.DialogTitleBuilder
import io.github.muntashirakon.dialog.ScrollableDialogBuilder
import io.github.muntashirakon.dialog.TextInputDialogBuilder
import java.io.IOException
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Controls mode of operation and other related functions
 */
object Ops {
    @JvmField
    val TAG: String = Ops::class.java.simpleName

    @StringDef(MODE_AUTO, MODE_ROOT, MODE_ADB_OVER_TCP, MODE_ADB_WIFI, MODE_SHIZUKU, MODE_NO_ROOT)
    @Retention(AnnotationRetention.SOURCE)
    annotation class Mode

    const val MODE_AUTO = "auto"
    const val MODE_ROOT = "root"
    const val MODE_ADB_OVER_TCP = "adb_tcp"
    const val MODE_ADB_WIFI = "adb_wifi"
    const val MODE_SHIZUKU = "shizuku" // ENHANCEMENT: Added Shizuku as first-class mode
    const val MODE_NO_ROOT = "no-root"

    @IntDef(
        STATUS_SUCCESS,
        STATUS_FAILURE,
        STATUS_AUTO_CONNECT_WIRELESS_DEBUGGING,
        STATUS_WIRELESS_DEBUGGING_CHOOSER_REQUIRED,
        STATUS_ADB_PAIRING_REQUIRED,
        STATUS_ADB_CONNECT_REQUIRED,
        STATUS_FAILURE_ADB_NEED_MORE_PERMS
    )
    @Retention(AnnotationRetention.SOURCE)
    annotation class Status

    const val STATUS_SUCCESS = 0
    const val STATUS_FAILURE = 1
    const val STATUS_AUTO_CONNECT_WIRELESS_DEBUGGING = 2
    const val STATUS_WIRELESS_DEBUGGING_CHOOSER_REQUIRED = 3
    const val STATUS_ADB_PAIRING_REQUIRED = 4
    const val STATUS_ADB_CONNECT_REQUIRED = 5
    const val STATUS_FAILURE_ADB_NEED_MORE_PERMS = 6

    @JvmField
    var ROOT_UID = 0
    @JvmField
    var SHELL_UID = 2000
    @JvmField
    var PHONE_UID = Process.PHONE_UID
    @JvmField
    var SYSTEM_UID = Process.SYSTEM_UID

    @Volatile
    private var sWorkingUid = Process.myUid()
    @Volatile
    private var sDirectRoot = false // AM has root AND that root is being used
    private var sIsAdb = false // UID = 2000
    private var sIsSystem = false // UID = 1000
    private var sIsRoot = false // UID = 0
    private var sIsShizuku = false // ENHANCEMENT: Shizuku mode

    // Security
    private val sSecurityLock = Any()
    private var sIsAuthenticated = false

    @JvmStatic
    @AnyThread
    fun getWorkingUid(): Int {
        return sWorkingUid
    }

    @JvmStatic
    @AnyThread
    fun setWorkingUid(newUid: Int) {
        sWorkingUid = newUid
    }

    @JvmStatic
    @AnyThread
    fun getWorkingUidOrRoot(): Int {
        val uid = getWorkingUid()
        if (uid != ROOT_UID && sDirectRoot) {
            return ROOT_UID
        }
        return uid
    }

    @JvmStatic
    @AnyThread
    fun isWorkingUidRoot(): Boolean {
        return getWorkingUid() == ROOT_UID
    }

    /**
     * Whether App Manager is currently using direct root (e.g. root granted to the app) to perform operations. The
     * result returned by this method may not reflect the actual state due to other factors.
     */
    @JvmStatic
    @AnyThread
    fun isDirectRoot(): Boolean {
        return sDirectRoot
    }

    /**
     * Whether App Manager is running in system mode
     */
    @JvmStatic
    @AnyThread
    fun isSystem(): Boolean {
        return sIsSystem
    }

    /**
     * Whether App Manager is running in ADB mode
     */
    @JvmStatic
    @AnyThread
    fun isAdb(): Boolean {
        return sIsAdb
    }

    /**
     * Whether App Manager is running in Shizuku mode
     */
    @JvmStatic
    @AnyThread
    fun isShizuku(): Boolean {
        return sIsShizuku
    }

    /**
     * Whether the current App Manager session is authenticated by the user. It does two things:
     * <ol>
     *     <li>If security is enabled, it marks that the user has got passed the security challenge.
     *     <li>It checks if a mode of operation is set before proceeding further.
     * </ol>
     */
    @JvmStatic
    @AnyThread
    fun isAuthenticated(): Boolean {
        synchronized(sSecurityLock) {
            return sIsAuthenticated
        }
    }

    @JvmStatic
    @MainThread
    fun setAuthenticated(context: Context, authenticated: Boolean) {
        synchronized(sSecurityLock) {
            sIsAuthenticated = authenticated
            if (Prefs.Privacy.isPersistentSessionAllowed()) {
                val service = Intent(context, SessionMonitoringService::class.java)
                if (authenticated) {
                    ContextCompat.startForegroundService(context, service)
                } else {
                    context.stopService(service)
                }
            }
        }
    }

    @JvmStatic
    fun getInferredMode(context: Context): CharSequence {
        val uid = Users.getSelfOrRemoteUid()
        if (uid == ROOT_UID) {
            return context.getString(R.string.root)
        }
        if (uid == SHELL_UID) {
            return "ADB"
        }
        if (uid != Process.myUid()) {
            val uidOwnerMap = Owners.getUidOwnerMap(false)
            val uidStr = uidOwnerMap[uid]
            if (!TextUtils.isEmpty(uidStr)) {
                return uidStr!!.substring(0, 1).uppercase(Locale.ROOT) +
                        (if (uidStr.length > 1) uidStr.substring(1) else "")
            }
        }
        return context.getString(R.string.no_root)
    }

    @JvmStatic
    @NoOps
    fun getMode(): String {
        var mode = AppPref.getString(AppPref.PrefKey.PREF_MODE_OF_OPS_STR)
        // Backward compatibility for v2.6.0
        if (mode == "adb") {
            mode = MODE_ADB_OVER_TCP
        }
        if ((MODE_ADB_OVER_TCP == mode || MODE_ADB_WIFI == mode)
            && !SelfPermissions.checkSelfPermission(Manifest.permission.INTERNET)
        ) {
            // ADB enabled but the INTERNET permission is not granted, replace current with auto.
            return MODE_AUTO
        }
        return mode
    }

    @JvmStatic
    @NoOps
    fun setMode(newMode: String) {
        AppPref.set(AppPref.PrefKey.PREF_MODE_OF_OPS_STR, newMode)
    }

    @JvmStatic
    @WorkerThread
    @NoOps // Although we've used Ops checks, its overall usage does not affect anything
    @Status
    fun init(context: Context, force: Boolean): Int {
        val mode = getMode()
        sDirectRoot = hasRoot()
        if (MODE_AUTO == mode) {
            autoDetectRootSystemOrAdbAndPersist(context)
            return if (sIsAdb) STATUS_SUCCESS else initPermissionsWithSuccess()
        }
        if (MODE_NO_ROOT == mode) {
            sDirectRoot = false
            sIsShizuku = false
            sIsRoot = sIsShizuku
            sIsSystem = sIsRoot
            sIsAdb = sIsSystem
            // Also, stop existing services if any
            if (LocalServices.alive()) {
                LocalServices.stopServices()
            }
            if (LocalServer.alive(context)) {
                // We don't care about its results
                ThreadUtils.postOnBackgroundThread {
                    ExUtils.exceptionAsIgnored {
                        LocalServer.getInstance().closeBgServer()
                    }
                }
            }
            return STATUS_SUCCESS
        }
        if (!force && isAMServiceUpAndRunning(context, mode)) {
            // An instance of AMService is already running
            return if (sIsAdb) STATUS_SUCCESS else initPermissionsWithSuccess()
        }
        try {
            when (mode) {
                MODE_ROOT -> {
                    if (!sDirectRoot) {
                        throw Exception("Root is unavailable.")
                    }
                    // Disable server first
                    ExUtils.exceptionAsIgnored {
                        if (LocalServer.alive(context)) {
                            LocalServer.getInstance().closeBgServer()
                        }
                    }
                    sIsAdb = false
                    sIsSystem = sIsAdb
                    sIsRoot = true
                    LocalServices.bindServicesIfNotAlready()
                    return initPermissionsWithSuccess()
                }
                MODE_ADB_WIFI -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (!Utils.isWifiActive(context.applicationContext)) {
                        throw Exception("Wifi not enabled.")
                    }
                    if (AdbUtils.enableWirelessDebugging(context)) {
                        // Wireless debugging enabled, try auto-connect
                        return STATUS_AUTO_CONNECT_WIRELESS_DEBUGGING
                    } else {
                        // Wireless debugging is turned off or there's no permission
                        return STATUS_WIRELESS_DEBUGGING_CHOOSER_REQUIRED
                    }
                } // else fallback to ADB over TCP
                MODE_ADB_OVER_TCP -> {
                    sIsSystem = false
                    sIsRoot = sIsSystem
                    sIsAdb = true
                    ServerConfig.setAdbPort(findAdbPort(context, 10, AdbUtils.getAdbPortOrDefault()))
                    LocalServer.restart()
                    LocalServices.bindServicesIfNotAlready()
                    return checkRootOrIncompleteUsbDebuggingInAdb()
                }
                MODE_SHIZUKU -> {
                    // ENHANCEMENT: Shizuku mode support
                    // Check if Shizuku is available and has permission
                    if (!ShizukuUtils.isShizukuInstalled()) {
                        ThreadUtils.postOnMainThread {
                            UIUtils.displayLongToast(
                                "Shizuku is not installed. Please install Shizuku app first."
                            )
                        }
                        throw Exception("Shizuku is not installed.")
                    }
                    if (ShizukuUtils.needsPermission()) {
                        ThreadUtils.postOnMainThread {
                            UIUtils.displayLongToast(
                                "Shizuku permission required. Please grant permission in Shizuku app."
                            )
                        }
                        throw Exception("Shizuku permission not granted.")
                    }
                    if (!ShizukuUtils.isShizukuAvailable()) {
                        ThreadUtils.postOnMainThread {
                            UIUtils.displayLongToast(
                                "Shizuku is not running. Please start Shizuku first."
                            )
                        }
                        throw Exception("Shizuku is unavailable.")
                    }
                    // Disable other services
                    ExUtils.exceptionAsIgnored {
                        if (LocalServer.alive(context)) {
                            LocalServer.getInstance().closeBgServer()
                        }
                    }
                    if (LocalServices.alive()) {
                        LocalServices.stopServices()
                    }
                    sIsAdb = false
                    sIsSystem = sIsAdb
                    sIsRoot = sIsSystem
                    sIsShizuku = true
                    ThreadUtils.postOnMainThread { UIUtils.displayShortToast("Working in Shizuku mode") }
                    // Shizuku doesn't need LocalServices or LocalServer
                    return STATUS_SUCCESS
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, e)
            // Fallback to no-root mode for this session, this does not modify the user preference
            sIsShizuku = false
            sIsRoot = sIsShizuku
            sIsSystem = sIsRoot
            sIsAdb = sIsSystem
            ThreadUtils.postOnMainThread {
                val message: String = if (e is Exception && e.message != null && e.message!!.isNotEmpty()) {
                    e.message!!
                } else {
                    context.getString(R.string.failed_to_use_the_current_mode_of_operation)
                }
                UIUtils.displayLongToast(message)
            }
        }
        return STATUS_FAILURE
    }

    /**
     * Whether App Manager has been granted root permission.
     *
     * @return `true` iff root is granted.
     */
    @JvmStatic
    @AnyThread
    @NoOps
    fun hasRoot(): Boolean {
        return RunnerUtils.isRootGiven()
    }

    @JvmStatic
    @WorkerThread
    @NoOps // Although we've used Ops checks, its overall usage does not affect anything
    private fun autoDetectRootSystemOrAdbAndPersist(context: Context) {
        sIsRoot = sDirectRoot
        if (sDirectRoot) {
            // Root permission was granted
            setMode(MODE_ROOT)
            // Disable remote server
            ExUtils.exceptionAsIgnored {
                if (LocalServer.alive(context)) {
                    LocalServer.getInstance().closeBgServer()
                }
            }
            // Disable ADB and force root
            sIsAdb = false
            sIsSystem = sIsAdb
            if (LocalServices.alive()) {
                if (Users.getSelfOrRemoteUid() == ROOT_UID) {
                    // Service is already running in root mode
                    return
                }
                // Service is running in ADB/other mode, but we need root
                LocalServices.stopServices()
            }
            try {
                // Service is confirmed dead
                LocalServices.bindServices()
                if (LocalServices.alive() && Users.getSelfOrRemoteUid() == ROOT_UID) {
                    // Service is running in root
                    return
                }
            } catch (e: RemoteException) {
                Log.e(TAG, e)
            }
            // Root is granted but Binder communication cannot be initiated
            Log.e(TAG, "Root granted but could not use root to initiate a connection. Trying ADB...")
            if (AdbUtils.startAdb(AdbUtils.getAdbPortOrDefault())) {
                Log.i(TAG, "Started ADB over TCP via root.")
            } else {
                Log.w(TAG, "Could not start ADB over TCP via root.")
            }
            sIsRoot = false
            // Fall-through, in case we can use other options
        }
        // ENHANCEMENT: Check for Shizuku before falling back to ADB
        if (ShizukuUtils.isShizukuAvailable()) {
            Log.i(TAG, "Shizuku is available, using Shizuku mode")
            setMode(MODE_SHIZUKU)
            sIsAdb = false
            sIsSystem = sIsAdb
            sIsRoot = sIsSystem
            sIsShizuku = true
            ThreadUtils.postOnMainThread { UIUtils.displayShortToast("Working in Shizuku mode") }
            return
        }
        // Root and Shizuku were not working/granted, but check for AM service just in case
        if (LocalServices.alive()) {
            setMode(MODE_ADB_OVER_TCP)
            val uid = Users.getSelfOrRemoteUid()
            if (uid == ROOT_UID) {
                sIsAdb = false
                sIsSystem = sIsAdb
                sIsRoot = true
                return
            }
            if (uid == SYSTEM_UID) {
                sIsRoot = false
                sIsAdb = sIsRoot
                sIsSystem = true
                return
            }
            if (uid == SHELL_UID) {
                sIsRoot = false
                sIsSystem = sIsRoot
                sIsAdb = true
                ThreadUtils.postOnMainThread { UIUtils.displayShortToast(R.string.working_on_adb_mode) }
                return
            }
        }
        // Root not granted
        if (!SelfPermissions.checkSelfPermission(Manifest.permission.INTERNET)) {
            // INTERNET permission is not granted
            setMode(MODE_NO_ROOT)
            // Skip checking for ADB
            sIsAdb = false
            return
        }
        // Check for ADB
        if (!AdbUtils.isAdbdRunning()) {
            // ADB not running. In auto mode, we do not attempt to enable it either
            setMode(MODE_NO_ROOT)
            sIsRoot = false
            sIsSystem = sIsRoot
            sIsAdb = sIsSystem
            return
        }
        sIsAdb = true // First enable ADB if not already
        try {
            ServerConfig.setAdbPort(findAdbPort(context, 7, ServerConfig.getAdbPort()))
            LocalServer.restart()
            LocalServices.bindServicesIfNotAlready()
        } catch (e: Throwable) {
            Log.e(TAG, e)
        }
        sIsAdb = LocalServices.alive()
        if (sIsAdb) {
            // No need to return anything here because we're in auto-mode.
            // Any message produced by the method below is just a helpful message.
            checkRootOrIncompleteUsbDebuggingInAdb()
        }
        setMode(if (getWorkingUid() != Process.myUid()) MODE_ADB_OVER_TCP else MODE_NO_ROOT)
    }

    @JvmStatic
    @UiThread
    @RequiresApi(Build.VERSION_CODES.R)
    @NoOps // Although we've used Ops checks, its overall usage does not affect anything
    fun connectWirelessDebugging(
        activity: FragmentActivity,
        callback: AdbConnectionInterface
    ) {
        val builder = DialogTitleBuilder(activity)
            .setTitle(R.string.wireless_debugging)
            .setEndIcon(R.drawable.ic_open_in_new) { v ->
                val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(":settings:fragment_args_key", "toggle_adb_wireless")
                activity.startActivity(intent)
            }
            .setEndIconContentDescription(R.string.open_developer_options_page)

        MaterialAlertDialogBuilder(activity)
            .setCustomTitle(builder.build())
            .setMessage(R.string.choose_what_to_do)
            .setCancelable(false)
            .setPositiveButton(R.string.adb_connect) { dialog1, which1 -> callback.onStatusReceived(STATUS_ADB_CONNECT_REQUIRED) }
            .setNeutralButton(R.string.adb_pair) { dialog1, which1 -> callback.onStatusReceived(STATUS_ADB_PAIRING_REQUIRED) }
            .setNegativeButton(R.string.cancel) { dialog, which -> callback.connectAdb(-1) }
            .show()
    }

    @JvmStatic
    @WorkerThread
    @NoOps // Although we've used Ops checks, its overall usage does not affect anything
    @Status
    fun autoConnectWirelessDebugging(context: Context): Int {
        val lastAdb = sIsAdb
        val lastSystem = sIsSystem
        val lastRoot = sIsRoot
        sIsAdb = true
        sIsRoot = false
        sIsSystem = sIsRoot
        return try {
            ServerConfig.setAdbPort(findAdbPort(context, 5, ServerConfig.getAdbPort()))
            LocalServer.restart()
            LocalServices.bindServicesIfNotAlready()
            checkRootOrIncompleteUsbDebuggingInAdb()
        } catch (e: RemoteException) {
            Log.e(TAG, "Could not auto-connect to adbd", e)
            // Go back to the last mode
            sIsAdb = lastAdb
            sIsSystem = lastSystem
            sIsRoot = lastRoot
            STATUS_WIRELESS_DEBUGGING_CHOOSER_REQUIRED
        } catch (e: IOException) {
            Log.e(TAG, "Could not auto-connect to adbd", e)
            // Go back to the last mode
            sIsAdb = lastAdb
            sIsSystem = lastSystem
            sIsRoot = lastRoot
            STATUS_WIRELESS_DEBUGGING_CHOOSER_REQUIRED
        } catch (e: AdbPairingRequiredException) {
            Log.e(TAG, "Could not auto-connect to adbd", e)
            // Go back to the last mode
            sIsAdb = lastAdb
            sIsSystem = lastSystem
            sIsRoot = lastRoot
            STATUS_ADB_PAIRING_REQUIRED
        }
    }

    @JvmStatic
    @WorkerThread
    @NoOps // Although we've used Ops checks, its overall usage does not affect anything
    @Status
    fun connectAdb(context: Context, port: Int, @Status returnCodeOnFailure: Int): Int {
        if (port < 0) return returnCodeOnFailure
        val lastAdb = sIsAdb
        val lastSystem = sIsSystem
        val lastRoot = sIsRoot
        sIsAdb = true
        sIsRoot = false
        sIsSystem = sIsRoot
        return try {
            ServerConfig.setAdbPort(port)
            LocalServer.restart()
            LocalServices.bindServicesIfNotAlready()
            checkRootOrIncompleteUsbDebuggingInAdb()
        } catch (e: RemoteException) {
            Log.e(TAG, "Could not connect to adbd using port $port", e)
            // Go back to the last mode
            sIsAdb = lastAdb
            sIsSystem = lastSystem
            sIsRoot = lastRoot
            returnCodeOnFailure
        } catch (e: IOException) {
            Log.e(TAG, "Could not connect to adbd using port $port", e)
            // Go back to the last mode
            sIsAdb = lastAdb
            sIsSystem = lastSystem
            sIsRoot = lastRoot
            returnCodeOnFailure
        } catch (e: AdbPairingRequiredException) {
            Log.e(TAG, "Could not connect to adbd using port $port", e)
            // Go back to the last mode
            sIsAdb = lastAdb
            sIsSystem = lastSystem
            sIsRoot = lastRoot
            returnCodeOnFailure
        }
    }

    @JvmStatic
    @UiThread
    @NoOps
    fun connectAdbInput(
        activity: FragmentActivity,
        callback: AdbConnectionInterface
    ) {
        TextInputDialogBuilder(activity, R.string.port_number)
            .setTitle(R.string.wireless_debugging)
            .setInputText(ServerConfig.getAdbPort().toString())
            .setHelperText(R.string.adb_connect_port_number_description)
            .setPositiveButton(R.string.ok) { dialog2, which2, inputText, isChecked ->
                if (TextUtils.isEmpty(inputText)) {
                    UIUtils.displayShortToast(R.string.port_number_empty)
                    callback.connectAdb(-1)
                    return@setPositiveButton
                }
                try {
                    callback.connectAdb(Integer.decode(inputText.toString().trim()))
                } catch (e: NumberFormatException) {
                    UIUtils.displayShortToast(R.string.port_number_invalid)
                    callback.connectAdb(-1)
                }
            }
            .setNegativeButton(R.string.cancel) { dialog, which, inputText, isChecked -> callback.connectAdb(-1) }
            .setCancelable(false)
            .show()
    }

    @JvmStatic
    @RequiresApi(Build.VERSION_CODES.R)
    @UiThread
    @NoOps
    fun pairAdbInput(
        activity: FragmentActivity,
        callback: AdbConnectionInterface
    ) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.wireless_debugging)
            .setMessage(R.string.adb_pairing_instruction)
            .setCancelable(false)
            .setNeutralButton(R.string.action_manual) { dialog, which ->
                val adbPairingServiceIntent = Intent(activity, AdbPairingService::class.java)
                    .setAction(AdbPairingService.ACTION_START_PAIRING)
                ContextCompat.startForegroundService(activity, adbPairingServiceIntent)
                callback.pairAdb()
            }
            .setNegativeButton(R.string.cancel) { dialog, which -> callback.onStatusReceived(STATUS_FAILURE) }
            .setPositiveButton(R.string.go) { dialog, which ->
                val developerOptionsIntent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val adbPairingServiceIntent = Intent(activity, AdbPairingService::class.java)
                    .setAction(AdbPairingService.ACTION_START_PAIRING)
                activity.startActivity(developerOptionsIntent)
                ContextCompat.startForegroundService(activity, adbPairingServiceIntent)
                callback.pairAdb()
            }
            .show()
    }

    @JvmStatic
    @WorkerThread
    @NoOps
    @RequiresApi(Build.VERSION_CODES.R)
    fun pairAdb(context: Context): Int {
        return try {
            val conn = AdbConnectionManager.getInstance()
            val status = pairAdbInternal(context, conn)
            if (status == STATUS_ADB_CONNECT_REQUIRED) {
                connectAdb(
                    context, findAdbPort(context, 7, ServerConfig.getAdbPort()),
                    STATUS_ADB_CONNECT_REQUIRED
                )
            } else {
                STATUS_FAILURE
            }
        } catch (e: Exception) {
            ThreadUtils.postOnMainThread { UIUtils.displayShortToast(R.string.failed) }
            Log.e(TAG, e)
            STATUS_FAILURE
        }
    }

    @JvmStatic
    @WorkerThread
    @NoOps
    @RequiresApi(Build.VERSION_CODES.R)
    private fun pairAdbInternal(context: Context, conn: AdbConnectionManager): Int {
        val observerObserver = AtomicReference(CountDownLatch(1))
        val pairingError = AtomicReference<Exception>()
        val observer = Observer<Exception> { e ->
            pairingError.set(e)
            observerObserver.get().countDown()
        }
        ThreadUtils.postOnMainThread { conn.pairingObserver.observeForever(observer) }
        while (true) {
            var success: Boolean = try {
                observerObserver.get().await(1, TimeUnit.HOURS)
            } catch (ignore: InterruptedException) {
                false
            }
            if (success) {
                if (pairingError.get() != null) {
                    if (ServiceHelper.checkIfServiceIsRunning(context, AdbPairingService::class.java)) {
                        observerObserver.set(CountDownLatch(1))
                        continue
                    }
                    success = false
                }
            }
            ThreadUtils.postOnMainThread { conn.pairingObserver.removeObserver(observer) }
            return if (success) {
                STATUS_ADB_CONNECT_REQUIRED
            } else {
                context.stopService(Intent(context, AdbPairingService::class.java))
                STATUS_FAILURE
            }
        }
    }

    @JvmStatic
    @UiThread
    fun displayIncompleteUsbDebuggingMessage(activity: FragmentActivity) {
        ScrollableDialogBuilder(activity)
            .setTitle(R.string.adb_incomplete_usb_debugging_title)
            .setMessage(R.string.adb_incomplete_usb_debugging_message)
            .enableAnchors()
            .setNegativeButton(R.string.close, null)
            .setPositiveButton(R.string.open) { dialog, which, isChecked ->
                val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    activity.startActivity(intent)
                } catch (ignore: Throwable) {
                }
            }
            .show()
    }

    private fun initPermissionsWithSuccess(): Int {
        SelfPermissions.init()
        return STATUS_SUCCESS
    }

    /**
     * @return `true` iff AMService is up and running
     */
    @JvmStatic
    @WorkerThread
    @NoOps // Although we've used Ops checks, its overall usage does not affect anything
    private fun isAMServiceUpAndRunning(context: Context, @Mode mode: String): Boolean {
        val lastAdb = sIsAdb
        val lastSystem = sIsSystem
        val lastRoot = sIsRoot
        // At this point, we have already checked MODE_AUTO, and MODE_NO_ROOT has lower priority.
        sIsRoot = MODE_ROOT == mode
        sIsAdb = !sIsRoot // Because the rests are ADB
        sIsSystem = false
        if (LocalServer.alive(context)) {
            // Remote server is running, but local server may not be running
            try {
                LocalServer.getInstance()
                LocalServices.bindServicesIfNotAlready()
            } catch (e: RemoteException) {
                Log.e(TAG, e)
                // fall-through, because the remote service may still be alive
            } catch (e: IOException) {
                Log.e(TAG, e)
            } catch (e: AdbPairingRequiredException) {
                Log.e(TAG, e)
            }
        }
        if (LocalServices.alive()) {
            // AM service is running
            val uid = Users.getSelfOrRemoteUid()
            if (sIsRoot && uid == ROOT_UID) {
                // AM service is running as root
                return true
            }
            if (uid == SYSTEM_UID) {
                // AM service is running as system
                sIsSystem = true
                sIsAdb = false
                sIsRoot = sIsAdb
                return true
            }
            if (sIsAdb) {
                // AM service is running as ADB
                return checkRootOrIncompleteUsbDebuggingInAdb() == STATUS_SUCCESS
            }
            // All checks are failed, stop services
            LocalServices.stopServices()
        }
        // Checks are failed, revert everything
        sIsAdb = lastAdb
        sIsSystem = lastSystem
        sIsRoot = lastRoot
        return false
    }

    @JvmStatic
    @NoOps // Although we've used Ops checks, its overall usage does not affect anything
    private fun checkRootOrIncompleteUsbDebuggingInAdb(): Int {
        // ADB already granted and AM service is running
        val uid = Users.getSelfOrRemoteUid()
        if (uid == ROOT_UID) {
            // AM service is being run as root
            sIsRoot = true
            sIsAdb = false
            sIsSystem = sIsAdb
            ThreadUtils.postOnMainThread { UIUtils.displayLongToast(R.string.warning_working_on_root_mode) }
        } else if (uid == SYSTEM_UID) {
            // AM service is being run as system
            sIsSystem = true
            sIsAdb = false
            sIsRoot = sIsAdb
            ThreadUtils.postOnMainThread { UIUtils.displayLongToast(R.string.warning_working_on_system_mode) }
        } else if (uid == SHELL_UID) { // ADB mode
            if (!SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.GRANT_RUNTIME_PERMISSIONS)) {
                // USB debugging is incomplete, revert back to no-root
                sIsRoot = false
                sIsSystem = sIsRoot
                sIsAdb = sIsSystem
                return STATUS_FAILURE_ADB_NEED_MORE_PERMS
            }
            ThreadUtils.postOnMainThread { UIUtils.displayShortToast(R.string.working_on_adb_mode) }
        } else {
            // No-root mode
            sIsRoot = false
            sIsSystem = sIsRoot
            sIsAdb = sIsSystem
            return STATUS_FAILURE
        }
        return initPermissionsWithSuccess()
    }

    @JvmStatic
    @WorkerThread
    @RequiresApi(Build.VERSION_CODES.R)
    @NoOps
    @Throws(IOException::class, InterruptedException::class)
    private fun findAdbPort(context: Context, timeoutInSeconds: Long): Int {
        return AdbUtils.getLatestAdbDaemon(context, timeoutInSeconds, TimeUnit.SECONDS).second
    }

    @JvmStatic
    @WorkerThread
    @NoOps
    @Throws(IOException::class)
    private fun findAdbPort(context: Context, timeoutInSeconds: Long, defaultPort: Int): Int {
        if (!AdbUtils.isAdbdRunning()) {
            throw IOException("ADB daemon not running.")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Find ADB port only in Android 11 (R) or later
            try {
                return findAdbPort(context, timeoutInSeconds)
            } catch (e: IOException) {
                Log.w(TAG, "Could find ADB port", e)
            } catch (e: InterruptedException) {
                Log.w(TAG, "Could find ADB port", e)
            }
        }
        return defaultPort
    }

    @AnyThread
    interface AdbConnectionInterface {
        // TODO: 8/4/24 Remove the first two methods since the third method can be used instead of them
        fun connectAdb(port: Int)

        @RequiresApi(Build.VERSION_CODES.R)
        fun pairAdb()

        fun onStatusReceived(@Status status: Int)
    }
}
