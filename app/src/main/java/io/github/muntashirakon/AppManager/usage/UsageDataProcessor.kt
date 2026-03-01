// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.usage

import android.content.Context
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.utils.ArrayUtils
import io.github.muntashirakon.AppManager.utils.ContextUtils
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.*

object UsageDataProcessor {
    private const val HOUR_IN_MILLIS = 60 * 60 * 1000L
    private const val DAY_IN_MILLIS = 24L * 60 * 60 * 1000

    @JvmStatic
    fun updateChartWithAppUsage(chart: BarChartView, events: List<PackageUsageInfo.Entry>, @IntervalType interval: Int, targetDate: Long) {
        when (interval) {
            IntervalType.INTERVAL_WEEKLY -> updateChartWithDailyAppUsage(chart, events, targetDate)
            else -> updateChartWithHourlyAppUsage(chart, events, targetDate)
        }
    }

    @JvmStatic
    fun updateChartWithHourlyAppUsage(chart: BarChartView, events: List<PackageUsageInfo.Entry>, targetDate: Long) {
        val hourlyMinutes = convertToMinutes(groupIntoHourlyBucketsForDay(events, targetDate))
        val values = mutableListOf<Float>()
        val labels = mutableListOf<String>()
        val timeLabels = getHourLabels()
        for (i in 0 until Math.min(hourlyMinutes.size, timeLabels.size)) {
            values.add(hourlyMinutes[i])
            labels.add(timeLabels[i])
        }
        chart.setManualYAxisRange(0f, nextDivisibleBy4(Collections.max(values)))
        chart.setYAxisFormat(chart.context.getString(R.string.usage_bar_chart_y_axis_label_minute))
        chart.setData(values, labels)
        chart.setTooltipListener(object : BarChartView.TooltipListener {
            override fun getTooltipText(context: Context, barIndex: Int, value: Float, label: String): String {
                return context.getString(R.string.usage_bar_chart_tooltip_minutes, label, value)
            }

            override fun getAccessibilityText(context: Context, barIndex: Int, barCount: Int, value: Float, label: String): String {
                return context.getString(R.string.usage_daily_bar_chart_accessibility_description, label, value, barIndex + 1, barCount)
            }
        })
    }

    @JvmStatic
    fun updateChartWithDailyAppUsage(chart: BarChartView, events: List<PackageUsageInfo.Entry>, targetDate: Long) {
        val dailyMillis = groupIntoDailyBucketsForWeek(events, targetDate)
        val displayInHours = ArrayUtils.max(dailyMillis) > 2 * HOUR_IN_MILLIS
        val dailyUsage = if (displayInHours) convertToHours(dailyMillis) else convertToMinutes(dailyMillis)
        val values = mutableListOf<Float>()
        val labels = mutableListOf<String>()
        val weekDayLabels = getWeekDayLabels()
        for (i in 0 until Math.min(dailyUsage.size, weekDayLabels.size)) {
            values.add(dailyUsage[i])
            labels.add(getLocalizedShortDayNameFromLabel(weekDayLabels[i]))
        }
        chart.setManualYAxisRange(0f, nextDivisibleBy4(Collections.max(values)))
        val yAxisFormat = if (displayInHours) chart.context.getString(R.string.usage_bar_chart_y_axis_label_hour)
        else chart.context.getString(R.string.usage_bar_chart_y_axis_label_minute)
        chart.setYAxisFormat(yAxisFormat)
        chart.setData(values, labels)
        chart.setTooltipListener(object : BarChartView.TooltipListener {
            override fun getTooltipText(context: Context, barIndex: Int, value: Float, label: String): String {
                val localizedLabel = getLocalizedFullDayNameFromLabel(weekDayLabels[barIndex])
                return if (displayInHours) context.getString(R.string.usage_bar_chart_tooltip_hours, localizedLabel, value)
                else context.getString(R.string.usage_bar_chart_tooltip_minutes, localizedLabel, value)
            }

            override fun getAccessibilityText(context: Context, barIndex: Int, barCount: Int, value: Float, label: String): String {
                val localizedLabel = getLocalizedFullDayNameFromLabel(weekDayLabels[barIndex])
                return if (displayInHours) context.getString(R.string.usage_weekly_hours_bar_chart_accessibility_description, localizedLabel, value, barIndex + 1, barCount)
                else context.getString(R.string.usage_weekly_minutes_bar_chart_accessibility_description, localizedLabel, value, barIndex + 1, barCount)
            }
        })
    }

    @JvmStatic
    fun groupIntoHourlyBucketsForDay(events: List<PackageUsageInfo.Entry>, targetDate: Long): LongArray {
        val interval = UsageUtils.getDayBounds(targetDate)
        val dayStartTimestamp = interval.startTime
        val dayEndTimestamp = interval.endTime
        val hourlyDurations = LongArray(24)
        for (event in events) {
            if (event.endTime < dayStartTimestamp || event.startTime > dayEndTimestamp) continue
            val clippedStart = Math.max(event.startTime, dayStartTimestamp)
            val clippedEnd = Math.min(event.endTime, dayEndTimestamp)
            distributeClippedEventAcrossHours(clippedStart, clippedEnd, dayStartTimestamp, hourlyDurations)
        }
        return hourlyDurations
    }

    private fun distributeClippedEventAcrossHours(startTime: Long, endTime: Long, dayStart: Long, hourlyDurations: LongArray) {
        var currentTime = startTime
        while (currentTime <= endTime) {
            var hourBucket = ((currentTime - dayStart) / HOUR_IN_MILLIS).toInt()
            hourBucket = Math.min(hourBucket, 23)
            val hourEnd = dayStart + (hourBucket + 1) * HOUR_IN_MILLIS - 1
            val segmentEnd = Math.min(endTime, hourEnd)
            val segmentDuration = segmentEnd - currentTime + 1
            hourlyDurations[hourBucket] += segmentDuration
            currentTime = hourEnd + 1
        }
    }

