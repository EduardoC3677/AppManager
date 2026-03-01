// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import io.github.muntashirakon.AppManager.profiles.struct.BaseProfile
import io.github.muntashirakon.AppManager.profiles.struct.ProfileApplierResult
import io.github.muntashirakon.AppManager.progress.ProgressHandler
import io.github.muntashirakon.AppManager.utils.ContextUtils
import io.github.muntashirakon.AppManager.utils.ThreadUtils
import io.github.muntashirakon.io.Path
import io.github.muntashirakon.io.Paths
import org.json.JSONException
import java.io.IOException
import java.util.*

class ProfileManager @Throws(IOException::class) constructor(profileId: String, profilePath: Path?) {
    private val mProfile: BaseProfile
    private var mLogger: ProfileLogger? = null
    private var mRequiresRestart: Boolean = false

    init {
        try {
            mLogger = ProfileLogger(profileId)
        } catch (e: IOException) {
            e.printStackTrace()
        }
        try {
            val realProfilePath = profilePath ?: findProfilePathById(profileId)
            mProfile = BaseProfile.fromPath(realProfilePath)
        } catch (e: IOException) {
            mLogger?.println(null, e)
            throw e
        } catch (e: JSONException) {
            mLogger?.println(null, e)
            throw IOException(e)
        }
    }

    fun requiresRestart(): Boolean = mRequiresRestart

    @SuppressLint("SwitchIntDef")
    fun applyProfile(state: String?, progressHandler: ProgressHandler?) {
        val finalState = state ?: mProfile.state
        log("====> Started execution with state $finalState")
        val result = mProfile.apply(finalState, mLogger, progressHandler)
        mRequiresRestart = result.requiresRestart()
        log("====> Execution completed.")
    }

    fun conclude() {
        mLogger?.close()
    }

    private fun log(message: String?) {
        mLogger?.println(message)
    }

    companion object {
        const val TAG = "ProfileManager"
        const val PROFILE_EXT = ".am.json"

        @JvmStatic
        fun getProfileIntent(context: Context, @BaseProfile.ProfileType type: Int, profileId: String): Intent {
            return when (type) {
                BaseProfile.PROFILE_TYPE_APPS -> AppsProfileActivity.getProfileIntent(context, profileId)
                BaseProfile.PROFILE_TYPE_APPS_FILTER -> AppsFilterProfileActivity.getProfileIntent(context, profileId)
                else -> throw UnsupportedOperationException("Invalid type: $type")
            }
        }

        @JvmStatic
        fun getNewProfileIntent(context: Context, @BaseProfile.ProfileType type: Int, profileName: String): Intent {
            return when (type) {
                BaseProfile.PROFILE_TYPE_APPS -> AppsProfileActivity.getNewProfileIntent(context, profileName)
                BaseProfile.PROFILE_TYPE_APPS_FILTER -> AppsFilterProfileActivity.getNewProfileIntent(context, profileName)
                else -> throw UnsupportedOperationException("Invalid type: $type")
            }
        }

        @JvmStatic
        fun getCloneProfileIntent(context: Context, @BaseProfile.ProfileType type: Int, oldProfileId: String, newProfileName: String): Intent {
            return when (type) {
                BaseProfile.PROFILE_TYPE_APPS -> AppsProfileActivity.getCloneProfileIntent(context, oldProfileId, newProfileName)
                BaseProfile.PROFILE_TYPE_APPS_FILTER -> AppsFilterProfileActivity.getCloneProfileIntent(context, oldProfileId, newProfileName)
                else -> throw UnsupportedOperationException("Invalid type: $type")
            }
        }

        @JvmStatic
        fun getProfilesDir(): Path {
            val context = ContextUtils.getContext()
            return Paths.build(context.filesDir, "profiles")!!
        }

        @JvmStatic
        fun findProfilePathById(profileId: String): Path? {
            return Paths.build(getProfilesDir(), profileId + PROFILE_EXT)
        }

        @JvmStatic
        @Throws(IOException::class)
        fun requireProfilePathById(profileId: String): Path {
            val profilesDir = getProfilesDir()
            if (!profilesDir.exists()) {
                profilesDir.mkdirs()
            }
            return getProfilesDir().findOrCreateFile(profileId + PROFILE_EXT, null)
        }

        @JvmStatic
        fun deleteProfile(profileId: String): Boolean {
            val profilePath = findProfilePathById(profileId)
            return profilePath == null || !profilePath.exists() || profilePath.delete()
        }

        @JvmStatic
        fun getProfileName(filename: String): String {
            var index = filename.indexOf(PROFILE_EXT)
            if (index == -1) {
                index = filename.indexOf(".json")
            }
            return if (index != -1) filename.substring(0, index) else filename
        }

        @JvmStatic
        fun getProfileNames(): ArrayList<String> {
            val profilesPath = getProfilesDir()
            val profilesFiles = profilesPath.listFileNames { _, name -> name.endsWith(PROFILE_EXT) }
            val profileNames = ArrayList<String>(profilesFiles.size)
            for (profile in profilesFiles) {
                profileNames.add(getProfileName(profile))
            }
            return profileNames
        }

        @JvmStatic
        @Throws(IOException::class, JSONException::class)
        fun getProfileSummaries(context: Context): HashMap<BaseProfile, CharSequence> {
            val profilesPath = getProfilesDir()
            val profilePaths = profilesPath.listFiles { _, name -> name.endsWith(PROFILE_EXT) }
            val profiles = HashMap<BaseProfile, CharSequence>(profilePaths.size)
            for (profilePath in profilePaths) {
                if (ThreadUtils.isInterrupted()) {
                    return profiles
                }
                val profile = BaseProfile.fromPath(profilePath)
                profiles[profile] = profile.toLocalizedString(context)
            }
            return profiles
        }

        @Suppress("UNCHECKED_CAST")
        @JvmStatic
        @Throws(IOException::class, JSONException::class)
        fun <T> getProfiles(type: Int): List<T> {
            val profilesPath = getProfilesDir()
            val profilePaths = profilesPath.listFiles { _, name -> name.endsWith(PROFILE_EXT) }
            val profiles = ArrayList<T>(profilePaths.size)
            for (profilePath in profilePaths) {
                val profile = BaseProfile.fromPath(profilePath)
                if (profile.type == type) {
                    profiles.add(profile as T)
                }
            }
            return profiles
        }

        @JvmStatic
        @Throws(IOException::class, JSONException::class)
        fun getProfiles(): List<BaseProfile> {
            val profilesPath = getProfilesDir()
            val profilePaths = profilesPath.listFiles { _, name -> name.endsWith(PROFILE_EXT) }
            val profiles = ArrayList<BaseProfile>(profilePaths.size)
            for (profilePath in profilePaths) {
                profiles.add(BaseProfile.fromPath(profilePath))
            }
            return profiles
        }

        @JvmStatic
        fun getProfileIdCompat(profileName: String): String {
            val profileId = Paths.sanitizeFilename(profileName, "_", Paths.SANITIZE_FLAG_SPACE
                    or Paths.SANITIZE_FLAG_UNIX_ILLEGAL_CHARS or Paths.SANITIZE_FLAG_UNIX_RESERVED
                    or Paths.SANITIZE_FLAG_FAT_ILLEGAL_CHARS)
            return profileId ?: UUID.randomUUID().toString()
        }
    }
}
