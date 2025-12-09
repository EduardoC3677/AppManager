// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.struct

import io.github.muntashirakon.AppManager.magisk.MagiskProcess
import io.github.muntashirakon.AppManager.rules.RuleType
import java.util.StringTokenizer

class MagiskHideRule : RuleEntry {
    val magiskProcess: MagiskProcess

    constructor(magiskProcess: MagiskProcess) : super(
        magiskProcess.packageName,
        magiskProcess.name,
        RuleType.MAGISK_HIDE
    ) {
        this.magiskProcess = magiskProcess
    }

    @Throws(IllegalArgumentException::class)
    constructor(
        packageName: String,
        processName: String,
        tokenizer: StringTokenizer
    ) : super(
        packageName,
        if (processName == STUB) packageName else processName,
        RuleType.MAGISK_HIDE
    ) {
        magiskProcess = MagiskProcess(packageName, name) // name cannot be STUB
        magiskProcess.isAppZygote = name.endsWith("_zygote")

        if (tokenizer.hasMoreElements()) {
            magiskProcess.isEnabled = tokenizer.nextElement().toString().toBoolean()
        } else {
            throw IllegalArgumentException("Invalid format: isHidden not found")
        }

        if (tokenizer.hasMoreElements()) {
            magiskProcess.isIsolatedProcess = tokenizer.nextElement().toString().toBoolean()
        }
    }

    override fun toString(): String {
        return "MagiskHideRule{packageName='$packageName', processName='$name', " +
                "isHidden=${magiskProcess.isEnabled}, isIsolated=${magiskProcess.isIsolatedProcess}, " +
                "isAppZygote=${magiskProcess.isAppZygote}}"
    }

    override fun flattenToString(isExternal: Boolean): String {
        return addPackageWithTab(isExternal) + "$name\t${type.name}\t${magiskProcess.isEnabled}\t${magiskProcess.isIsolatedProcess}"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MagiskHideRule) return false
        if (!super.equals(other)) return false
        return magiskProcess.isEnabled == other.magiskProcess.isEnabled &&
                magiskProcess.isIsolatedProcess == other.magiskProcess.isIsolatedProcess
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + magiskProcess.isEnabled.hashCode()
        result = 31 * result + magiskProcess.isIsolatedProcess.hashCode()
        return result
    }
}
