// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.accessibility

import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Build
import android.os.SystemClock
import android.text.TextUtils
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import io.github.muntashirakon.AppManager.accessibility.activity.TrackerWindow
import io.github.muntashirakon.AppManager.utils.ResourceUtil
import io.github.muntashirakon.AppManager.utils.appearance.AppearanceUtils

class NoRootAccessibilityService : BaseAccessibilityService() {
    private val mMultiplexer = AccessibilityMultiplexer.instance
    private var mPm: PackageManager? = null
    private var mTries = 1
    private var mTrackerWindow: TrackerWindow? = null

    override fun onCreate() {
        super.onCreate()
        mPm = AppearanceUtils.getSystemContext(this).packageManager
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (mMultiplexer.isLeadingActivityTracker()) {
            if (mTrackerWindow == null) mTrackerWindow = TrackerWindow(this)
            mTrackerWindow!!.showOrUpdate(AccessibilityEvent.obtain(event))
        } else {
            mTrackerWindow?.dismiss(); mTrackerWindow = null
        }
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName
        if (INSTALLER_PACKAGE == packageName) {
            automateInstallationUninstallation(event)
            return
        }
        if (SETTING_PACKAGE == packageName) {
            if (event.className == "com.android.settings.applications.InstalledAppDetailsTop") {
                val node = findViewByText(getString(event, "force_stop"), true)
                if (mMultiplexer.isForceStopEnabled()) {
                    if (node != null) {
                        if (node.isEnabled) { mTries = 0; performViewClick(node) }
                        else if (mTries > 0 && navigateToStorageAndCache(event)) { performBackClick(); mTries-- }
                        else performBackClick()
                        node.recycle()
                    } else performBackClick()
                } else if (mMultiplexer.isNavigateToStorageAndCache()) {
                    SystemClock.sleep(1000)
                    navigateToStorageAndCache(event)
                }
            } else if (event.className == "com.android.settings.SubSettings" || getString(event, "storage_settings") == event.text.toString()) {
                if (mMultiplexer.isClearDataEnabled()) performViewClick(findViewByText(getString(event, "clear_user_data_text"), true))
                if (mMultiplexer.isClearCacheEnabled()) {
                    mMultiplexer.enableClearCache(false)
                    val node = findViewByText(getString(event, "clear_cache_btn_text"), true)
                    if (node != null) { if (node.isEnabled) performViewClick(node); performBackClick(); performBackClick(); node.recycle() }
                }
            } else if (event.className == "androidx.appcompat.app.AlertDialog") {
                if (mMultiplexer.isForceStopEnabled() && findViewByText(getString(event, "force_stop_dlg_title")) != null) {
                    mMultiplexer.enableForceStop(false); mTries = 1; performViewClick(findViewByText(getString(event, "dlg_ok"), true)); performBackClick()
                }
                if (mMultiplexer.isClearDataEnabled() && findViewByText(getString(event, "clear_data_dlg_title")) != null) {
                    mMultiplexer.enableClearData(false); performViewClick(findViewByText(getString(event, "dlg_ok"), true)); performBackClick(); performBackClick()
                }
            }
        }
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        mTrackerWindow?.dismiss(); mTrackerWindow = null
        return super.onUnbind(intent)
    }

    private fun automateInstallationUninstallation(event: AccessibilityEvent) {
        if (event.className == "android.app.Dialog") {
            if (mMultiplexer.isInstallEnabled()) performViewClick(findViewByText(getString(event, "install"), true))
        } else if (event.className == "com.android.packageinstaller.UninstallerActivity") {
            if (mMultiplexer.isUninstallEnabled()) performViewClick(findViewByText(getString(event, "ok"), true))
        }
    }

    private fun navigateToStorageAndCache(event: AccessibilityEvent): Boolean {
        val storageSettings = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) getString(event, "storage_settings_for_app")
            else getString(event, "storage_label")
        } catch (e: Resources.NotFoundException) { return false }
        SystemClock.sleep(500)
        val node = findViewByTextRecursive(rootInActiveWindow, storageSettings)
        if (node != null) { mMultiplexer.enableNavigateToStorageAndCache(false); performViewClick(node); node.recycle(); return true }
        performBackClick(); return false
    }

    private fun getString(event: AccessibilityEvent, stringRes: String): String {
        val pkg = event.packageName ?: throw Resources.NotFoundException("Empty package name")
        val cls = event.className
        val resUtil = ResourceUtil()
        if (!TextUtils.isEmpty(cls)) {
            if (!resUtil.loadResources(mPm!!, pkg.toString(), cls.toString()) && !resUtil.loadResources(mPm!!, pkg.toString()) && !resUtil.loadAndroidResources()) {
                throw Resources.NotFoundException("Couldn't load resources for package: $pkg, class: $cls")
            }
        } else if (!resUtil.loadResources(mPm!!, pkg.toString()) && !resUtil.loadAndroidResources()) {
            throw Resources.NotFoundException("Couldn't load resources for package: $pkg")
        }
        return resUtil.getString(stringRes)
    }

    companion object {
        private val SETTING_PACKAGE: CharSequence = "com.android.settings"\nprivate val INSTALLER_PACKAGE: CharSequence = "com.android.packageinstaller"
    }
}
