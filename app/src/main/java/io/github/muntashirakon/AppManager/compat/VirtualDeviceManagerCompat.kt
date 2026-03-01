// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
object VirtualDeviceManagerCompat {
    @JvmField
    val PERSISTENT_DEVICE_ID_DEFAULT: String = "default:" + Context.DEVICE_ID_DEFAULT
}
