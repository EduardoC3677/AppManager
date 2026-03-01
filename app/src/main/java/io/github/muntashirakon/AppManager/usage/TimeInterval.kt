// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.usage

import androidx.core.util.Pair

class TimeInterval : Pair<Long, Long> {
    val intervalType: Int

    constructor(intervalType: Int, begin: Long, end: Long) : super(begin, end) {
        this.intervalType = intervalType
    }

    constructor(begin: Long, end: Long) : super(begin, end) {
        intervalType = IntervalType.INTERVAL_DAILY
    }

    val startTime: Long
        get() = first!!

    val endTime: Long
        get() = second!!

    val duration: Long
        get() = endTime - startTime + 1

    override fun toString(): String {
        return "TimeInterval{startTime=$first, endTime=$second}"
    }
}
