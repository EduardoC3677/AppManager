// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings

import androidx.preference.PreferenceDataStore
import io.github.muntashirakon.AppManager.utils.AppPref

class SettingsDataStore : PreferenceDataStore() {
    private val mAppPref: AppPref = AppPref.getInstance()

    override fun putString(key: String, value: String?) {
        mAppPref.setPref(key, value)
    }

    override fun putInt(key: String, value: Int) {
        mAppPref.setPref(key, value)
    }

    override fun putLong(key: String, value: Long) {
        mAppPref.setPref(key, value)
    }

    override fun putFloat(key: String, value: Float) {
        mAppPref.setPref(key, value)
    }

    override fun putBoolean(key: String, value: Boolean) {
        mAppPref.setPref(key, value)
    }

    override fun getString(key: String, defValue: String?): String? {
        return mAppPref[key] as String?
    }

    override fun getInt(key: String, defValue: Int): Int {
        return mAppPref[key] as Int
    }

    override fun getLong(key: String, defValue: Long): Long {
        return mAppPref[key] as Long
    }

    override fun getFloat(key: String, defValue: Float): Float {
        return mAppPref[key] as Float
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        return mAppPref[key] as Boolean
    }
}
