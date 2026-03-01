// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer

import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import androidx.core.os.ParcelCompat
import io.github.muntashirakon.AppManager.apk.ApkSource
import io.github.muntashirakon.AppManager.history.IJsonSerializer
import io.github.muntashirakon.AppManager.history.JsonDeserializer
import io.github.muntashirakon.AppManager.intercept.IntentCompat
import io.github.muntashirakon.AppManager.utils.ContextUtils
import io.github.muntashirakon.AppManager.utils.ExUtils
import io.github.muntashirakon.AppManager.utils.JSONUtils
import org.json.JSONException
import org.json.JSONObject
import java.util.*

class ApkQueueItem : Parcelable, IJsonSerializer {
    var packageName: String? = null
    var appLabel: String? = null
    val isInstallExisting: Boolean
    private var mOriginatingPackage: String? = null
    private var mOriginatingUri: Uri? = null
    var apkSource: ApkSource? = null
    private var mInstallerOptions: InstallerOptions? = null
    var selectedSplits: ArrayList<String>? = null

    private constructor(packageName: String, installExisting: Boolean) {
        this.packageName = packageName
        this.isInstallExisting = installExisting
    }

    private constructor(apkSource: ApkSource) {
        this.apkSource = apkSource
        this.isInstallExisting = false
    }

    protected constructor(`in`: Parcel) {
        packageName = `in`.readString()
        appLabel = `in`.readString()
        isInstallExisting = `in`.readByte().toInt() != 0
        mOriginatingPackage = `in`.readString()
        mOriginatingUri = ParcelCompat.readParcelable(`in`, Uri::class.java.classLoader, Uri::class.java)
        apkSource = ParcelCompat.readParcelable(`in`, ApkSource::class.java.classLoader, ApkSource::class.java)
        mInstallerOptions = ParcelCompat.readParcelable(`in`, InstallerOptions::class.java.classLoader, InstallerOptions::class.java)
        selectedSplits = ArrayList()
        `in`.readStringList(selectedSplits!!)
    }

    var installerOptions: InstallerOptions?
        get() = mInstallerOptions
        set(value) {
            if (value != null) {
                value.setOriginatingPackage(mOriginatingPackage)
                value.setOriginatingUri(mOriginatingUri)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && mOriginatingPackage != null && SupportedAppStores.isAppStoreSupported(mOriginatingPackage!!)) {
                    value.packageSource = PackageInstaller.PACKAGE_SOURCE_STORE
                }
            }
            mInstallerOptions = value
        }

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(packageName)
        dest.writeString(appLabel)
        dest.writeByte((if (isInstallExisting) 1 else 0).toByte())
        dest.writeString(mOriginatingPackage)
        dest.writeParcelable(mOriginatingUri, flags)
        dest.writeParcelable(apkSource, flags)
        dest.writeParcelable(mInstallerOptions, flags)
        dest.writeStringList(selectedSplits)
    }

    @Throws(JSONException::class)
    protected constructor(jsonObject: JSONObject) {
        packageName = JSONUtils.optString(jsonObject, "package_name", null)
        appLabel = JSONUtils.optString(jsonObject, "app_label", null)
        isInstallExisting = jsonObject.optBoolean("install_existing", false)
        mOriginatingPackage = JSONUtils.optString(jsonObject, "originating_package", null)
        val originatingUriStr = JSONUtils.optString(jsonObject, "originating_uri", null)
        mOriginatingUri = originatingUriStr?.let { Uri.parse(it) }
        val apkSourceObj = jsonObject.optJSONObject("apk_source")
        apkSource = apkSourceObj?.let { ApkSource.DESERIALIZER.deserialize(it) }
        val installerOptionsObj = jsonObject.optJSONObject("installer_options")
        mInstallerOptions = installerOptionsObj?.let { InstallerOptions.DESERIALIZER.deserialize(it) }
        selectedSplits = JSONUtils.getArray(jsonObject.optJSONArray("selected_splits"))
    }

    @Throws(JSONException::class)
    override fun serializeToJson(): JSONObject {
        return JSONObject().apply {
            put("package_name", packageName)
            put("app_label", appLabel)
            put("install_existing", isInstallExisting)
            put("originating_package", mOriginatingPackage)
            put("originating_uri", mOriginatingUri?.toString())
            put("apk_source", apkSource?.serializeToJson())
            put("installer_options", mInstallerOptions?.serializeToJson())
            put("selected_splits", JSONUtils.getJSONArray(selectedSplits))
        }
    }

    companion object {
        @JvmStatic
        fun fromIntent(intent: Intent, originatingPackage: String?): List<ApkQueueItem> {
            val apkQueueItems = mutableListOf<ApkQueueItem>()
            val uris = IntentCompat.getDataUris(intent) ?: return apkQueueItems
            val cr = ContextUtils.getContext().contentResolver
            val mimeType = intent.type
            val originatingUri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_ORIGINATING_URI, Uri::class.java)
            val takeFlags = intent.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            for (uri in uris) {
                val item = if ("package" == uri.scheme) {
                    ApkQueueItem(uri.schemeSpecificPart, true)
                } else {
                    ApkQueueItem(ApkSource.getCachedApkSource(uri, mimeType)).apply {
                        mOriginatingUri = originatingUri
                        mOriginatingPackage = originatingPackage
                        if (takeFlags > 0) {
                            ExUtils.exceptionAsIgnored { cr.takePersistableUriPermission(uri, takeFlags) }
                        }
                    }
                }
                apkQueueItems.add(item)
            }
            return apkQueueItems
        }

        @JvmStatic
        fun fromApkSource(apkSource: ApkSource): ApkQueueItem {
            return ApkQueueItem(apkSource.toCachedSource())
        }

        @JvmField
        val DESERIALIZER = JsonDeserializer.Creator { ApkQueueItem(it) }

        @JvmField
        val CREATOR: Parcelable.Creator<ApkQueueItem> = object : Parcelable.Creator<ApkQueueItem> {
            override fun createFromParcel(`in`: Parcel): ApkQueueItem = ApkQueueItem(`in`)
            override fun newArray(size: Int): Array<ApkQueueItem?> = arrayOfNulls(size)
        }
    }
}
