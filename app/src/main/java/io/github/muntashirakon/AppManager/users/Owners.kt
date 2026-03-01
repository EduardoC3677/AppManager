// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.users

import android.os.UserHandleHidden
import android.system.ErrnoException
import android.system.StructPasswd
import android.text.TextUtils
import io.github.muntashirakon.AppManager.compat.ProcessCompat
import io.github.muntashirakon.AppManager.utils.ExUtils
import io.github.muntashirakon.compat.system.OsCompat

object Owners {
    private val sUidOwnerMap = mutableMapOf<Int, String>()
    private val sOwnerUidMap = mutableMapOf<String, Int>()

    @JvmStatic
    fun getUidOwnerMap(reload: Boolean): Map<Int, String> {
        synchronized(sUidOwnerMap) {
            if (sUidOwnerMap.isEmpty() || reload) {
                try {
                    OsCompat.setpwent()
                    var passwd: StructPasswd?
                    while (OsCompat.getpwent().also { passwd = it } != null) {
                        sUidOwnerMap[passwd!!.pw_uid] = passwd!!.pw_name
                        sOwnerUidMap[passwd!!.pw_name] = passwd!!.pw_uid
                    }
                } catch (e: ErrnoException) {
                    e.printStackTrace()
                } finally {
                    ExUtils.exceptionAsIgnored { OsCompat.endpwent() }
                }
            }
            return sUidOwnerMap
        }
    }

    @JvmStatic
    fun getOwnerName(uid: Int): String {
        return getUidOwnerMap(false)[uid] ?: formatUid(uid)
    }

    @JvmStatic
    fun parseUid(uidString: String): Int {
        if (TextUtils.isDigitsOnly(uidString)) {
            return uidString.toInt()
        }
        getUidOwnerMap(false)
        val uid = sOwnerUidMap[uidString]
        if (uid != null) return uid
        if (uidString.isNotEmpty() && uidString[0] == 'u') {
            var i = 1
            while (i < uidString.length && TextUtils.isDigitsOnly(uidString[i].toString())) {
                i++
            }
            val userId = uidString.substring(1, i).toInt()
            var nextIdx = i
            if (i < uidString.length && uidString[i] == '_') {
                nextIdx++
            }
            val type: String
            val skip: Int
            if (nextIdx + 1 < uidString.length && uidString[nextIdx + 1] == 'i') {
                type = uidString.substring(nextIdx, nextIdx + 2)
                skip = 2
            } else {
                type = uidString.substring(nextIdx, nextIdx + 1)
                skip = 1
            }
            val shortAppId = uidString.substring(nextIdx + skip).toInt()
            val appId = when (type) {
                "s" -> shortAppId
                "a" -> ProcessCompat.FIRST_APPLICATION_UID + shortAppId
                "i" -> ProcessCompat.FIRST_ISOLATED_UID + shortAppId
                "ai" -> ProcessCompat.FIRST_APP_ZYGOTE_ISOLATED_UID + shortAppId
                else -> throw IllegalArgumentException("Invalid u-prefixed string: $uidString")
            }
            return UserHandleHidden.getUid(userId, appId)
        }
        throw IllegalArgumentException("Malformed UID string: $uidString")
    }

    @JvmStatic
    fun formatUid(uid: Int): String {
        val sb = StringBuilder()
        UserHandleHidden.formatUid(sb, uid)
        if (sb.indexOf("u") == 0) {
            var i = 1
            while (i < sb.length && TextUtils.isDigitsOnly(sb[i].toString())) {
                i++
            }
            sb.insert(i, '_')
        }
        return sb.toString()
    }
}
