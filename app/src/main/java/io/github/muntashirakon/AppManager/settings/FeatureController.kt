// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.IntDef
import androidx.appcompat.app.AppCompatActivity
import androidx.collection.SparseArrayCompat
import io.github.muntashirakon.AppManager.BuildConfig
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerActivity
import io.github.muntashirakon.AppManager.details.AppDetailsActivity
import io.github.muntashirakon.AppManager.details.manifest.ManifestViewerActivity
import io.github.muntashirakon.AppManager.editor.CodeEditorActivity
import io.github.muntashirakon.AppManager.intercept.ActivityInterceptor
import io.github.muntashirakon.AppManager.logcat.LogViewerActivity
import io.github.muntashirakon.AppManager.scanner.ScannerActivity
import io.github.muntashirakon.AppManager.self.SelfPermissions
import io.github.muntashirakon.AppManager.terminal.TermActivity
import io.github.muntashirakon.AppManager.utils.AppPref
import io.github.muntashirakon.AppManager.utils.ContextUtils
import io.github.muntashirakon.AppManager.viewer.ExplorerActivity

class FeatureController private constructor() {
    @IntDef(
        flag = true, value = [
            FEAT_INTERCEPTOR,
            FEAT_MANIFEST,
            FEAT_SCANNER,
            FEAT_INSTALLER,
            FEAT_USAGE_ACCESS,
            FEAT_LOG_VIEWER,
            FEAT_INTERNET,
            FEAT_APP_EXPLORER,
            FEAT_APP_INFO,
            FEAT_CODE_EDITOR,
            FEAT_VIRUS_TOTAL,
            FEAT_TERMINAL
        ]
    )
    annotation class FeatureFlags

    companion object {
        const val FEAT_INTERCEPTOR = 1
        const val FEAT_MANIFEST = 1 shl 1
        const val FEAT_SCANNER = 1 shl 2
        const val FEAT_INSTALLER = 1 shl 3
        const val FEAT_USAGE_ACCESS = 1 shl 4
        const val FEAT_LOG_VIEWER = 1 shl 5
        const val FEAT_INTERNET = 1 shl 6
        const val FEAT_APP_EXPLORER = 1 shl 7
        const val FEAT_APP_INFO = 1 shl 8
        const val FEAT_CODE_EDITOR = 1 shl 9
        const val FEAT_VIRUS_TOTAL = 1 shl 10
        const val FEAT_TERMINAL = 1 shl 11

        @JvmStatic
        fun getInstance(): FeatureController {
            return FeatureController()
        }

        @JvmField
        val featureFlags = mutableListOf<Int>()

        private val sFeatureFlagsMap = LinkedHashMap<Int, Int>().apply {
            featureFlags.add(FEAT_APP_EXPLORER)
            put(FEAT_APP_EXPLORER, R.string.app_explorer)
            featureFlags.add(FEAT_APP_INFO)
            put(FEAT_APP_INFO, R.string.app_info)
            featureFlags.add(FEAT_CODE_EDITOR)
            put(FEAT_CODE_EDITOR, R.string.title_code_editor)
            featureFlags.add(FEAT_INTERCEPTOR)
            put(FEAT_INTERCEPTOR, R.string.interceptor)
            featureFlags.add(FEAT_LOG_VIEWER)
            put(FEAT_LOG_VIEWER, R.string.log_viewer)
            featureFlags.add(FEAT_MANIFEST)
            put(FEAT_MANIFEST, R.string.manifest_viewer)
            featureFlags.add(FEAT_INSTALLER)
            put(FEAT_INSTALLER, R.string.package_installer)
            featureFlags.add(FEAT_SCANNER)
            put(FEAT_SCANNER, R.string.scanner)
            featureFlags.add(FEAT_TERMINAL)
            put(FEAT_TERMINAL, R.string.title_terminal_emulator)
            featureFlags.add(FEAT_USAGE_ACCESS)
            put(FEAT_USAGE_ACCESS, R.string.usage_access)
            featureFlags.add(FEAT_VIRUS_TOTAL)
            put(FEAT_VIRUS_TOTAL, R.string.virus_total)
        }

        @JvmStatic
        fun getFormattedFlagNames(context: Context): Array<CharSequence?> {
            val flagNames = arrayOfNulls<CharSequence>(featureFlags.size)
            for (i in flagNames.indices) {
                flagNames[i] = context.getText(sFeatureFlagsMap[featureFlags[i]]!!)
            }
            return flagNames
        }

        private val sComponentCache = SparseArrayCompat<ComponentName>(4)

        @JvmStatic
        fun isInterceptorEnabled(): Boolean {
            return getInstance().isEnabled(FEAT_INTERCEPTOR)
        }

        @JvmStatic
        fun isManifestEnabled(): Boolean {
            return getInstance().isEnabled(FEAT_MANIFEST)
        }

        @JvmStatic
        fun isScannerEnabled(): Boolean {
            return getInstance().isEnabled(FEAT_SCANNER)
        }

        @JvmStatic
        fun isInstallerEnabled(): Boolean {
            return getInstance().isEnabled(FEAT_INSTALLER)
        }

        @JvmStatic
        fun isUsageAccessEnabled(): Boolean {
            return getInstance().isEnabled(FEAT_USAGE_ACCESS)
        }

        @JvmStatic
        fun isLogViewerEnabled(): Boolean {
            return getInstance().isEnabled(FEAT_LOG_VIEWER)
        }

        @JvmStatic
        fun isInternetEnabled(): Boolean {
            return getInstance().isEnabled(FEAT_INTERNET)
        }

        @JvmStatic
        fun isVirusTotalEnabled(): Boolean {
            return getInstance().isEnabled(FEAT_VIRUS_TOTAL)
        }

        @JvmStatic
        fun isCodeEditorEnabled(): Boolean {
            return getInstance().isEnabled(FEAT_CODE_EDITOR)
        }

        @JvmStatic
        fun isTerminalEnabled(): Boolean {
            return getInstance().isEnabled(FEAT_TERMINAL)
        }
    }

