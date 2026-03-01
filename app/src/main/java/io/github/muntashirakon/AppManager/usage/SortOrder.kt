// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.usage

import androidx.annotation.IntDef

@IntDef(
    SortOrder.SORT_BY_APP_LABEL,
    SortOrder.SORT_BY_LAST_USED,
    SortOrder.SORT_BY_MOBILE_DATA,
    SortOrder.SORT_BY_PACKAGE_NAME,
    SortOrder.SORT_BY_SCREEN_TIME,
    SortOrder.SORT_BY_TIMES_OPENED,
    SortOrder.SORT_BY_WIFI_DATA
)
@Retention(AnnotationRetention.SOURCE)
annotation class SortOrder {
    companion object {
        const val SORT_BY_APP_LABEL = 0
        const val SORT_BY_LAST_USED = 1
        const val SORT_BY_MOBILE_DATA = 2
        const val SORT_BY_PACKAGE_NAME = 3
        const val SORT_BY_SCREEN_TIME = 4
        const val SORT_BY_TIMES_OPENED = 5
        const val SORT_BY_WIFI_DATA = 6
    }
}
