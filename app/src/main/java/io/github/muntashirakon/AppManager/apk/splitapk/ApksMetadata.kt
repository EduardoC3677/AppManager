// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.splitapk

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.text.TextUtils
import androidx.annotation.StringDef
import androidx.core.content.pm.PackageInfoCompat
import io.github.muntashirakon.AppManager.BuildConfig
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.apk.ApkUtils
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.self.filecache.FileCache
import io.github.muntashirakon.AppManager.utils.ContextUtils
import io.github.muntashirakon.AppManager.utils.JSONUtils
import io.github.muntashirakon.io.Paths
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.zip.ZipOutputStream

class ApksMetadata {
    class Dependency {
        @StringDef(DEPENDENCY_MATCH_EXACT, DEPENDENCY_MATCH_GREATER, DEPENDENCY_MATCH_LESS)
        @Retention(AnnotationRetention.SOURCE)
        annotation class DependencyMatch

        var packageName: String? = null
        var displayName: String? = null
        var versionName: String? = null
        var versionCode: Long = 0
        var signatures: Array<String>? = null
        @DependencyMatch
        var match: String? = null
        var required: Boolean = false
        var path: String? = null

        companion object {
            const val DEPENDENCY_MATCH_EXACT = "exact"
            const val DEPENDENCY_MATCH_GREATER = "greater"
            const val DEPENDENCY_MATCH_LESS = "less"
        }
    }

    class BuildInfo {
        val timestamp: Long
        val builderId: String
        val builderLabel: String
        val builderVersion: String
        val platform: String

        constructor() {
            timestamp = System.currentTimeMillis()
            builderId = BuildConfig.APPLICATION_ID
            builderLabel = ContextUtils.getContext().getString(R.string.app_name)
            builderVersion = BuildConfig.VERSION_NAME
            platform = "android"
        }

        constructor(timestamp: Long, builderId: String, builderLabel: String, builderVersion: String, platform: String) {
            this.timestamp = timestamp
            this.builderId = builderId
            this.builderLabel = builderLabel
            this.builderVersion = builderVersion
            this.platform = platform
        }
    }

    var exportTimestamp: Long = 0
    var metaVersion: Long = 1L
    var packageName: String? = null
    var displayName: String? = null
    var versionName: String? = null
    var versionCode: Long = 0
    var minSdk: Long = 0L
    var targetSdk: Long = 0
    var buildInfo: BuildInfo? = null
    val dependencies = ArrayList<Dependency>()

    private val mPackageInfo: PackageInfo?

    constructor() {
        mPackageInfo = null
    }

    constructor(packageInfo: PackageInfo) {
        mPackageInfo = packageInfo
    }

    @Throws(JSONException::class)
    fun readMetadata(jsonString: String) {
        val jsonObject = JSONObject(jsonString)
        metaVersion = jsonObject.getLong("info_version")
        packageName = jsonObject.getString("package_name")
        displayName = jsonObject.getString("display_name")
        versionName = jsonObject.getString("version_name")
        versionCode = jsonObject.getLong("version_code")
        minSdk = jsonObject.optLong("min_sdk", 0)
        targetSdk = jsonObject.getLong("target_sdk")
        jsonObject.optJSONObject("build_info")?.let {
            buildInfo = BuildInfo(it.getLong("timestamp"), it.getString("builder_id"), it.getString("builder_label"), it.getString("builder_version"), it.getString("platform"))
        }
        jsonObject.optJSONArray("dependencies")?.let { array ->
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val dependency = Dependency().apply {
                    packageName = obj.getString("package_name")
                    displayName = obj.getString("display_name")
                    versionName = obj.getString("version_name")
                    versionCode = obj.getLong("version_code")
                    JSONUtils.getString(obj, "signature", null)?.let { signatures = it.split(",").toTypedArray() }
                    match = obj.getString("match")
                    required = obj.getBoolean("required")
                    path = JSONUtils.getString(obj, "path", null)
                }
                dependencies.add(dependency)
            }
        }
    }

    @Throws(IOException::class)
    fun writeMetadata(zipOutputStream: ZipOutputStream) {
        val pm = ContextUtils.getContext().packageManager
        val applicationInfo = mPackageInfo!!.applicationInfo!!
        packageName = mPackageInfo.packageName
        displayName = applicationInfo.loadLabel(pm).toString()
        versionName = mPackageInfo.versionName
        versionCode = PackageInfoCompat.getLongVersionCode(mPackageInfo)
        exportTimestamp = 946684800000L // Fake time
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) minSdk = applicationInfo.minSdkVersion.toLong()
        targetSdk = applicationInfo.targetSdkVersion.toLong()
        applicationInfo.sharedLibraryFiles?.filter { it.endsWith(".apk") }?.forEach { file ->
            val packageInfo = pm.getPackageArchiveInfo(file, PackageManager.GET_SHARED_LIBRARY_FILES) ?: return@forEach
            if (packageInfo.applicationInfo.sourceDir == null) packageInfo.applicationInfo.sourceDir = file
            if (packageInfo.applicationInfo.publicSourceDir == null) packageInfo.applicationInfo.publicSourceDir = file
            val tempFile = FileCache.getGlobalFileCache().createCachedFile("apks")
            try {
                val tempPath = Paths.get(tempFile)
                SplitApkExporter.saveApks(packageInfo, tempPath)
                val path = packageInfo.packageName + ApkUtils.EXT_APKS
                SplitApkExporter.addFile(zipOutputStream, tempPath, path, exportTimestamp)
                dependencies.add(Dependency().apply {
                    packageName = packageInfo.packageName
                    displayName = packageInfo.applicationInfo.loadLabel(pm).toString()
                    versionName = packageInfo.versionName
                    versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)
                    required = true
                    match = Dependency.DEPENDENCY_MATCH_EXACT
                    this.path = path
                })
            } finally {
                FileCache.getGlobalFileCache().delete(tempFile)
            }
        }
        val meta = getMetadataAsJson().toByteArray(StandardCharsets.UTF_8)
        SplitApkExporter.addBytes(zipOutputStream, meta, META_FILE, exportTimestamp)
    }

    fun getMetadataAsJson(): String {
        val jsonObject = JSONObject()
        try {
            jsonObject.put("info_version", metaVersion)
            jsonObject.put("package_name", packageName)
            jsonObject.put("display_name", displayName)
            jsonObject.put("version_name", versionName)
            jsonObject.put("version_code", versionCode)
            jsonObject.put("min_sdk", minSdk)
            jsonObject.put("target_sdk", targetSdk)
            val dependenciesArray = JSONArray()
            for (dependency in dependencies) {
                val obj = JSONObject().apply {
                    put("package_name", dependency.packageName)
                    put("display_name", dependency.displayName)
                    put("version_name", dependency.versionName)
                    put("version_code", dependency.versionCode)
                    dependency.signatures?.let { put("signature", TextUtils.join(",", it)) }
                    put("match", dependency.match)
                    put("required", dependency.required)
                    dependency.path?.let { put("path", it) }
                }
                dependenciesArray.put(obj)
            }
            if (dependenciesArray.length() > 0) jsonObject.put("dependencies", dependenciesArray)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return jsonObject.toString()
    }

    companion object {
        val TAG: String = ApksMetadata::class.java.simpleName
        const val META_FILE = "info.json"
        const val ICON_FILE = "icon.png"
    }
}
