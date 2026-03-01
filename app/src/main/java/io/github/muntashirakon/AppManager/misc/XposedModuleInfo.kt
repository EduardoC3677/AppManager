// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.text.SpannableStringBuilder
import android.text.TextUtils
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.utils.UIUtils.getBoldString
import io.github.muntashirakon.AppManager.utils.UIUtils.getStyledKeyValue
import io.github.muntashirakon.util.LocalizedString
import io.github.muntashirakon.util.UiUtils
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.*
import java.util.zip.ZipFile

// Source: https://github.com/LSPosed/LSPosed/blob/1c586fe41f22fac84c46d33db61e3a04ad528409/app/src/main/java/org/lsposed/manager/util/ModuleUtil.java#L88
// Copyright 2020 EdXposed Contributors
// Copyright 2021 LSPosed Contributors
// Copyright 2023 Muntashir Al-Islam
class XposedModuleInfo(private val mApp: ApplicationInfo, modernModuleApk: ZipFile?) : LocalizedString {
    val packageName: String = mApp.packageName
    val legacy: Boolean = modernModuleApk == null
    val minVersion: Int
    val targetVersion: Int
    val staticScope: Boolean

    private var mAppLabel: CharSequence? = null
    private var mDescription: CharSequence? = null
    private var mScopeList: MutableList<String>? = null

    init {
        if (legacy) {
            val minVersionRaw = mApp.metaData?.get("xposedminversion")
            minVersion = when (minVersionRaw) {
                is Int -> minVersionRaw
                is String -> extractIntPart(minVersionRaw)
                else -> 0
            }
            targetVersion = minVersion
            staticScope = false
        } else {
            var minV = 100
            var targetV = 100
            var staticS = false
            try {
                val propEntry = modernModuleApk!!.getEntry("META-INF/xposed/module.prop")
                if (propEntry != null) {
                    val prop = Properties()
                    prop.load(modernModuleApk.getInputStream(propEntry))
                    minV = extractIntPart(prop.getProperty("minApiVersion") ?: "")
                    targetV = extractIntPart(prop.getProperty("targetApiVersion") ?: "")
                    staticS = prop.getProperty("staticScope") == "true"
                }
                val scopeEntry = modernModuleApk.getEntry("META-INF/xposed/scope.list")
                if (scopeEntry != null) {
                    BufferedReader(InputStreamReader(modernModuleApk.getInputStream(scopeEntry))).use { reader ->
                        mScopeList = mutableListOf()
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            mScopeList!!.add(line!!)
                        }
                    }
                } else {
                    mScopeList = mutableListOf()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error while reading modern module APK", e)
            }
            minVersion = minV
            targetVersion = targetV
            staticScope = staticS
        }
    }

    fun getAppLabel(pm: PackageManager): CharSequence {
        if (mAppLabel == null) mAppLabel = mApp.loadLabel(pm)
        return mAppLabel!!
    }

    fun getDescription(pm: PackageManager): CharSequence {
        if (mDescription != null) return mDescription!!
        var descriptionTmp: CharSequence = ""
        if (legacy) {
            val descriptionRaw = mApp.metaData?.get("xposeddescription")
            if (descriptionRaw is String) {
                descriptionTmp = descriptionRaw.trim()
            } else if (descriptionRaw is Int && descriptionRaw != 0) {
                try {
                    descriptionTmp = pm.getResourcesForApplication(mApp).getString(descriptionRaw).trim()
                } catch (ignored: Exception) {}
            }
        } else {
            mApp.loadDescription(pm)?.let { descriptionTmp = it }
        }
        mDescription = descriptionTmp
        return mDescription!!
    }

    fun getScopeList(pm: PackageManager): List<String>? {
        if (mScopeList != null) return mScopeList
        var list: MutableList<String>? = null
        try {
            val scopeListResourceId = mApp.metaData?.getInt("xposedscope") ?: 0
            if (scopeListResourceId != 0) {
                list = pm.getResourcesForApplication(mApp).getStringArray(scopeListResourceId).toMutableList()
            } else {
                val scopeListString = mApp.metaData?.getString("xposedscope")
                if (scopeListString != null) list = scopeListString.split(";").toMutableList()
            }
        } catch (ignored: Exception) {}
        list?.let {
            it.replaceAll { s ->
                when (s) {
                    "android" -> "system"
                    "system" -> "android"
                    else -> s
                }
            }
            mScopeList = it
        }
        return mScopeList
    }

    override fun toLocalizedString(context: Context): CharSequence {
        val pm = context.packageManager
        val sb = SpannableStringBuilder()
            .append(getStyledKeyValue(context, R.string.module_name, getAppLabel(pm))).append("
")
            .append(getStyledKeyValue(context, R.string.title_description, getDescription(pm))).append("
")
            .append(getStyledKeyValue(context, R.string.type, if (legacy) "Legacy" else "Modern")).append("
")
        if (legacy) {
            sb.append(getStyledKeyValue(context, "Xposed Minimum API", minVersion.toString())).append("
")
        } else {
            sb.append(getStyledKeyValue(context, "Xposed API", "")).append("
")
                .append(getStyledKeyValue(context, "  Min", minVersion.toString())).append(", ")
                .append(getStyledKeyValue(context, "Target", targetVersion.toString())).append("
")
                .append(getStyledKeyValue(context, "Scope", if (staticScope) "Static" else "Dynamic")).append("
")
        }
        getScopeList(pm)?.takeIf { it.isNotEmpty() }?.let {
            sb.append(getBoldString("Scopes")).append("
").append(UiUtils.getOrderedList(it))
        }
        return sb
    }

    companion object {
        val TAG: String = XposedModuleInfo::class.java.simpleName

        @JvmStatic
        fun isXposedModule(app: ApplicationInfo, zipFile: ZipFile): Boolean? {
            if (app.metaData?.containsKey("xposedminversion") == true) return null
            return zipFile.getEntry("META-INF/xposed/module.prop") != null
        }

        @JvmStatic
        fun extractIntPart(str: String): Int {
            var result = 0
            for (c in str) {
                if (c in '0'..'9') result = result * 10 + (c - '0')
                else break
            }
            return result
        }
    }
}
