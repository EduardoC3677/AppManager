// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import androidx.core.graphics.ColorUtils
import com.google.android.material.chip.Chip
import io.github.muntashirakon.AppManager.R

class TagItem {
    @StringRes
    private var mTextRes: Int = 0
    private var mText: CharSequence? = null
    @ColorInt
    private var mColor: Int = 0
    private var mColorSet = false
    private var mOnClickListener: View.OnClickListener? = null

    fun setTextRes(@StringRes textRes: Int): TagItem {
        mTextRes = textRes
        return this
    }

    fun setText(text: CharSequence?): TagItem {
        mText = text
        return this
    }

    fun setColor(@ColorInt color: Int): TagItem {
        mColor = color
        mColorSet = true
        return this
    }

    fun setOnClickListener(clickListener: View.OnClickListener?): TagItem {
        mOnClickListener = clickListener
        return this
    }

    fun toChip(context: Context, parent: ViewGroup): Chip {
        val chip = LayoutInflater.from(context).inflate(R.layout.item_chip, parent, false) as Chip
        if (mTextRes != 0) {
            chip.setText(mTextRes)
        } else {
            chip.text = mText
        }
        if (mColorSet) {
            chip.chipBackgroundColor = ColorStateList.valueOf(mColor)
            val luminance = ColorUtils.calculateLuminance(mColor)
            chip.setTextColor(if (luminance < 0.5) Color.WHITE else Color.BLACK)
        }
        if (mOnClickListener != null) {
            chip.setOnClickListener(mOnClickListener)
        }
        return chip
    }
}
