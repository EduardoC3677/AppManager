// SPDX-License-Identifier: GPL-3.0-or-later

package org.slf4j

import android.util.Log
import io.github.muntashirakon.AppManager.compat.BuildConfig
import java.util.*

internal class LoggerImpl(private val mTag: String) : Logger {
    override fun getName(): String {
        return mTag
    }

    override fun isTraceEnabled(): Boolean {
        return false
    }

    override fun trace(msg: String) {}

    override fun trace(format: String, arg: Any?) {}

    override fun trace(format: String, arg1: Any?, arg2: Any?) {}

    override fun trace(format: String, vararg arguments: Any?) {}

    override fun trace(msg: String, t: Throwable?) {}

    override fun isDebugEnabled(): Boolean {
        return BuildConfig.DEBUG
    }

    override fun debug(msg: String) {
        Log.d(mTag, msg)
    }

    override fun debug(format: String, arg: Any?) {
        Log.d(mTag, String.format(format, arg))
    }

    override fun debug(format: String, arg1: Any?, arg2: Any?) {
        Log.d(mTag, String.format(format, arg1, arg2))
    }

    override fun debug(format: String, vararg arguments: Any?) {
        Log.d(mTag, String.format(format, *arguments))
    }

    override fun debug(msg: String, t: Throwable?) {
        Log.d(mTag, msg, t)
    }

    override fun isInfoEnabled(): Boolean {
        return true
    }

    override fun info(msg: String) {
        Log.i(mTag, msg)
    }

    override fun info(format: String, arg: Any?) {
        Log.i(mTag, String.format(format, arg))
    }

    override fun info(format: String, arg1: Any?, arg2: Any?) {
        Log.i(mTag, String.format(format, arg1, arg2))
    }

    override fun info(format: String, vararg arguments: Any?) {
        Log.i(mTag, String.format(format, *arguments))
    }

    override fun info(msg: String, t: Throwable?) {
        Log.i(mTag, msg, t)
    }

    override fun isWarnEnabled(): Boolean {
        return true
    }

    override fun warn(msg: String) {
        Log.w(mTag, msg)
    }

    override fun warn(format: String, arg: Any?) {
        Log.w(mTag, String.format(Locale.ROOT, format, arg))
    }

    override fun warn(format: String, vararg arguments: Any?) {
        Log.w(mTag, String.format(Locale.ROOT, format, *arguments))
    }

    override fun warn(format: String, arg1: Any?, arg2: Any?) {
        Log.w(mTag, String.format(Locale.ROOT, format, arg1, arg2))
    }

    override fun warn(msg: String, t: Throwable?) {
        Log.w(mTag, msg, t)
    }

    override fun isErrorEnabled(): Boolean {
        return true
    }

    override fun error(msg: String) {
        Log.e(mTag, msg)
    }

    override fun error(format: String, arg: Any?) {
        Log.e(mTag, String.format(Locale.ROOT, format, arg))
    }

    override fun error(format: String, arg1: Any?, arg2: Any?) {
        Log.e(mTag, String.format(Locale.ROOT, format, arg1, arg2))
    }

    override fun error(format: String, vararg arguments: Any?) {
        Log.e(mTag, String.format(Locale.ROOT, format, *arguments))
    }

    override fun error(msg: String, t: Throwable?) {
        Log.e(mTag, msg, t)
    }
}
