// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server.common

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Message
import android.os.ParcelFileDescriptor
import android.os.ResultReceiver
import android.os.UserHandle
import android.os.WorkSource
import android.util.LruCache

// Copyright 2017 Zheng Li
object ClassUtils {
    private val sDefaultClassMap: MutableMap<String, Class<*>> = HashMap()
    private val sClassCache = LruCache<String, Class<*>>(128)

    init {
        // Primitive types
        defCacheClass(Byte::class.javaPrimitiveType!!)
        defCacheClass(Boolean::class.javaPrimitiveType!!)
        defCacheClass(Short::class.javaPrimitiveType!!)
        defCacheClass(Char::class.javaPrimitiveType!!)
        defCacheClass(Int::class.javaPrimitiveType!!)
        defCacheClass(Float::class.javaPrimitiveType!!)
        defCacheClass(Long::class.javaPrimitiveType!!)
        defCacheClass(Double::class.javaPrimitiveType!!)
        // Non-primitive types
        defCacheClass(String::class.java)
        defCacheClass(Bundle::class.java)
        defCacheClass(ComponentName::class.java)
        defCacheClass(Message::class.java)
        defCacheClass(ParcelFileDescriptor::class.java)
        defCacheClass(ResultReceiver::class.java)
        defCacheClass(WorkSource::class.java)
        defCacheClass(Intent::class.java)
        defCacheClass(IntentFilter::class.java)
        defCacheClass(UserHandle::class.java)
        // Arrays
        defCacheClass(ByteArray::class.java)
        defCacheClass(IntArray::class.java)
        defCacheClass(Array<String>::class.java)
        defCacheClass(Array<Intent>::class.java)
    }

    private fun defCacheClass(clazz: Class<*>) {
        sDefaultClassMap[clazz.name] = clazz
    }

    @JvmStatic
    fun string2Class(vararg names: String): Array<Class<*>?>? {
        val ret = arrayOfNulls<Class<*>>(names.size)
        for (i in names.indices) {
            ret[i] = string2Class(names[i])
        }
        return ret
    }

    @JvmStatic
    fun string2Class(name: String): Class<*>? {
        return try {
            var clazz = sDefaultClassMap[name]
            if (clazz == null) {
                clazz = sClassCache[name]
            }
            if (clazz == null) {
                clazz = Class.forName(name, false, null)
                sClassCache.put(name, clazz)
            }
            clazz
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
