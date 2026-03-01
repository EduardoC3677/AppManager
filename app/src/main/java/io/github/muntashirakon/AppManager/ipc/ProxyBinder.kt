// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ipc

import android.os.*
import androidx.annotation.RequiresApi
import androidx.collection.ArrayMap
import io.github.muntashirakon.AppManager.compat.BinderCompat
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.server.common.IRootServiceManager
import io.github.muntashirakon.compat.os.ParcelCompat2
import java.io.FileDescriptor
import java.util.*

/**
 * Copyright 2020 Rikka
 */
class ProxyBinder(private val mOriginal: IBinder) : IBinder {
    companion object {
        private val TAG = ProxyBinder::class.java.simpleName
        const val PROXY_BINDER_TRANSACTION = 2

        /**
         * IBinder protocol transaction code: execute a shell command.
         */
        @JvmField
        val SHELL_COMMAND_TRANSACTION = ('_'.toInt() shl 24) or ('C'.toInt() shl 16) or ('M'.toInt() shl 8) or 'D'.toInt()

        private val sServiceCache = Collections.synchronizedMap(ArrayMap<String, IBinder>())

        @JvmStatic
        @Throws(ServiceNotFoundException::class)
        fun getService(serviceName: String): IBinder {
            var binder = sServiceCache[serviceName]
            if (binder == null) {
                binder = getServiceInternal(serviceName)
                sServiceCache[serviceName] = binder
            }
            return ProxyBinder(binder)
        }

        /**
         * Some services can't be called without certain permissions
         * so we redirect to AMService who can make that call no mater which mode it's in.
         * as 0, 1000, and 2000 all have access to the overlay service.
         *
         * @param serviceName service to be loaded
         * @return binder to that service
         */
        @JvmStatic
        @Throws(ServiceNotFoundException::class)
        private fun getServiceInternal(serviceName: String): IBinder {
            var binder = ServiceManager.getService(serviceName)
            if (LocalServices.alive() && binder == null) {
                try {
                    binder = LocalServices.getAmService().getService(serviceName)
                } catch (e: RemoteException) {
                    Log.e(TAG, e)
                    throw ServiceNotFoundException("Service couldn't be loaded: $serviceName", e)
                }
            }
            if (binder == null) {
                throw ServiceNotFoundException("Service couldn't be found: $serviceName")
            }
            return binder
        }

        @JvmStatic
        @Throws(ServiceNotFoundException::class)
        fun getUnprivilegedService(serviceName: String): IBinder {
            var binder = sServiceCache[serviceName]
            if (binder == null) {
                binder = ServiceManager.getService(serviceName)
                sServiceCache[serviceName] = binder
            }
            if (binder == null) {
                throw ServiceNotFoundException("Service couldn't be found: $serviceName")
            }
            return binder
        }

        /**
         * @see BinderCompat.shellCommand
         */
        @JvmStatic
        @RequiresApi(Build.VERSION_CODES.N)
        @Throws(RemoteException::class)
        fun shellCommand(
            binder: IBinder,
            `in`: FileDescriptor, out: FileDescriptor,
            err: FileDescriptor,
            args: Array<String>, callback: ShellCallback?,
            resultReceiver: ResultReceiver
        ) {
            if (binder !is ProxyBinder) {
                BinderCompat.shellCommand(binder, `in`, out, err, args, callback, resultReceiver)
                return
            }
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            data.writeFileDescriptor(`in`)
            data.writeFileDescriptor(out)
            data.writeFileDescriptor(err)
            data.writeStringArray(args)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ShellCallback.writeToParcel(callback, data)
            }
            resultReceiver.writeToParcel(data, 0)
            try {
                binder.transact(SHELL_COMMAND_TRANSACTION, data, reply, 0)
                reply.readException()
            } finally {
                data.recycle()
                reply.recycle()
            }
        }
    }

    @Throws(RemoteException::class)
    override fun transact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (LocalServices.alive()) {
            val targetBinder = LocalServices.getAmService().asBinder()
            val newData = ParcelCompat2.obtain(targetBinder)
            try {
                newData.writeInterfaceToken(IRootServiceManager::class.java.name)
                newData.writeStrongBinder(mOriginal)
                newData.writeInt(code)
                newData.writeInt(flags)
                newData.appendFrom(data, 0, data.dataSize())
                // Transact via AMService
                targetBinder.transact(PROXY_BINDER_TRANSACTION, newData, reply, 0)
            } finally {
                newData.recycle()
            }
            return true
        }
        // Run unprivileged code as a fallback method
        return mOriginal.transact(code, data, reply, flags)
    }

    @Throws(RemoteException::class)
    override fun getInterfaceDescriptor(): String? {
        return try {
            mOriginal.interfaceDescriptor
        } catch (e: RemoteException) {
            throw IllegalStateException(e.javaClass.simpleName, e)
        }
    }

    override fun pingBinder(): Boolean {
        return mOriginal.pingBinder()
    }

    override fun isBinderAlive(): Boolean {
        return mOriginal.isBinderAlive
    }

    override fun queryLocalInterface(descriptor: String): IInterface? {
        return null
    }

    @Throws(RemoteException::class)
    override fun dump(fd: FileDescriptor, args: Array<out String>?) {
        try {
            mOriginal.dump(fd, args)
        } catch (e: RemoteException) {
            throw IllegalStateException(e.javaClass.simpleName, e)
        }
    }

    @Throws(RemoteException::class)
    override fun dumpAsync(fd: FileDescriptor, args: Array<out String>?) {
        try {
            mOriginal.dumpAsync(fd, args)
        } catch (e: RemoteException) {
            throw IllegalStateException(e.javaClass.simpleName, e)
        }
    }

    @Throws(RemoteException::class)
    override fun linkToDeath(recipient: IBinder.DeathRecipient, flags: Int) {
        try {
            mOriginal.linkToDeath(recipient, flags)
        } catch (e: RemoteException) {
            throw IllegalStateException(e.javaClass.simpleName, e)
        }
    }

    override fun unlinkToDeath(recipient: IBinder.DeathRecipient, flags: Int): Boolean {
        return mOriginal.unlinkToDeath(recipient, flags)
    }
}
