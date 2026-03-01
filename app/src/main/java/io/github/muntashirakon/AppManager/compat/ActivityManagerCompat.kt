// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat

import android.Manifest
import android.annotation.UserIdInt
import android.app.ActivityManager
import android.app.ActivityManagerNative
import android.app.IActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.IContentProvider
import android.content.IIntentReceiver
import android.content.Intent
import android.os.*
import android.provider.Settings
import android.text.TextUtils
import android.view.KeyEvent
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import io.github.muntashirakon.AppManager.BuildConfig
import io.github.muntashirakon.AppManager.fm.FmProvider
import io.github.muntashirakon.AppManager.ipc.ProxyBinder
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.runner.Runner
import io.github.muntashirakon.AppManager.self.SelfPermissions
import io.github.muntashirakon.AppManager.users.Users
import io.github.muntashirakon.AppManager.utils.ExUtils
import io.github.muntashirakon.AppManager.utils.ThreadUtils
import java.util.*
import java.util.regex.Pattern

class ActivityManagerCompat {
    interface ActivityLaunchUserInteractionRequiredCallback {
        @WorkerThread
        fun onInteraction()
    }

    companion object {
        private val APP_PROCESS_REGEX = Pattern.compile("\*[A-Z]+\* UID (\d+) ProcessRecord\{[0-9a-f]+ (\d+):([^/]+)/[^\}]+\}")
        private val PKG_LIST_REGEX = Pattern.compile("packageList=\{([^/]+)\}")
        private val SERVICE_RECORD_REGEX = Pattern.compile("\* ServiceRecord\{[0-9a-f]+ u(\d+) ([^\}]+)\}")
        private val PROCESS_RECORD_REGEX = Pattern.compile("app=ProcessRecord\{[0-9a-f]+ (\d+):([^/]+)/([^\}]+)\}")

        @JvmStatic
        @RequiresPermission(allOf = [Manifest.permission.WRITE_SECURE_SETTINGS, ManifestCompat.permission.INJECT_EVENTS])
        @MainThread
        fun startActivityViaAssist(
            context: Context, activity: ComponentName,
            callback: ActivityLaunchUserInteractionRequiredCallback?
        ): Boolean {
            // Need two permissions: WRITE_SECURE_SETTINGS and INJECT_EVENTS
            SelfPermissions.requireSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
            val canInjectEvents = SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.INJECT_EVENTS)
            val resolver = context.contentResolver
            // Backup assistant value
            val assistantComponent = Settings.Secure.getString(resolver, "assistant")
            if (canInjectEvents) {
                ThreadUtils.postOnBackgroundThread {
                    try {
                        // Set assistant value to the target activity component
                        Settings.Secure.putString(resolver, "assistant", activity.flattenToShortString())
                        // Run it as an assistant by injecting KEYCODE_ASSIST (219)
                        InputManagerCompat.sendKeyEvent(KeyEvent.KEYCODE_ASSIST, false)
                        // Wait until system opens the new assistant (i.e., activity), this is an empirical value
                        SystemClock.sleep(500)
                    } finally {
                        // Restore assistant value
                        Settings.Secure.putString(resolver, "assistant", assistantComponent)
                    }
                }
            } else if (callback != null) {
                // Cannot launch event by default, use callback
                ThreadUtils.postOnBackgroundThread {
                    try {
                        // Set assistant value to the target activity component
                        Settings.Secure.putString(resolver, "assistant", activity.flattenToShortString())
                        // Trigger callback
                        callback.onInteraction()
                    } finally {
                        // Restore assistant value
                        Settings.Secure.putString(resolver, "assistant", assistantComponent)
                    }
                }
            } // else do nothing
            return canInjectEvents
        }

