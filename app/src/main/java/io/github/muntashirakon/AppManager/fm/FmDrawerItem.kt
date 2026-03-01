// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm

import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.IntDef

class FmDrawerItem(
    val id: Long,
    val name: String,
    val options: FmActivity.Options?,
    @DrawerItemType val type: Int
) {
    @IntDef(ITEM_TYPE_LABEL, ITEM_TYPE_FAVORITE, ITEM_TYPE_LOCATION, ITEM_TYPE_TAG)
    @Retention(AnnotationRetention.SOURCE)
    annotation class DrawerItemType

    @DrawableRes
    var iconRes: Int = 0
    var icon: Drawable? = null
    @ColorInt
    var color: Int = 0

    companion object {
        const val ITEM_TYPE_LABEL = 0
        const val ITEM_TYPE_FAVORITE = 1
        const val ITEM_TYPE_LOCATION = 2
        const val ITEM_TYPE_TAG = 3
    }
}
