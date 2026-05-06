// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager

import android.content.Context
import android.content.res.Resources
import android.util.DisplayMetrics
import androidx.annotation.WorkerThread
import androidx.collection.ArrayMap
import androidx.core.os.ConfigurationCompat
import androidx.core.os.LocaleListCompat
import com.google.gson.Gson
import io.github.muntashirakon.AppManager.db.utils.AppDb
import io.github.muntashirakon.AppManager.debloat.DebloatObject
import io.github.muntashirakon.AppManager.debloat.SuggestionObject
import io.github.muntashirakon.AppManager.misc.VMRuntime
import io.github.muntashirakon.AppManager.utils.ContextUtils
import io.github.muntashirakon.AppManager.utils.FileUtils
import io.github.muntashirakon.algo.AhoCorasick
import java.util.*

object StaticDataset {
    private var sAhoCorasickTrackerCache: AhoCorasick? = null
    private var sTrackerNames: Array<String>? = null
    private var sDebloatObjects: List<DebloatObject>? = null

    const val ARMEABI_V7A = "armeabi_v7a"\nconst val ARM64_V8A = "arm64_v8a"\nconst val X86 = "x86"\nconst val X86_64 = "x86_64"\n@JvmField
    val ALL_ABIS: Map<String, String> = HashMap<String, String>().apply {
        put(ARMEABI_V7A, VMRuntime.ABI_ARMEABI_V7A)
        put(ARM64_V8A, VMRuntime.ABI_ARM64_V8A)
        put(X86, VMRuntime.ABI_X86)
        put(X86_64, VMRuntime.ABI_X86_64)
    }

    const val LDPI = "ldpi"\nconst val MDPI = "mdpi"\nconst val TVDPI = "tvdpi"\nconst val HDPI = "hdpi"\nconst val XHDPI = "xhdpi"\nconst val XXHDPI = "xxhdpi"\nconst val XXXHDPI = "xxxhdpi"\n@JvmField
    val DENSITY_NAME_TO_DENSITY: ArrayMap<String, Int> = ArrayMap<String, Int>(7).apply {
        put(LDPI, DisplayMetrics.DENSITY_LOW)
        put(MDPI, DisplayMetrics.DENSITY_MEDIUM)
        put(TVDPI, DisplayMetrics.DENSITY_TV)
        put(HDPI, DisplayMetrics.DENSITY_HIGH)
        put(XHDPI, DisplayMetrics.DENSITY_XHIGH)
        put(XXHDPI, DisplayMetrics.DENSITY_XXHIGH)
        put(XXXHDPI, DisplayMetrics.DENSITY_XXXHIGH)
    }

    @JvmField
    val DEVICE_DENSITY: Int = Resources.getSystem().displayMetrics.densityDpi

    @JvmField
    val LOCALE_RANKING: Map<String, Int> = HashMap<String, Int>().apply {
        val localeList = ConfigurationCompat.getLocales(Resources.getSystem().configuration)
        for (i in 0 until localeList.size()) {
            put(localeList[i]!!.language, i)
        }
    }

    @JvmStatic
    fun getTrackerCodeSignatures(): Array<String> {
        return ContextUtils.getContext().resources.getStringArray(R.array.tracker_signatures)
    }

    @JvmStatic
    fun getSearchableTrackerSignatures(): AhoCorasick {
        if (sAhoCorasickTrackerCache == null) {
            sAhoCorasickTrackerCache = AhoCorasick(getTrackerCodeSignatures())
        }
        return sAhoCorasickTrackerCache!!
    }

    @JvmStatic
    fun cleanup() {
        sAhoCorasickTrackerCache?.let {
            it.close()
            sAhoCorasickTrackerCache = null
        }
    }

    @JvmStatic
    fun getTrackerNames(): Array<String> {
        if (sTrackerNames == null) {
            sTrackerNames = ContextUtils.getContext().resources.getStringArray(R.array.tracker_names)
        }
        return sTrackerNames!!
    }

    @JvmStatic
    @WorkerThread
    fun getDebloatObjects(): List<DebloatObject> {
        if (sDebloatObjects == null) {
            sDebloatObjects = loadDebloatObjects(ContextUtils.getContext(), Gson())
        }
        return sDebloatObjects!!
    }

    @JvmStatic
    @WorkerThread
    fun getDebloatObjectsWithInstalledInfo(context: Context): List<DebloatObject> {
        val appDb = AppDb()
        if (sDebloatObjects == null) {
            sDebloatObjects = loadDebloatObjects(context, Gson())
        }
        sDebloatObjects!!.forEach { it.fillInstallInfo(context, appDb) }
        return sDebloatObjects!!
    }

    @WorkerThread
    private fun loadDebloatObjects(context: Context, gson: Gson): List<DebloatObject> {
        val idSuggestionObjectsMap = loadSuggestions(context, gson)
        val jsonContent = FileUtils.getContentFromAssets(context, "debloat.json")
        return try {
            val debloatObjects = listOf(*gson.fromJson(jsonContent, Array<DebloatObject>::class.java))
            var id = 0
            debloatObjects.forEach { obj ->
                obj.suggestions = idSuggestionObjectsMap[obj.getSuggestionId()]
                obj.id = id++
            }
            debloatObjects
        } catch (e: Throwable) {
            e.printStackTrace()
            emptyList()
        }
    }

    @WorkerThread
    private fun loadSuggestions(context: Context, gson: Gson): Map<String, List<SuggestionObject>> {
        val jsonContent = FileUtils.getContentFromAssets(context, "suggestions.json")
        val idSuggestionObjectsMap = HashMap<String, MutableList<SuggestionObject>>()
        try {
            val suggestionObjects = gson.fromJson(jsonContent, Array<SuggestionObject>::class.java)
            suggestionObjects?.forEach { obj ->
                idSuggestionObjectsMap.getOrPut(obj.suggestionId!!) { mutableListOf() }.add(obj)
            }
        } catch (th: Throwable) {
            th.printStackTrace()
        }
        return idSuggestionObjectsMap
    }
}
