// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat

import android.os.*
import androidx.annotation.RequiresApi
import dev.rikka.tools.refine.Refine
import java.io.FileDescriptor

object BinderCompat {
    /**
     * Execute a shell command on this object.  This may be performed asynchrously from the caller;
     * the implementation must always call resultReceiver when finished.
     *
     * @param binder         The binder object to call.
     * @param in             The raw file descriptor that an input data stream can be read from.
     * @param out            The raw file descriptor that normal command messages should be written to.
     * @param err            The raw file descriptor that command error messages should be written to.
     * @param args           Command-line arguments.
     * @param shellCallback  Optional callback to the caller's shell to perform operations in it.
     * @param resultReceiver Called when the command has finished executing, with the result code.
     */
    @JvmStatic
    @RequiresApi(Build.VERSION_CODES.N)
    @Throws(RemoteException::class)
    fun shellCommand(
        binder: IBinder,
        `in`: FileDescriptor, out: FileDescriptor,
        err: FileDescriptor,
        args: Array<String>, shellCallback: ShellCallback?,
        resultReceiver: ResultReceiver
    ) {
        val binderHidden = Refine.unsafeCast<IBinderHidden>(binder)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            binderHidden.shellCommand(`in`, out, err, args, shellCallback, resultReceiver)
        } else {
            @Suppress("DEPRECATION")
            binderHidden.shellCommand(`in`, out, err, args, resultReceiver)
        }
    }
}
