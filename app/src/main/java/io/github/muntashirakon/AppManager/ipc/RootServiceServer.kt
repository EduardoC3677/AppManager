// SPDX-License-Identifier: Apache-2.0

package io.github.muntashirakon.AppManager.ipc

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.*
import android.util.ArrayMap
import android.util.ArraySet
import android.util.SparseArray
import androidx.annotation.RestrictTo
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.internal.UiThreadHandler
import com.topjohnwu.superuser.internal.Utils
import io.github.muntashirakon.AppManager.server.common.IRootServiceManager
import io.github.muntashirakon.AppManager.server.common.ServerUtils
import io.github.muntashirakon.AppManager.utils.ContextUtils
import java.io.File
import java.util.concurrent.Callable
import kotlin.system.exitProcess

/**
 * Runs in the root (server) process.
 *
 * Manages the lifecycle of RootServices and the root process.
 */
// Copyright 2021 John "topjohnwu" Wu
@RestrictTo(RestrictTo.Scope.LIBRARY)
@SuppressLint("RestrictedApi")
class RootServiceServer private constructor(context: Context) : IRootServiceManager.Stub(), Runnable {
    companion object {
        @JvmField
        val TAG: String = RootServiceServer::class.java.simpleName

        @SuppressLint("StaticFieldLeak")
        private var sInstance: RootServiceServer? = null

        @JvmStatic
        fun getInstance(context: Context): RootServiceServer {
            if (sInstance == null) {
                sInstance = RootServiceServer(context)
            }
            return sInstance!!
        }
    }

    private val mObserver: FileObserver /* A strong reference is required */
    private val mServices: MutableMap<ComponentName, ServiceRecord> = ArrayMap()
    private val mClients = SparseArray<ClientProcess>()
    private val mIsDaemon: Boolean
    private val mContext: Context

