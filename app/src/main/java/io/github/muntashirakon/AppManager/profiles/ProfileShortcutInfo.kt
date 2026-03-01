// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles

import android.content.Context
import android.content.Intent
import android.os.Parcel
import android.os.Parcelable
import io.github.muntashirakon.AppManager.shortcut.ShortcutInfo

class ProfileShortcutInfo : ShortcutInfo {
    val profileId: String
    @ProfileApplierActivity.ShortcutType
    val shortcutType: String

    constructor(profileId: String, profileName: String,
                @ProfileApplierActivity.ShortcutType shortcutType: String,
                readableShortcutType: CharSequence?) : super() {
        this.profileId = profileId
        this.shortcutType = shortcutType
        name = "$profileName - ${readableShortcutType ?: shortcutType}"
    }

    protected constructor(`in`: Parcel) : super(`in`) {
        profileId = `in`.readString()!!
        shortcutType = `in`.readString()!!
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        super.writeToParcel(dest, flags)
        dest.writeString(profileId)
        dest.writeString(shortcutType)
    }

    override fun toShortcutIntent(context: Context): Intent {
        val intent = ProfileApplierActivity.getShortcutIntent(context, profileId, shortcutType, null)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        return intent
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<ProfileShortcutInfo> = object : Parcelable.Creator<ProfileShortcutInfo> {
            override fun createFromParcel(source: Parcel): ProfileShortcutInfo {
                return ProfileShortcutInfo(source)
            }

            override fun newArray(size: Int): Array<ProfileShortcutInfo?> {
                return arrayOfNulls(size)
            }
        }
    }
}
