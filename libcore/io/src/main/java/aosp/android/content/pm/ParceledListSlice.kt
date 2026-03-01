// SPDX-License-Identifier: GPL-3.0-or-later

package aosp.android.content.pm

import android.os.Parcel
import android.os.Parcelable
import java.util.Collections

class ParceledListSlice<T : Parcelable> : BaseParceledListSlice<T> {
    constructor(list: List<T>) : super(list)

    private constructor(`in`: Parcel) : super(`in`)

    override fun describeContents(): Int {
        var contents = 0
        val list = list
        for (i in list.indices) {
            contents = contents or list[i].describeContents()
        }
        return contents
    }

    override fun writeElement(parcelable: T, reply: Parcel, callFlags: Int) {
        parcelable.writeToParcel(reply, callFlags)
    }

    override fun writeParcelableCreator(parcelable: T, dest: Parcel) {
        ParcelUtils.writeParcelableCreator(parcelable, dest)
    }

    override fun readParcelableCreator(from: Parcel, loader: ClassLoader?): Parcelable.Creator<*> {
        return ParcelUtils.readParcelableCreator(from, loader)
    }

    companion object {
        @JvmStatic
        fun <T : Parcelable> emptyList(): ParceledListSlice<T> {
            return ParceledListSlice(Collections.emptyList())
        }

        @JvmField
        val CREATOR: Parcelable.ClassLoaderCreator<ParceledListSlice<*>> =
            object : Parcelable.ClassLoaderCreator<ParceledListSlice<*>> {
                override fun createFromParcel(source: Parcel): ParceledListSlice<*> {
                    return ParceledListSlice<Parcelable>(source)
                }

                override fun createFromParcel(source: Parcel, loader: ClassLoader): ParceledListSlice<*> {
                    return ParceledListSlice<Parcelable>(source)
                }

                override fun newArray(size: Int): Array<ParceledListSlice<*>?> {
                    return arrayOfNulls(size)
                }
            }
    }
}
