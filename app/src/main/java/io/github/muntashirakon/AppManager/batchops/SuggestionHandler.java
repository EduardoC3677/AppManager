// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.db.entity.App;
import io.github.muntashirakon.AppManager.main.ApplicationItem;

/**
 * Logic for suggesting apps for archiving based on usage patterns.
 */
public class SuggestionHandler {
    // Thresholds for "infrequent use"
    private static final long INFREQUENT_TIME_THRESHOLD_MS = TimeUnit.DAYS.toMillis(30);
    private static final int LOW_OPEN_COUNT_THRESHOLD = 5;

    /**
     * Filters a list of apps and returns those suggested for archiving.
     */
    @NonNull
    public static List<App> getArchivingSuggestions(@NonNull List<App> allApps) {
        List<App> suggestions = new ArrayList<>();
        long now = System.currentTimeMillis();

        for (App app : allApps) {
            if (app.isSystemApp()) continue;
            if (!app.isInstalled) continue;

            boolean isInfrequentlyUsed = (now - app.lastUsageTime) > INFREQUENT_TIME_THRESHOLD_MS;
            boolean hasLowUsageCount = app.openCount < LOW_OPEN_COUNT_THRESHOLD;

            if (isInfrequentlyUsed && hasLowUsageCount) {
                suggestions.add(app);
            }
        }
        return suggestions;
    }

    /**
     * Filters a list of ApplicationItems and returns those suggested for archiving.
     */
    @NonNull
    public static List<ApplicationItem> getApplicationItemSuggestions(@NonNull List<ApplicationItem> allItems) {
        List<ApplicationItem> suggestions = new ArrayList<>();
        long now = System.currentTimeMillis();

        for (ApplicationItem item : allItems) {
            if (item.isSystem) continue;
            if (!item.isInstalled) continue;

            boolean isInfrequentlyUsed = (now - item.lastUsageTime) > INFREQUENT_TIME_THRESHOLD_MS;
            boolean hasLowUsageCount = item.openCount < LOW_OPEN_COUNT_THRESHOLD;

            if (isInfrequentlyUsed && hasLowUsageCount) {
                suggestions.add(item);
            }
        }
        return suggestions;
    }
}
