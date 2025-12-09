// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils

import android.content.Context
import android.net.Uri
import androidx.collection.ArrayMap

object SAFUtils {
    @JvmStatic
    fun getUrisWithDate(context: Context): ArrayMap<Uri, Long> {
        val permissionList = context.contentResolver.persistedUriPermissions
        val uris = ArrayMap<Uri, Long>(permissionList.size)
        for (permission in permissionList) {
            if (permission.isReadPermission && permission.isWritePermission) {
                // We only work with rw directories
                uris[permission.uri] = permission.persistedTime
            }
        }
        return uris
    }
}
