// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo

abstract class BaseAccessibilityService : AccessibilityService() {
    private var mContext: Context? = null

    fun init(context: Context) {
        mContext = context.applicationContext
    }

    fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        mContext?.startActivity(intent)
    }

    fun performViewClick(nodeInfo: AccessibilityNodeInfo?) {
        var node = nodeInfo
        while (node != null) {
            if (node.isClickable) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                break
            }
            node = node.parent
        }
    }

    fun performBackClick() {
        SystemClock.sleep(500)
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    fun performScrollBackward() {
        SystemClock.sleep(500)
        performGlobalAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
    }

    fun performScrollForward() {
        SystemClock.sleep(500)
        performGlobalAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }

    fun findViewByText(text: String): AccessibilityNodeInfo? = findViewByText(text, false)

    fun findViewByText(text: String, clickable: Boolean): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val nodeInfoList = root.findAccessibilityNodeInfosByText(text)
        if (nodeInfoList != null) {
            for (nodeInfo in nodeInfoList) {
                if (nodeInfo.isClickable == clickable) return nodeInfo
                nodeInfo.recycle()
            }
        }
        return null
    }

    fun findViewById(id: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        return root.findAccessibilityNodeInfosByViewId(id)?.firstOrNull()
    }

    fun clickTextViewByText(text: String) {
        val root = rootInActiveWindow ?: return
        root.findAccessibilityNodeInfosByText(text)?.firstOrNull()?.let {
            performViewClick(it); it.recycle()
        }
    }

    fun clickTextViewByID(id: String) {
        val root = rootInActiveWindow ?: return
        root.findAccessibilityNodeInfosByViewId(id)?.firstOrNull()?.let {
            performViewClick(it); it.recycle()
        }
    }

    fun inputText(nodeInfo: AccessibilityNodeInfo, text: String) {
        val arguments = Bundle()
        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        nodeInfo.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    companion object {
        @JvmStatic
        fun isAccessibilityEnabled(context: Context): Boolean {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val services = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            return services.any { ComponentName.unflattenFromString(it.id)?.packageName == context.packageName }
        }

        @JvmStatic
        protected fun findViewByText(root: AccessibilityNodeInfo?, text: String, clickable: Boolean): AccessibilityNodeInfo? {
            root?.findAccessibilityNodeInfosByText(text)?.forEach {
                if (it.isClickable == clickable) return it
                it.recycle()
            }
            return null
        }

        @JvmStatic
        protected fun findViewByTextRecursive(root: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
            if (root == null) return null
            root.findAccessibilityNodeInfosByText(text)?.firstOrNull()?.let { return it }
            for (i in 0 until root.childCount) {
                findViewByTextRecursive(root.getChild(i), text)?.let { return it }
            }
            return null
        }

        @JvmStatic
        protected fun findViewByClassName(root: AccessibilityNodeInfo?, className: CharSequence): AccessibilityNodeInfo? {
            if (root == null) return null
            for (i in 0 until root.childCount) {
                val child = root.getChild(i)
                if (className == child.className) return child
                child.recycle()
            }
            return null
        }

        @JvmStatic
        protected fun waitUntilEnabled(nodeInfo: AccessibilityNodeInfo, times: Int = 10) {
            var t = if (times == 0) 10 else times
            while (!nodeInfo.isEnabled && t > 0) {
                SystemClock.sleep(500)
                t--
            }
        }
    }
}
