// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner.vt

import org.json.JSONException
import org.json.JSONObject

class VtAvEngineStats @Throws(JSONException::class) constructor(stats: JSONObject) {
    val confirmedTimeout: Int = stats.getInt("confirmed-timeout")
    val failure: Int = stats.getInt("failure")
    val harmless: Int = stats.getInt("harmless")
    val malicious: Int = stats.getInt("malicious")
    val suspicious: Int = stats.getInt("suspicious")
    val timeout: Int = stats.getInt("timeout")
    val unsupported: Int = stats.getInt("type-unsupported")
    val undetected: Int = stats.getInt("undetected")

    val total: Int = harmless + malicious + suspicious + undetected
    val detected: Int = malicious
}