    private val mPackageName = BuildConfig.APPLICATION_ID
    private val mPm: PackageManager = ContextUtils.getContext().packageManager
    private var mFlags: Int = AppPref.getInt(AppPref.PrefKey.PREF_ENABLED_FEATURES_INT)

    fun getFlags(): Int {
        return mFlags
    }

    private fun isEnabled(@FeatureFlags key: Int): Boolean {
        val cn: ComponentName? = when (key) {
            FEAT_INSTALLER -> getComponentName(key, PackageInstallerActivity::class.java)
            FEAT_INTERCEPTOR -> getComponentName(key, ActivityInterceptor::class.java)
            FEAT_MANIFEST -> getComponentName(key, ManifestViewerActivity::class.java)
            FEAT_SCANNER -> getComponentName(key, ScannerActivity::class.java)
            FEAT_USAGE_ACCESS ->                 // Only depends on flag
                return (mFlags and key) != 0
            FEAT_VIRUS_TOTAL -> return (mFlags and key) != 0 && isEnabled(FEAT_INTERNET)
            FEAT_INTERNET -> return (mFlags and key) != 0 && SelfPermissions.checkSelfPermission(Manifest.permission.INTERNET)
            FEAT_LOG_VIEWER -> getComponentName(key, LogViewerActivity::class.java)
            FEAT_APP_EXPLORER -> getComponentName(key, ExplorerActivity::class.java)
            FEAT_APP_INFO -> getComponentName(key, AppDetailsActivity.ALIAS_APP_INFO)
            FEAT_CODE_EDITOR -> getComponentName(key, CodeEditorActivity.ALIAS_EDITOR)
            FEAT_TERMINAL -> getComponentName(key, TermActivity::class.java)
            else -> throw IllegalArgumentException()
        }
        return isComponentEnabled(cn) && (mFlags and key) != 0
    }

    fun modifyState(@FeatureFlags key: Int, enabled: Boolean) {
        when (key) {
            FEAT_INSTALLER -> modifyState(key, PackageInstallerActivity::class.java, enabled)
            FEAT_INTERCEPTOR -> modifyState(key, ActivityInterceptor::class.java, enabled)
            FEAT_MANIFEST -> modifyState(key, ManifestViewerActivity::class.java, enabled)
            FEAT_SCANNER -> modifyState(key, ScannerActivity::class.java, enabled)
            FEAT_USAGE_ACCESS, FEAT_INTERNET, FEAT_VIRUS_TOTAL -> {}
            FEAT_LOG_VIEWER -> modifyState(key, LogViewerActivity::class.java, enabled)
            FEAT_APP_EXPLORER -> modifyState(key, ExplorerActivity::class.java, enabled)
            FEAT_APP_INFO -> modifyState(key, AppDetailsActivity.ALIAS_APP_INFO, enabled)
            FEAT_CODE_EDITOR -> modifyState(key, CodeEditorActivity.ALIAS_EDITOR, enabled)
            FEAT_TERMINAL -> modifyState(key, TermActivity::class.java, enabled)
        }
        // Modify flags
        mFlags = if (enabled) mFlags or key else mFlags and key.inv()
        // Save to pref
        AppPref.set(AppPref.PrefKey.PREF_ENABLED_FEATURES_INT, mFlags)
    }

    private fun modifyState(
        @FeatureFlags key: Int,
        clazz: Class<out AppCompatActivity>?,
        enabled: Boolean
    ) {
        val cn = getComponentName(key, clazz) ?: return
        val state = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        mPm.setComponentEnabledSetting(cn, state, PackageManager.DONT_KILL_APP)
    }

    private fun modifyState(@FeatureFlags key: Int, name: String?, enabled: Boolean) {
        val cn = getComponentName(key, name) ?: return
        val state = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        mPm.setComponentEnabledSetting(cn, state, PackageManager.DONT_KILL_APP)
    }

    private fun getComponentName(@FeatureFlags key: Int, clazz: Class<out AppCompatActivity>?): ComponentName? {
        if (clazz == null) return null
        var cn = sComponentCache[key]
        if (cn == null) {
            cn = ComponentName(mPackageName, clazz.name)
            sComponentCache.put(key, cn)
        }
        return cn
    }

    private fun getComponentName(@FeatureFlags key: Int, name: String?): ComponentName? {
        if (name == null) return null
        var cn = sComponentCache[key]
        if (cn == null) {
            cn = ComponentName(mPackageName, name)
            sComponentCache.put(key, cn)
        }
        return cn
    }

    private fun isComponentEnabled(componentName: ComponentName?): Boolean {
        if (componentName == null) return true
        val status = mPm.getComponentEnabledSetting(componentName)
        return status == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT || status == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    }
}
