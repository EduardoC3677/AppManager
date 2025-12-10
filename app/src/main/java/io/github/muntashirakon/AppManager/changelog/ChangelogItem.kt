// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.changelog

import androidx.annotation.IntDef
import androidx.core.text.HtmlCompat

// Copyright 2013 Gabriele Mariotti <gabri.mariotti@gmail.com>
// Copyright 2022 Muntashir Al-Islam
open class ChangelogItem {

    @IntDef(HEADER, TITLE, NOTE, NEW, IMPROVE, FIX)
    @Retention(AnnotationRetention.SOURCE)
    annotation class ChangelogType

    @IntDef(TEXT_SMALL, TEXT_MEDIUM, TEXT_LARGE)
    @Retention(AnnotationRetention.SOURCE)
    annotation class ChangeTextType

    @ChangelogType
    val type: Int

    val changeText: CharSequence

    var isBulletedList: Boolean = false
    var isSubtext: Boolean = false
        set(value) {
            field = value
            changeTextType = if (value) TEXT_SMALL else TEXT_MEDIUM
        }

    var changeTitle: String? = null
        internal set

    @ChangeTextType
    var changeTextType: Int = TEXT_MEDIUM

    constructor(@ChangelogType type: Int) {
        this.changeText = ""
        this.type = type
    }

    constructor(changeText: CharSequence, @ChangelogType type: Int) {
        this.changeText = changeText
        this.type = type
    }

    constructor(changeText: String, @ChangelogType type: Int) {
        this.changeText = parseChangeText(changeText)
        this.type = type
    }

    companion object {
        const val HEADER = -1
        const val TITLE = 0
        const val NOTE = 1
        const val NEW = 2
        const val IMPROVE = 3
        const val FIX = 4

        const val TEXT_SMALL = 0
        const val TEXT_MEDIUM = 1
        const val TEXT_LARGE = 2

        @JvmStatic
        fun parseChangeText(changeText: String): CharSequence {
            // TODO: Supported markups **Bold**, __Italic__, `Monospace`, ~~Strikethrough~~, [Link](link_name)
            val processed = changeText.replace("[", "<").replace("]", ">")
            return HtmlCompat.fromHtml(processed, HtmlCompat.FROM_HTML_MODE_COMPACT)
        }
    }
}
