// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner.vt

import org.json.JSONException
import org.json.JSONObject
import java.util.*

class VtFileReport @Throws(JSONException::class) constructor(jsonObject: JSONObject) {
    val results: ArrayList<VtAvEngineResult>
    val stats: VtAvEngineStats
    val scanId: String
    val scanDate: Long
    val permalink: String

    init {
        val data = jsonObject.getJSONObject("data")
        assert(data.getString("type") == "file")
        scanId = data.getString("id")
        permalink = VirusTotal.getPermalink(scanId)
        val attrs = data.getJSONObject("attributes")
        scanDate = attrs.optLong("last_analysis_date") * 1000
        stats = VtAvEngineStats(attrs.getJSONObject("last_analysis_stats"))
        val jsonResults = attrs.getJSONObject("last_analysis_results")
        val avEnginesIt = jsonResults.keys()
        results = ArrayList()
        while (avEnginesIt.hasNext()) {
            results.add(VtAvEngineResult(jsonResults.getJSONObject(avEnginesIt.next())))
        }
    }

    fun hasReport(): Boolean = scanDate != 0L

    val total: Int
        get() = stats.total

    val positives: Int
        get() = stats.detected

    override fun toString(): String {
        return "VtFileReport{results=$results, stats=$stats, scanId='$scanId', scanDate=$scanDate, permalink='$permalink'}"
    }
}
