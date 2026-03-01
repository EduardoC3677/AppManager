// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles.struct

import aosp.libcore.util.EmptyArray
import io.github.muntashirakon.AppManager.history.JsonDeserializer
import io.github.muntashirakon.AppManager.profiles.ProfileLogger
import io.github.muntashirakon.AppManager.progress.ProgressHandler
import io.github.muntashirakon.AppManager.users.Users
import io.github.muntashirakon.AppManager.utils.ArrayUtils
import io.github.muntashirakon.AppManager.utils.JSONUtils
import org.json.JSONException
import org.json.JSONObject

class AppsProfile : AppsBaseProfile {
    var packages: Array<String> = EmptyArray.STRING

    constructor(profileId: String, profileName: String) : super(profileId, profileName, PROFILE_TYPE_APPS)

    constructor(profileId: String, profileName: String, profile: AppsProfile) : super(profileId, profileName, profile) {
        packages = profile.packages.clone()
    }

    override fun apply(state: String, logger: ProfileLogger?, progressHandler: ProgressHandler?): ProfileApplierResult {
        if (packages.isEmpty()) return ProfileApplierResult.EMPTY_RESULT
        val finalUsers = users ?: Users.getUsersIds()
        val size = packages.size * finalUsers.size
        val packageList = ArrayList<String>(size)
        val assocUsers = ArrayList<Int>(size)
        for (packageName in packages) {
            for (user in finalUsers) {
                packageList.add(packageName)
                assocUsers.add(user)
            }
        }
        return apply(packageList, assocUsers, state, logger, progressHandler)
    }

    fun appendPackages(packageList: Array<String>) {
        val uniquePackages = mutableListOf<String>()
        for (newPackage in packageList) {
            if (!ArrayUtils.contains(packages, newPackage)) {
                uniquePackages.add(newPackage)
            }
        }
        packages = ArrayUtils.concatElements(String::class.java, packages, uniquePackages.toTypedArray())
    }

    @Throws(JSONException::class)
    override fun serializeToJson(): JSONObject {
        return super.serializeToJson().put("packages", JSONUtils.getJSONArray(packages))
    }

    @Throws(JSONException::class)
    protected constructor(profileObj: JSONObject) : super(profileObj) {
        packages = JSONUtils.getArray(String::class.java, profileObj.getJSONArray("packages"))
    }

    companion object {
        @JvmField
        val DESERIALIZER = JsonDeserializer.Creator { profileObj -> AppsProfile(profileObj) }
    }
}
