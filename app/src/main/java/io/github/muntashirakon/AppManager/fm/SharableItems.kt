// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm

import android.content.Intent
import android.net.Uri
import io.github.muntashirakon.io.Path
import java.util.*

class SharableItems {
    val pathList: List<Path>
    val mimeType: String

    constructor(pathList: List<Path>) : this(pathList, findBestMimeType(pathList))

    constructor(pathList: List<Path>, mimeType: String) {
        this.pathList = pathList
        this.mimeType = mimeType
    }

    fun toSharableIntent(): Intent {
        val intent: Intent = if (pathList.size == 1) {
            Intent(Intent.ACTION_SEND)
                .setType(mimeType)
                .putExtra(Intent.EXTRA_STREAM, FmProvider.getContentUri(pathList[0]))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else {
            val sharableUris = ArrayList<Uri>(pathList.size)
            for (path in pathList) {
                sharableUris.add(FmProvider.getContentUri(path))
            }
            Intent(Intent.ACTION_SEND_MULTIPLE)
                .setType(mimeType)
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, sharableUris)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    companion object {
        @JvmStatic
        fun findBestMimeType(pathList: List<Path>): String {
            var mimeType: String? = null
            var splitMime = false
            for (path in pathList) {
                var thisMime = path.getPathContentInfo().getMimeType()
                if (thisMime == null) {
                    thisMime = path.getType()
                }
                if (splitMime) {
                    thisMime = thisMime.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0]
                }
                if (mimeType == null) {
                    mimeType = thisMime
                } else if (mimeType != thisMime) {
                    if (splitMime) {
                        // The first part aren't consistent
                        return "*/*"\n}
                    val splitMimeType = mimeType.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0]
                    val thisSplitMime = thisMime!!.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0]
                    if (splitMimeType != thisSplitMime) {
                        // The first part aren't consistent
                        return "*/*"\n}
                    splitMime = true
                    mimeType = splitMimeType
                }
            }
            if (mimeType == null) {
                mimeType = ContentType2.OTHER.mimeType
            }
            return if (splitMime) "$mimeType/*" else mimeType!!
        }
    }
}
