// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops

import io.github.muntashirakon.AppManager.main.ApplicationItem
import java.util.concurrent.TimeUnit

object SuggestionHandler {
    private const val UNUSED_DAYS_THRESHOLD = 30
    private const val OPEN_COUNT_THRESHOLD = 5

    @JvmStatic
    fun getApplicationItemSuggestions(items: List<ApplicationItem>): List<ApplicationItem> {
        val now = System.currentTimeMillis()
        val thresholdMillis = TimeUnit.DAYS.toMillis(UNUSED_DAYS_THRESHOLD.toLong())
        
        return items.filter { item ->
            item.isInstalled && !item.isSystem && 
            item.openCount < OPEN_COUNT_THRESHOLD &&
            (item.lastUsageTime > 0 && (now - item.lastUsageTime) > thresholdMillis)
        }.sortedBy { it.lastUsageTime }
    }
}
