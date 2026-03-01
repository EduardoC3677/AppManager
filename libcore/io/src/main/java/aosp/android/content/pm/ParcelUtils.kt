// SPDX-License-Identifier: GPL-3.0-or-later

package aosp.android.content.pm

import android.os.Parcel
import android.os.Parcelable

/**
 * Helper class for [BaseParceledListSlice]
 */
internal object ParcelUtils {
    @JvmStatic
    fun writeParcelableCreator(parcelable: Parcelable, dest: Parcel) {
        val name = parcelable.javaClass.name
        dest.writeString(name)
    }

    @JvmStatic
    fun readParcelableCreator(from: Parcel, loader: ClassLoader?): Parcelable.Creator<*> {
        val name = from.readString() ?: throw IllegalArgumentException("Parcelable class name is null")
        val parcelableClass: Class<*>
        try {
            parcelableClass = Class.forName(name, false, loader)
        } catch (e: ClassNotFoundException) {
            throw IllegalArgumentException("Could not find class $name", e)
        }
        val creatorField = try {
            parcelableClass.getField("CREATOR")
        } catch (e: NoSuchFieldException) {
            throw IllegalArgumentException("Class $name must have a CREATOR field", e)
        }
        return try {
            creatorField.get(null) as Parcelable.Creator<*>
        } catch (e: IllegalAccessException) {
            throw IllegalArgumentException("Could not access CREATOR field in class $name", e)
        } catch (e: ClassCastException) {
            throw IllegalArgumentException("CREATOR field in class $name is not a Parcelable.Creator", e)
        }
    }
}
