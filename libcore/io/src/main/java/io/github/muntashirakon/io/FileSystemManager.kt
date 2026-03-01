// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io

import android.os.Binder
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import androidx.annotation.IntDef
import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.channels.FileChannel

/**
 * Access file system APIs.
 */
// Copyright 2022 John "topjohnwu" Wu
abstract class FileSystemManager {

    @Retention(AnnotationRetention.SOURCE)
    @IntDef(
        value = [MODE_READ_ONLY, MODE_WRITE_ONLY, MODE_READ_WRITE, MODE_CREATE, MODE_TRUNCATE, MODE_APPEND],
        flag = true
    )
    annotation class OpenMode

    /**
     * @see File.File
     */
    abstract fun getFile(pathname: String): ExtendedFile

    /**
     * @see File.File
     */
    abstract fun getFile(parent: String?, child: String): ExtendedFile

    /**
     * @see File.File
     */
    fun getFile(parent: File?, child: String): ExtendedFile {
        return getFile(parent?.path, child)
    }

    /**
     * @see File.File
     */
    fun getFile(uri: URI): ExtendedFile {
        return getFile(File(uri).path)
    }

    /**
     * Opens a file channel to access the file.
     *
     * @param pathname the file to be opened.
     * @param mode     the desired access mode.
     * @return a new FileChannel pointing to the given file.
     * @throws IOException if the given file can not be opened with the requested mode.
     */
    @Throws(IOException::class)
    fun openChannel(pathname: String, @OpenMode mode: Int): FileChannel {
        return openChannel(File(pathname), mode)
    }

    /**
     * Opens a file channel to access the file.
     *
     * @param file the file to be opened.
     * @param mode the desired access mode.
     * @return a new FileChannel pointing to the given file.
     * @throws IOException if the given file can not be opened with the requested mode.
     */
    @Throws(IOException::class)
    abstract fun openChannel(file: File, @OpenMode mode: Int): FileChannel

    companion object {

        /**
         * For use with [.openChannel]: open the file with read-only access.
         */
        const val MODE_READ_ONLY = ParcelFileDescriptor.MODE_READ_ONLY

        /**
         * For use with [.openChannel]: open the file with write-only access.
         */
        const val MODE_WRITE_ONLY = ParcelFileDescriptor.MODE_WRITE_ONLY

        /**
         * For use with [.openChannel]: open the file with read and write access.
         */
        const val MODE_READ_WRITE = ParcelFileDescriptor.MODE_READ_WRITE

        /**
         * For use with [.openChannel]: create the file if it doesn't already exist.
         */
        const val MODE_CREATE = ParcelFileDescriptor.MODE_CREATE

        /**
         * For use with [.openChannel]: erase contents of file when opening.
         */
        const val MODE_TRUNCATE = ParcelFileDescriptor.MODE_TRUNCATE

        /**
         * For use with [.openChannel]: append to end of file while writing.
         */
        const val MODE_APPEND = ParcelFileDescriptor.MODE_APPEND

        private val LOCAL = NIOFactory.createLocal()

        private var fsService: Binder? = null

        /**
         * Get the service that exports the file system of the current process over Binder IPC.
         *
         *
         * Sending the [Binder] obtained from this method to a client process enables
         * the current process to perform file system operations on behalf of the client.
         * This allows a client process to access files normally denied by its permissions.
         * This method is usually called in a root process, and the Binder service returned will
         * be send over to a non-root client process.
         *
         *
         * You can pass this [Binder] object in multiple ways, such as returning it in the
         * `onBind()` method of root services, passing it around in a [android.os.Bundle],
         * or returning it in an AIDL interface method. The receiving end will get an [IBinder],
         * which the developer should then pass to [.getRemote] for usage.
         */
        @JvmStatic
        @Synchronized
        fun getService(): Binder {
            if (fsService == null) {
                fsService = NIOFactory.createFsService()
            }
            return fsService!!
        }

        /**
         * Get the [FileSystemManager] to access the file system of the current local process.
         */
        @JvmStatic
        fun getLocal(): FileSystemManager {
            return LOCAL
        }

        /**
         * Create a [FileSystemManager] to access the file system of a remote process.
         *
         *
         * Several APIs are not supported through a remote process:
         *
         *  * [File.deleteOnExit]
         *  * [FileChannel.map]
         *  * [FileChannel.lock]
         *  * [FileChannel.lock]
         *  * [FileChannel.tryLock]
         *  * [FileChannel.tryLock]
         *
         * Calling these APIs will throw [UnsupportedOperationException].
         *
         * @param binder a remote proxy of the [Binder] obtained from [.getService]
         * @throws RemoteException if the remote process has died.
         */
        @JvmStatic
        @Throws(RemoteException::class)
        fun getRemote(binder: IBinder): FileSystemManager {
            return NIOFactory.createRemote(binder)
        }
    }
}
