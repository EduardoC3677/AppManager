// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.debloat

import android.content.Intent
import android.net.Uri
import com.google.gson.annotations.SerializedName
import io.github.muntashirakon.AppManager.utils.ArrayUtils

class SuggestionObject {
    @SerializedName("_id")
    @JvmField
    var suggestionId: String? = null

    @SerializedName("id")
    @JvmField
    var packageName: String? = null

    @SerializedName("label")
    var label: String? = null
        private set

    @SerializedName("reason")
    var reason: String? = null
        private set

    @SerializedName("source")
    private var mSource: String? = null

    @SerializedName("repo")
    var repo: String? = null
        private set

    var users: IntArray? = null
        private set

    fun isInFDroidMarket(): Boolean = mSource?.contains("f") == true

    fun getMarketLink(): Intent {
        val uri = if (isInFDroidMarket()) {
            Uri.parse("https://f-droid.org/packages/$packageName")
        } else {
            Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
        }
        return Intent(Intent.ACTION_VIEW, uri)
    }

    fun addUser(userId: Int) {
        if (users == null) {
            users = intArrayOf(userId)
        } else if (!ArrayUtils.contains(users, userId)) {
            users = ArrayUtils.appendInt(users, userId)
        }
    }
}
