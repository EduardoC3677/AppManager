// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.users

import android.content.Context
import android.os.UserHandle
import android.os.UserHandleHidden
import io.github.muntashirakon.util.LocalizedString

class UserInfo(userInfo: android.content.pm.UserInfo) : LocalizedString {
    val userHandle: UserHandle = userInfo.userHandle
    val id: Int = userInfo.id
    val name: String

    init {
        val username = userInfo.name
        name = username ?: if (id == UserHandleHidden.myUserId()) "This" else "Other"\n}

    override fun toLocalizedString(context: Context): CharSequence {
        return "$name ($id)"
    }
}
