// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details

import android.annotation.UserIdInt
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageItemInfo
import android.os.Parcel
import android.os.Parcelable
import io.github.muntashirakon.AppManager.BuildConfig
import io.github.muntashirakon.AppManager.compat.UserHandleHidden
import io.github.muntashirakon.AppManager.shortcut.ShortcutInfo

class PackageItemShortcutInfo<T> : ShortcutInfo where T : PackageItemInfo, T : Parcelable {
    private val mPackageItemInfo: T
    private val mClazz: Class<T>
    @UserIdInt
    private val mUserId: Int
    private val mLaunchViaAssist: Boolean

    constructor(packageItemInfo: T, clazz: Class<T>, @UserIdInt userId: Int) : this(packageItemInfo, clazz, userId, false)

    constructor(packageItemInfo: T, clazz: Class<T>, @UserIdInt userId: Int, launchViaAssist: Boolean) : super() {
        mPackageItemInfo = packageItemInfo
        mClazz = clazz
        mUserId = userId
        mLaunchViaAssist = if (packageItemInfo is ActivityInfo) launchViaAssist else false
    }

    @Suppress("UNCHECKED_CAST")
    constructor(`in`: Parcel) : super(`in`) {
        mClazz = `in`.readSerializable() as Class<T>
        mPackageItemInfo = `in`.readParcelable(mClazz.classLoader)!!
        mUserId = `in`.readInt()
        mLaunchViaAssist = `in`.readInt() != 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        super.writeToParcel(dest, flags)
        dest.writeSerializable(mClazz)
        dest.writeParcelable(mPackageItemInfo, flags)
        dest.writeInt(mUserId)
        dest.writeInt(if (mLaunchViaAssist) 1 else 0)
    }

    override fun toShortcutIntent(context: Context): Intent {
        return if (requireProxy()) getProxyIntent(context) else getIntent()
    }

    private fun getIntent(): Intent {
        return Intent().apply {
            setClassName(mPackageItemInfo.packageName, mPackageItemInfo.name)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
    }

    private fun getProxyIntent(context: Context): Intent {
        return ActivityLauncherShortcutActivity.getShortcutIntent(context, mPackageItemInfo.packageName,
            mPackageItemInfo.name, mUserId, mLaunchViaAssist)
    }

    private fun requireProxy(): Boolean {
        return BuildConfig.APPLICATION_ID != mPackageItemInfo.packageName || mUserId != UserHandleHidden.myUserId()
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<PackageItemShortcutInfo<*>> = object : Parcelable.Creator<PackageItemShortcutInfo<*>> {
            override fun createFromParcel(source: Parcel): PackageItemShortcutInfo<*> = PackageItemShortcutInfo<PackageItemInfo>(source)
            override fun newArray(size: Int): Array<PackageItemShortcutInfo<*>?> = arrayOfNulls(size)
        }
    }
}
