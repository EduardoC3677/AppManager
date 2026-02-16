// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server.common

import android.os.ParcelFileDescriptor
import java.io.FileDescriptor
import java.io.IOException

// Copyright 2017 Zheng Li
object ParamsFixer {
    @JvmStatic
    fun wrap(caller: Caller): Caller {
        val params = caller.parameters
        val paramsType = caller.parameterTypes
        if (paramsType != null && params != null) {
            for (i in params.indices) {
                params[i] = marshall(paramsType[i], params[i])
            }
        }
        return caller
    }

    @JvmStatic
    fun unwrap(caller: Caller): Caller {
        val params = caller.parameters
        val paramsType = caller.parameterTypes
        if (paramsType != null && params != null) {
            for (i in params.indices) {
                params[i] = unmarshall(paramsType[i], params[i])
            }
        }
        return caller
    }

    private fun marshall(type: Class<*>?, obj: Any?): Any? {
        if (FileDescriptor::class.java == type && obj is FileDescriptor) {
            try {
                return ParcelFileDescriptor.dup(obj)
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        return obj
    }

    private fun unmarshall(type: Class<*>?, obj: Any?): Any? {
        if (FileDescriptor::class.java == type && obj is ParcelFileDescriptor) {
            return obj.fileDescriptor
        }
        return obj
    }
}
