// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.crypto.auth

import io.github.muntashirakon.AppManager.crypto.RandomChar
import io.github.muntashirakon.AppManager.utils.AppPref

object AuthManager {
    const val AUTH_KEY_SIZE = 24

    @JvmStatic
    fun getKey(): String {
        return AppPref.getString(AppPref.PrefKey.PREF_AUTHORIZATION_KEY_STR) ?: ""
    }

    @JvmStatic
    fun setKey(key: String) {
        AppPref.set(AppPref.PrefKey.PREF_AUTHORIZATION_KEY_STR, key)
    }

    @JvmStatic
    fun generateKey(): String {
        val authKey = CharArray(AUTH_KEY_SIZE)
        RandomChar().nextChars(authKey)
        return String(authKey)
    }
}
