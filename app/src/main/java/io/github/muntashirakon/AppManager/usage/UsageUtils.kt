// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.usage

import android.content.Context
import android.text.format.DateFormat
import io.github.muntashirakon.AppManager.R
import java.util.*

object UsageUtils {
    @JvmStatic
    fun getTimeInterval(@IntervalType interval: Int, date: Long): TimeInterval {
        return when (interval) {
            IntervalType.INTERVAL_WEEKLY -> getWeekBounds(date)
            else -> getDayBounds(date)
        }
    }

    @JvmStatic
    fun getIntervalDescription(context: Context, @IntervalType interval: Int, date: Long): CharSequence {
        return when (interval) {
            IntervalType.INTERVAL_WEEKLY -> getWeekDescription(context, date)
            else -> getDateDescription(context, date)
        }
    }

    @JvmStatic
    fun getNextDateFromInterval(@IntervalType interval: Int, currentDate: Long): Long {
        return when (interval) {
            IntervalType.INTERVAL_WEEKLY -> getNextWeekDay(currentDate)
            else -> getNextDay(currentDate)
        }
    }

    @JvmStatic
    fun getPreviousDateFromInterval(@IntervalType interval: Int, currentDate: Long): Long {
        return when (interval) {
            IntervalType.INTERVAL_WEEKLY -> getPreviousWeekDay(currentDate)
            else -> getPreviousDay(currentDate)
        }
    }

    @JvmStatic
    fun isToday(date: Long): Boolean {
        val calendar = Calendar.getInstance(TimeZone.getDefault())
        calendar.timeInMillis = date
        moveToStartOfDay(calendar)
        val targetTime = calendar.timeInMillis
        calendar.timeInMillis = System.currentTimeMillis()
        moveToStartOfDay(calendar)
        return targetTime == calendar.timeInMillis
    }

    @JvmStatic
    fun getToday(): TimeInterval {
        val timeNow = System.currentTimeMillis()
        return getDayBounds(timeNow)
    }

    @JvmStatic
    fun getLastWeek(): TimeInterval {
        val timeNow = System.currentTimeMillis()
        return getWeekBounds(timeNow)
    }

    @JvmStatic
    fun getDayBounds(date: Long): TimeInterval {
        val calendar = Calendar.getInstance(TimeZone.getDefault())
        calendar.timeInMillis = date
        moveToStartOfDay(calendar)
        val beginningOfDay = calendar.timeInMillis
        moveToEndOfDay(calendar)
        val endOfDay = calendar.timeInMillis
        return TimeInterval(IntervalType.INTERVAL_DAILY, beginningOfDay, endOfDay)
    }

    @JvmStatic
    fun hasNextDay(date: Long): Boolean {
        val calendar = Calendar.getInstance(TimeZone.getDefault())
        calendar.timeInMillis = date
        calendar.add(Calendar.DAY_OF_MONTH, 1)
        moveToStartOfDay(calendar)
        val dayStart = calendar.timeInMillis
        calendar.timeInMillis = System.currentTimeMillis()
        moveToStartOfDay(calendar)
        val todayStart = calendar.timeInMillis
        return dayStart <= todayStart
    }

    @JvmStatic
    fun getNextDay(date: Long): Long {
        val calendar = Calendar.getInstance(TimeZone.getDefault())
        calendar.timeInMillis = date
        calendar.add(Calendar.DAY_OF_MONTH, 1)
        return calendar.timeInMillis
    }

    @JvmStatic
    fun getNextWeekDay(date: Long): Long {
        val calendar = Calendar.getInstance(TimeZone.getDefault())
        calendar.timeInMillis = date
        calendar.add(Calendar.DAY_OF_MONTH, 7)
        return calendar.timeInMillis
    }

    @JvmStatic
    fun getPreviousDay(date: Long): Long {
        val calendar = Calendar.getInstance(TimeZone.getDefault())
        calendar.timeInMillis = date
        calendar.add(Calendar.DAY_OF_MONTH, -1)
        return calendar.timeInMillis
    }

    @JvmStatic
    fun getPreviousWeekDay(date: Long): Long {
        val calendar = Calendar.getInstance(TimeZone.getDefault())
        calendar.timeInMillis = date
        calendar.add(Calendar.DAY_OF_MONTH, -7)
        return calendar.timeInMillis
    }

    @JvmStatic
    fun getWeekBounds(date: Long): TimeInterval {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = date
        moveToStartOfDay(calendar)
        calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
        val startOfWeekMillis = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_WEEK, 6)
        moveToEndOfDay(calendar)
        val endOfWeekMillis = calendar.timeInMillis
        return TimeInterval(IntervalType.INTERVAL_WEEKLY, startOfWeekMillis, endOfWeekMillis)
    }

    @JvmStatic
    fun getWeekDescription(context: Context, dateMillis: Long): CharSequence {
        val targetInterval = getWeekBounds(dateMillis)
        val today = System.currentTimeMillis()
        if (today >= targetInterval.startTime && today <= targetInterval.endTime) {
            return context.getString(R.string.usage_this_week)
        }
        val sameDayLastWeek = getPreviousWeekDay(today)
        if (sameDayLastWeek >= targetInterval.startTime && sameDayLastWeek <= targetInterval.endTime) {
            return context.getString(R.string.usage_last_week)
        }
        val formatter = DateFormat.getMediumDateFormat(context)
        return "${formatter.format(targetInterval.startTime)}–${formatter.format(targetInterval.endTime)}"
    }

    @JvmStatic
    fun getDateDescription(context: Context, dateMillis: Long): CharSequence {
        val inputCal = Calendar.getInstance()
        inputCal.timeInMillis = dateMillis
        val todayCal = Calendar.getInstance()
        moveToStartOfDay(todayCal)
        moveToStartOfDay(inputCal)
        val diffMillis = todayCal.timeInMillis - inputCal.timeInMillis
        val oneDayMillis = 24 * 60 * 60 * 1000L
        return when {
            diffMillis == 0L -> context.getString(R.string.usage_today)
            diffMillis == oneDayMillis -> context.getString(R.string.usage_yesterday)
            else -> DateFormat.getMediumDateFormat(context).format(dateMillis)
        }
    }

    @JvmStatic
    fun moveToStartOfDay(cal: Calendar) {
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
    }

    @JvmStatic
    fun moveToEndOfDay(cal: Calendar) {
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
    }
}
