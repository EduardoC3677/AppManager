// SPDX-License-Identifier: MIT

package org.slf4j

object LoggerFactory {
    @JvmStatic
    fun getLogger(name: String): Logger {
        return LoggerImpl(name)
    }

    @JvmStatic
    fun getLogger(clazz: Class<*>): Logger {
        return getLogger(clazz.name)
    }
}
