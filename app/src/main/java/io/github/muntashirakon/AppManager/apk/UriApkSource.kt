// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import androidx.core.os.ParcelCompat
import io.github.muntashirakon.AppManager.history.JsonDeserializer
import org.json.JSONException
import org.json.JSONObject

class UriApkSource : ApkSource {
    private val mUri: Uri
    private val mMimeType: String?
    private var mApkFileKey: Int = 0

    constructor(uri: Uri, mimeType: String?) {
        mUri = uri
        mMimeType = mimeType
    }

    @Throws(ApkFile.ApkFileException::class)
    override fun resolve(): ApkFile {
        val apkFile = ApkFile.getInstance(mApkFileKey)
        if (apkFile != null && !apkFile.isClosed) {
            // Usable past instance
            return apkFile
        }
        mApkFileKey = ApkFile.createInstance(mUri, mMimeType)
        return ApkFile.getInstance(mApkFileKey)!!
    }

    override fun toCachedSource(): ApkSource {
        return CachedApkSource(mUri, mMimeType)
    }

    protected constructor(`in`: Parcel) {
        mUri = ParcelCompat.readParcelable(`in`, Uri::class.java.classLoader, Uri::class.java)!!
        mMimeType = `in`.readString()
        mApkFileKey = `in`.readInt()
    }

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeParcelable(mUri, flags)
        dest.writeString(mMimeType)
        dest.writeInt(mApkFileKey)
    }

    @Throws(JSONException::class)
    protected constructor(jsonObject: JSONObject) {
        mUri = Uri.parse(jsonObject.getString("uri"))
        mMimeType = jsonObject.getString("mime_type")
        mApkFileKey = jsonObject.getInt("apk_file_key")
    }

    @Throws(JSONException::class)
    override fun serializeToJson(): JSONObject {
        val jsonObject = JSONObject()
        jsonObject.put("tag", TAG)
        jsonObject.put("uri", mUri.toString())
        jsonObject.put("mime_type", mMimeType)
        jsonObject.put("apk_file_key", mApkFileKey)
        return jsonObject
    }

    companion object {
        @JvmField
        val TAG: String = UriApkSource::class.java.simpleName

        @JvmField
        val DESERIALIZER = JsonDeserializer.Creator { jsonObject -> UriApkSource(jsonObject) }

        @JvmField
        val CREATOR: Parcelable.Creator<UriApkSource> = object : Parcelable.Creator<UriApkSource> {
            override fun createFromParcel(source: Parcel): UriApkSource = UriApkSource(source)
            override fun newArray(size: Int): Array<UriApkSource?> = arrayOfNulls(size)
        }
    }
}
