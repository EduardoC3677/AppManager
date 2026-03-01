// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import androidx.core.os.ParcelCompat
import io.github.muntashirakon.AppManager.history.IJsonSerializer
import io.github.muntashirakon.AppManager.history.JsonDeserializer
import io.github.muntashirakon.AppManager.profiles.ProfileApplierActivity.ProfileApplierInfo
import io.github.muntashirakon.AppManager.profiles.struct.BaseProfile
import io.github.muntashirakon.AppManager.self.filecache.FileCache
import io.github.muntashirakon.AppManager.utils.JSONUtils
import io.github.muntashirakon.io.Path
import io.github.muntashirakon.io.Paths
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.charset.StandardCharsets

class ProfileQueueItem : Parcelable, IJsonSerializer {
    val profileId: String
    @BaseProfile.ProfileType
    val profileType: Int
    val profileName: String
    val state: String?
    val tempProfilePath: Path?

    private constructor(profile: BaseProfile, state: String?) {
        profileId = profile.profileId
        profileType = profile.type
        profileName = profile.name
        this.state = state
        tempProfilePath = null
    }

    protected constructor(`in`: Parcel) {
        profileId = `in`.readString()!!
        profileType = `in`.readInt()
        profileName = `in`.readString()!!
        state = `in`.readString()
        val uri = ParcelCompat.readParcelable(`in`, Uri::class.java.classLoader, Uri::class.java)
        tempProfilePath = uri?.let { Paths.get(it) }
    }

    @Throws(JSONException::class)
    protected constructor(jsonObject: JSONObject) {
        profileId = jsonObject.getString("profile_id")
        profileType = jsonObject.optInt("profile_type", BaseProfile.PROFILE_TYPE_APPS)
        profileName = jsonObject.getString("profile_name")
        state = JSONUtils.getString(jsonObject, "state")
        val profile = jsonObject.optJSONObject("profile")
        var profilePath: java.io.File? = null
        if (profile != null) {
            try {
                ByteArrayInputStream(profile.toString().toByteArray(StandardCharsets.UTF_8)).use { `is` ->
                    profilePath = FileCache.getGlobalFileCache().getCachedFile(`is`, ProfileManager.PROFILE_EXT)
                }
            } catch (e: IOException) {
                throw JSONException(e.message).apply { initCause(e) }
            }
        }
        tempProfilePath = profilePath?.let { Paths.get(it) }
    }

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(profileId)
        dest.writeInt(profileType)
        dest.writeString(profileName)
        dest.writeString(state)
        dest.writeParcelable(tempProfilePath?.uri, flags)
    }

    @Throws(JSONException::class)
    override fun serializeToJson(): JSONObject {
        val jsonObject = JSONObject()
        jsonObject.put("profile_id", profileId)
        jsonObject.put("profile_type", profileType)
        jsonObject.put("profile_name", profileName)
        jsonObject.put("state", state)
        try {
            val profile = BaseProfile.fromPath(ProfileManager.findProfilePathById(profileId))
            jsonObject.put("profile", profile.serializeToJson())
        } catch (e: IOException) {
            throw JSONException(e.message).apply { initCause(e) }
        }
        return jsonObject
    }

    companion object {
        @JvmStatic
        fun fromProfiledApplierInfo(info: ProfileApplierInfo): ProfileQueueItem {
            return ProfileQueueItem(info.profile, info.state)
        }

        @JvmField
        val DESERIALIZER = JsonDeserializer.Creator { jsonObject -> ProfileQueueItem(jsonObject) }

        @JvmField
        val CREATOR: Parcelable.Creator<ProfileQueueItem> = object : Parcelable.Creator<ProfileQueueItem> {
            override fun createFromParcel(`in`: Parcel): ProfileQueueItem = ProfileQueueItem(`in`)
            override fun newArray(size: Int): Array<ProfileQueueItem?> = arrayOfNulls(size)
        }
    }
}
