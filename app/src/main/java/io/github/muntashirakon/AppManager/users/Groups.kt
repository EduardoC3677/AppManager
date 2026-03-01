// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.users

import android.os.UserHandleHidden
import android.system.ErrnoException
import io.github.muntashirakon.AppManager.utils.ExUtils
import io.github.muntashirakon.compat.system.OsCompat
import io.github.muntashirakon.compat.system.StructGroup
import java.util.*

object Groups {
    private const val AID_USER_OFFSET = 100000
    private const val AID_APP_START = 10000
    private const val AID_APP_END = 19999
    private const val AID_CACHE_GID_START = 20000
    private const val AID_CACHE_GID_END = 29999
    private const val AID_EXT_GID_START = 30000
    private const val AID_EXT_GID_END = 39999
    private const val AID_EXT_CACHE_GID_START = 40000
    private const val AID_EXT_CACHE_GID_END = 49999
    private const val AID_SHARED_GID_START = 50000
    private const val AID_SHARED_GID_END = 59999
    private const val AID_ISOLATED_START = 99000

    private val sGidGroupMap = mutableMapOf<Int, String>()

    @JvmStatic
    fun getGidGroupMap(reload: Boolean): Map<Int, String> {
        synchronized(sGidGroupMap) {
            if (sGidGroupMap.isEmpty() || reload) {
                try {
                    OsCompat.setgrent()
                    var passwd: StructGroup?
                    while (OsCompat.getgrent().also { passwd = it } != null) {
                        sGidGroupMap[passwd!!.gr_id] = passwd!!.gr_name
                    }
                } catch (e: ErrnoException) {
                    e.printStackTrace()
                } finally {
                    ExUtils.exceptionAsIgnored { OsCompat.endgrent() }
                }
            }
            return sGidGroupMap
        }
    }

    @JvmStatic
    fun getGroupName(uid: Int): String {
        return getGidGroupMap(false)[uid] ?: formatGid(uid)
    }

    @JvmStatic
    fun formatGid(gid: Int): String {
        val appid = gid % AID_USER_OFFSET
        val userid = gid / AID_USER_OFFSET
        return when {
            appid >= AID_ISOLATED_START -> String.format(Locale.ROOT, "u%d_i%d", userid, appid - AID_ISOLATED_START)
            userid == 0 && appid in AID_SHARED_GID_START..AID_SHARED_GID_END -> String.format(Locale.ROOT, "all_a%d", appid - AID_SHARED_GID_START)
            appid in AID_EXT_CACHE_GID_START..AID_EXT_CACHE_GID_END -> String.format(Locale.ROOT, "u%d_a%d_ext_cache", userid, appid - AID_EXT_CACHE_GID_START)
            appid in AID_EXT_GID_START..AID_EXT_GID_END -> String.format(Locale.ROOT, "u%d_a%d_ext", userid, appid - AID_EXT_GID_START)
            appid in AID_CACHE_GID_START..AID_CACHE_GID_END -> String.format(Locale.ROOT, "u%d_a%d_cache", userid, appid - AID_CACHE_GID_START)
            appid < AID_APP_START -> gid.toString()
            else -> String.format(Locale.ROOT, "u%d_a%d", userid, appid - AID_APP_START)
        }
    }

    @JvmStatic
    fun getCacheAppGid(uid: Int): Int {
        val appId = uid % AID_USER_OFFSET
        val userId = uid / AID_USER_OFFSET
        return if (appId in AID_APP_START..AID_APP_END) {
            UserHandleHidden.getUid(userId, appId - AID_APP_START + AID_CACHE_GID_START)
        } else {
            -1
        }
    }
}
