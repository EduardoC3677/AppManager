// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.*
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

import io.github.muntashirakon.AppManager.server.common.IRootServiceManager
import io.github.muntashirakon.AppManager.server.common.ServerUtils.CMDLINE_START_DAEMON
import io.github.muntashirakon.AppManager.server.common.ServerUtils.CMDLINE_START_SERVICE
import io.github.muntashirakon.AppManager.server.common.ServerUtils.CMDLINE_STOP_SERVICE
import io.github.muntashirakon.AppManager.server.common.ServerUtils.getServiceName

/**
 * Trampoline to start a root service.
 *
 * This is the only class included in main.jar as raw resources.
 * The client code will execute this main method in a root shell.
 *
 * This class will get the system context by calling into Android private APIs with reflection, and
 * uses that to create our client package context. The client context will have the full APK loaded,
 * just like it was launched in a non-root environment.
 *
 * Expected command-line args:
 * args[0]: client service component name
 * args[1]: client UID
 * args[2]: CMDLINE_START_SERVICE, CMDLINE_START_DAEMON, or CMDLINE_STOP_SERVICE
 *
 * **Note:** This class is hardcoded in `IPCClient#IPCMAIN_CLASSNAME`. Don't change the class name or package
 * path without changing them there.
 */
// Copyright 2020 John "topjohnwu" Wu
class RootServiceMain : ContextWrapper, Callable<Array<Any?>> {
    private val uid: Int
    private val isDaemon: Boolean

    init {
        // Close STDOUT/STDERR since it belongs to the parent shell
        System.out.close()
        System.err.close()
        require(args.size >= 3) { "Expected at least 3 arguments: componentName, uid, action" }

        Looper.prepareMainLooper()

        @SuppressLint("DiscouragedPrivateApi", "PrivateApi")
        val smClazz = Class.forName("android.os.ServiceManager")
        val getServiceMethod = smClazz.getDeclaredMethod("getService", String::class.java)

        val attachBaseContextMethod = ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java)
        attachBaseContextMethod.isAccessible = true

        val name = ComponentName.unflattenFromString(args[0]) ?: throw IllegalArgumentException("Invalid component name: ${args[0]}")
        uid = args[1].toIntOrNull() ?: throw IllegalArgumentException("Invalid UID: ${args[1]}")
        val action = args[2]

        var stop = false
        when (action) {
            CMDLINE_STOP_SERVICE -> {
                stop = true
                // fallthrough
            }
            CMDLINE_START_DAEMON -> isDaemon = true
            CMDLINE_START_SERVICE -> isDaemon = false
            else -> throw IllegalArgumentException("Unknown action: $action")
        }

        if (isDaemon) daemon@{
            // Get existing daemon process
            val binder = getServiceMethod.invoke(null, getServiceName(name.packageName))
            val rootServiceManager = IRootServiceManager.Stub.asInterface(binder as IBinder?)
            if (rootServiceManager == null) return@daemon@daemon

            if (stop) {
                rootServiceManager.stop(name, uid)
            } else {
                rootServiceManager.broadcast(uid)
                // Terminate process if broadcast went through without exception
                System.exit(0)
            }
        } finally {
            if (stop) System.exit(0)
        }

        // Calling createPackageContext crashes on LG ROM
        // Override the system resources object to prevent crashing
        try {
            // This class only exists on LG ROMs with broken implementations
            Class.forName("com.lge.systemservice.core.integrity.IntegrityManager")
            // If control flow goes here, we need the resource hack
            val systemRes = Resources.getSystem()
            val wrapper = ResourcesWrapper(systemRes)
            val systemResField: Field = Resources::class.java.getDeclaredField("mSystem")
            systemResField.isAccessible = true
            systemResField.set(null, wrapper)
        } catch (ignored: ReflectiveOperationException) {
        }

