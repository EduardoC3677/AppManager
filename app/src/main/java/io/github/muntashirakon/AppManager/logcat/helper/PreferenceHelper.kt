// SPDX-License-Identifier: WTFPL AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat.helper

import android.content.Context
import androidx.preference.PreferenceManager
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.settings.Prefs

/**
 * Copyright 2012 Nolan Lawson
 */
object PreferenceHelper {
    private const val WIDGET_EXISTS_PREFIX = "widget_"

    @JvmStatic
    fun getWidgetExistsPreference(context: Context, appWidgetId: Int): Boolean {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        val widgetExists = WIDGET_EXISTS_PREFIX + appWidgetId
        return sharedPrefs.getBoolean(widgetExists, false)
    }

    @JvmStatic
    fun setWidgetExistsPreference(context: Context, appWidgetIds: IntArray) {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = sharedPrefs.edit()
        for (appWidgetId in appWidgetIds) {
            val widgetExists = WIDGET_EXISTS_PREFIX + appWidgetId
            editor.putBoolean(widgetExists, true)
        }
        editor.apply()
    }

    @JvmStatic
    fun getBuffers(): List<Int> {
        return getBuffers(Prefs.LogViewer.getBuffers())
    }

    @JvmStatic
    fun getBuffers(@LogcatHelper.LogBufferId buffers: Int): List<Int> {
        val separatedBuffers = mutableListOf<Int>()
        if (buffers and LogcatHelper.LOG_ID_MAIN != 0) separatedBuffers.add(LogcatHelper.LOG_ID_MAIN)
        if (buffers and LogcatHelper.LOG_ID_RADIO != 0) separatedBuffers.add(LogcatHelper.LOG_ID_RADIO)
        if (buffers and LogcatHelper.LOG_ID_EVENTS != 0) separatedBuffers.add(LogcatHelper.LOG_ID_EVENTS)
        if (buffers and LogcatHelper.LOG_ID_SYSTEM != 0) separatedBuffers.add(LogcatHelper.LOG_ID_SYSTEM)
        if (buffers and LogcatHelper.LOG_ID_CRASH != 0) separatedBuffers.add(LogcatHelper.LOG_ID_CRASH)
        return separatedBuffers
    }

    @JvmStatic
    fun getIncludeDeviceInfoPreference(context: Context): Boolean {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        return sharedPrefs.getBoolean(context.getString(R.string.pref_include_device_info), true)
    }

    @JvmStatic
    fun setIncludeDeviceInfoPreference(context: Context, value: Boolean) {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        sharedPrefs.edit().putBoolean(context.getString(R.string.pref_include_device_info), value).apply()
    }

    @JvmStatic
    fun getIncludeDmesgPreference(context: Context): Boolean {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        return sharedPrefs.getBoolean(context.getString(R.string.pref_include_dmesg), true)
    }

    @JvmStatic
    fun setIncludeDmesgPreference(context: Context, value: Boolean) {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        sharedPrefs.edit().putBoolean(context.getString(R.string.pref_include_dmesg), value).apply()
    }
}