    init {
        Shell.enableVerboseLogging = System.getenv(RootServiceManager.LOGGING_ENV) != null
        mContext = context
        ContextUtils.rootContext = context

        // Wait for debugger to attach if needed
        if (System.getenv(RootServiceManager.DEBUG_ENV) != null) {
            // ActivityThread.attach(true, 0) will set this to system_process
            HiddenAPIs.setAppName(context.packageName + ":priv")
            Utils.log(TAG, "Waiting for debugger to be attached...")
            // For some reason Debug.waitForDebugger() won't work, manual spin lock...
            while (!Debug.isDebuggerConnected()) {
                try {
                    Thread.sleep(200)
                } catch (ignored: InterruptedException) {
                }
            }
            Utils.log(TAG, "Debugger attached!")
        }

        mObserver = AppObserver(File(context.packageCodePath))
        mObserver.startWatching()
        if (context is Callable<*>) {
            try {
                val objs = context.call() as Array<*>
                mIsDaemon = objs[1] as Boolean
                if (mIsDaemon) {
                    // Register ourselves as system service
                    HiddenAPIs.addService(ServerUtils.getServiceName(context.packageName), this)
                }
                broadcast(objs[0] as Int)
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
        } else {
            throw IllegalArgumentException("Expected Context to be Callable")
        }
        if (!mIsDaemon) {
            // Terminate the process if idle for 10 seconds,
            UiThreadHandler.handler.postDelayed(this, 10 * 1000)
        }
    }

    override fun run() {
        if (mClients.size() == 0) {
            exit("No active clients")
        }
    }

    override fun connect(binder: IBinder) {
        val uid = getCallingUid()
        UiThreadHandler.run { connectInternal(uid, binder) }
    }

    private fun connectInternal(uid: Int, binder: IBinder) {
        if (mClients[uid] != null) return
        try {
            mClients.put(uid, ClientProcess(binder, uid))
            UiThreadHandler.handler.removeCallbacks(this)
        } catch (e: RemoteException) {
            Utils.err(TAG, e)
        }
    }

    override fun broadcast(uid: Int) {
        // Use the UID argument iff caller is root
        val targetUid = if (getCallingUid() == 0) uid else getCallingUid()
        Utils.log(TAG, "broadcast to uid=$targetUid")
        val intent = RootServiceManager.getBroadcastIntent(this, mIsDaemon)
        if (Build.VERSION.SDK_INT >= 24) {
            val h = UserHandle.getUserHandleForUid(targetUid)
            mContext.sendBroadcastAsUser(intent, h)
        } else {
            mContext.sendBroadcast(intent)
        }
    }

    override fun bind(intent: Intent): IBinder? {
        val b = arrayOfNulls<IBinder>(1)
        val uid = getCallingUid()
        UiThreadHandler.runAndWait {
            try {
                b[0] = bindInternal(uid, intent)
            } catch (e: Exception) {
                Utils.err(TAG, e)
            }
        }
        return b[0]
    }

    override fun unbind(name: ComponentName) {
        val uid = getCallingUid()
        UiThreadHandler.run {
            Utils.log(TAG, name.className + " unbind")
            unbindService(uid, name)
        }
    }

    override fun stop(name: ComponentName, uid: Int) {
        // Use the UID argument iff caller is root
        val clientUid = if (getCallingUid() == 0) uid else getCallingUid()
        UiThreadHandler.run {
            Utils.log(TAG, name.className + " stop")
            unbindService(-1, name)
            // If we aren't killed yet, send another broadcast
            broadcast(clientUid)
        }
    }

    fun selfStop(name: ComponentName) {
        UiThreadHandler.run {
            Utils.log(TAG, name.className + " selfStop")
            unbindService(-1, name)
        }
    }

    fun register(service: RootService) {
        val s = ServiceRecord(service)
        mServices[service.componentName] = s
    }

    @Throws(Exception::class)
    private fun bindInternal(uid: Int, intent: Intent): IBinder? {
        if (mClients[uid] == null) return null

        val name = intent.component!!

        var s = mServices[name]
        if (s == null) {
            val clz = mContext.classLoader.loadClass(name.className)
            val ctor = clz.getDeclaredConstructor()
            ctor.isAccessible = true
            HiddenAPIs.attachBaseContext(ctor.newInstance(), mContext)

            // RootService should be registered after attachBaseContext
            s = mServices[name] ?: return null
        }

        if (s.binder != null) {
            Utils.log(TAG, name.className + " rebind")
            if (s.rebind) s.service.onRebind(s.intent!!)
        } else {
            Utils.log(TAG, name.className + " bind")
            s.binder = s.service.onBind(intent)
            s.intent = intent.cloneFilter()
        }
        s.users.add(uid)

        return s.binder
    }

    private fun unbindInternal(s: ServiceRecord, uid: Int, onDestroy: Runnable) {
        val hadUsers = s.users.isNotEmpty()
        s.users.remove(uid)
        if (uid < 0 || s.users.isEmpty()) {
            if (hadUsers) {
                s.rebind = s.service.onUnbind(s.intent!!)
            }
            if (uid < 0 || !mIsDaemon) {
                s.service.onDestroy()
                onDestroy.run()

                // Notify all other users
                for (user in s.users) {
                    val c = mClients[user] ?: continue
                    val msg = Message.obtain()
                    msg.what = RootServiceManager.MSG_STOP
                    msg.arg1 = if (mIsDaemon) 1 else 0
                    msg.obj = s.intent!!.component
                    try {
                        c.m.send(msg)
                    } catch (e: RemoteException) {
                        Utils.err(TAG, e)
                    } finally {
                        msg.recycle()
                    }
                }
            }
        }
        if (mServices.isEmpty()) {
            exit("No active services")
        }
    }

    private fun unbindService(uid: Int, name: ComponentName) {
        val s = mServices[name] ?: return
        unbindInternal(s, uid) { mServices.remove(name) }
    }

    private fun unbindServices(uid: Int) {
        val it = mServices.entries.iterator()
        while (it.hasNext()) {
            val s = it.next().value
            if (uid < 0) {
                // App is updated/deleted, all clients will get killed anyway,
                // no need to notify anyone.
                s.users.clear()
            }
            unbindInternal(s, uid) { it.remove() }
        }
    }

    private fun exit(reason: String) {
        Utils.log(TAG, "Terminate process: $reason")
        exitProcess(0)
    }

    private inner class AppObserver(path: File) : FileObserver(path.parent, CREATE or DELETE or DELETE_SELF or MOVED_TO or MOVED_FROM) {
        private val mName: String = path.name

        init {
            Utils.log(TAG, "Start monitoring: " + path.parent)
        }

        override fun onEvent(event: Int, path: String?) {
            // App APK update, force close the root process
            if (event == DELETE_SELF || mName == path) {
                exit("Package updated")
            }
        }
    }

    private inner class ClientProcess(b: IBinder, val uid: Int) : BinderHolder(b) {
        val m: Messenger = Messenger(b)

        override fun onBinderDied() {
            Utils.log(TAG, "Client process terminated, uid=$uid")
            mClients.remove(uid)
            unbindServices(uid)
        }
    }

    private class ServiceRecord(val service: RootService) {
        val users: MutableSet<Int> = if (Build.VERSION.SDK_INT >= 23) ArraySet() else HashSet()
        var intent: Intent? = null
        var binder: IBinder? = null
        var rebind: Boolean = false
    }
}
