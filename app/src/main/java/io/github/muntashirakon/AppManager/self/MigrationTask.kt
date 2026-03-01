// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.self

import android.content.Context
import io.github.muntashirakon.AppManager.utils.ContextUtils

abstract class MigrationTask @JvmOverloads constructor(
    @JvmField val fromVersion: Long,
    @JvmField val toVersion: Long,
    @JvmField val mainThread: Boolean = false
) : Runnable {
    @JvmField
    val context: Context = ContextUtils.getContext()
}
