// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.util

import android.os.Parcel
import androidx.collection.ArrayMap
import androidx.collection.ArraySet
import java.util.*

object ParcelUtils {
    /**
     * Write an array set to the parcel.
     *
     * @param val  The array set to write.
     * @param dest The parcel to write to.
     */
    @JvmStatic
    fun writeArraySet(`val`: ArraySet<*>?, dest: Parcel) {
        val size = `val`?.size ?: -1
        dest.writeInt(size)
        if (`val` != null) {
            for (i in 0 until size) {
                dest.writeValue(`val`.valueAt(i))
            }
        }
    }

    /**
     * Reads an array set.
     *
     * @param in     The parcel to read from.
     * @param loader The class loader to use.
     * @return The array set.
     */
    @JvmStatic
    fun readArraySet(`in`: Parcel, loader: ClassLoader?): ArraySet<*>? {
        val size = `in`.readInt()
        if (size < 0) {
            return null
        }
        val result = ArraySet<Any?>(size)
        for (i in 0 until size) {
            val value = `in`.readValue(loader)
            result.add(value)
        }
        return result
    }

    @JvmStatic
    fun writeMap(map: Map<*, *>, parcel: Parcel) {
        parcel.writeInt(map.size)
        for ((key, value) in map) {
            parcel.writeValue(key)
            parcel.writeValue(value)
        }
    }

    @JvmStatic
    fun <K, V> readMap(parcel: Parcel, keyCl: ClassLoader?, valCl: ClassLoader?): Map<K, V> {
        val size = parcel.readInt()
        val map = HashMap<K, V>(size)
        for (i in 0 until size) {
            @Suppress("UNCHECKED_CAST")
            map[parcel.readValue(keyCl) as K] = parcel.readValue(valCl) as V
        }
        return map
    }

    @JvmStatic
    fun <K, V> readArrayMap(parcel: Parcel, keyCl: ClassLoader?, valCl: ClassLoader?): ArrayMap<K, V> {
        val size = parcel.readInt()
        val map = ArrayMap<K, V>(size)
        for (i in 0 until size) {
            @Suppress("UNCHECKED_CAST")
            map[parcel.readValue(keyCl) as K] = parcel.readValue(valCl) as V
        }
        return map
    }

    @JvmStatic
    fun writeArrayList(`val`: ArrayList<*>?, dest: Parcel) {
        val size = `val`?.size ?: -1
        dest.writeInt(size)
        if (`val` != null) {
            for (i in 0 until size) {
                dest.writeValue(`val`[i])
            }
        }
    }

    @JvmStatic
    fun <T> readArrayList(`in`: Parcel, loader: ClassLoader?): ArrayList<T>? {
        val size = `in`.readInt()
        if (size < 0) {
            return null
        }
        val result = ArrayList<T>(size)
        for (i in 0 until size) {
            val value = `in`.readValue(loader)
            @Suppress("UNCHECKED_CAST")
            result.add(value as T)
        }
        return result
    }
}
