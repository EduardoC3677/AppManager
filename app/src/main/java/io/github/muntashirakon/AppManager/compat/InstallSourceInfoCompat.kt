// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat

import android.content.pm.InstallSourceInfo
import android.content.pm.SigningInfo
import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import androidx.annotation.RequiresApi
import androidx.core.os.ParcelCompat

class InstallSourceInfoCompat : Parcelable {
    private val mInitiatingPackageName: String?

    @get:RequiresApi(Build.VERSION_CODES.P)
    private var mInitiatingPackageSigningInfo: SigningInfo? = null

    private val mOriginatingPackageName: String?
    private val mInstallingPackageName: String?

    private var mInitiatingPackageLabel: CharSequence? = null
    private var mOriginatingPackageLabel: CharSequence? = null
    private var mInstallingPackageLabel: CharSequence? = null

    @RequiresApi(Build.VERSION_CODES.R)
    constructor(installSourceInfo: InstallSourceInfo?) {
        if (installSourceInfo != null) {
            mInitiatingPackageName = installSourceInfo.initiatingPackageName
            mInitiatingPackageSigningInfo = installSourceInfo.initiatingPackageSigningInfo
            mOriginatingPackageName = installSourceInfo.originatingPackageName
            mInstallingPackageName = installSourceInfo.installingPackageName
        } else {
            mInitiatingPackageName = null
            mOriginatingPackageName = null
            mInstallingPackageName = null
        }
    }

    constructor(installingPackageName: String?) {
        mInitiatingPackageName = null
        mOriginatingPackageName = null
        mInstallingPackageName = installingPackageName
    }

    override fun describeContents(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && mInitiatingPackageSigningInfo != null) {
            return mInitiatingPackageSigningInfo!!.describeContents()
        }
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(mInitiatingPackageName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            dest.writeParcelable(mInitiatingPackageSigningInfo, flags)
        }
        dest.writeString(mOriginatingPackageName)
        dest.writeString(mInstallingPackageName)
    }

    private constructor(source: Parcel) {
        mInitiatingPackageName = source.readString()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            mInitiatingPackageSigningInfo = ParcelCompat.readParcelable(source, SigningInfo::class.java.classLoader, SigningInfo::class.java)
        }
        mOriginatingPackageName = source.readString()
        mInstallingPackageName = source.readString()
    }

    fun setInitiatingPackageLabel(label: CharSequence?) {
        mInitiatingPackageLabel = label
    }

    fun getInitiatingPackageLabel(): CharSequence? = mInitiatingPackageLabel

    fun setOriginatingPackageLabel(label: CharSequence?) {
        mOriginatingPackageLabel = label
    }

    fun getOriginatingPackageLabel(): CharSequence? = mOriginatingPackageLabel

    fun setInstallingPackageLabel(label: CharSequence?) {
        mInstallingPackageLabel = label
    }

    fun getInstallingPackageLabel(): CharSequence? = mInstallingPackageLabel

    fun getInitiatingPackageName(): String? = mInitiatingPackageName

    @RequiresApi(Build.VERSION_CODES.P)
    fun getInitiatingPackageSigningInfo(): SigningInfo? = mInitiatingPackageSigningInfo

    fun getOriginatingPackageName(): String? = mOriginatingPackageName

    fun getInstallingPackageName(): String? = mInstallingPackageName

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<InstallSourceInfoCompat> = object : Parcelable.Creator<InstallSourceInfoCompat> {
            override fun createFromParcel(source: Parcel): InstallSourceInfoCompat = InstallSourceInfoCompat(source)
            override fun newArray(size: Int): Array<InstallSourceInfoCompat?> = arrayOfNulls(size)
        }
    }
}
