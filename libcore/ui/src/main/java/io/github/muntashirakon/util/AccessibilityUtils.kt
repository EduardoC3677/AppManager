// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.util

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.ViewCompat

object AccessibilityUtils {
    @JvmStatic
    fun requestParentAccessibilityFocus(view: View) {
        requestAccessibilityFocus(view.parentForAccessibility)
    }

    @JvmStatic
    fun requestParentAccessibilityFocus(view: View, delayMillis: Long) {
        requestAccessibilityFocus(view.parentForAccessibility, delayMillis)
    }

    @JvmStatic
    @SuppressLint("AccessibilityFocus")
    fun <T> requestAccessibilityFocus(anyView: T?) {
        if (anyView is View) {
            val view = anyView as View
            view.post { view.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null) }
        }
    }

    @JvmStatic
    @SuppressLint("AccessibilityFocus")
    fun <T> requestAccessibilityFocus(anyView: T?, delayMillis: Long) {
        if (anyView is View) {
            val view = anyView as View
            view.postDelayed({
                view.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null)
            }, delayMillis)
        }
    }

    @JvmStatic
    fun setAccessibilityHeading(view: View, enable: Boolean) {
        if (view is TextView) {
            ViewCompat.setAccessibilityHeading(view, enable)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            view.setAccessibilityDelegate(object : View.AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.isHeading = enable && info.contentDescription != null
                }
            })
        }
    }

    @JvmStatic
    fun popupMenuToAccessibleOptions(view: View, popupMenu: PopupMenu) {
        view.setAccessibilityDelegate(object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                // Add each PopupMenu item as an AccessibilityAction
                for (i in 0 until popupMenu.menu.size()) {
                    val item = popupMenu.menu.getItem(i)
                    if (item.isVisible && item.isEnabled) {
                        info.addAction(
                            AccessibilityNodeInfo.AccessibilityAction(
                                item.itemId,
                                item.title
                            )
                        )
                    }
                }
            }

            override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean {
                val menuItem = popupMenu.menu.findItem(action)
                if (menuItem != null) {
                    // Invoke the corresponding PopupMenu action programmatically
                    popupMenu.menu.performIdentifierAction(action, 0)
                    return true
                }
                return super.performAccessibilityAction(host, action, args)
            }
        })
    }
}