    @JvmStatic
    fun groupIntoDailyBucketsForWeek(events: List<PackageUsageInfo.Entry>, targetDate: Long): LongArray {
        val interval = UsageUtils.getWeekBounds(targetDate)
        val numDays = 7
        val msBuckets = LongArray(numDays)
        val periodStart = interval.startTime
        val periodEnd = interval.endTime
        for (e in events) {
            val start = Math.max(e.startTime, periodStart)
            val end = Math.min(e.endTime, periodEnd)
            if (start > end) continue
            var current = start
            while (current <= end) {
                var bucketIndex = ((current - periodStart) / DAY_IN_MILLIS).toInt()
                bucketIndex = Math.min(bucketIndex, numDays - 1)
                val bucketEnd = periodStart + (bucketIndex + 1) * DAY_IN_MILLIS - 1
                val segmentEnd = Math.min(end, bucketEnd)
                val segmentDur = segmentEnd - current + 1
                msBuckets[bucketIndex] += segmentDur
                current = bucketEnd + 1
            }
        }
        return msBuckets
    }

    @JvmStatic
    fun getLocalizedFullDayNameFromLabel(label: String): String {
        return when (label) {
            "Mon" -> DayOfWeek.MONDAY.getDisplayName(TextStyle.FULL, Locale.getDefault())
            "Tue" -> DayOfWeek.TUESDAY.getDisplayName(TextStyle.FULL, Locale.getDefault())
            "Wed" -> DayOfWeek.WEDNESDAY.getDisplayName(TextStyle.FULL, Locale.getDefault())
            "Thu" -> DayOfWeek.THURSDAY.getDisplayName(TextStyle.FULL, Locale.getDefault())
            "Fri" -> DayOfWeek.FRIDAY.getDisplayName(TextStyle.FULL, Locale.getDefault())
            "Sat" -> DayOfWeek.SATURDAY.getDisplayName(TextStyle.FULL, Locale.getDefault())
            "Sun" -> DayOfWeek.SUNDAY.getDisplayName(TextStyle.FULL, Locale.getDefault())
            else -> throw IllegalArgumentException("Invalid label $label")
        }
    }

    @JvmStatic
    fun getLocalizedShortDayNameFromLabel(label: String): String {
        return when (label) {
            "Mon" -> DayOfWeek.MONDAY.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            "Tue" -> DayOfWeek.TUESDAY.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            "Wed" -> DayOfWeek.WEDNESDAY.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            "Thu" -> DayOfWeek.THURSDAY.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            "Fri" -> DayOfWeek.FRIDAY.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            "Sat" -> DayOfWeek.SATURDAY.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            "Sun" -> DayOfWeek.SUNDAY.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            else -> throw IllegalArgumentException("Invalid label $label")
        }
    }

    @JvmStatic
    fun getHourLabels(): Array<String> {
        val is24Hour = android.text.format.DateFormat.is24HourFormat(ContextUtils.getContext())
        return if (is24Hour) {
            arrayOf("00:00", "01:00", "02:00", "03:00", "04:00", "05:00", "06:00", "07:00", "08:00", "09:00", "10:00", "11:00", "12:00", "13:00", "14:00", "15:00", "16:00", "17:00", "18:00", "19:00", "20:00", "21:00", "22:00", "23:00")
        } else {
            arrayOf("12 AM", "1 AM", "2 AM", "3 AM", "4 AM", "5 AM", "6 AM", "7 AM", "8 AM", "9 AM", "10 AM", "11 AM", "12 PM", "1 PM", "2 PM", "3 PM", "4 PM", "5 PM", "6 PM", "7 PM", "8 PM", "9 PM", "10 PM", "11 PM")
        }
    }

    @JvmStatic
    fun getWeekDayLabels(): Array<String> {
        val firstDayOfWeek = Calendar.getInstance(TimeZone.getDefault()).firstDayOfWeek
        return when (firstDayOfWeek) {
            Calendar.MONDAY -> arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
            Calendar.TUESDAY -> arrayOf("Tue", "Wed", "Thu", "Fri", "Sat", "Sun", "Mon")
            Calendar.WEDNESDAY -> arrayOf("Wed", "Thu", "Fri", "Sat", "Sun", "Mon", "Tue")
            Calendar.THURSDAY -> arrayOf("Thu", "Fri", "Sat", "Sun", "Mon", "Tue", "Wed")
            Calendar.FRIDAY -> arrayOf("Fri", "Sat", "Sun", "Mon", "Tue", "Wed", "Thu")
            Calendar.SATURDAY -> arrayOf("Sat", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri")
            else -> arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        }
    }

    @JvmStatic
    fun nextDivisibleBy4(maxValue: Float): Float {
        val ceil = Math.ceil(maxValue.toDouble()).toFloat()
        val multiple = (ceil.toInt() + 3) / 4
        var upperBound = multiple * 4f
        if (upperBound <= maxValue) upperBound += 4f
        return upperBound
    }

    @JvmStatic
    fun convertToMinutes(durationsMillis: LongArray): FloatArray {
        return FloatArray(durationsMillis.size) { i -> durationsMillis[i] / 60000f }
    }

    @JvmStatic
    fun convertToHours(durationsMillis: LongArray): FloatArray {
        return FloatArray(durationsMillis.size) { i -> durationsMillis[i] / 3600000f }
    }
}
