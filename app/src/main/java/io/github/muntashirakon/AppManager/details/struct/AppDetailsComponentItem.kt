// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.struct

import android.content.pm.ComponentInfo
import io.github.muntashirakon.AppManager.rules.struct.ComponentRule

/**
 * Stores individual app details component item
 */
open class AppDetailsComponentItem(
    componentInfo: ComponentInfo
) : AppDetailsItem<ComponentInfo>(componentInfo) {

    @JvmField var label: CharSequence? = null
    @JvmField var canLaunch: Boolean = false

    @JvmField var isTracker: Boolean = false
    @JvmField var rule: ComponentRule? = null
    var isDisabled: Boolean = false
        private set

    init {
        name = componentInfo.name
        isDisabled = !componentInfo.isEnabled()
    }

    fun isBlocked(): Boolean {
        val rule = this.rule ?: return false
        return rule.isBlocked() && (rule.isIfw() || isDisabled)
    }

    fun setDisabled(disabled: Boolean) {
        isDisabled = disabled
    }
}
