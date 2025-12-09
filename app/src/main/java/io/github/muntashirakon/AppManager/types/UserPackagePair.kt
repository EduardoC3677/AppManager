// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.types

import android.annotation.UserIdInt
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class UserPackagePair(
    val packageName: String,
    @UserIdInt val userId: Int
) : Parcelable {
    override fun toString(): String {
        return "($packageName, $userId)"
    }
}
