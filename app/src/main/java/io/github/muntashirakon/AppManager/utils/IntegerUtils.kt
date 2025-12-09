// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils

import java.nio.ByteBuffer

object IntegerUtils {
    @JvmStatic
    fun getUInt8(buffer: ByteBuffer): Int {
        return buffer.get().toInt() and 0xff
    }

    @JvmStatic
    fun getUInt16(buffer: ByteBuffer): Int {
        return buffer.short.toInt() and 0xffff
    }

    @JvmStatic
    fun getUInt32(buffer: ByteBuffer): Long {
        return buffer.int.toLong() and 0xffffffffL
    }

    @JvmStatic
    fun getUInt32(buffer: ByteBuffer, position: Int): Long {
        return buffer.getInt(position).toLong() and 0xffffffffL
    }
}
