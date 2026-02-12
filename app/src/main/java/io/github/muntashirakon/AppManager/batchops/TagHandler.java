// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.muntashirakon.AppManager.db.entity.App;

/**
 * Utility for managing tags/groups on apps.
 */
public class TagHandler {

    /**
     * Adds a tag to an app's existing tags.
     */
    public static void addTag(@NonNull App app, @NonNull String newTag) {
        Set<String> tags = getTagSet(app.tags);
        tags.add(newTag.trim());
        app.tags = serializeTags(tags);
    }

    /**
     * Removes a tag from an app.
     */
    public static void removeTag(@NonNull App app, @NonNull String tagToRemove) {
        Set<String> tags = getTagSet(app.tags);
        tags.remove(tagToRemove.trim());
        app.tags = serializeTags(tags);
    }

    /**
     * Checks if an app has a specific tag.
     */
    public static boolean hasTag(@NonNull App app, @NonNull String tag) {
        if (app.tags == null) return false;
        Set<String> tags = getTagSet(app.tags);
        return tags.contains(tag.trim());
    }

    @NonNull
    private static Set<String> getTagSet(@Nullable String tagsString) {
        if (tagsString == null || tagsString.isEmpty()) {
            return new HashSet<>();
        }
        return new HashSet<>(Arrays.asList(tagsString.split(",")));
    }

    @Nullable
    private static String serializeTags(@NonNull Set<String> tags) {
        if (tags.isEmpty()) return null;
        return String.join(",", tags);
    }

    /**
     * Filters apps by a specific tag.
     */
    @NonNull
    public static List<App> filterByTag(@NonNull List<App> apps, @NonNull String tag) {
        List<App> filtered = new ArrayList<>();
        for (App app : apps) {
            if (hasTag(app, tag)) {
                filtered.add(app);
            }
        }
        return filtered;
    }
}
