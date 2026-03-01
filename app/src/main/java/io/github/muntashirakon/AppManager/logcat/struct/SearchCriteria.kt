// SPDX-License-Identifier: WTFPL AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat.struct

import android.os.Build
import android.text.TextUtils
import io.github.muntashirakon.AppManager.utils.ArrayUtils
import java.util.*
import java.util.regex.Pattern

/**
 * Copyright 2012 Nolan Lawson
 */
class SearchCriteria(val query: String?) {
    private val mFilters = mutableListOf<Filter>()

    init {
        if (query != null) {
            val parts = query.split(" ".toRegex()).toTypedArray()
            var lastString: StringBuilder? = null
            val queryString = StringBuilder()
            for (part in parts) {
                if (lastString != null) {
                    if (part.endsWith(""")) {
                        val filter = mFilters[mFilters.size - 1]
                        filter.setValue(lastString.toString() + " " + part.substring(0, part.length - 1))
                        lastString = null
                    } else {
                        lastString.append(" ").append(part)
                    }
                    continue
                }
                val colon = part.indexOf(":")
                if (colon > 0) {
                    var type = part.substring(0, colon)
                    val inv = type.startsWith("-")
                    val regex = type.endsWith("~")
                    val exact = type.endsWith("=")
                    if (inv && type.length > 1) type = type.substring(1)
                    if (regex && type.length > 1) type = type.substring(0, type.length() - 1)
                    if (exact && type.length > 1) type = type.substring(0, type.length() - 1)
                    if (ArrayUtils.contains(TYPES, type)) {
                        val filter = Filter(type, regex, inv, exact)
                        mFilters.add(filter)
                        if (colon + 1 < part.length) {
                            val value = part.substring(colon + 1)
                            if (value.startsWith(""")) {
                                if (value.length > 1) {
                                    if (value.endsWith(""")) {
                                        filter.setValue(value.substring(1, value.length - 1))
                                    } else {
                                        lastString = StringBuilder(value.substring(1))
                                    }
                                } else {
                                    lastString = StringBuilder()
                                }
                            } else {
                                filter.setValue(value)
                            }
                        }
                        continue
                    }
                }
                queryString.append(" ").append(part)
            }
            val text = queryString.toString().trim()
            if (text.isNotEmpty()) {
                mFilters.add(Filter(TYPE_MSG, text))
            }
        }
    }

    val isEmpty: Boolean
        get() = mFilters.all { it.isEmpty }

    fun matches(logLine: LogLine): Boolean = mFilters.all { it.matches(logLine) }

    private class Filter {
        private val mType: String
        private val mRegex: Boolean
        private val mExact: Boolean
        private val mInverse: Boolean
        private var mValue: Any? = null

        constructor(type: String, regex: Boolean, inverse: Boolean, exact: Boolean) {
            mType = type
            mRegex = regex
            mInverse = inverse
            mExact = exact
        }

        constructor(type: String, value: String?) {
            mType = type
            mRegex = false
            mInverse = false
            mExact = false
            mValue = getRealValue(value)
        }

        fun setValue(value: String?) {
            mValue = getRealValue(value)
        }

        val isEmpty: Boolean
            get() = if (mValue is CharSequence) TextUtils.isEmpty(mValue as CharSequence) else mValue == null

        fun matches(logLine: LogLine): Boolean {
            if (isEmpty) return true
            val matches = when (mType) {
                TYPE_MSG -> {
                    val tag = logLine.tagName
                    val out = logLine.logOutput
                    if (mRegex) {
                        val p = mValue as Pattern
                        matchPattern(p, tag) || matchPattern(p, out)
                    } else {
                        val query = mValue as String
                        matchQuery(query, tag, mExact) || matchQuery(query, out, mExact)
                    }
                }
                TYPE_PID -> if (mRegex) {
                    matchPattern(mValue as Pattern, logLine.pid.toString())
                } else {
                    logLine.pid == mValue as Int
                }
                TYPE_PKG -> {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return true
                    val pkg = logLine.packageName
                    if (mRegex) matchPattern(mValue as Pattern, pkg) else matchQuery(mValue as String, pkg, mExact)
                }
                TYPE_UID -> {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return true
                    val owner = logLine.uidOwner
                    val uid = logLine.uid
                    if (mRegex) {
                        val p = mValue as Pattern
                        matchPattern(p, owner) || matchPattern(p, uid.toString())
                    } else {
                        if (mValue is Int) uid == mValue as Int else matchQuery(mValue as String, owner, mExact)
                    }
                }
                TYPE_TAG -> {
                    val tag = logLine.tagName
                    if (mRegex) matchPattern(mValue as Pattern, tag) else matchQuery(mValue as String, tag, mExact)
                }
                else -> throw IllegalArgumentException("Invalid filter: $mType")
            }
            return mInverse != matches
        }

        private fun getRealValue(value: String?): Any? {
            if (value == null) return null
            if (mRegex) return Pattern.compile(Pattern.quote(value))
            return when (mType) {
                TYPE_UID -> if (TextUtils.isDigitsOnly(value)) value.toInt() else if (mExact) value else value.lowercase(Locale.ROOT)
                TYPE_MSG, TYPE_PKG, TYPE_TAG -> if (mExact) value else value.lowercase(Locale.ROOT)
                TYPE_PID -> if (TextUtils.isDigitsOnly(value)) value.toInt() else null
                else -> throw IllegalArgumentException("Invalid filter: $mType")
            }
        }

        override fun toString(): String {
            return "Filter(mType='$mType', mRegex=$mRegex, mExact=$mExact, mInverse=$mInverse, mValue=$mValue)"
        }

        private fun matchPattern(pattern: Pattern, value: String?): Boolean = value?.let { pattern.matcher(it).matches() } ?: false
        private fun matchQuery(query: String, value: String?, exact: Boolean): Boolean = value?.let { if (exact) it == query else it.lowercase(Locale.ROOT).contains(query) } ?: false
    }

    companion object {
        private const val TYPE_MSG = "msg"
        private const val TYPE_PID = "pid"
        private const val TYPE_PKG = "pkg"
        private const val TYPE_TAG = "tag"
        private const val TYPE_UID = "uid"

        private val TYPES = arrayOf(TYPE_MSG, TYPE_PID, TYPE_PKG, TYPE_TAG, TYPE_UID)

        const val PID_KEYWORD = "$TYPE_PID:"
        const val PKG_KEYWORD = "$TYPE_PKG=:"
        const val TAG_KEYWORD = "$TYPE_TAG=:"
        const val UID_KEYWORD = "$TYPE_UID=:"
    }
}
