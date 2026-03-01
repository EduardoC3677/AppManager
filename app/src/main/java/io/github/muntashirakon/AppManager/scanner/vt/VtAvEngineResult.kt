// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner.vt

import androidx.annotation.IntDef
import io.github.muntashirakon.AppManager.utils.JSONUtils
import org.json.JSONException
import org.json.JSONObject

class VtAvEngineResult @Throws(JSONException::class) constructor(avResult: JSONObject) {
    @IntDef(CAT_UNSUPPORTED, CAT_TIMEOUT, CAT_FAILURE, CAT_UNDETECTED, CAT_HARMLESS, CAT_SUSPICIOUS, CAT_MALICIOUS)
    @Retention(AnnotationRetention.SOURCE)
    annotation class Category

    private val internalCategory: String = avResult.getString("category")
    @Category
    val category: Int = getCategory(internalCategory)
    val engineName: String = avResult.getString("engine_name")
    val engineUpdate: String? = JSONUtils.optString(avResult, "engine_update", null)
    val engineVersion: String? = JSONUtils.optString(avResult, "engine_version", null)
    val method: String = avResult.getString("method")
    val result: String? = JSONUtils.optString(avResult, "result", null)

    override fun toString(): String {
        return "VtFileReportScanItem{category='$internalCategory', engineName='$engineName', engineUpdate='$engineUpdate', engineVersion='$engineVersion', method='$method', result='$result'}"
    }

    companion object {
        const val CAT_UNSUPPORTED = 0
        const val CAT_TIMEOUT = 1
        const val CAT_FAILURE = 2
        const val CAT_UNDETECTED = 3
        const val CAT_HARMLESS = 4
        const val CAT_SUSPICIOUS = 5
        const val CAT_MALICIOUS = 6

        @Category
        private fun getCategory(internalCategory: String): Int {
            return when (internalCategory) {
                "confirmed-timeout", "timeout" -> CAT_TIMEOUT
                "harmless" -> CAT_HARMLESS
                "undetected" -> CAT_UNDETECTED
                "suspicious" -> CAT_SUSPICIOUS
                "malicious" -> CAT_MALICIOUS
                "type-unsupported" -> CAT_UNSUPPORTED
                else -> CAT_FAILURE
            }
        }
    }
}
