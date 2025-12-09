// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils

import android.content.Context
import io.github.muntashirakon.AppManager.R
import java.text.DateFormat
import java.util.Date

object DateUtils {
    @JvmStatic
    fun formatDate(context: Context, millis: Long): String {
        val dateTime = Date(millis)
        return getDateFormat(context).format(dateTime)
    }

    @JvmStatic
    fun formatDateTime(context: Context, millis: Long): String {
        val dateTime = Date(millis)
        val date = getDateFormat(context).format(dateTime)
        val time = getTimeFormat(context).format(dateTime)
        return "$date $time"
    }

    @JvmStatic
    fun formatMediumDateTime(context: Context, millis: Long): String {
        val dateTime = Date(millis)
        val date = getMediumDateFormat(context).format(dateTime)
        val time = getTimeFormat(context).format(dateTime)
        return "$date $time"
    }

    @JvmStatic
    fun formatLongDateTime(context: Context, millis: Long): String {
        val dateTime = Date(millis)
        val date = getLongDateFormat(context).format(dateTime)
        val time = getTimeFormat(context).format(dateTime)
        return "$date $time"
    }

    @JvmStatic
    @JvmOverloads
    fun getFormattedDuration(context: Context, millis: Long, addSign: Boolean = false, includeSeconds: Boolean = false): String {
        val res = context.resources
        if (millis == 0L) {
            return res.getQuantityString(R.plurals.usage_minutes, 0, 0)
        }
        var fTime = ""
        var remainingMillis = millis
        if (remainingMillis < 0) {
            remainingMillis = -remainingMillis
            if (addSign) fTime = "- "
        }
        var time = remainingMillis / 1000 // seconds
        val month = time / 2_592_000
        time %= 2_592_000
        val day = time / 86_400
        time %= 86_400
        val hour = time / 3_600
        time %= 3_600
        val min = time / 60
        val sec = time % 60
        var count = 0
        if (month != 0L) {
            fTime += res.getQuantityString(R.plurals.usage_months, month.toInt(), month)
            ++count
        }
        if (day != 0L) {
            fTime += (if (count > 0) " " else "") + res.getQuantityString(R.plurals.usage_days, day.toInt(), day)
            ++count
        }
        if (hour != 0L) {
            fTime += (if (count > 0) " " else "") + res.getQuantityString(R.plurals.usage_hours, hour.toInt(), hour)
            ++count
        }
        if (min != 0L) {
            fTime += (if (count > 0) " " else "") + res.getQuantityString(R.plurals.usage_minutes, min.toInt(), min)
            ++count
        } else if (count == 0 && !includeSeconds) {
            fTime = context.getString(R.string.usage_less_than_a_minute)
        }
        if (includeSeconds) {
            fTime += (if (count > 0) " " else "") + res.getQuantityString(R.plurals.usage_seconds, sec.toInt(), sec)
        }
        return fTime
    }

    @JvmStatic
    fun getFormattedDurationShort(millis: Long, addSign: Boolean, includeMinutes: Boolean, includeSeconds: Boolean): String {
        val fTime = StringBuilder()
        var remainingMillis = millis
        val isNegative = if (remainingMillis < 0) {
            remainingMillis = -remainingMillis
            true
        } else {
            false
        }
        var time = remainingMillis / 1000 // seconds
        val month = time / 2_592_000
        time %= 2_592_000
        val day = time / 86_400
        time %= 86_400
        val hour = time / 3_600
        time %= 3_600
        val min = time / 60
        val sec = time % 60
        if (!includeMinutes && (min > 0 || sec > 0)) {
            fTime.append("~")
        }
        if (isNegative && addSign) {
            fTime.append("-")
        }
        var count = 0
        if (month != 0L) {
            fTime.append(month).append("mo")
            ++count
        }
        if (day != 0L) {
            if (count > 0) fTime.append(" ")
            fTime.append(day).append("d")
            ++count
        }
        if (hour != 0L) {
            if (count > 0) fTime.append(" ")
            fTime.append(hour).append("h")
            ++count
        }
        if (min != 0L) {
            if (count > 0) fTime.append(" ")
            fTime.append(min).append("m")
            ++count
        } else if (count == 0 && !includeSeconds) {
            fTime.append("1m")
        }
        if (includeSeconds) {
            if (count > 0) fTime.append(" ")
            fTime.append(sec).append("s")
        }
        return fTime.toString()
    }

    @JvmStatic
    fun getFormattedDurationSingle(millis: Long, addSign: Boolean): String {
        val fTime = StringBuilder()
        var remainingMillis = millis
        val isNegative = if (remainingMillis < 0) {
            remainingMillis = -remainingMillis
            true
        } else {
            false
        }
        var time = remainingMillis / 1000 // seconds
        val month = time / 2_592_000
        time %= 2_592_000
        val day = time / 86_400
        time %= 86_400
        val hour = time / 3_600
        time %= 3_600
        val min = time / 60
        if (month > 0) {
            if (day > 0) {
                fTime.append('~')
            }
            fTime.append(month).append("mo")
        } else if (day > 0) {
            if (hour > 0) fTime.append('~')
            fTime.append(day).append('d')
        } else if (hour > 0) {
            if (min > 0) fTime.append('~')
            fTime.append(hour).append('h')
        } else if (min > 0) {
            fTime.append(min).append("m")
        } else {
            // Seconds not included
            fTime.append("<1m")
        }
        return (if (addSign && isNegative) "-" else "") + fTime
    }

    private fun getDateFormat(context: Context): DateFormat {
        return android.text.format.DateFormat.getDateFormat(context)
    }

    private fun getMediumDateFormat(context: Context): DateFormat {
        return android.text.format.DateFormat.getMediumDateFormat(context)
    }

    private fun getLongDateFormat(context: Context): DateFormat {
        return android.text.format.DateFormat.getLongDateFormat(context)
    }

    private fun getTimeFormat(context: Context): DateFormat {
        return android.text.format.DateFormat.getTimeFormat(context)
    }
}
