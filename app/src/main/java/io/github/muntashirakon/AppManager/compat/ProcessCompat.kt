// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat

import android.os.RemoteException
import io.github.muntashirakon.AppManager.ipc.LocalServices
import io.github.muntashirakon.AppManager.ipc.RemoteProcess
import io.github.muntashirakon.AppManager.ipc.RemoteProcessImpl
import java.io.File
import java.io.IOException

object ProcessCompat {
    /**
     * Defines the start of a range of UIDs (and GIDs), going from this
     * number to {@link #LAST_APPLICATION_UID} that are reserved for assigning
     * to applications.
     */
    @JvmField
    val FIRST_APPLICATION_UID: Int = android.os.Process.FIRST_APPLICATION_UID

    /**
     * Last of application-specific UIDs starting at
     * {@link #FIRST_APPLICATION_UID}.
     */
    @JvmField
    val LAST_APPLICATION_UID: Int = android.os.Process.LAST_APPLICATION_UID

    /**
     * First uid used for fully isolated sandboxed processes spawned from an app zygote
     */
    const val FIRST_APP_ZYGOTE_ISOLATED_UID: Int = 90000

    /**
     * Last uid used for fully isolated sandboxed processes spawned from an app zygote
     */
    const val LAST_APP_ZYGOTE_ISOLATED_UID: Int = 98999

    /**
     * First uid used for fully isolated sandboxed processes (with no permissions of their own)
     */
    const val FIRST_ISOLATED_UID: Int = 99000

    /**
     * Last uid used for fully isolated sandboxed processes (with no permissions of their own)
     */
    const val LAST_ISOLATED_UID: Int = 99999

    @JvmStatic
    @Throws(IOException::class)
    @JvmOverloads
    fun exec(cmd: Array<String>?, env: Array<String>? = null, dir: File? = null): Process {
        if (LocalServices.alive()) {
            return try {
                RemoteProcess(
                    LocalServices.getAmService().newProcess(
                        cmd, env, dir?.absolutePath
                    )
                )
            } catch (e: RemoteException) {
                throw IOException(e)
            }
        }
        return RemoteProcess(RemoteProcessImpl(Runtime.getRuntime().exec(cmd, env, dir)))
    }

    @JvmStatic
    fun isAlive(process: Process): Boolean {
        return try {
            process.exitValue()
            false
        } catch (e: IllegalThreadStateException) {
            // Note: Java implementation had IllegalArgumentException, but exitValue() throws IllegalThreadStateException
            true
        } catch (e: IllegalArgumentException) {
            true
        }
    }
}
