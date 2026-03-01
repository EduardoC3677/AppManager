// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.compat.os

import android.os.Build
import android.os.IBinder
import android.os.Parcel

object ParcelCompat2 {
    @JvmStatic
    fun obtain(binder: IBinder): Parcel {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Parcel.obtain(binder)
        } else {
            Parcel.obtain()
        }
    }
}