        val userId = uid / 100_000
        val flags = Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY
        val context = createPackageContextAsUser(name.packageName, userId, flags)
        attachBaseContextMethod.invoke(this, context)
    }

    override fun call(): Array<Any?> {
        return arrayOf(uid, isDaemon)
    }

    @SuppressLint("PrivateApi")
    private fun createPackageContextAsUser(packageName: String, userId: Int, flags: Int): Context {
        val systemContext = getSystemContext()
        try {
            val userHandle = try {
                UserHandle::class.java.getDeclaredMethod("of", Int::class.javaPrimitiveType).invoke(null, userId) as UserHandle
            } catch (e: NoSuchMethodException) {
                UserHandle::class.java.getDeclaredConstructor(Int::class.javaPrimitiveType).newInstance(userId) as UserHandle
            }
            return systemContext.javaClass
                .getDeclaredMethod("createPackageContextAsUser", String::class.java, Int::class.javaPrimitiveType, UserHandle::class.java)
                .invoke(systemContext, packageName, flags, userHandle) as Context
        } catch (e: Throwable) {
            Log.w("IPC", "Failed to create package context as user: $userId", e)
            return systemContext.createPackageContext(packageName, flags)
        }
    }

    private fun allowBinderCommunication(): Boolean {
        try {
            val SELinuxClass = Class.forName("android.os.SELinux")
            val getContext = SELinuxClass.getMethod("getContext")
            val context = getContext.invoke(null) as String
            val checkSELinuxAccess = SELinuxClass.getMethod("checkSELinuxAccess", String::class.java, String::class.java, String::class.java, String::class.java)
            return java.lang.Boolean.TRUE == checkSELinuxAccess.invoke(null, "u:r:untrusted_app:s0", context, "binder", "call") &&
                    java.lang.Boolean.TRUE == checkSELinuxAccess.invoke(null, "u:r:untrusted_app:s0", context, "binder", "transfer")
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    companion object {
        private val TAG = RootServiceMain::class.java.simpleName

        private val getService: Method
        private val attachBaseContext: Method

        init {
            try {
                val smClazz = Class.forName("android.os.ServiceManager")
                getService = smClazz.getDeclaredMethod("getService", String::class.java)
                attachBaseContext = ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java)
                attachBaseContext.isAccessible = true
            } catch (e: Exception) {
                // Shall not happen!
                throw RuntimeException(e)
            }
        }

        @JvmStatic
        fun getSystemContext(): Context {
            return try {
                synchronized(Looper::class.java) {
                    if (Looper.getMainLooper() == null) Looper.prepareMainLooper()
                }
                val atClazz = Class.forName("android.app.ActivityThread")
                val systemMain = atClazz.getMethod("systemMain")
                val activityThread = systemMain.invoke(null)
                val getSystemContext = atClazz.getMethod("getSystemContext")
                getSystemContext.invoke(activityThread) as Context
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
        }

        @JvmStatic
        fun main(args: Array<String>) {
            // Close STDOUT/STDERR since it belongs to the parent shell
            System.out.close()
            System.err.close()
            if (args.size < 3) {
                System.exit(1)
            }

            Looper.prepareMainLooper()

            try {
                RootServiceMain(args)
            } catch (e: Throwable) {
                Log.e("IPC", "Error in IPCMain", e)
                System.exit(1)
            }

            // Main thread event loop
            Looper.loop()
            System.exit(1)
        }
    }

    private class ResourcesWrapper : Resources {
        @SuppressWarnings("JavaReflectionMemberAccess", "deprecation")
        @SuppressLint("DiscouragedPrivateApi")
        constructor(res: Resources) : super(res.assets, res.displayMetrics, res.configuration) {
            val getImplMethod = Resources::class.java.getDeclaredMethod("getImpl")
            getImplMethod.isAccessible = true
            val setImplMethod = Resources::class.java.getDeclaredMethod("setImpl", getImplMethod.returnType)
            setImplMethod.isAccessible = true
            val impl = getImplMethod.invoke(res)
            setImplMethod.invoke(this, impl)
        }

        override fun getBoolean(id: Int): Boolean {
            return try {
                super.getBoolean(id)
            } catch (e: NotFoundException) {
                false
            }
        }
    }
}
