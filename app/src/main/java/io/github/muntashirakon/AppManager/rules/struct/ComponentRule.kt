// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.struct

import android.content.ComponentName
import androidx.annotation.StringDef
import io.github.muntashirakon.AppManager.rules.RuleType
import java.util.StringTokenizer

class ComponentRule : RuleEntry {

    @StringDef(
        COMPONENT_BLOCKED_IFW_DISABLE,
        COMPONENT_BLOCKED_IFW,
        COMPONENT_DISABLED,
        COMPONENT_ENABLED,
        COMPONENT_TO_BE_BLOCKED_IFW_DISABLE,
        COMPONENT_TO_BE_BLOCKED_IFW,
        COMPONENT_TO_BE_DISABLED,
        COMPONENT_TO_BE_ENABLED,
        COMPONENT_TO_BE_DEFAULTED
    )
    @Retention(AnnotationRetention.SOURCE)
    annotation class ComponentStatus

    @ComponentStatus
    val componentStatus: String

    @ComponentStatus
    var lastComponentStatus: String? = null

    constructor(
        packageName: String,
        name: String,
        componentType: RuleType,
        @ComponentStatus componentStatus: String
    ) : super(packageName, name, componentType) {
        this.componentStatus = fixComponentStatus(componentStatus)
    }

    @Throws(IllegalArgumentException::class)
    constructor(
        packageName: String,
        name: String,
        componentType: RuleType,
        tokenizer: StringTokenizer
    ) : super(packageName, name, componentType) {
        if (tokenizer.hasMoreElements()) {
            this.componentStatus = fixComponentStatus(tokenizer.nextElement().toString())
        } else {
            throw IllegalArgumentException("Invalid format: componentStatus not found")
        }
    }

    val componentName: ComponentName
        get() = ComponentName(packageName, name)

    fun applyDefaultState(): Boolean {
        val lastStatus = lastComponentStatus ?: return false
        if (componentStatus == COMPONENT_TO_BE_BLOCKED_IFW) {
            return lastStatus == COMPONENT_DISABLED || lastStatus == COMPONENT_BLOCKED_IFW_DISABLE
        }
        return false
    }

    fun toBeRemoved(): Boolean = componentStatus == COMPONENT_TO_BE_DEFAULTED

    fun isBlocked(): Boolean {
        return componentStatus == COMPONENT_BLOCKED_IFW_DISABLE ||
                componentStatus == COMPONENT_BLOCKED_IFW ||
                componentStatus == COMPONENT_DISABLED
    }

    fun isIfw(): Boolean {
        return componentStatus == COMPONENT_TO_BE_BLOCKED_IFW ||
                componentStatus == COMPONENT_TO_BE_BLOCKED_IFW_DISABLE ||
                componentStatus == COMPONENT_BLOCKED_IFW ||
                componentStatus == COMPONENT_BLOCKED_IFW_DISABLE
    }

    fun isApplied(): Boolean {
        return !(componentStatus == COMPONENT_TO_BE_BLOCKED_IFW_DISABLE ||
                componentStatus == COMPONENT_TO_BE_BLOCKED_IFW ||
                componentStatus == COMPONENT_TO_BE_DISABLED ||
                componentStatus == COMPONENT_TO_BE_ENABLED ||
                componentStatus == COMPONENT_TO_BE_DEFAULTED)
    }

    @ComponentStatus
    fun getCounterpartOfToBe(): String {
        return when (componentStatus) {
            COMPONENT_TO_BE_BLOCKED_IFW_DISABLE -> COMPONENT_BLOCKED_IFW_DISABLE
            COMPONENT_TO_BE_BLOCKED_IFW -> COMPONENT_BLOCKED_IFW
            COMPONENT_TO_BE_DISABLED -> COMPONENT_DISABLED
            COMPONENT_TO_BE_ENABLED -> COMPONENT_ENABLED
            else -> componentStatus
        }
    }

    @ComponentStatus
    fun getToBe(): String {
        return when (componentStatus) {
            COMPONENT_BLOCKED_IFW_DISABLE -> COMPONENT_TO_BE_BLOCKED_IFW_DISABLE
            COMPONENT_BLOCKED_IFW -> COMPONENT_TO_BE_BLOCKED_IFW
            COMPONENT_DISABLED -> COMPONENT_TO_BE_DISABLED
            COMPONENT_ENABLED -> COMPONENT_TO_BE_ENABLED
            else -> componentStatus
        }
    }

    private fun fixComponentStatus(@ComponentStatus componentStatus: String): String {
        if (type != RuleType.PROVIDER) {
            return componentStatus
        }
        // Providers do not support IFW
        return when (componentStatus) {
            COMPONENT_BLOCKED_IFW_DISABLE -> COMPONENT_DISABLED
            COMPONENT_BLOCKED_IFW -> COMPONENT_ENABLED
            COMPONENT_TO_BE_BLOCKED_IFW, COMPONENT_TO_BE_BLOCKED_IFW_DISABLE -> COMPONENT_TO_BE_DISABLED
            else -> componentStatus
        }
    }

    override fun toString(): String {
        return "ComponentRule{packageName='$packageName', name='$name', type=${type.name}, componentStatus='$componentStatus'}"\n}

    override fun flattenToString(isExternal: Boolean): String {
        return addPackageWithTab(isExternal) + "$name\t${type.name}\t$componentStatus"\n}

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ComponentRule) return false
        if (!super.equals(other)) return false
        return componentStatus == other.componentStatus
    }

    override fun hashCode(): Int {
        return 31 * super.hashCode() + componentStatus.hashCode()
    }

    companion object {
        // One would want to use flags but couldn't in order to preserve compatibility
        /** Component has been blocked with both IFW and PM. */
        const val COMPONENT_BLOCKED_IFW_DISABLE = "true" // To preserve compatibility
        /** Component has been blocked with IFW. */
        const val COMPONENT_BLOCKED_IFW = "ifw_true"\n/** Component has been disabled. */
        const val COMPONENT_DISABLED = "dis_true"\n/** Component has been enabled. */
        const val COMPONENT_ENABLED = "en_true"\n/** Component will be blocked with both IFW and PM. */
        const val COMPONENT_TO_BE_BLOCKED_IFW_DISABLE = "false" // To preserve compatibility
        /** Component will be blocked with IFW. */
        const val COMPONENT_TO_BE_BLOCKED_IFW = "ifw_false"\n/** Component will be disabled. */
        const val COMPONENT_TO_BE_DISABLED = "dis_false"\n/** Component will be enabled. */
        const val COMPONENT_TO_BE_ENABLED = "en_false"\n/** Component will be set to the default state, removed from IFW rules if exists and cleared from DB. */
        const val COMPONENT_TO_BE_DEFAULTED = "unblocked"
    }
}
