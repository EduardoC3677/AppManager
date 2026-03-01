// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles.struct

import io.github.muntashirakon.AppManager.filters.FilterItem
import io.github.muntashirakon.AppManager.filters.FilteringUtils
import io.github.muntashirakon.AppManager.history.JsonDeserializer
import io.github.muntashirakon.AppManager.profiles.ProfileLogger
import io.github.muntashirakon.AppManager.progress.ProgressHandler
import io.github.muntashirakon.AppManager.users.Users
import org.json.JSONException
import org.json.JSONObject

class AppsFilterProfile : AppsBaseProfile {
    val filterItem: FilterItem

    constructor(profileId: String, profileName: String) : super(profileId, profileName, PROFILE_TYPE_APPS_FILTER) {
        filterItem = FilterItem()
    }

    constructor(profileId: String, profileName: String, profile: AppsFilterProfile) : super(profileId, profileName, profile.type) {
        filterItem = try {
            FilterItem.DESERIALIZER.deserialize(profile.filterItem.serializeToJson())
        } catch (e: JSONException) {
            throw IllegalArgumentException("Invalid profile", e)
        }
    }

    override fun apply(state: String, logger: ProfileLogger?, progressHandler: ProgressHandler?): ProfileApplierResult {
        val finalUsers = users ?: Users.getUsersIds()
        val filterableAppInfoList = FilteringUtils.loadFilterableAppInfo(finalUsers)
        val filteredList = filterItem.getFilteredList(filterableAppInfoList)
        if (filteredList.isEmpty()) return ProfileApplierResult.EMPTY_RESULT

        val packages = ArrayList<String>(filteredList.size)
        val assocUsers = ArrayList<Int>(filteredList.size)
        logger?.println("====> Filtered packages: ${filteredList.size}")
        val sb = StringBuilder()
        for (info in filteredList) {
            packages.add(info.info.packageName)
            assocUsers.add(info.info.userId)
            sb.append("(").append(info.info.packageName).append(", ")
                .append(info.info.userId).append("), ")
        }
        logger?.println(sb.toString())
        return apply(packages, assocUsers, state, logger, progressHandler)
    }

    @Throws(JSONException::class)
    override fun serializeToJson(): JSONObject {
        return super.serializeToJson().put("filters", filterItem.serializeToJson())
    }

    @Throws(JSONException::class)
    protected constructor(profileObj: JSONObject) : super(profileObj) {
        filterItem = FilterItem.DESERIALIZER.deserialize(profileObj.getJSONObject("filters"))
    }

    companion object {
        @JvmField
        val DESERIALIZER = JsonDeserializer.Creator { profileObj -> AppsFilterProfile(profileObj) }
    }
}
