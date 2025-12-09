// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.appcompat.widget.LinearLayoutCompat
import io.github.muntashirakon.AppManager.R

// TODO: 2/7/23 Replace this with preferences
class ListItemCreator(
    activity: Activity,
    @IdRes resIdMenuContainer: Int
) {
    private val mListContainer: LinearLayoutCompat = activity.findViewById(resIdMenuContainer)
    private val mLayoutInflater: LayoutInflater = activity.layoutInflater

    var listItem: View? = null
    var itemTitle: TextView? = null
    var itemSubtitle: TextView? = null
    var itemIcon: ImageView? = null

    init {
        mListContainer.removeAllViews()
    }

    fun addItemWithTitleSubtitle(title: CharSequence, subtitle: CharSequence): View {
        return addItemWithTitleSubtitle(title, subtitle, EMPTY)
    }

    fun addItemWithTitleSubtitle(title: CharSequence, subtitle: CharSequence, resIdIcon: Int): View {
        return addItemWithIconTitleSubtitle(title, subtitle, resIdIcon)
    }

    /**
     * Add a menu item to the main menu container.
     *
     * @param title     Title
     * @param subtitle  Subtitle (null to remove it)
     * @param resIdIcon Resource ID for icon (ListItemCreator.EMPTY to leave it empty)
     * @return The menu item is returned which can be used for other purpose
     */
    private fun addItemWithIconTitleSubtitle(
        title: CharSequence,
        subtitle: CharSequence?,
        @DrawableRes resIdIcon: Int
    ): View {
        listItem = mLayoutInflater.inflate(io.github.muntashirakon.ui.R.layout.m3_preference, mListContainer, false)
        listItem!!.findViewById<View>(R.id.icon_frame).visibility = View.GONE
        // Item title
        itemTitle = listItem!!.findViewById(android.R.id.title)
        itemTitle!!.text = title
        // Item subtitle
        itemSubtitle = listItem!!.findViewById(android.R.id.summary)
        if (subtitle != null) {
            itemSubtitle!!.text = subtitle
        } else {
            itemSubtitle!!.visibility = View.GONE
        }
        // Item icon
        itemIcon = listItem!!.findViewById(android.R.id.icon)
        if (resIdIcon != EMPTY) {
            itemIcon!!.setImageResource(resIdIcon)
        } else {
            itemIcon!!.visibility = View.GONE
        }
        // Add new menu to the container
        mListContainer.addView(listItem)
        return listItem!!
    }

    companion object {
        private const val EMPTY = -1
    }
}
