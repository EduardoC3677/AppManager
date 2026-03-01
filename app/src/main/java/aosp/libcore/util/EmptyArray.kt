// SPDX-License-Identifier: Apache-2.0

package aosp.libcore.util

// Copyright 2006 The Android Open Source Project
object EmptyArray {
    @JvmField
    val BOOLEAN = BooleanArray(0)
    @JvmField
    val BYTE = ByteArray(0)
    @JvmField
    val CHAR = CharArray(0)
    @JvmField
    val DOUBLE = DoubleArray(0)
    @JvmField
    val FLOAT = FloatArray(0)
    @JvmField
    val INT = IntArray(0)
    @JvmField
    val LONG = LongArray(0)

    @JvmField
    val CLASS: Array<Class<*>> = emptyArray()
    @JvmField
    val OBJECT = emptyArray<Any>()
    @JvmField
    val STRING = emptyArray<String>()
    @JvmField
    val THROWABLE = emptyArray<Throwable>()
    @JvmField
    val STACK_TRACE_ELEMENT = emptyArray<StackTraceElement>()
    @JvmField
    val TYPE = emptyArray<java.lang.reflect.Type>()
    @JvmField
    val TYPE_VARIABLE = emptyArray<java.lang.reflect.TypeVariable<*>>()
}
