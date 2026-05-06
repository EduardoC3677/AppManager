// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk

import android.content.pm.ApplicationInfo
import android.os.Parcel
import android.os.Parcelable
import androidx.core.os.ParcelCompat
import io.github.muntashirakon.AppManager.history.JsonDeserializer
import io.github.muntashirakon.AppManager.utils.ContextUtils
import org.json.JSONException
import org.json.JSONObject
import java.io.File

class ApplicationInfoApkSource : ApkSource {
    private val mApplicationInfo: ApplicationInfo
    private var mApkFileKey: Int = 0

    internal constructor(applicationInfo: ApplicationInfo) {
        mApplicationInfo = applicationInfo
    }

    @Throws(ApkFile.ApkFileException::class)
    override fun resolve(): ApkFile {
        val apkFile = ApkFile.getInstance(mApkFileKey)
        if (apkFile != null && !apkFile.isClosed) {
            // Usable past instance
            return apkFile
        }
        mApkFileKey = ApkFile.createInstance(mApplicationInfo)
        return ApkFile.getInstance(mApkFileKey)!!
    }

    override fun toCachedSource(): ApkSource {
        return CachedApkSource(
            android.net.Uri.fromFile(File(mApplicationInfo.publicSourceDir)),
            "application/vnd.android.package-archive"\n)
    }

    protected constructor(`in`: Parcel) {
        mApplicationInfo = ParcelCompat.readParcelable(`in`, ApplicationInfo::class.java.classLoader, ApplicationInfo::class.java)!!
        mApkFileKey = `in`.readInt()
    }

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeParcelable(mApplicationInfo, flags)
        dest.writeInt(mApkFileKey)
    }

    @Throws(JSONException::class)
    protected constructor(jsonObject: JSONObject) {
        val pm = ContextUtils.getContext().packageManager
        val file = jsonObject.getString("file")
        val packageInfo = pm.getPackageArchiveInfo(file, 0)!!
        mApplicationInfo = packageInfo.applicationInfo!!
        mApplicationInfo.publicSourceDir = file
        mApplicationInfo.sourceDir = file
        mApkFileKey = jsonObject.getInt("apk_file_key")
    }

    @Throws(JSONException::class)
    override fun serializeToJson(): JSONObject {
        val jsonObject = JSONObject()
        jsonObject.put("tag", TAG)
        jsonObject.put("file", mApplicationInfo.publicSourceDir)
        jsonObject.put("apk_file_key", mApkFileKey)
        return jsonObject
    }

    companion object {
        @JvmField
        val TAG: String = ApplicationInfoApkSource::class.java.simpleName

        @JvmField
        val DESERIALIZER = JsonDeserializer.Creator { jsonObject -> ApplicationInfoApkSource(jsonObject) }

        @JvmField
        val CREATOR: Parcelable.Creator<ApplicationInfoApkSource> = object : Parcelable.Creator<ApplicationInfoApkSource> {
            override fun createFromParcel(source: Parcel): ApplicationInfoApkSource = ApplicationInfoApkSource(source)
            override fun newArray(size: Int): Array<ApplicationInfoApkSource?> = arrayOfNulls(size)
        }
    }
}
