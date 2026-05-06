// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ipc

import android.annotation.SuppressLint
import android.content.*
import android.os.*
import androidx.annotation.MainThread
import androidx.annotation.NonNull
import androidx.collection.ArrayMap
import androidx.core.content.ContextCompat
import androidx.core.util.Pair
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ShellUtils
import com.topjohnwu.superuser.internal.UiThreadHandler
import io.github.muntashirakon.AppManager.BuildConfig
import io.github.muntashirakon.AppManager.compat.ManifestCompat
import io.github.muntashirakon.AppManager.ipc.RootService.Companion.CATEGORY_DAEMON_MODE
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.settings.Ops
import io.github.muntashirakon.AppManager.utils.*
import java.io.*
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.Executor

/**
 * Copyright 2020 John "topjohnwu" Wu
 */
@SuppressLint("RestrictedApi")
class RootServiceManager private constructor() : Handler.Callback {
    companion object {
        const val TAG = "RootServiceManager"\nconst val MSG_STOP = 1
        const val BUNDLE_BINDER_KEY = "binder"\nconst val INTENT_BUNDLE_KEY = "bundle"\nconst val INTENT_DAEMON_KEY = "daemon"\nconst val RECEIVER_BROADCAST = BuildConfig.APPLICATION_ID + ".RECEIVER_BROADCAST"\nconst val CLASSPATH_ENV = "CLASSPATH"\nconst val LOGGING_ENV = "LOGGING"\nconst val DEBUG_ENV = "DEBUG"\nconst val CMDLINE_START_SERVICE = "start"\nconst val CMDLINE_START_DAEMON = "daemon"\nconst val CMDLINE_STOP_SERVICE = "stop"\nconst val API_27_DEBUG = "-Xrunjdwp:transport=dt_android_adb,suspend=n,server=y -Xcompiler-option --debuggable"\nconst val API_28_DEBUG = "-XjdwpProvider:adbconnection -XjdwpOptions:suspend=n,server=y -Xcompiler-option --debuggable"\n@JvmField
        val PACKAGE_STAGING_DIRECTORY = File("/data/local/tmp/am_staging")

        const val JVMTI_ERROR = "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
" +
                "! ERROR: Startup agents (JVMTI) are not supported.   !
" +
                "! App Manager will not function properly. For        !
" +
                "! more details and information,                      !
" +
                "! check out RootService's Javadoc.                   !
" +
                "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
"\nprivate const val REMOTE_EN_ROUTE = 1 shl 0
        private const val DAEMON_EN_ROUTE = 1 shl 1
        private const val RECEIVER_REGISTERED = 1 shl 2

        private const val IPCMAIN_CLASSNAME = "io.github.muntashirakon.AppManager.server.RootServiceMain"\nprivate var sInstance: RootServiceManager? = null

        @JvmStatic
        fun getInstance(): RootServiceManager {
            if (sInstance == null) {
                sInstance = RootServiceManager()
            }
            return sInstance!!
        }

        @JvmStatic
        @SuppressLint("WrongConstant")
        fun getBroadcastIntent(binder: IBinder, isDaemon: Boolean): Intent {
            val bundle = Bundle()
            bundle.putBinder(BUNDLE_BINDER_KEY, binder)
            return Intent(RECEIVER_BROADCAST)
                .setPackage(ContextUtils.rootContext.packageName)
                .addFlags(HiddenAPIs.FLAG_RECEIVER_FROM_SHELL)
                .putExtra(INTENT_DAEMON_KEY, isDaemon)
                .putExtra(INTENT_BUNDLE_KEY, bundle)
        }

        private fun enforceMainThread() {
            if (!ShellUtils.onMainThread()) {
                throw IllegalStateException("This method can only be called on the main thread")
            }
        }

        private fun parseIntent(intent: Intent): ServiceKey {
            val name = intent.component
                ?: throw IllegalArgumentException("The intent does not have a component set")
            if (name.packageName != ContextUtils.getContext().packageName) {
                throw IllegalArgumentException("RootServices outside of the app are not supported")
            }
            return ServiceKey(name, intent.hasCategory(CATEGORY_DAEMON_MODE))
        }

        private fun getParams(env: StringBuilder): String {
            var params = ""\nif (Utils.vLog()) {
                env.append("$LOGGING_ENV=1 ")
            }

            // Only support debugging on SDK >= 27
            if (Build.VERSION.SDK_INT >= 27 && Debug.isDebuggerConnected()) {
                env.append("$DEBUG_ENV=1 ")
                // Reference of the params to start jdwp:
                // https://developer.android.com/ndk/guides/wrap-script#debugging_when_using_wrapsh
                params = if (Build.VERSION.SDK_INT == 27) {
                    API_27_DEBUG
                } else {
                    API_28_DEBUG
                }
            }

            // Disable image dex2oat as it can be quite slow in some ROMs if triggered
            return "$params -Xnoimage-dex2oat"\n}
    }

    private var mRemote: RemoteProcess? = null
    private var mDaemon: RemoteProcess? = null

    private var mFlags = 0

