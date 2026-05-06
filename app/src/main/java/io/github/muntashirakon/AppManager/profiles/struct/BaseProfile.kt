// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles.struct

import androidx.annotation.CallSuper
import androidx.annotation.IntDef
import io.github.muntashirakon.AppManager.history.IJsonSerializer
import io.github.muntashirakon.AppManager.history.JsonDeserializer
import io.github.muntashirakon.AppManager.profiles.ProfileLogger
import io.github.muntashirakon.AppManager.profiles.ProfileManager
import io.github.muntashirakon.AppManager.profiles.ProfileManager.PROFILE_EXT
import io.github.muntashirakon.AppManager.progress.ProgressHandler
import io.github.muntashirakon.AppManager.utils.JSONUtils
import io.github.muntashirakon.io.Path
import io.github.muntashirakon.io.Paths
import io.github.muntashirakon.util.LocalizedString
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.io.OutputStream

abstract class BaseProfile : LocalizedString, IJsonSerializer {
    @NonNull
    val profileId: String
    @NonNull
    val name: String
    @ProfileType
    val type: Int
    @ProfileState
    var state: String = STATE_OFF

    @IntDef(PROFILE_TYPE_APPS, PROFILE_TYPE_APPS_FILTER)
    @Retention(AnnotationRetention.SOURCE)
    annotation class ProfileType

    @Retention(AnnotationRetention.SOURCE)
    annotation class ProfileState

    protected constructor(profileId: String, profileName: String, profileType: Int) {
        this.profileId = profileId
        this.name = profileName
        this.type = profileType
    }

    @Throws(JSONException::class)
    protected constructor(profileObj: JSONObject) {
        name = profileObj.getString("name")
        profileId = JSONUtils.getString(profileObj, "id", ProfileManager.getProfileIdCompat(name))
        type = profileObj.getInt("type")
        state = JSONUtils.getString(profileObj, "state", STATE_ON)
    }

    abstract fun apply(state: String, logger: ProfileLogger?, progressHandler: ProgressHandler?): ProfileApplierResult

    @Throws(IOException::class)
    fun write(out: OutputStream) {
        try {
            out.write(serializeToJson().toString().toByteArray())
        } catch (e: JSONException) {
            throw IOException(e)
        }
    }

    @CallSuper
    @Throws(JSONException::class)
    override fun serializeToJson(): JSONObject {
        return JSONObject().apply {
            put("id", profileId)
            put("name", name)
            put("type", type)
            put("state", state)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BaseProfile) return false
        return profileId == other.profileId
    }

    override fun hashCode(): Int = profileId.hashCode()

    companion object {
        const val PROFILE_TYPE_APPS = 0
        const val PROFILE_TYPE_APPS_FILTER = 1

        const val STATE_ON = "on"\nconst val STATE_OFF = "off"\n@JvmStatic
        @Throws(IOException::class, JSONException::class)
        fun fromPath(profilePath: Path?): BaseProfile {
            if (profilePath == null) throw IOException("Empty profile path")
            val profileStr = profilePath.contentAsString
            return DESERIALIZER.deserialize(JSONObject(profileStr))
        }

        @JvmStatic
        fun newProfile(newProfileName: String, type: Int, source: BaseProfile?): BaseProfile {
            var profileId = ProfileManager.getProfileIdCompat(newProfileName)
            val profilesDir = ProfileManager.getProfilesDir()
            var profilePath = Paths.build(profilesDir, profileId + PROFILE_EXT)
            var profileName = newProfileName
            var i = 1
            while (profilePath != null && profilePath.exists()) {
                profileName = "$newProfileName ($i)"\nprofileId = ProfileManager.getProfileIdCompat(profileName)
                profilePath = Paths.build(profilesDir, profileId + PROFILE_EXT)
                i++
            }
            return when (type) {
                PROFILE_TYPE_APPS -> {
                    if (source != null) AppsProfile(profileId, profileName, source as AppsProfile)
                    else AppsProfile(profileId, profileName)
                }
                PROFILE_TYPE_APPS_FILTER -> {
                    if (source != null) AppsFilterProfile(profileId, profileName, source as AppsFilterProfile)
                    else AppsFilterProfile(profileId, profileName)
                }
                else -> throw IllegalArgumentException("Invalid type: $type")
            }
        }

        @JvmField
        val DESERIALIZER = JsonDeserializer.Creator { jsonObject ->
            when (val type = jsonObject.getInt("type")) {
                PROFILE_TYPE_APPS -> AppsProfile.DESERIALIZER.deserialize(jsonObject)
                PROFILE_TYPE_APPS_FILTER -> AppsFilterProfile.DESERIALIZER.deserialize(jsonObject)
                else -> throw JSONException("Invalid type: $type")
            }
        }
    }
}
