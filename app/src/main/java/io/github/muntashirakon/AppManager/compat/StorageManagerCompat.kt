// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.storage.StorageManager
import android.os.storage.StorageManagerHidden
import android.os.storage.StorageVolume
import android.system.ErrnoException
import android.system.OsConstants
import android.util.Log
import androidx.annotation.IntDef
import dev.rikka.tools.refine.Refine
import io.github.muntashirakon.AppManager.self.SelfPermissions
import io.github.muntashirakon.io.IoUtils.DEFAULT_BUFFER_SIZE
import java.io.IOException
import java.util.*

// Copyright 2018 Fung Gwo <fythonx@gmail.com>
// Copyright 2021 Muntashir Al-Islam
// Modified from https://gist.github.com/fython/924f8d9019bca75d22de116bb69a54a1
object StorageManagerCompat {
    private val TAG = StorageManagerCompat::class.java.simpleName

    /**
     * Flag indicating that a disk space allocation request should be allowed to
     * clear up to all reserved disk space.
     */
    const val FLAG_ALLOCATE_DEFY_ALL_RESERVED: Int = 1 shl 1

    /**
     * Flag indicating that a disk space allocation request should be allowed to
     * clear up to half of all reserved disk space.
     */
    const val FLAG_ALLOCATE_DEFY_HALF_RESERVED: Int = 1 shl 2

    @IntDef(
        flag = true, value = [
            FLAG_ALLOCATE_DEFY_ALL_RESERVED,
            FLAG_ALLOCATE_DEFY_HALF_RESERVED
        ]
    )
    @Retention(AnnotationRetention.SOURCE)
    annotation class AllocateFlags

    @JvmStatic
    fun from(context: Context): StorageManager {
        return context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
    }

    @JvmStatic
    @Throws(SecurityException::class)
    fun getVolumeList(context: Context, userId: Int, flags: Int): Array<StorageVolume> {
        if (!SelfPermissions.checkCrossUserPermission(userId, false, Process.myUid())) {
            return emptyArray()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return StorageManagerHidden.getVolumeList(userId, flags)
        } else {
            val volumes = Refine.unsafeCast<StorageManagerHidden>(from(context)).volumeList
            if (volumes != null) {
                return volumes
            }
        }
        return emptyArray()
    }

