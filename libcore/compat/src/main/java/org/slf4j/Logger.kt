// SPDX-License-Identifier: MIT

package org.slf4j

interface Logger {
    fun getName(): String

    fun isTraceEnabled(): Boolean

    fun trace(msg: String)

    fun trace(format: String, arg: Any?)

    fun trace(format: String, arg1: Any?, arg2: Any?)

    fun trace(format: String, vararg arguments: Any?)

    fun trace(msg: String, t: Throwable?)

    fun isDebugEnabled(): Boolean

    fun debug(msg: String)

    fun debug(format: String, arg: Any?)

    fun debug(format: String, arg1: Any?, arg2: Any?)

    fun debug(format: String, vararg arguments: Any?)

    fun debug(msg: String, t: Throwable?)

    fun isInfoEnabled(): Boolean

    fun info(msg: String)

    fun info(format: String, arg: Any?)

    fun info(format: String, arg1: Any?, arg2: Any?)

    fun info(format: String, vararg arguments: Any?)

    fun info(msg: String, t: Throwable?)

    fun isWarnEnabled(): Boolean

    fun warn(msg: String)

    fun warn(format: String, arg: Any?)

    fun warn(format: String, vararg arguments: Any?)

    fun warn(format: String, arg1: Any?, arg2: Any?)

    fun warn(msg: String, t: Throwable?)

    fun isErrorEnabled(): Boolean

    fun error(msg: String)

    fun error(format: String, arg: Any?)

    fun error(format: String, arg1: Any?, arg2: Any?)

    fun error(format: String, vararg arguments: Any?)

    fun error(msg: String, t: Throwable?)
}
