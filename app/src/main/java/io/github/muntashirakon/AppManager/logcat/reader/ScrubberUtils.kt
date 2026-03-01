// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat.reader

import java.util.regex.Pattern

/**
 * Copyright 2014 CyanogenMod Project
 */
object ScrubberUtils {
    private val EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9_]+(?:\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*(@|%40)(?!([a-zA-Z0-9]*\.[a-zA-Z0-9]*\.[a-zA-Z0-9]*))([a-zA-Z0-9]*\.[a-zA-Z0-9]*\.[a-zA-Z0-9]{2,}|[a-zA-Z0-9]*\.[a-zA-Z0-9]{2,})")
    private val IP_PATTERN = Pattern.compile("((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)")
    private val LOC_PATTERN = Pattern.compile("(\(?(-?\d{1,3}\.\d{4,})[+\-,] *(-?\d{1,3}\.\d{4,})\)?)")
    private val IMEI_MEID_IMSI_PATTERN = Pattern.compile("(?:IMEI|MEID|IMSI).*?([0-9]{15})")
    private val PHONE_NUMBER_PATTERN = Pattern.compile("((?:(?:\+|00)[1-4]\d{1,2}|\(?(?:\d{3}\)?[ -.]?\d{3}[ -.]\d{4}))")

    @JvmStatic
    fun scrubLine(line: String): String {
        var scrubbedLine = line
        scrubbedLine = EMAIL_PATTERN.matcher(scrubbedLine).replaceAll("EMAIL")
        scrubbedLine = IP_PATTERN.matcher(scrubbedLine).replaceAll("IP_ADDRESS")
        scrubbedLine = LOC_PATTERN.matcher(scrubbedLine).replaceAll("LAT_LONG")
        scrubbedLine = IMEI_MEID_IMSI_PATTERN.matcher(scrubbedLine).replaceAll("ID")
        scrubbedLine = PHONE_NUMBER_PATTERN.matcher(scrubbedLine).replaceAll("PHONE_NUMBER")
        return scrubbedLine
    }
}
