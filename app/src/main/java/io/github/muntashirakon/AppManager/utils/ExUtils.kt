// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils

import android.os.Build
import android.os.DeadObjectException
import android.os.DeadSystemException
import android.os.RemoteException
import io.github.muntashirakon.AppManager.backup.BackupException
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.compat.ObjectsCompat
import java.io.IOException

object ExUtils {
    fun interface ThrowingRunnable<T> {
        @Throws(Throwable::class)
        fun run(): T
    }

    fun interface ThrowingRunnableNoReturn {
        @Throws(Throwable::class)
        fun run()
    }

    @JvmStatic
    @Throws(IOException::class)
    fun <T> rethrowAsIOException(e: Throwable): T {
        val ioException = IOException(e.message)
        @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
        ioException.initCause(e)
        throw ioException
    }

    @JvmStatic
    @Throws(BackupException::class)
    fun <T> rethrowAsBackupException(message: String, e: Throwable): T {
        val backupException = BackupException(message)
        @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
        backupException.initCause(e)
        throw backupException
    }

    @JvmStatic
    fun <T> rethrowAsRuntimeException(th: Throwable): T {
        throw RuntimeException(th)
    }

    /**
     * Rethrow this exception when we know it came from the system server. This
     * gives us an opportunity to throw a nice clean
     * [DeadSystemException] signal to avoid spamming logs with
     * misleading stack traces.
     *
     * Apps making calls into the system server may end up persisting internal
     * state or making security decisions based on the perceived success or
     * failure of a call, or any default values returned. For this reason, we
     * want to strongly throw when there was trouble with the transaction.
     */
    @JvmStatic
    fun <T> rethrowFromSystemServer(e: RemoteException): T {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            throw e.rethrowFromSystemServer()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && e is DeadObjectException) {
            throw RuntimeException(DeadSystemException())
        } else {
            throw RuntimeException(e)
        }
    }

    @JvmStatic
    fun <T> exceptionAsNull(r: ThrowingRunnable<T>): T? {
        return try {
            r.run()
        } catch (th: Throwable) {
            Log.w("ExUtils", th)
            null
        }
    }

    @JvmStatic
    fun <T> requireNonNullElse(r: ThrowingRunnable<T>, defaultObj: T & Any): T & Any {
        return ObjectsCompat.requireNonNullElse(exceptionAsNull(r), defaultObj)
    }

    @JvmStatic
    fun <T> asRuntimeException(r: ThrowingRunnable<T>): T? {
        return try {
            r.run()
        } catch (th: Throwable) {
            throw RuntimeException(th)
        }
    }

    @JvmStatic
    fun exceptionAsIgnored(r: ThrowingRunnableNoReturn) {
        try {
            r.run()
        } catch (th: Throwable) {
            Log.w("ExUtils", th)
        }
    }
}