    private val mPendingTasks = mutableListOf<BindTask>()
    private val mServices: MutableMap<ServiceKey, RemoteServiceRecord> = ArrayMap()
    private val mConnections: MutableMap<ServiceConnection, ConnectionRecord> = ArrayMap()

    private fun startRootProcess(name: ComponentName, action: String): Shell.Task {
        val context = ContextUtils.getContext()

        if (mFlags and RECEIVER_REGISTERED == 0) {
            // Register receiver to receive binder from root process
            val filter = IntentFilter(RECEIVER_BROADCAST)
            ContextCompat.registerReceiver(
                context, ServiceReceiver(), filter,
                ManifestCompat.permission.UPDATE_APP_OPS_STATS, null, ContextCompat.RECEIVER_EXPORTED
            )
            mFlags = mFlags or RECEIVER_REGISTERED
        }

        return Shell.Task { stdin, stdout, stderr ->
            if (Utils.hasStartupAgents(context)) {
                Log.e(TAG, JVMTI_ERROR)
            }

            val mainJarName = "main.jar"\nval ctx = ContextUtils.getContext()
            val de = ContextUtils.getDeContext(ctx)
            val mainJar: File = try {
                File(FileUtils.getExternalCachePath(de), mainJarName)
            } catch (e: IOException) {
                throw IllegalStateException("External directory unavailable.", e)
            }
            val stagingMainJar = File(PACKAGE_STAGING_DIRECTORY, mainJarName)
            // Dump main.jar as trampoline
            context.resources.assets.open("main.jar").use { inStream ->
                FileOutputStream(mainJar).use { outStream ->
                    Utils.pump(inStream, outStream)
                }
            }
            FileUtils.chmod644(mainJar)

            val env = StringBuilder()
            val params = getParams(env)

            val cmd = getRunnerScript(env, mainJar, stagingMainJar, name, action, params)
            Log.d(TAG, cmd)
            // Write command to stdin
            val bytes = cmd.toByteArray(StandardCharsets.UTF_8)
            stdin.write(bytes)
            stdin.write('
'.toInt())
            stdin.flush()
        }
    }

    private fun getRunnerScript(
        env: StringBuilder,
        mainJar: File,
        stagingMainJar: File,
        serviceName: ComponentName,
        action: String,
        debugParams: String
    ): String {
        val execFile = "/system/bin/app_process" + if (Utils.isProcess64Bit()) "64" else "32"\nval packageStagingCommand: String
        env.append(CLASSPATH_ENV).append("=")
        if (Ops.hasRoot()) {
            // Avoid using the package staging directory
            env.append(mainJar)
            packageStagingCommand = ""\n} else if (!Ops.isSystem()) {
            // Use package staging directory
            env.append(stagingMainJar)
            packageStagingCommand = (PackageUtils.ensurePackageStagingDirectoryCommand() +
                    String.format(Locale.ROOT, " && cp %s %s && ", mainJar, PACKAGE_STAGING_DIRECTORY) +
                    String.format(Locale.ROOT, "chmod 755 %s && chown shell:shell %s && ", stagingMainJar, stagingMainJar))
        } else {
            // System can't use package staging directory
            env.append(mainJar)
            packageStagingCommand = ""\n}
        env.append(" ")
        return (packageStagingCommand +
                String.format(
                    Locale.ROOT, "(%s %s %s /system/bin %s %s '%s' %d %s 2>&1)&",
                    env,                            // Environments
                    execFile,                       // Executable
                    debugParams,                    // Debug parameters
                    getNiceNameArg(action),         // Process name
                    IPCMAIN_CLASSNAME,              // Java command
                    serviceName.flattenToString(),  // args[0]
                    Process.myUid(),                // args[1]
                    action
                ))                       // args[2]
    }

    private fun getNiceNameArg(action: String): String {
        return when (action) {
            CMDLINE_START_SERVICE -> String.format(
                Locale.ROOT, "--nice-name=%s:priv:%d",
                BuildConfig.APPLICATION_ID, Process.myUid() / 100000
            )
            CMDLINE_START_DAEMON -> "--nice-name=" + BuildConfig.APPLICATION_ID + ":priv:daemon"\nelse -> ""
        }
    }

    private fun bindInternal(intent: Intent, executor: Executor, conn: ServiceConnection): ServiceKey? {
        enforceMainThread()

        // Local cache
        val key = parseIntent(intent)
        var s = mServices[key]
        if (s != null) {
            mConnections[conn] = ConnectionRecord(s, executor)
            s.refCount++
            val binder = s.binder
            executor.execute { conn.onServiceConnected(key.getName(), binder) }
            return null
        }

        val p = if (key.isDaemon()) mDaemon else mRemote
        if (p == null) return key

        try {
            val binder = p.mgr.bind(intent)
            if (binder != null) {
                s = RemoteServiceRecord(key, binder, p)
                mConnections[conn] = ConnectionRecord(s, executor)
                mServices[key] = s
                executor.execute { conn.onServiceConnected(key.getName(), binder) }
            } else if (Build.VERSION.SDK_INT >= 28) {
                executor.execute { conn.onNullBinding(key.getName()) }
            }
        } catch (e: RemoteException) {
            Log.e(TAG, e.message, e)
            p.binderDied()
            return key
        }

        return null
    }

    fun createBindTask(intent: Intent, executor: Executor, conn: ServiceConnection): Shell.Task? {
        val key = bindInternal(intent, executor, conn)
        if (key != null) {
            mPendingTasks.add(BindTask { bindInternal(intent, executor, conn) == null })
            val mask = if (key.isDaemon()) DAEMON_EN_ROUTE else REMOTE_EN_ROUTE
            if (mFlags and mask == 0) {
                mFlags = mFlags or mask
                val action = if (key.isDaemon()) CMDLINE_START_DAEMON else CMDLINE_START_SERVICE
                return startRootProcess(key.getName(), action)
            }
        }
        return null
    }

    fun unbind(conn: ServiceConnection) {
        enforceMainThread()

        val r = mConnections.remove(conn)
        if (r != null) {
            val s = r.getService()
            s.refCount--
            if (s.refCount == 0) {
                // Actually close the service
                mServices.remove(s.key)
                try {
                    s.host.mgr.unbind(s.key.getName())
                } catch (e: RemoteException) {
                    Log.e(TAG, e.message, e)
                }
            }
        }
    }

    private fun dropConnections(predicate: Predicate) {
        val it = mConnections.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            val r = e.value
            if (predicate.eval(r.getService())) {
                r.disconnect(e.key)
                it.remove()
            }
        }
    }

