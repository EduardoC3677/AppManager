// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ipc

import android.content.Intent
import android.os.*
import android.system.ErrnoException
import android.system.Os
import io.github.muntashirakon.AppManager.IAMService
import io.github.muntashirakon.AppManager.IRemoteProcess
import io.github.muntashirakon.AppManager.IRemoteShell
import io.github.muntashirakon.AppManager.ipc.ps.ProcessEntry
import io.github.muntashirakon.AppManager.ipc.ps.Ps
import io.github.muntashirakon.AppManager.server.common.IRootServiceManager
import io.github.muntashirakon.compat.os.ParcelCompat2
import aosp.android.content.pm.ParceledListSlice
import io.github.muntashirakon.AppManager.logs.Log
import java.io.File

class AMService : RootService() {
    internal class IAMServiceImpl : IAMService.Stub() {
        /**
         * To get [Process], wrap it using [RemoteProcess]. Since the streams are piped,
         * I/O operations may have to be done in different threads.
         */
        @Throws(RemoteException::class)
        override fun newProcess(cmd: Array<String>, env: Array<String>?, dir: String?): IRemoteProcess {
            val process: Process = try {
                Runtime.getRuntime().exec(cmd, env, if (dir != null) File(dir) else null)
            } catch (e: Exception) {
                throw RemoteException(e.message)
            }
            return RemoteProcessImpl(process)
        }

        override fun getShell(cmd: Array<String>): IRemoteShell {
            return RemoteShellImpl(cmd)
        }

        override fun getRunningProcesses(): ParceledListSlice<ProcessEntry> {
            val ps = Ps()
            ps.loadProcesses()
            return ParceledListSlice(ps.processes)
        }

        override fun getUid(): Int {
            return android.os.Process.myUid()
        }

        @Throws(RemoteException::class)
        override fun symlink(file: String, link: String) {
            try {
                Os.symlink(file, link)
            } catch (e: ErrnoException) {
                throw RemoteException(e.message)
            }
        }

        @Throws(RemoteException::class)
        override fun getService(serviceName: String): IBinder? {
            return ServiceManager.getService(serviceName)
        }

        @Throws(RemoteException::class)
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == ProxyBinder.PROXY_BINDER_TRANSACTION) {
                data.enforceInterface(IRootServiceManager::class.java.name)
                transactRemote(data, reply)
                return true
            }
            return super.onTransact(code, data, reply, flags)
        }

        /**
         * Call target Binder received through [ProxyBinder].
         *
         * @author Rikka
         */
        @Throws(RemoteException::class)
        private fun transactRemote(data: Parcel, reply: Parcel?) {
            val targetBinder = data.readStrongBinder() ?: throw RemoteException("Target binder is null")
            val targetCode = data.readInt()
            val targetFlags = data.readInt()

            val newData = ParcelCompat2.obtain(targetBinder)
            try {
                newData.appendFrom(data, data.dataPosition(), data.dataAvail())
                val id = Binder.clearCallingIdentity()
                targetBinder.transact(targetCode, newData, reply, targetFlags)
                Binder.restoreCallingIdentity(id)
            } catch (e: RemoteException) {
                throw e
            } catch (th: Throwable) {
                throw RemoteException(th.message).initCause(th) as RemoteException
            } finally {
                newData.recycle()
            }
        }
    }

    override fun onBind(intent: Intent): IBinder {
        Log.d(TAG, "AMService: onBind")
        return IAMServiceImpl()
    }

    override fun onRebind(intent: Intent) {
        super.onRebind(intent)
    }

    override fun onUnbind(intent: Intent): Boolean {
        return true
    }
}
