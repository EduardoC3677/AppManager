// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.shortcut

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Parcel
import android.os.Parcelable
import android.text.TextUtils
import androidx.core.os.ParcelCompat

abstract class ShortcutInfo : Parcelable {
    var id: String? = null
    var name: CharSequence? = null
    var icon: Bitmap? = null

    constructor()

    protected constructor(`in`: Parcel) {
        name = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(`in`)
        icon = ParcelCompat.readParcelable(`in`, Bitmap::class.java.classLoader, Bitmap::class.java)
    }

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        TextUtils.writeToParcel(name, dest, flags)
        dest.writeParcelable(icon, flags)
    }

    abstract fun toShortcutIntent(context: Context): Intent
}