        @JvmStatic
        @Throws(SecurityException::class)
        fun startActivity(intent: Intent, @UserIdInt userHandle: Int): Int {
            val am: IActivityManager
            val callingPackage: String
            if (intent.data != null && FmProvider.AUTHORITY == intent.data!!.authority) {
                // We need unprivileged authority for this
                am = activityManagerUnprivileged
                callingPackage = BuildConfig.APPLICATION_ID
            } else {
                am = activityManager
                callingPackage = SelfPermissions.getCallingPackage(Users.getSelfOrRemoteUid())
            }
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    am.startActivityAsUserWithFeature(
                        null, callingPackage,
                        null, intent, intent.type, null, null,
                        0, 0, null, null, userHandle
                    )
                } else {
                    am.startActivityAsUser(
                        null, callingPackage, intent, intent.type,
                        null, null, 0, 0, null,
                        null, userHandle
                    )
                }
            } catch (e: RemoteException) {
                ExUtils.rethrowFromSystemServer(e)
            }
        }

        @JvmStatic
        @Throws(RemoteException::class)
        fun startService(intent: Intent, @UserIdInt userHandle: Int, asForeground: Boolean): ComponentName? {
            val am = activityManager
            val callingPackage = SelfPermissions.getCallingPackage(Users.getSelfOrRemoteUid())
            return when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> am.startService(null, intent, intent.type, asForeground, callingPackage, null, userHandle)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> am.startService(null, intent, intent.type, asForeground, callingPackage, userHandle)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> am.startService(null, intent, intent.type, callingPackage, userHandle)
                else -> am.startService(null, intent, intent.type, userHandle)
            }
        }

        @JvmStatic
        @Throws(RemoteException::class)
        fun sendBroadcast(intent: Intent, @UserIdInt userHandle: Int): Int {
            val am = activityManager
            val receiver = IntentReceiver()
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                am.broadcastIntentWithFeature(null, null, intent, null, receiver, 0, null, null, null, AppOpsManagerCompat.OP_NONE, null, true, false, userHandle)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.broadcastIntent(null, intent, null, null, 0, null, null, null, AppOpsManagerCompat.OP_NONE, null, true, false, userHandle)
            } else {
                am.broadcastIntent(null, intent, null, null, 0, null, null, null, AppOpsManagerCompat.OP_NONE, true, false, userHandle)
            }
        }

        @JvmStatic
        @Throws(RemoteException::class)
        fun getContentProviderExternal(name: String, userId: Int, token: IBinder, tag: String?): IContentProvider? {
            val am = activityManager
            return try {
                when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> am.getContentProviderExternal(name, userId, token, tag).provider
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> (am.getContentProviderExternal(name, userId, token) as android.app.ContentProviderHolder).provider
                    else -> (am.getContentProviderExternal(name, userId, token) as IActivityManager.ContentProviderHolder).provider
                }
            } catch (e: NullPointerException) {
                null
            }
        }

        @JvmStatic
        fun getRunningServices(packageName: String, @UserIdInt userId: Int): List<ActivityManager.RunningServiceInfo> {
            val runningServices: List<ActivityManager.RunningServiceInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1 &&
                !SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.REAL_GET_TASKS) &&
                canDumpRunningServices()
            ) {
                // Fetch running services by parsing dumpsys output
                getRunningServicesUsingDumpSys(packageName)
            } else {
                // For no-root, this returns services running in the current UID since Android Oreo
                try {
                    activityManager.getServices(100, 0)
                } catch (e: RemoteException) {
                    emptyList()
                }
            }
            val res: MutableList<ActivityManager.RunningServiceInfo> = ArrayList()
            for (info in runningServices) {
                if (info.service.packageName == packageName && userId == UserHandleHidden.getUserId(info.uid)) {
                    res.add(info)
                }
            }
            return res
        }

        @JvmStatic
        fun getRunningAppProcesses(): List<ActivityManager.RunningAppProcessInfo> {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                !SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.REAL_GET_TASKS) &&
                canDumpRunningServices()
            ) {
                // Fetch running app processes by parsing dumpsys output if root/ADB is disabled
                // and android.permission.DUMP is granted
                getRunningAppProcessesUsingDumpSys()
            } else {
                // For no-root, this returns app processes running in the current UID since Android M
                ExUtils.requireNonNullElse({ activityManager.runningAppProcesses }, emptyList())
            }
        }

        @JvmStatic
        @RequiresPermission("android.permission.KILL_UID")
        @Throws(RemoteException::class)
        fun killUid(uid: Int, reason: String?) {
            activityManager.killUid(UserHandleHidden.getAppId(uid), UserHandleHidden.getUserId(uid), reason)
        }

        @get:JvmStatic
        val activityManager: IActivityManager
            get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                IActivityManager.Stub.asInterface(ProxyBinder.getService(Context.ACTIVITY_SERVICE))
            } else {
                ActivityManagerNative.asInterface(ProxyBinder.getService(Context.ACTIVITY_SERVICE))
            }

        @get:JvmStatic
        val activityManagerUnprivileged: IActivityManager
            get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                IActivityManager.Stub.asInterface(ProxyBinder.getUnprivilegedService(Context.ACTIVITY_SERVICE))
            } else {
                ActivityManagerNative.asInterface(ProxyBinder.getUnprivilegedService(Context.ACTIVITY_SERVICE))
            }

        private fun getRunningAppProcessesUsingDumpSys(): List<ActivityManager.RunningAppProcessInfo> {
            val result = Runner.runCommand(arrayOf("dumpsys", "activity", "processes"))
            if (!result.isSuccessful) return emptyList()
            val appProcessDump = result.getOutputAsList(1)
            return parseRunningAppProcesses(appProcessDump)
        }

        @JvmStatic
        @VisibleForTesting
        fun parseRunningAppProcesses(appProcessesDump: List<String>): List<ActivityManager.RunningAppProcessInfo> {
            val runningAppProcessInfos: MutableList<ActivityManager.RunningAppProcessInfo> = ArrayList()
            val it = appProcessesDump.listIterator()
            if (!it.hasNext()) return runningAppProcessInfos
            var aprMatcher = APP_PROCESS_REGEX.matcher(it.next())
            while (it.hasNext()) {
                if (!aprMatcher.find(0)) {
                    // No matches found, check the next line
                    aprMatcher = APP_PROCESS_REGEX.matcher(it.next())
                    continue
                }
                // Matches found
                val uid = aprMatcher.group(1)
                val pid = aprMatcher.group(2)
                val processName = aprMatcher.group(3)
                if (uid == null || pid == null || processName == null) {
                    // Criteria didn't match
                    aprMatcher = APP_PROCESS_REGEX.matcher(it.next())
                    continue
                }
                var line = it.next()
                aprMatcher = APP_PROCESS_REGEX.matcher(line)
                while (it.hasNext()) {
                    if (aprMatcher.find(0)) {
                        // found next ProcessRecord, no need to continue the search for pkgList
                        break
                    }
                    val pkgrMatcher = PKG_LIST_REGEX.matcher(line)
                    if (!pkgrMatcher.find(0)) {
                        // Process didn't match, find next line
                        line = it.next()
                        aprMatcher = APP_PROCESS_REGEX.matcher(line)
                        continue
                    }
                    // Found a pkgList
                    val pkgList = pkgrMatcher.group(1)
                    if (pkgList != null) {
                        val info = ActivityManager.RunningAppProcessInfo()
                        info.uid = Integer.decode(uid)
                        info.pid = Integer.decode(pid)
                        info.processName = processName
                        val split = pkgList.split(", ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                        info.pkgList = arrayOfNulls(split.size)
                        System.arraycopy(split, 0, info.pkgList, 0, split.size)
                        runningAppProcessInfos.add(info)
                    }
                    line = it.next()
                    aprMatcher = APP_PROCESS_REGEX.matcher(line)
                }
            }
            return runningAppProcessInfos
        }

        private fun getRunningServicesUsingDumpSys(packageName: String): List<ActivityManager.RunningServiceInfo> {
            val result = Runner.runCommand(arrayOf("dumpsys", "activity", "services", "-p", packageName))
            if (!result.isSuccessful) return emptyList()
            val serviceDump = result.getOutputAsList(1)
            return parseRunningServices(serviceDump)
        }

        @JvmStatic
        @VisibleForTesting
        fun parseRunningServices(serviceDump: List<String>): List<ActivityManager.RunningServiceInfo> {
            val runningServices: MutableList<ActivityManager.RunningServiceInfo> = ArrayList()
            val it = serviceDump.listIterator()
            if (!it.hasNext()) return runningServices
            var srMatcher = SERVICE_RECORD_REGEX.matcher(it.next())
            while (it.hasNext()) { // hasNext check doesn't omit anything since we'd still have to check for ProcessRecord
                if (!srMatcher.find(0)) {
                    // No matches found, check the next line
                    srMatcher = SERVICE_RECORD_REGEX.matcher(it.next())
                    continue
                }
                // Matches found
                val userId = srMatcher.group(1)
                val serviceName = srMatcher.group(2)
                if (userId == null || serviceName == null) {
                    // Criteria didn't match
                    srMatcher = SERVICE_RECORD_REGEX.matcher(it.next())
                    continue
                }
                // This is actually the short process name, original service name is under intent (in the next line)
                val i = serviceName.indexOf(':')
                val service = ComponentName.unflattenFromString(if (i == -1) serviceName else serviceName.substring(0, i))
                var line = it.next()
                srMatcher = SERVICE_RECORD_REGEX.matcher(line)
                while (it.hasNext()) {
                    if (srMatcher.find(0)) {
                        // found next ServiceRecord, no need to continue the search for ProcessRecord
                        break
                    }
                    val prMatcher = PROCESS_RECORD_REGEX.matcher(line)
                    if (!prMatcher.find(0)) {
                        // Process didn't match, find next line
                        line = it.next()
                        srMatcher = SERVICE_RECORD_REGEX.matcher(line)
                        continue
                    }
                    // Found a ProcessRecord
                    val pid = prMatcher.group(1)
                    val processName = prMatcher.group(2)
                    var userInfo = prMatcher.group(3)
                    if (pid != null && processName != null && userInfo != null) {
                        val info = ActivityManager.RunningServiceInfo()
                        info.pid = Integer.decode(pid)
                        info.process = processName
                        info.service = service
                        // UID
                        if (TextUtils.isDigitsOnly(userInfo)) {  // UID < 10000
                            info.uid = Integer.decode(userInfo)
                        } else if (userInfo.startsWith("u")) {  // u<USER_ID>(a|s)<APP_ID>[i<ISOLATION_ID>]
                            userInfo = userInfo.substring(("u" + userId).length) // u<USER_ID> removed
                            val iIdx = userInfo.indexOf('i')
                            val iIndex = if (iIdx == -1) userInfo.length else iIdx
                            if (userInfo.startsWith("a")) {
                                // User app
                                info.uid = UserHandleHidden.getUid(Integer.decode(userId), 10000 + Integer.decode(userInfo.substring(1, iIndex)))
                            } else if (userInfo.startsWith("s")) {
                                // System app
                                info.uid = UserHandleHidden.getUid(Integer.decode(userId), Integer.decode(userInfo.substring(1, iIndex)))
                            } else throw IllegalStateException("No valid UID info found in ProcessRecord")
                        } else throw IllegalStateException("Invalid user info section in ProcessRecord")
                        // TODO: 1/9/21 Parse others
                        runningServices.add(info)
                    }
                    line = it.next()
                    srMatcher = SERVICE_RECORD_REGEX.matcher(line)
                }
            }
            return runningServices
        }

        private fun canDumpRunningServices(): Boolean {
            return SelfPermissions.checkSelfPermission(Manifest.permission.DUMP) &&
                    SelfPermissions.checkSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS)
        }
    }

    internal class IntentReceiver : IIntentReceiver.Stub() {
        private var mFinished = false

        override fun performReceive(
            intent: Intent?, resultCode: Int, data: String?, extras: Bundle?,
            ordered: Boolean, sticky: Boolean, sendingUser: Int
        ) {
            var line = "Broadcast completed: result=$resultCode"
            if (data != null) line += ", data="$data""
            if (extras != null) line += ", extras: $extras"
            Log.e("AM", line)
            synchronized(this) {
                mFinished = true
                (this as Object).notifyAll()
            }
        }

        @Synchronized
        fun waitForFinish() {
            try {
                while (!mFinished) (this as Object).wait()
            } catch (e: InterruptedException) {
                throw IllegalStateException(e)
            }
        }
    }
}
