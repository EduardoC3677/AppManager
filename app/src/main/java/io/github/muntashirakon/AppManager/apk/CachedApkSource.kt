// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk

import android.content.ContentResolver
import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import androidx.core.os.ParcelCompat
import io.github.muntashirakon.AppManager.fm.FmProvider
import io.github.muntashirakon.AppManager.history.JsonDeserializer
import io.github.muntashirakon.AppManager.self.filecache.FileCache
import io.github.muntashirakon.AppManager.utils.FileUtils
import io.github.muntashirakon.AppManager.utils.JSONUtils
import io.github.muntashirakon.io.Paths
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException

class CachedApkSource : ApkSource {
    private val mUri: Uri
    private val mMimeType: String?
    private var mApkFileKey: Int = 0
    private var mCachedFile: File? = null

    internal constructor(uri: Uri, mimeType: String?) {
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
        // May need to cache the APK if it's not from our own content provider
        if (mCachedFile != null && mCachedFile!!.exists()) {
            mApkFileKey = ApkFile.createInstance(Uri.fromFile(mCachedFile), mMimeType)
        } else if (ContentResolver.SCHEME_FILE == mUri.scheme) {
            mApkFileKey = ApkFile.createInstance(mUri, mMimeType)
        } else if (ContentResolver.SCHEME_CONTENT == mUri.scheme &&
            FmProvider.AUTHORITY == mUri.authority
        ) {
            mApkFileKey = ApkFile.createInstance(mUri, mMimeType)
        } else {
            // Need caching
            try {
                mCachedFile = FileCache.getGlobalFileCache().getCachedFile(Paths.get(mUri))
                mApkFileKey = ApkFile.createInstance(Uri.fromFile(mCachedFile), mMimeType)
            } catch (e: IOException) {
                throw ApkFile.ApkFileException(e)
            } catch (e: SecurityException) {
                throw ApkFile.ApkFileException(e)
            }
        }
        return ApkFile.getInstance(mApkFileKey)!!
    }

    override fun toCachedSource(): ApkSource {
        val uri = if (mCachedFile != null && mCachedFile!!.exists()) {
            Uri.fromFile(mCachedFile)
        } else mUri
        return CachedApkSource(uri, mMimeType)
    }

    fun cleanup() {
        FileUtils.deleteSilently(mCachedFile)
        mCachedFile = null
    }

    protected constructor(`in`: Parcel) {
        mUri = ParcelCompat.readParcelable(`in`, Uri::class.java.classLoader, Uri::class.java)!!
        mMimeType = `in`.readString()
        mApkFileKey = `in`.readInt()
        val file = `in`.readString()
        if (file != null) {
            mCachedFile = File(file)
        }
    }

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeParcelable(mUri, flags)
        dest.writeString(mMimeType)
        dest.writeInt(mApkFileKey)
        val file = mCachedFile?.absolutePath
        dest.writeString(file)
    }

    @Throws(JSONException::class)
    protected constructor(jsonObject: JSONObject) {
        mUri = Uri.parse(jsonObject.getString("uri"))
        mMimeType = jsonObject.getString("mime_type")
        mApkFileKey = jsonObject.getInt("apk_file_key")
        val cachedFile = JSONUtils.optString(jsonObject, "cached_file", null)
        mCachedFile = if (cachedFile != null) File(cachedFile) else null
    }

    @Throws(JSONException::class)
    override fun serializeToJson(): JSONObject {
        val jsonObject = JSONObject()
        jsonObject.put("tag", TAG)
        jsonObject.put("uri", mUri.toString())
        jsonObject.put("mime_type", mMimeType)
        jsonObject.put("apk_file_key", mApkFileKey)
        jsonObject.put("cached_file", mCachedFile?.absolutePath)
        return jsonObject
    }

    companion object {
        @JvmField
        val TAG: String = CachedApkSource::class.java.simpleName

        @JvmField
        val DESERIALIZER = JsonDeserializer.Creator { jsonObject -> CachedApkSource(jsonObject) }

        @JvmField
        val CREATOR: Parcelable.Creator<CachedApkSource> = object : Parcelable.Creator<CachedApkSource> {
            override fun createFromParcel(source: Parcel): CachedApkSource = CachedApkSource(source)
            override fun newArray(size: Int): Array<CachedApkSource?> = arrayOfNulls(size)
        }
    }
}
