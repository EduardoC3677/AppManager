// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner.vt

import android.text.TextUtils
import org.json.JSONException
import org.json.JSONObject

class VtError(val httpErrorCode: Int, rawJson: String?) {
    val code: String?
    val message: String?

    init {
        if (TextUtils.isEmpty(rawJson)) {
            code = null
            message = null
        } else {
            var c: String? = null
            var m: String? = null
            try {
                val errorObject = JSONObject(rawJson!!).optJSONObject("error")
                if (errorObject != null) {
                    c = errorObject.getString("code")
                    m = errorObject.getString("message")
                }
            } catch (e: JSONException) {
                e.printStackTrace()
            }
            code = c
            message = m
        }
    }

    override fun toString(): String {
        return "VtError{httpErrorCode=$httpErrorCode, code='$code', message='$message'}"
    }
}
