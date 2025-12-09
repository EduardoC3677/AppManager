// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.google.android.material.button.MaterialButton
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.utils.appearance.ColorCodes

internal class ActionItem(
    @StringRes private val titleRes: Int,
    @DrawableRes private val iconRes: Int
) {
    private var onClickListener: View.OnClickListener? = null
    private var onLongClickListener: View.OnLongClickListener? = null

    fun setOnClickListener(clickListener: View.OnClickListener): ActionItem {
        onClickListener = clickListener
        return this
    }

    fun setOnLongClickListener(longClickListener: View.OnLongClickListener): ActionItem {
        onLongClickListener = longClickListener
        return this
    }

    fun toActionButton(context: Context, parent: ViewGroup): MaterialButton {
        val button = LayoutInflater.from(context)
            .inflate(R.layout.item_app_info_action, parent, false) as MaterialButton
        button.backgroundTintList = ColorStateList.valueOf(ColorCodes.getListItemColor1(context))
        button.setText(titleRes)
        button.setIconResource(iconRes)
        onClickListener?.let { button.setOnClickListener(it) }
        onLongClickListener?.let { button.setOnLongClickListener(it) }
        return button
    }
}
