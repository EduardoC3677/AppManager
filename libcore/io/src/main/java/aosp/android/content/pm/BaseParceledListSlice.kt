// SPDX-License-Identifier: GPL-3.0-or-later

package aosp.android.content.pm

import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Parcel
import android.os.Parcelable
import android.os.RemoteException
import android.util.Log
import io.github.muntashirakon.compat.os.ParcelCompat2
import io.github.muntashirakon.io.IoUtils
import java.util.*

/**
 * Transfer a large list of Parcelable objects across an IPC.  Splits into
 * multiple transactions if needed.
 * <p>
 * Caveat: for efficiency and security, all elements must be the same concrete type.
 * In order to avoid writing the class name of each object, we must ensure that
 * each object is the same type, or else unparceling then reparceling the data may yield
 * a different result if the class name encoded in the Parcelable is a Base type.
 * See b/17671747.
 */
// Copyright 2011 The Android Open Source Project
abstract class BaseParceledListSlice<T> : Parcelable {
    val list: List<T>

    private var mInlineCountLimit = Int.MAX_VALUE

    constructor(list: List<T>) {
        this.list = list
    }

    @Suppress("UNCHECKED_CAST")
    internal constructor(p: Parcel) {
        // Unlike the Android frameworks, we have no access to certain remote class
        val loader = BaseParceledListSlice::class.java.classLoader
        val N = p.readInt()
        list = ArrayList(N)
        if (DEBUG) Log.d(TAG, "Retrieving $N items")
        if (N <= 0) {
            return
        }

        val creator = readParcelableCreator(p, loader)
        var listElementClass: Class<*>? = null

        var i = 0
        while (i < N) {
            if (p.readInt() == 0) {
                break
            }

            val parcelable = readCreator(creator, p, loader)
            if (listElementClass == null) {
                listElementClass = parcelable!!::class.java
            } else {
                verifySameType(listElementClass, parcelable!!::class.java)
            }

            (list as MutableList<T>).add(parcelable)

            if (DEBUG) Log.d(TAG, "Read inline #" + i + ": " + list[list.size - 1])
            i++
        }
        if (i >= N) {
            return
        }
        val retriever = p.readStrongBinder()
        while (i < N) {
            if (DEBUG) Log.d(TAG, "Reading more @" + i + " of " + N + ": retriever=" + retriever)
            val data = ParcelCompat2.obtain(retriever)
            val reply = ParcelCompat2.obtain(retriever)
            data.writeInt(i)
            try {
                retriever.transact(IBinder.FIRST_CALL_TRANSACTION, data, reply, 0)
            } catch (e: RemoteException) {
                Log.w(TAG, "Failure retrieving array; only received $i of $N", e)
                return
            }
            while (i < N && reply.readInt() != 0) {
                val parcelable = readCreator(creator, reply, loader)
                verifySameType(listElementClass, parcelable!!::class.java)

                (list as MutableList<T>).add(parcelable)

                if (DEBUG) Log.d(TAG, "Read extra #" + i + ": " + list[list.size - 1])
                i++
            }
            reply.recycle()
            data.recycle()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun readCreator(creator: Parcelable.Creator<*>, p: Parcel, loader: ClassLoader?): T {
        if (creator is Parcelable.ClassLoaderCreator<*>) {
            val classLoaderCreator = creator as Parcelable.ClassLoaderCreator<*>
            return classLoaderCreator.createFromParcel(p, loader) as T
        }
        return creator.createFromParcel(p) as T
    }

    /**
     * Set a limit on the maximum number of entries in the array that will be included
     * inline in the initial parcelling of this object.
     */
    fun setInlineCountLimit(maxCount: Int) {
        mInlineCountLimit = maxCount
    }

    /**
     * Write this to another Parcel. Note that this discards the internal Parcel
     * and should not be used anymore. This is so we can pass this to a Binder
     * where we won't have a chance to call recycle on this.
     */
    override fun writeToParcel(dest: Parcel, flags: Int) {
        val N = list.size
        dest.writeInt(N)
        if (DEBUG) Log.d(TAG, "Writing $N items")
        if (N > 0) {
            val listElementClass = list[0]!!::class.java
            writeParcelableCreator(list[0], dest)
            var i = 0
            while (i < N && i < mInlineCountLimit && dest.dataSize() < MAX_IPC_SIZE) {
                dest.writeInt(1)

                val parcelable = list[i]
                verifySameType(listElementClass, parcelable!!::class.java)
                writeElement(parcelable, dest, flags)

                if (DEBUG) Log.d(TAG, "Wrote inline #" + i + ": " + list[i])
                i++
            }
            if (i < N) {
                dest.writeInt(0)
                val retriever = object : Binder() {
                    @Throws(RemoteException::class)
                    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                        if (code != FIRST_CALL_TRANSACTION || reply == null) {
                            return super.onTransact(code, data, reply, flags)
                        }
                        var idx = data.readInt()
                        if (DEBUG) Log.d(TAG, "Writing more @$idx of $N")
                        while (idx < N && reply.dataSize() < MAX_IPC_SIZE) {
                            reply.writeInt(1)

                            val parcelable = list[idx]
                            verifySameType(listElementClass, parcelable!!::class.java)
                            writeElement(parcelable, reply, flags)

                            if (DEBUG) Log.d(TAG, "Wrote extra #" + idx + ": " + list[idx])
                            idx++
                        }
                        if (idx < N) {
                            if (DEBUG) Log.d(TAG, "Breaking @$idx of $N")
                            reply.writeInt(0)
                        }
                        return true
                    }
                }
                if (DEBUG) Log.d(TAG, "Breaking @" + i + " of " + N + ": retriever=" + retriever)
                dest.writeStrongBinder(retriever)
            }
        }
    }

    protected abstract fun writeElement(parcelable: T, reply: Parcel, callFlags: Int)

    protected abstract fun writeParcelableCreator(parcelable: T, dest: Parcel)

    protected abstract fun readParcelableCreator(from: Parcel, loader: ClassLoader?): Parcelable.Creator<*>

    companion object {
        private const val TAG = "ParceledListSlice"
        private const val DEBUG = false

        private val MAX_IPC_SIZE: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            IBinder.getSuggestedMaxIpcSizeBytes()
        } else {
            IoUtils.DEFAULT_BUFFER_SIZE
        }

        private fun verifySameType(expected: Class<*>?, actual: Class<*>) {
            if (actual != expected) {
                throw IllegalArgumentException(
                    "Can't unparcel type "
                            + actual.name + " in list of type "
                            + (if (expected == null) null else expected.name)
                )
            }
        }
    }
}
