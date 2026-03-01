// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ssaid

interface SettingsState {
    fun getSettingLocked(name: String): Setting?

    fun insertSettingLocked(name: String, value: String?, tag: String?, makeDefault: Boolean, packageName: String): Boolean

    interface Setting {
        val value: String?
        fun isNull(): Boolean
    }

    companion object {
        const val SYSTEM_PACKAGE_NAME = "android"
        const val MAX_BYTES_PER_APP_PACKAGE_UNLIMITED = -1
        const val MAX_BYTES_PER_APP_PACKAGE_LIMITED = 20000
        const val SETTINGS_TYPE_GLOBAL = 0
        const val SETTINGS_TYPE_SYSTEM = 1
        const val SETTINGS_TYPE_SECURE = 2
        const val SETTINGS_TYPE_SSAID = 3
        const val SETTINGS_TYPE_CONFIG = 4
    }
}
