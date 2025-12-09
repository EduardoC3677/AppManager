// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.dexopt

import android.os.Parcelable
import android.os.SystemProperties
import android.text.TextUtils
import kotlinx.parcelize.Parcelize
import org.json.JSONException
import org.json.JSONObject
import io.github.muntashirakon.AppManager.history.IJsonSerializer
import io.github.muntashirakon.AppManager.history.JsonDeserializer
import io.github.muntashirakon.AppManager.utils.JSONUtils

@Parcelize
data class DexOptOptions(
    var packages: Array<String>? = null,
    var compilerFiler: String? = null,
    var compileLayouts: Boolean = false,
    var clearProfileData: Boolean = false,
    var checkProfiles: Boolean = false,
    var bootComplete: Boolean = false,
    var forceCompilation: Boolean = false,
    var forceDexOpt: Boolean = false
) : Parcelable, IJsonSerializer {

    @Throws(JSONException::class)
    constructor(jsonObject: JSONObject) : this(
        packages = JSONUtils.getArray(String::class.java, jsonObject.optJSONArray("packages")),
        compilerFiler = jsonObject.getString("compiler_filter"),
        compileLayouts = jsonObject.getBoolean("compile_layouts"),
        clearProfileData = jsonObject.getBoolean("clear_profile_data"),
        checkProfiles = jsonObject.getBoolean("check_profiles"),
        bootComplete = jsonObject.getBoolean("boot_complete"),
        forceCompilation = jsonObject.getBoolean("force_compilation"),
        forceDexOpt = jsonObject.getBoolean("force_dex_opt")
    )

    @Throws(JSONException::class)
    override fun serializeToJson(): JSONObject {
        return JSONObject().apply {
            put("packages", JSONUtils.getJSONArray(packages))
            put("compiler_filer", compilerFiler)
            put("compile_layouts", compileLayouts)
            put("clear_profile_data", clearProfileData)
            put("check_profiles", checkProfiles)
            put("boot_complete", bootComplete)
            put("force_compilation", forceCompilation)
            put("force_dex_opt", forceDexOpt)
        }
    }

    // Override equals and hashCode because of Array property
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DexOptOptions

        if (packages != null) {
            if (other.packages == null) return false
            if (!packages.contentEquals(other.packages)) return false
        } else if (other.packages != null) return false
        if (compilerFiler != other.compilerFiler) return false
        if (compileLayouts != other.compileLayouts) return false
        if (clearProfileData != other.clearProfileData) return false
        if (checkProfiles != other.checkProfiles) return false
        if (bootComplete != other.bootComplete) return false
        if (forceCompilation != other.forceCompilation) return false
        if (forceDexOpt != other.forceDexOpt) return false

        return true
    }

    override fun hashCode(): Int {
        var result = packages?.contentHashCode() ?: 0
        result = 31 * result + (compilerFiler?.hashCode() ?: 0)
        result = 31 * result + compileLayouts.hashCode()
        result = 31 * result + clearProfileData.hashCode()
        result = 31 * result + checkProfiles.hashCode()
        result = 31 * result + bootComplete.hashCode()
        result = 31 * result + forceCompilation.hashCode()
        result = 31 * result + forceDexOpt.hashCode()
        return result
    }

    companion object {
        @JvmStatic
        fun getDefault(): DexOptOptions {
            return DexOptOptions().apply {
                compilerFiler = getDefaultCompilerFilterForInstallation()
                checkProfiles = SystemProperties.getBoolean("dalvik.vm.usejitprofiles", false)
                bootComplete = true
            }
        }

        @JvmField
        val DESERIALIZER = JsonDeserializer.Creator { jsonObject: JSONObject ->
            DexOptOptions(jsonObject)
        }

        @JvmStatic
        fun getDefaultCompilerFilterForInstallation(): String {
            val profile = SystemProperties.get("pm.dexopt.install")
            return if (TextUtils.isEmpty(profile)) "speed" else profile
        }

        @JvmStatic
        fun getDefaultCompilerFilter(): String {
            val profile = SystemProperties.get("dalvik.vm.dex2oat-filter")
            return if (TextUtils.isEmpty(profile)) "speed" else profile
        }
    }
}
