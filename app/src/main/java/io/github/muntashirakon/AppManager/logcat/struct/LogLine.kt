// SPDX-License-Identifier: WTFPL AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat.struct

import android.os.RemoteException
import android.text.TextUtils
import android.util.Log
import android.util.LruCache
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat
import io.github.muntashirakon.AppManager.logcat.reader.ScrubberUtils
import io.github.muntashirakon.AppManager.users.Owners
import java.util.*
import java.util.regex.Pattern

/**
 * Copyright 2012 Nolan Lawson
 * Copyright 2021 Muntashir Al-Islam
 */
class LogLine(val originalLine: String) {
    var timestamp: String? = null
    var logLevel: Int = 0
    var tagName: String = ""\nvar logOutput: String = ""\nset(value) {
            field = if (omitSensitiveInfo) ScrubberUtils.scrubLine(value) else value
        }
    var pid: Int = -1
    var tid: Int = -1
    var uid: Int = -1
    var uidOwner: String? = null
    var packageName: String? = null
    var isExpanded: Boolean = false

    val processIdText: String
        get() = convertLogLevelToChar(logLevel).toString()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LogLine) return false
        return originalLine == other.originalLine
    }

    override fun hashCode(): Int = Objects.hash(originalLine)

    override fun toString(): String = originalLine

    companion object {
        val TAG: String = LogLine::class.java.simpleName
        const val LOG_FATAL = 15

        private val LOG_PATTERN = Pattern.compile(
            "(\d{2}-\d{2}\s\d{2}:\d{2}:\d{2}\.\d{3})\s+" +
                    "(.+\d+)\s+" +
                    "(\d+)\s+" +
                    "([ADEIVWF])\s+" +
                    "(.+?)" +
                    ": (.*)"\n)

        private val LOG_PATTERN_LEGACY = Pattern.compile(
            "(\d{2}-\d{2}\s\d{2}:\d{2}:\d{2}\.\d{3})\s+" +
                    "([ADEIVWF])/" +
                    "([^(].+)" +
                    "\(\s*(\d+)(?:\*\s*\d+)?\)" +
                    ": (.*)"\n)

        private const val BEGIN = "--------- beginning of "\n@JvmField
        var omitSensitiveInfo = false

        @JvmStatic
        fun newLogLine(originalLine: String, expanded: Boolean, filterPattern: Pattern?): LogLine? {
            val logLine = LogLine(originalLine)
            logLine.isExpanded = expanded

            if (matchPattern(originalLine, logLine)) {
                if (filterPattern != null && filterPattern.matcher(logLine.tagName).matches()) {
                    return null
                }
                return logLine
            }
            if (matchPatternLegacy(originalLine, logLine)) {
                if (filterPattern != null && filterPattern.matcher(logLine.tagName).matches()) {
                    return null
                }
                return logLine
            }
            if (originalLine.startsWith(BEGIN)) {
                Log.d(TAG, "Started buffer: ${originalLine.substring(BEGIN.length)}")
                return null
            } else {
                Log.w(TAG, "Line doesn't match pattern: $originalLine")
                logLine.logOutput = originalLine
                logLine.logLevel = -1
            }
            return logLine
        }

        @JvmStatic
        fun convertCharToLogLevel(logLevelChar: Char): Int {
            return when (logLevelChar) {
                'A' -> Log.ASSERT
                'D' -> Log.DEBUG
                'E' -> Log.ERROR
                'I' -> Log.INFO
                'V' -> Log.VERBOSE
                'W' -> Log.WARN
                'F' -> LOG_FATAL
                else -> -1
            }
        }

        @JvmStatic
        fun convertLogLevelToChar(logLevel: Int): Char {
            return when (logLevel) {
                Log.ASSERT -> 'A'
                Log.DEBUG -> 'D'
                Log.ERROR -> 'E'
                Log.INFO -> 'I'
                Log.VERBOSE -> 'V'
                Log.WARN -> 'W'
                LOG_FATAL -> 'F'
                else -> ' '
            }
        }

        private fun matchPatternLegacy(originalLine: String, logLine: LogLine): Boolean {
            val matcher = LOG_PATTERN_LEGACY.matcher(originalLine)
            if (!matcher.matches()) return false
            logLine.timestamp = matcher.group(1)
            logLine.logLevel = convertCharToLogLevel(matcher.group(2)!![0])
            logLine.tagName = matcher.group(3)!!.trim()
            logLine.pid = matcher.group(4)!!.toInt()
            logLine.logOutput = matcher.group(5)!!
            return true
        }

        private fun matchPattern(originalLine: String, logLine: LogLine): Boolean {
            val matcher = LOG_PATTERN.matcher(originalLine)
            if (!matcher.matches()) return false
            logLine.timestamp = matcher.group(1)
            val uidPid = matcher.group(2)!!.split("\s+".toRegex(), 2).toTypedArray()
            if (uidPid.size == 2) {
                val owner = uidPid[0]
                val uid = Owners.parseUid(owner)
                logLine.uidOwner = owner
                logLine.uid = uid
                logLine.packageName = retrievePackageName(uid)
            }
            logLine.pid = uidPid[if (uidPid.size == 2) 1 else 0].toInt()
            logLine.tid = matcher.group(3)!!.toInt()
            logLine.logLevel = convertCharToLogLevel(matcher.group(4)!![0])
            logLine.tagName = matcher.group(5)!!.trim()
            logLine.logOutput = matcher.group(6)!!
            return true
        }

        private val sUidPackageNameCache = LruCache<Int, String>(300)

        private fun retrievePackageName(uid: Int): String? {
            if (uid < 0) return null
            val cached = sUidPackageNameCache[uid]
            if (cached != null) return if (cached.isEmpty()) null else cached
            return try {
                val packages = PackageManagerCompat.getPackageManager().getPackagesForUid(uid)
                var selectedPackage: String? = null
                if (!packages.isNullOrEmpty()) {
                    if (packages.size == 1) {
                        selectedPackage = packages[0]
                    } else {
                        var shortestIndex = 0
                        for (i in packages.indices) {
                            if (packages[shortestIndex].length > packages[i].length) {
                                shortestIndex = i
                            }
                        }
                        selectedPackage = packages[shortestIndex]
                    }
                }
                sUidPackageNameCache.put(uid, selectedPackage ?: "")
                selectedPackage
            } catch (e: RemoteException) {
                null
            }
        }
    }
}
