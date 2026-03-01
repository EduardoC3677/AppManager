// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk

import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Parcelable
import androidx.annotation.AnyThread
import io.github.muntashirakon.AppManager.history.IJsonSerializer
import io.github.muntashirakon.AppManager.history.JsonDeserializer
import io.github.muntashirakon.AppManager.utils.JSONUtils
import org.json.JSONException

abstract class ApkSource : Parcelable, IJsonSerializer {
    @AnyThread
    @Throws(ApkFile.ApkFileException::class)
    abstract fun resolve(): ApkFile

    @AnyThread
    abstract fun toCachedSource(): ApkSource

    companion object {
        @JvmStatic
        fun getApkSource(uri: Uri, mimeType: String?): ApkSource {
            return UriApkSource(uri, mimeType)
        }

        @JvmStatic
        fun getCachedApkSource(uri: Uri, mimeType: String?): ApkSource {
            return CachedApkSource(uri, mimeType)
        }

        @JvmStatic
        fun getApkSource(applicationInfo: ApplicationInfo): ApkSource {
            return ApplicationInfoApkSource(applicationInfo)
        }

        @JvmField
        val DESERIALIZER = JsonDeserializer.Creator<ApkSource> { jsonObject ->
            val tag = JSONUtils.getString(jsonObject, "tag")
            when (tag) {
                ApplicationInfoApkSource.TAG -> ApplicationInfoApkSource.DESERIALIZER.deserialize(jsonObject)
                CachedApkSource.TAG -> CachedApkSource.DESERIALIZER.deserialize(jsonObject)
                UriApkSource.TAG -> UriApkSource.DESERIALIZER.deserialize(jsonObject)
                else -> throw JSONException("Invalid tag: $tag")
            }
        }
    }
}
