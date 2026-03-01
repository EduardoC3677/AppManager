// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm

import androidx.annotation.IntDef
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.misc.ListOptions
import java.util.*

class FmListOptions : ListOptions() {
    @IntDef(SORT_BY_NAME, SORT_BY_LAST_MODIFIED, SORT_BY_SIZE, SORT_BY_TYPE)
    @Retention(AnnotationRetention.SOURCE)
    annotation class SortOrder

    @IntDef(flag = true, value = [OPTIONS_DISPLAY_DOT_FILES, OPTIONS_FOLDERS_FIRST])
    @Retention(AnnotationRetention.SOURCE)
    annotation class Options

    override fun getSortIdLocaleMap(): LinkedHashMap<Int, Int>? = SORT_ITEMS_MAP

    override fun getFilterFlagLocaleMap(): LinkedHashMap<Int, Int>? = null

    override fun getOptionIdLocaleMap(): LinkedHashMap<Int, Int>? = OPTIONS_MAP

    companion object {
        val TAG: String = FmListOptions::class.java.simpleName

        const val SORT_BY_NAME = 0
        const val SORT_BY_LAST_MODIFIED = 1
        const val SORT_BY_SIZE = 2
        const val SORT_BY_TYPE = 3

        const val OPTIONS_DISPLAY_DOT_FILES = 1 shl 0
        const val OPTIONS_FOLDERS_FIRST = 1 shl 1
        const val OPTIONS_ONLY_FOR_THIS_FOLDER = 1 shl 2 // TODO: 11/12/22

        private val SORT_ITEMS_MAP = LinkedHashMap<Int, Int>().apply {
            put(SORT_BY_NAME, R.string.sort_by_filename)
            put(SORT_BY_LAST_MODIFIED, R.string.sort_by_last_modified)
            put(SORT_BY_SIZE, R.string.sort_by_file_size)
            put(SORT_BY_TYPE, R.string.sort_by_file_type)
        }

        private val OPTIONS_MAP = LinkedHashMap<Int, Int>().apply {
            put(OPTIONS_DISPLAY_DOT_FILES, R.string.option_display_dot_files)
            put(OPTIONS_FOLDERS_FIRST, R.string.option_display_folders_on_top)
        }
    }
}
