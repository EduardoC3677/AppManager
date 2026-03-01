// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer

import android.annotation.UserIdInt
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import androidx.core.os.ParcelCompat
import io.github.muntashirakon.AppManager.BuildConfig
import io.github.muntashirakon.AppManager.compat.UserHandleHidden
import io.github.muntashirakon.AppManager.history.IJsonSerializer
import io.github.muntashirakon.AppManager.history.JsonDeserializer
import io.github.muntashirakon.AppManager.settings.Prefs
import io.github.muntashirakon.AppManager.utils.JSONUtils
import org.json.JSONException
import org.json.JSONObject

class InstallerOptions : Parcelable, IJsonSerializer {
    @UserIdInt
    var userId: Int = UserHandleHidden.myUserId()
    var installLocation: Int = Prefs.Installer.getInstallLocation()
    var installerName: String? = Prefs.Installer.getInstallerPackageName()
    var originatingPackage: String? = null
    var originatingUri: Uri? = null
    var isSetOriginatingPackage: Boolean = Prefs.Installer.isSetOriginatingPackage()
    var packageSource: Int = Prefs.Installer.getPackageSource()
    var installScenario: Int = 0
    var requestUpdateOwnership: Boolean = Prefs.Installer.requestUpdateOwnership()
    var isDisableApkVerification: Boolean = Prefs.Installer.isDisableApkVerification()
    var isSignApkFiles: Boolean = Prefs.Installer.canSignApk()
    var isForceDexOpt: Boolean = Prefs.Installer.forceDexOpt()
    var isBlockTrackers: Boolean = Prefs.Installer.blockTrackers()

    constructor() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            installScenario = if (Prefs.Installer.installInBackground())
                PackageManager.INSTALL_SCENARIO_BULK
            else PackageManager.INSTALL_SCENARIO_FAST
        }
    }

    protected constructor(`in`: Parcel) {
        userId = `in`.readInt()
        installLocation = `in`.readInt()
        installerName = `in`.readString()
        originatingPackage = `in`.readString()
        originatingUri = ParcelCompat.readParcelable(`in`, Uri::class.java.classLoader, Uri::class.java)
        isSetOriginatingPackage = ParcelCompat.readBoolean(`in`)
        packageSource = `in`.readInt()
        installScenario = `in`.readInt()
        requestUpdateOwnership = ParcelCompat.readBoolean(`in`)
        isDisableApkVerification = ParcelCompat.readBoolean(`in`)
        isSignApkFiles = ParcelCompat.readBoolean(`in`)
        isForceDexOpt = ParcelCompat.readBoolean(`in`)
        isBlockTrackers = ParcelCompat.readBoolean(`in`)
    }

    fun copy(options: InstallerOptions) {
        userId = options.userId
        installLocation = options.installLocation
        installerName = options.installerName
        originatingPackage = options.originatingPackage
        originatingUri = options.originatingUri
        isSetOriginatingPackage = options.isSetOriginatingPackage
        packageSource = options.packageSource
        installScenario = options.installScenario
        requestUpdateOwnership = options.requestUpdateOwnership
        isDisableApkVerification = options.isDisableApkVerification
        isSignApkFiles = options.isSignApkFiles
        isForceDexOpt = options.isForceDexOpt
        isBlockTrackers = options.isBlockTrackers
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(userId)
        dest.writeInt(installLocation)
        dest.writeString(installerName)
        dest.writeString(originatingPackage)
        dest.writeParcelable(originatingUri, flags)
        ParcelCompat.writeBoolean(dest, isSetOriginatingPackage)
        dest.writeInt(packageSource)
        dest.writeInt(installScenario)
        ParcelCompat.writeBoolean(dest, requestUpdateOwnership)
        ParcelCompat.writeBoolean(dest, isDisableApkVerification)
        ParcelCompat.writeBoolean(dest, isSignApkFiles)
        ParcelCompat.writeBoolean(dest, isForceDexOpt)
        ParcelCompat.writeBoolean(dest, isBlockTrackers)
    }

    @Throws(JSONException::class)
    protected constructor(jsonObject: JSONObject) {
        userId = jsonObject.getInt("user_id")
        installLocation = jsonObject.getInt("install_location")
        installerName = JSONUtils.optString(jsonObject, "installer_name", null)
        originatingPackage = JSONUtils.optString(jsonObject, "originating_package")
        val originatingUriStr = JSONUtils.optString(jsonObject, "originating_uri", null)
        originatingUri = originatingUriStr?.let { Uri.parse(it) }
        isSetOriginatingPackage = jsonObject.optBoolean("set_originating_package", Prefs.Installer.isSetOriginatingPackage())
        packageSource = jsonObject.getInt("package_source")
        installScenario = jsonObject.getInt("install_scenario")
        requestUpdateOwnership = jsonObject.getBoolean("request_update_ownership")
        isDisableApkVerification = jsonObject.getBoolean("disable_apk_verification")
        isSignApkFiles = jsonObject.getBoolean("sign_apk_files")
        isForceDexOpt = jsonObject.getBoolean("force_dex_opt")
        isBlockTrackers = jsonObject.getBoolean("block_trackers")
    }

    @Throws(JSONException::class)
    override fun serializeToJson(): JSONObject {
        return JSONObject().apply {
            put("user_id", userId)
            put("install_location", installLocation)
            put("installer_name", installerName)
            put("originating_package", originatingPackage)
            put("originating_uri", originatingUri?.toString())
            put("set_originating_package", isSetOriginatingPackage)
            put("package_source", packageSource)
            put("install_scenario", installScenario)
            put("request_update_ownership", requestUpdateOwnership)
            put("disable_apk_verification", isDisableApkVerification)
            put("sign_apk_files", isSignApkFiles)
            put("force_dex_opt", isForceDexOpt)
            put("block_trackers", isBlockTrackers)
        }
    }

    override fun describeContents(): Int = 0

    fun getInstallerNameNonNull(): String {
        return if (!installerName.isNullOrEmpty()) installerName!! else BuildConfig.APPLICATION_ID
    }

    fun requestUpdateOwnership(): Boolean = requestUpdateOwnership

    companion object {
        @JvmStatic
        fun getDefault(): InstallerOptions = InstallerOptions()

        @JvmField
        val DESERIALIZER = JsonDeserializer.Creator { InstallerOptions(it) }

        @JvmField
        val CREATOR: Parcelable.Creator<InstallerOptions> = object : Parcelable.Creator<InstallerOptions> {
            override fun createFromParcel(`in`: Parcel): InstallerOptions = InstallerOptions(`in`)
            override fun newArray(size: Int): Array<InstallerOptions?> = arrayOfNulls(size)
        }
    }
}