    @JvmStatic
    @Throws(IOException::class, UnsupportedOperationException::class)
    fun openProxyFileDescriptor(mode: Int, callback: ProxyFileDescriptorCallbackCompat): ParcelFileDescriptor {
        // We cannot use StorageManager#openProxyFileDescriptor directly due to its limitation on how callbacks are handled
        val pipe = ParcelFileDescriptor.createReliablePipe()
        return if (mode and ParcelFileDescriptor.MODE_READ_ONLY != 0) {
            // Reading requested i.e. we have to read from our side and write it to the target
            callback.handler.post {
                try {
                    ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { os ->
                        val totalSize = callback.onGetSize()
                        var currOffset: Long = 0
                        val buf = ByteArray(DEFAULT_BUFFER_SIZE)
                        var size: Int
                        while (callback.onRead(currOffset, DEFAULT_BUFFER_SIZE, buf).also { size = it } > 0) {
                            os.write(buf, 0, size)
                            currOffset += size.toLong()
                        }
                        if (totalSize > 0 && currOffset != totalSize) {
                            throw IOException(
                                String.format(
                                    Locale.ROOT,
                                    "Could not read the whole resource (total = %d, read = %d)",
                                    totalSize,
                                    currOffset
                                )
                            )
                        }
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "Failed to read file.", e)
                    try {
                        pipe[1].closeWithError(e.message)
                    } catch (exc: IOException) {
                        Log.e(TAG, "Can't even close PFD with error.", exc)
                    }
                } catch (e: ErrnoException) {
                    Log.e(TAG, "Failed to read file.", e)
                    try {
                        pipe[1].closeWithError(e.message)
                    } catch (exc: IOException) {
                        Log.e(TAG, "Can't even close PFD with error.", exc)
                    }
                } finally {
                    callback.onRelease()
                }
            }
            pipe[0]
        } else if (mode and ParcelFileDescriptor.MODE_WRITE_ONLY != 0) {
            // Writing requested i.e. we have to read from the target and write it to our side
            callback.handler.post {
                try {
                    ParcelFileDescriptor.AutoCloseInputStream(pipe[0]).use { `is` ->
                        var currOffset: Long = 0
                        val buf = ByteArray(DEFAULT_BUFFER_SIZE)
                        var size: Int
                        while (`is`.read(buf).also { size = it } != -1) {
                            callback.onWrite(currOffset, size, buf)
                            currOffset += size.toLong()
                        }
                        val totalSize = callback.onGetSize()
                        if (totalSize > 0 && currOffset != totalSize) {
                            throw IOException(
                                String.format(
                                    Locale.ROOT,
                                    "Could not write the whole resource (total = %d, read = %d)",
                                    totalSize,
                                    currOffset
                                )
                            )
                        }
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "Failed to write file.", e)
                    try {
                        pipe[0].closeWithError(e.message)
                    } catch (exc: IOException) {
                        Log.e(TAG, "Can't even close PFD with error.", exc)
                    }
                } catch (e: ErrnoException) {
                    Log.e(TAG, "Failed to write file.", e)
                    try {
                        pipe[0].closeWithError(e.message)
                    } catch (exc: IOException) {
                        Log.e(TAG, "Can't even close PFD with error.", exc)
                    }
                } finally {
                    callback.onRelease()
                }
            }
            pipe[1]
        } else {
            // Should never happen.
            pipe[0].close()
            pipe[1].close()
            Log.e(TAG, "Mode $mode is not supported.")
            throw UnsupportedOperationException("Mode $mode is not supported.")
        }
    }

    abstract class ProxyFileDescriptorCallbackCompat(val handler: Handler) {
        /**
         * Returns size of bytes provided by the file descriptor.
         *
         * @return Size of bytes.
         * @throws ErrnoException Containing E constants in OsConstants.
         */
        @Throws(ErrnoException::class)
        open fun onGetSize(): Long {
            throw ErrnoException("onGetSize", OsConstants.EBADF)
        }

        /**
         * Provides bytes read from file descriptor.
         * It needs to return exact requested size of bytes unless it reaches file end.
         *
         * @param offset Offset in bytes from the file head specifying where to read bytes. If a seek
         * operation is conducted on the file descriptor, then a read operation is requested, the
         * offset refrects the proper position of requested bytes.
         * @param size   Size for read bytes.
         * @param data   Byte array to store read bytes.
         * @return Size of bytes returned by the function.
         * @throws ErrnoException Containing E constants in OsConstants.
         */
        @Throws(ErrnoException::class)
        open fun onRead(offset: Long, size: Int, data: ByteArray?): Int {
            throw ErrnoException("onRead", OsConstants.EBADF)
        }

        /**
         * Handles bytes written to file descriptor.
         *
         * @param offset Offset in bytes from the file head specifying where to write bytes. If a seek
         * operation is conducted on the file descriptor, then a write operation is requested, the
         * offset refrects the proper position of requested bytes.
         * @param size   Size for write bytes.
         * @param data   Byte array to be written to somewhere.
         * @return Size of bytes processed by the function.
         * @throws ErrnoException Containing E constants in OsConstants.
         */
        @Throws(ErrnoException::class)
        open fun onWrite(offset: Long, size: Int, data: ByteArray?): Int {
            throw ErrnoException("onWrite", OsConstants.EBADF)
        }

        /**
         * Ensures all the written data are stored in permanent storage device.
         * For example, if it has data stored in on memory cache, it needs to flush data to storage
         * device.
         *
         * @throws ErrnoException Containing E constants in OsConstants.
         */
        @Throws(ErrnoException::class)
        open fun onFsync() {
            throw ErrnoException("onFsync", OsConstants.EINVAL)
        }

        /**
         * Invoked after the file is closed.
         */
        open fun onRelease() {
        }
    }
}
