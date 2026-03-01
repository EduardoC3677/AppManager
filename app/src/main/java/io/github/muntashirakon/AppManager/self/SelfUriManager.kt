// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.self

import android.net.Uri
import android.os.UserHandleHidden
import android.text.TextUtils
import io.github.muntashirakon.AppManager.types.UserPackagePair
import io.github.muntashirakon.AppManager.utils.PackageUtils

object SelfUriManager {
    const val APP_MANAGER_SCHEME = "app-manager"
    const val SETTINGS_HOST = "settings"
    const val DETAILS_HOST = "details"

    @JvmStatic
    fun getUserPackagePairFromUri(detailsUri: Uri?): UserPackagePair? {
        // Required format app-manager://details?id=<pkg>&user=<user_id>
        if (detailsUri == null || APP_MANAGER_SCHEME != detailsUri.scheme || DETAILS_HOST != detailsUri.host) {
            return null
        }
        val pkg = detailsUri.getQueryParameter("id")
        val userIdStr = detailsUri.getQueryParameter("user")
        if (pkg != null && PackageUtils.validateName(pkg.trim { it <= ' ' })) {
            val userId = if (userIdStr != null && TextUtils.isDigitsOnly(userIdStr)) {
                userIdStr.toInt()
            } else {
                UserHandleHidden.myUserId()
            }
            return UserPackagePair(pkg, userId)
        }
        return null
    }
}
