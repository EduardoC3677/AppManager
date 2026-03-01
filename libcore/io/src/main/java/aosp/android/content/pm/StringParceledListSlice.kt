// SPDX-License-Identifier: GPL-3.0-or-later

package aosp.android.content.pm

import android.os.Parcel
import android.os.Parcelable
import java.util.Collections

class StringParceledListSlice : BaseParceledListSlice<String> {
    constructor(list: List<String>) : super(list)

    private constructor(`in`: Parcel) : super(`in`)

    override fun describeContents(): Int {
        return 0
    }

    override fun writeElement(parcelable: String, reply: Parcel, callFlags: Int) {
        reply.writeString(parcelable)
    }

    override fun writeParcelableCreator(parcelable: String, dest: Parcel) {
        return
    }

    override fun readParcelableCreator(from: Parcel, loader: ClassLoader?): Parcelable.Creator<*> {
        return Parcel.STRING_CREATOR
    }

    companion object {
        @JvmStatic
        fun emptyList(): StringParceledListSlice {
            return StringParceledListSlice(Collections.emptyList())
        }

        @JvmField
        val CREATOR: Parcelable.ClassLoaderCreator<StringParceledListSlice> =
            object : Parcelable.ClassLoaderCreator<StringParceledListSlice> {
                override fun createFromParcel(source: Parcel): StringParceledListSlice {
                    return StringParceledListSlice(source)
                }

                override fun createFromParcel(source: Parcel, loader: ClassLoader): StringParceledListSlice {
                    return StringParceledListSlice(source)
                }

                override fun newArray(size: Int): Array<StringParceledListSlice?> {
                    return arrayOfNulls(size)
                }
            }
    }
}
