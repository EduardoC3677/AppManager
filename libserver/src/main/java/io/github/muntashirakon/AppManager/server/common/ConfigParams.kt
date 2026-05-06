// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server.common

class ConfigParams {
    @JvmField
    var isDebug: Boolean = false

    @JvmField
    var appName: String? = null

    @JvmField
    var path: String? = null

    @JvmField
    var isRunInBackground: Boolean = false

    @JvmField
    var token: String? = null

    @JvmField
    var uid: String? = null

    fun put(key: String, value: String) {
        when (key) {
            PARAM_DEBUG -> isDebug = "1" == value
            PARAM_APP -> appName = value
            PARAM_PATH -> path = value
            PARAM_RUN_IN_BACKGROUND -> isRunInBackground = "1" == value
            PARAM_TOKEN -> token = value
            PARAM_UID -> uid = value
        }
    }

    override fun toString(): String {
        return "ConfigParam{" +
                "mIsDebug=$isDebug" +
                ", mPath='$path'" +
                ", mRunInBackground=$isRunInBackground" +
                ", mToken='$token'" +
                ", mUid='$uid'" +
                '}'
    }

    companion object {
        const val PARAM_DEBUG: String = "debug"
        const val PARAM_APP: String = "app"
        const val PARAM_PATH: String = "path"
        const val PARAM_RUN_IN_BACKGROUND: String = "bgrun"
        const val PARAM_TOKEN: String = "token"
        const val PARAM_UID: String = "uid"
    }
}
