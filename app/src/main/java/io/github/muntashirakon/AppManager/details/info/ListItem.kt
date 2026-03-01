// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info

import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.IntDef
import androidx.annotation.StringRes
import io.github.muntashirakon.AppManager.R

class ListItem(@ListItemType val type: Int) {
    @IntDef(value = [LIST_ITEM_GROUP_BEGIN, LIST_ITEM_REGULAR, LIST_ITEM_REGULAR_ACTION, LIST_ITEM_INLINE])
    @Retention(AnnotationRetention.SOURCE)
    annotation class ListItemType

    var title: CharSequence? = null
    var subtitle: CharSequence? = null
    @DrawableRes
    var actionIconRes: Int = 0
    @StringRes
    var actionContentDescriptionRes: Int = 0
    var actionContentDescription: CharSequence? = null
    var onActionClickListener: View.OnClickListener? = null
    var isSelectable: Boolean = false
    var isMonospace: Boolean = false

    override fun toString(): String {
        return "ListItem{type=$type, title='$title', subtitle='$subtitle'}"
    }

    companion object {
        const val LIST_ITEM_GROUP_BEGIN = 0
        const val LIST_ITEM_REGULAR = 1
        const val LIST_ITEM_REGULAR_ACTION = 2
        const val LIST_ITEM_INLINE = 3

        @JvmStatic
        fun newGroupStart(header: CharSequence?): ListItem {
            return ListItem(LIST_ITEM_GROUP_BEGIN).apply { title = header }
        }

        @JvmStatic
        fun newInlineItem(title: CharSequence?, subtitle: CharSequence?): ListItem {
            return ListItem(LIST_ITEM_INLINE).apply {
                this.title = title
                this.subtitle = subtitle
            }
        }

        @JvmStatic
        fun newRegularItem(title: CharSequence?, subtitle: CharSequence?): ListItem {
            return ListItem(LIST_ITEM_REGULAR).apply {
                this.title = title
                this.subtitle = subtitle
            }
        }

        @JvmStatic
        fun newSelectableRegularItem(title: CharSequence?, subtitle: CharSequence?): ListItem {
            return ListItem(LIST_ITEM_REGULAR).apply {
                isSelectable = true
                this.title = title
                this.subtitle = subtitle
            }
        }

        @JvmStatic
        fun newSelectableRegularItem(title: CharSequence?, subtitle: CharSequence?, actionListener: View.OnClickListener?): ListItem {
            return ListItem(LIST_ITEM_REGULAR_ACTION).apply {
                isSelectable = true
                this.title = title
                this.subtitle = subtitle
                actionIconRes = R.drawable.ic_open_in_new
                onActionClickListener = actionListener
            }
        }
    }
}
