// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import android.provider.DocumentsContract
import io.github.muntashirakon.AppManager.shortcut.ShortcutInfo
import io.github.muntashirakon.io.Path
import java.util.*

class FmShortcutInfo : ShortcutInfo {
    private val mIsDirectory: Boolean
    private val mUri: Uri
    private val mCustomMimeType: String?

    constructor(path: Path, customMimeType: String?) {
        mIsDirectory = path.isDirectory()
        mCustomMimeType = customMimeType
        mUri = path.getUri()
        name = path.getName()
        id = UUID.randomUUID().toString()
    }

    protected constructor(`in`: Parcel) : super(`in`) {
        mIsDirectory = `in`.readByte() != 0.toByte()
        mUri = `in`.readParcelable(Uri::class.java.classLoader)!!
        mCustomMimeType = `in`.readString()
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        super.writeToParcel(dest, flags)
        dest.writeByte(if (mIsDirectory) 1 else 0)
        dest.writeParcelable(mUri, flags)
        dest.writeString(mCustomMimeType)
    }

    override fun toShortcutIntent(context: Context): Intent {
        val intent: Intent = if (mIsDirectory) {
            Intent(context, FmActivity::class.java).apply {
                setDataAndType(mUri, mCustomMimeType ?: DocumentsContract.Document.MIME_TYPE_DIR)
            }
        } else {
            Intent(context, OpenWithActivity::class.java).apply {
                if (mCustomMimeType != null) {
                    setDataAndType(mUri, mCustomMimeType)
                } else {
                    data = mUri
                }
            }
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        return intent
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<FmShortcutInfo> = object : Parcelable.Creator<FmShortcutInfo> {
            override fun createFromParcel(source: Parcel): FmShortcutInfo {
                return FmShortcutInfo(source)
            }

            override fun newArray(size: Int): Array<FmShortcutInfo?> {
                return arrayOfNulls(size)
            }
        }
    }
}