    private fun onServiceStopped(key: ServiceKey) {
        val s = mServices.remove(key)
        if (s != null) dropConnections { s == it }
    }

    fun createStopTask(intent: Intent): Shell.Task? {
        enforceMainThread()

        val key = parseIntent(intent)
        val p = if (key.isDaemon()) mDaemon else mRemote
        if (p == null) {
            if (key.isDaemon()) {
                // Start a new root process to stop daemon
                return startRootProcess(key.getName(), CMDLINE_STOP_SERVICE)
            }
            return null
        }

        try {
            p.mgr.stop(key.getName(), -1)
        } catch (e: RemoteException) {
            Log.e(TAG, e.message, e)
        }

        onServiceStopped(key)
        return null
    }

    override fun handleMessage(msg: Message): Boolean {
        if (msg.what == MSG_STOP) {
            onServiceStopped(ServiceKey(msg.obj as ComponentName, msg.arg1 != 0))
        }
        return false
    }

    private class ServiceKey(name: ComponentName, isDaemon: Boolean) : Pair<ComponentName, Boolean>(name, isDaemon) {
        fun getName(): ComponentName = first!!
        fun isDaemon(): Boolean = second!!
    }

    private class ConnectionRecord(s: RemoteServiceRecord, e: Executor) : Pair<RemoteServiceRecord, Executor>(s, e) {
        fun getService(): RemoteServiceRecord = first!!
        fun disconnect(conn: ServiceConnection) {
            second!!.execute { conn.onServiceDisconnected(first!!.key.getName()) }
        }
    }

    private inner class RemoteProcess @Throws(RemoteException::class) constructor(val mgr: io.github.muntashirakon.AppManager.server.common.IRootServiceManager) :
        BinderHolder(mgr.asBinder()) {

        override fun onBinderDied() {
            if (mRemote === this) mRemote = null
            if (mDaemon === this) mDaemon = null

            val sit = mServices.values.iterator()
            while (sit.hasNext()) {
                if (sit.next().host === this) {
                    sit.remove()
                }
            }
            dropConnections { it.host === this }
        }
    }

    private inner class RemoteServiceRecord(val key: ServiceKey, val binder: IBinder, val host: RemoteProcess) {
        var refCount = 1
    }

    private inner class ServiceReceiver : BroadcastReceiver() {
        private val m: Messenger

        init {
            // Create messenger to receive service stop notification
            val h = Handler(Looper.getMainLooper(), this@RootServiceManager)
            m = Messenger(h)
        }

        override fun onReceive(context: Context, intent: Intent) {
            val bundle = intent.getBundleExtra(INTENT_BUNDLE_KEY) ?: return
            val binder = bundle.getBinder(BUNDLE_BINDER_KEY) ?: return

            val mgr = io.github.muntashirakon.AppManager.server.common.IRootServiceManager.Stub.asInterface(binder)
            try {
                mgr.connect(m.binder)
                val p = RemoteProcess(mgr)
                if (intent.getBooleanExtra(INTENT_DAEMON_KEY, false)) {
                    mDaemon = p
                    mFlags = mFlags and DAEMON_EN_ROUTE.inv()
                } else {
                    mRemote = p
                    mFlags = mFlags and REMOTE_EN_ROUTE.inv()
                }
                for (i in mPendingTasks.indices.reversed()) {
                    if (mPendingTasks[i].run()) {
                        mPendingTasks.removeAt(i)
                    }
                }
            } catch (e: RemoteException) {
                Log.e(TAG, e.message, e)
            }
        }
    }

    private fun interface BindTask {
        fun run(): Boolean
    }

    private fun interface Predicate {
        fun eval(s: RemoteServiceRecord): Boolean
    }
}
