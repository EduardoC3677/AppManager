// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.debloat

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.RemoteException
import androidx.annotation.IntDef
import com.google.gson.annotations.SerializedName
import io.github.muntashirakon.AppManager.compat.ApplicationInfoCompat
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat
import io.github.muntashirakon.AppManager.db.utils.AppDb
import io.github.muntashirakon.AppManager.utils.ArrayUtils
import io.github.muntashirakon.AppManager.utils.FreezeUtils

class DebloatObject {
    @IntDef(REMOVAL_SAFE, REMOVAL_REPLACE, REMOVAL_CAUTION, REMOVAL_UNSAFE)
    @Retention(AnnotationRetention.SOURCE)
    annotation class Removal

    @SerializedName("id")
    @JvmField
    var packageName: String? = null

    @SerializedName("label")
    private var mInternalLabel: String? = null

    @SerializedName("tags")
    private var mTags: Array<String>? = null

    @SerializedName("dependencies")
    private var mDependencies: Array<String>? = null

    @SerializedName("required_by")
    private var mRequiredBy: Array<String>? = null

    @SerializedName("type")
    @JvmField
    var type: String? = null

    @SerializedName("description")
    private var mDescription: String? = null

    @SerializedName("web")
    private var mWebRefs: Array<String>? = null

    @SerializedName("removal")
    private var mRemoval: String? = null

    @SerializedName("warning")
    private var mWarning: String? = null

    @SerializedName("suggestions")
    private var mSuggestionId: String? = null

    var id: Int = 0

    var icon: Drawable? = null
        private set
    var label: CharSequence? = null
        private set
    var users: IntArray? = null
        private set
    var isInstalled: Boolean = false
        private set
    var isSystemApp: Boolean = false
        private set
    var isFrozen: Boolean = false
        private set
    var suggestions: List<SuggestionObject>? = null

    fun getDependencies(): Array<String> = ArrayUtils.defeatNullable(mDependencies)

    fun getRequiredBy(): Array<String> = ArrayUtils.defeatNullable(mRequiredBy)

    @Removal
    fun getRemoval(): Int {
        return when (mRemoval) {
            "replace" -> REMOVAL_REPLACE
            "caution" -> REMOVAL_CAUTION
            "unsafe" -> REMOVAL_UNSAFE
            else -> REMOVAL_SAFE
        }
    }

    fun getWarning(): String? = mWarning

    fun getDescription(): String? = mDescription

    fun getWebRefs(): Array<String> = ArrayUtils.defeatNullable(mWebRefs)

    fun getSuggestionId(): String? = mSuggestionId

    fun getLabelOrPackageName(): CharSequence {
        return label ?: mInternalLabel ?: packageName ?: ""
    }

    fun isUserApp(): Boolean = !isSystemApp

    private fun addUser(userId: Int) {
        users = if (users == null) {
            intArrayOf(userId)
        } else {
            ArrayUtils.appendInt(users, userId)
        }
    }

    fun fillInstallInfo(context: Context, appDb: AppDb) {
        val pm = context.packageManager
        suggestions?.forEach { suggestion ->
            appDb.getAllApplications(suggestion.packageName).forEach { app ->
                if (app.isInstalled) suggestion.addUser(app.userId)
            }
        }
        isInstalled = false
        users = null
        isSystemApp = false
        isFrozen = false
        appDb.getAllApplications(packageName).forEach { app ->
            if (!app.isInstalled) return@forEach
            isInstalled = true
            addUser(app.userId)
            isSystemApp = app.isSystemApp()
            isFrozen = !app.isEnabled
            label = app.packageLabel
            if (icon == null) {
                try {
                    val ai = PackageManagerCompat.getApplicationInfo(packageName!!,
                        PackageManagerCompat.MATCH_UNINSTALLED_PACKAGES or PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, app.userId)
                    isInstalled = (ai.flags and ApplicationInfo.FLAG_INSTALLED) != 0
                    isSystemApp = ApplicationInfoCompat.isSystemApp(ai)
                    label = ai.loadLabel(pm)
                    icon = ai.loadIcon(pm)
                    isFrozen = FreezeUtils.isFrozen(ai)
                } catch (ignore: Exception) {}
            }
        }
    }

    companion object {
        const val REMOVAL_SAFE = 1
        const val REMOVAL_REPLACE = 1 shl 1
        const val REMOVAL_CAUTION = 1 shl 2
        const val REMOVAL_UNSAFE = 1 shl 3
    }
}
