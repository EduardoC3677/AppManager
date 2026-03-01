// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.users

import android.annotation.UserIdInt
import android.content.Context
import android.os.*
import io.github.muntashirakon.AppManager.compat.ManifestCompat
import io.github.muntashirakon.AppManager.ipc.LocalServices
import io.github.muntashirakon.AppManager.ipc.ProxyBinder
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.self.SelfPermissions
import io.github.muntashirakon.AppManager.settings.Ops
import io.github.muntashirakon.AppManager.settings.Prefs
import io.github.muntashirakon.AppManager.utils.ArrayUtils
import io.github.muntashirakon.AppManager.utils.ExUtils

object Users {
    const val TAG = "Users"

    private val sUserInfoList = mutableListOf<UserInfo>()
    private var sUnprivilegedMode = false

    @JvmStatic
    fun getAllUsers(): List<UserInfo> {
        if (sUserInfoList.isEmpty() || sUnprivilegedMode) {
            val uid = getSelfOrRemoteUid()
            val userManager = IUserManager.Stub.asInterface(ProxyBinder.getService(Context.USER_SERVICE))
            if (SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.MANAGE_USERS)
                || SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.CREATE_USERS)) {
                if (sUnprivilegedMode) {
                    sUnprivilegedMode = false
                    sUserInfoList.clear()
                }
                var userInfoList: List<android.content.pm.UserInfo>? = null
                try {
                    userInfoList = userManager.getUsers(true)
                } catch (e: Exception) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        userInfoList = ExUtils.exceptionAsNull { userManager.getUsers(true, true, true) }
                    }
                }
                userInfoList?.forEach { userInfo ->
                    try {
                        if (uid == Ops.SHELL_UID && userManager.hasUserRestriction(UserManager.DISALLOW_DEBUGGING_FEATURES, userInfo.id)) {
                            Log.w(TAG, "Shell cannot access user %s as debugging is disallowed.", userInfo.id)
                            return@forEach
                        }
                    } catch (e: RemoteException) {
                        ExUtils.rethrowFromSystemServer(e)
                    }
                    sUserInfoList.add(UserInfo(userInfo))
                }
            }
            if (sUserInfoList.isEmpty()) {
                sUnprivilegedMode = true
                Log.d(TAG, "Missing required permission: MANAGE_USERS or CREATE_USERS (7+). Falling back to unprivileged mode.")
                val userInfoList = userManager.getProfiles(UserHandleHidden.getUserId(uid), false)
                userInfoList.forEach { sUserInfoList.add(UserInfo(it)) }
            }
        }
        return sUserInfoList
    }

    @JvmStatic
    @UserIdInt
    fun getAllUserIds(): IntArray {
        getAllUsers()
        return ArrayUtils.convertToIntArray(sUserInfoList.map { it.id })
    }

    @JvmStatic
    fun getUsers(): List<UserInfo> {
        getAllUsers()
        val selectedUserIds = Prefs.Misc.getSelectedUsers()
        return sUserInfoList.filter { selectedUserIds == null || ArrayUtils.contains(selectedUserIds, it.id) }
    }

    @JvmStatic
    @UserIdInt
    fun getUsersIds(): IntArray {
        getAllUsers()
        val selectedUserIds = Prefs.Misc.getSelectedUsers()
        return ArrayUtils.convertToIntArray(sUserInfoList.filter { selectedUserIds == null || ArrayUtils.contains(selectedUserIds, it.id) }.map { it.id })
    }

    @JvmStatic
    fun getUserHandle(@UserIdInt userId: Int): UserHandle? {
        getAllUsers()
        return sUserInfoList.find { it.id == userId }?.userHandle
    }

    @JvmStatic
    fun getSelfOrRemoteUid(): Int {
        return try {
            LocalServices.getAmService().uid
        } catch (e: RemoteException) {
            Process.myUid()
        }
    }
}
