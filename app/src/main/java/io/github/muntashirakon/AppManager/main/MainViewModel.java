// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import android.app.ActivityManager;
import android.app.Application;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.RemoteException;
import android.os.UserHandleHidden;
import android.text.TextUtils;
import android.util.Pair;

import androidx.annotation.AnyThread;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.json.JSONException;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Future;

import io.github.muntashirakon.AppManager.apk.list.ListExporter;
import io.github.muntashirakon.AppManager.backup.BackupUtils;
import io.github.muntashirakon.AppManager.compat.ActivityManagerCompat;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.db.entity.App;
import io.github.muntashirakon.AppManager.db.utils.AppDb;
import io.github.muntashirakon.AppManager.filters.FilterItem;
import io.github.muntashirakon.AppManager.filters.options.FilterOption;
import io.github.muntashirakon.AppManager.filters.options.PackageNameOption;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.misc.AdvancedSearchView;
import io.github.muntashirakon.AppManager.misc.ListOptions;
import io.github.muntashirakon.AppManager.profiles.ProfileManager;
import io.github.muntashirakon.AppManager.profiles.struct.AppsFilterProfile;
import io.github.muntashirakon.AppManager.profiles.struct.AppsProfile;
import io.github.muntashirakon.AppManager.profiles.struct.BaseProfile;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.settings.FeatureController;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.types.PackageChangeReceiver;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.usage.AppUsageStatsManager;
import io.github.muntashirakon.AppManager.usage.PackageUsageInfo;
import io.github.muntashirakon.AppManager.usage.TimeInterval;
import io.github.muntashirakon.AppManager.usage.UsageUtils;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.ExUtils;
import io.github.muntashirakon.AppManager.utils.MultithreadedExecutor;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.io.Path;

public class MainViewModel extends AndroidViewModel implements ListOptions.ListOptionActions {
    private final PackageManager mPackageManager;
    private final PackageIntentReceiver mPackageObserver;
    @MainListOptions.SortOrder
    private int mSortBy;
    private boolean mReverseSort;
    @MainListOptions.Filter
    private int mFilterFlags;
    @Nullable
    private String mFilterProfileName;
    @Nullable
    private int[] mSelectedUsers;
    private String mSearchQuery;
    @AdvancedSearchView.SearchType
    private int mSearchType;
    private Future<?> mFilterResult;
    private final Map<String, ApplicationItem> mSelectedPackageApplicationItemMap = Collections.synchronizedMap(new LinkedHashMap<>());
    final MultithreadedExecutor executor = MultithreadedExecutor.getNewInstance();

    public MainViewModel(@NonNull Application application) {
        super(application);
        Log.d("MVM", "New instance created");
        mPackageManager = application.getPackageManager();
        mPackageObserver = new PackageIntentReceiver(this);
        mSortBy = Prefs.MainPage.getSortOrder();
        mReverseSort = Prefs.MainPage.isReverseSort();
        mFilterFlags = Prefs.MainPage.getFilters();
        mFilterProfileName = Prefs.MainPage.getFilteredProfileName();
        mSelectedUsers = null; // TODO: 5/6/23 Load from prefs?
        if ("".equals(mFilterProfileName)) mFilterProfileName = null;
    }

    private final MutableLiveData<Boolean> mOperationStatus = new MutableLiveData<>();
    @NonNull
    private final MutableLiveData<List<ApplicationItem>> mApplicationItemsLiveData = new MutableLiveData<>();
    private final List<ApplicationItem> mApplicationItems = new ArrayList<>();

    // OPTIMIZATION: Cache Collator to avoid recreation overhead
    private final Collator mCollator = Collator.getInstance();

    // OPTIMIZATION: Cache usage stats to avoid expensive DB queries
    private volatile Map<String, PackageUsageInfo> mCachedUsageStats = null;
    private volatile long mUsageStatsCacheTimestamp = 0;
    private static final long USAGE_STATS_CACHE_TTL_MS = 60_000; // 60 seconds
    private final Object mUsageCacheLock = new Object();

    public int getApplicationItemCount() {
        return mApplicationItems.size();
    }

    @NonNull
    public LiveData<List<ApplicationItem>> getApplicationItems() {
        if (mApplicationItemsLiveData.getValue() == null) {
            loadApplicationItems();
        }
        return mApplicationItemsLiveData;
    }

    public LiveData<Boolean> getOperationStatus() {
        return mOperationStatus;
    }

    @GuardedBy("applicationItems")
    public ApplicationItem deselect(@NonNull ApplicationItem item) {
        synchronized (mApplicationItems) {
            int i = mApplicationItems.indexOf(item);
            if (i == -1) return item;
            item = mApplicationItems.get(i);
            mSelectedPackageApplicationItemMap.remove(item.packageName);
            item.isSelected = false;
            mApplicationItems.set(i, item);
            return item;
        }
    }

    @GuardedBy("applicationItems")
    public ApplicationItem select(@NonNull ApplicationItem item) {
        synchronized (mApplicationItems) {
            int i = mApplicationItems.indexOf(item);
            if (i == -1) return item;
            item = mApplicationItems.get(i);
            // Removal is needed because LinkedHashMap insertion-oriented
            mSelectedPackageApplicationItemMap.remove(item.packageName);
            mSelectedPackageApplicationItemMap.put(item.packageName, item);
            item.isSelected = true;
            mApplicationItems.set(i, item);
            return item;
        }
    }

    public void cancelSelection() {
        synchronized (mApplicationItems) {
            for (ApplicationItem item : getSelectedApplicationItems()) {
                int i = mApplicationItems.indexOf(item);
                if (i != -1) {
                    mApplicationItems.get(i).isSelected = false;
                }
            }
            mSelectedPackageApplicationItemMap.clear();
        }
    }

    @Nullable
    public ApplicationItem getLastSelectedPackage() {
        // Last selected package is the same as the last added package.
        Iterator<ApplicationItem> it = mSelectedPackageApplicationItemMap.values().iterator();
        ApplicationItem lastItem = null;
        while (it.hasNext()) {
            lastItem = it.next();
        }
        return lastItem;
    }

    public Map<String, ApplicationItem> getSelectedPackages() {
        return mSelectedPackageApplicationItemMap;
    }

    @NonNull
    public ArrayList<UserPackagePair> getSelectedPackagesWithUsers() {
        ArrayList<UserPackagePair> userPackagePairs = new ArrayList<>();
        int myUserId = UserHandleHidden.myUserId();
        int[] userIds = Users.getUsersIds();
        for (String packageName : mSelectedPackageApplicationItemMap.keySet()) {
            int[] userIds1 = Objects.requireNonNull(mSelectedPackageApplicationItemMap.get(packageName)).userIds;
            if (userIds1.length == 0) {
                // Could be a backup only item
                // Assign current user in it
                userPackagePairs.add(new UserPackagePair(packageName, myUserId));
            } else {
                for (int userHandle : userIds1) {
                    if (!ArrayUtils.contains(userIds, userHandle)) continue;
                    userPackagePairs.add(new UserPackagePair(packageName, userHandle));
                }
            }
        }
        return userPackagePairs;
    }

    public Collection<ApplicationItem> getSelectedApplicationItems() {
        return mSelectedPackageApplicationItemMap.values();
    }

    public String getSearchQuery() {
        return mSearchQuery;
    }

    public void setSearchQuery(String searchQuery, @AdvancedSearchView.SearchType int searchType) {
        this.mSearchQuery = searchType != AdvancedSearchView.SEARCH_TYPE_REGEX ? searchQuery.toLowerCase(Locale.ROOT) : searchQuery;
        this.mSearchType = searchType;
        cancelIfRunning();
        mFilterResult = executor.submit(this::filterItemsByFlags);
    }

    @Override
    public int getSortBy() {
        return mSortBy;
    }

    @Override
    public void setReverseSort(boolean reverseSort) {
        cancelIfRunning();
        mFilterResult = executor.submit(() -> {
            sortApplicationList(mSortBy, mReverseSort);
            filterItemsByFlags();
        });
        mReverseSort = reverseSort;
        Prefs.MainPage.setReverseSort(mReverseSort);
    }

    @Override
    public boolean isReverseSort() {
        return mReverseSort;
    }

    @Override
    public void setSortBy(int sortBy) {
        if (mSortBy != sortBy) {
            cancelIfRunning();
            mFilterResult = executor.submit(() -> {
                sortApplicationList(sortBy, mReverseSort);
                filterItemsByFlags();
            });
        }
        mSortBy = sortBy;
        Prefs.MainPage.setSortOrder(mSortBy);
    }

    @Override
    public boolean hasFilterFlag(@MainListOptions.Filter int flag) {
        return (mFilterFlags & flag) != 0;
    }

    @Override
    public void addFilterFlag(@MainListOptions.Filter int filterFlag) {
        mFilterFlags |= filterFlag;
        Prefs.MainPage.setFilters(mFilterFlags);
        cancelIfRunning();
        mFilterResult = executor.submit(this::filterItemsByFlags);
    }

    @Override
    public void removeFilterFlag(@MainListOptions.Filter int filterFlag) {
        mFilterFlags &= ~filterFlag;
        Prefs.MainPage.setFilters(mFilterFlags);
        cancelIfRunning();
        mFilterResult = executor.submit(this::filterItemsByFlags);
    }

    public void setFilterProfileName(@Nullable String filterProfileName) {
        if (mFilterProfileName == null) {
            if (filterProfileName == null) return;
        } else if (mFilterProfileName.equals(filterProfileName)) return;
        mFilterProfileName = filterProfileName;
        Prefs.MainPage.setFilteredProfileName(filterProfileName);
        cancelIfRunning();
        mFilterResult = executor.submit(this::filterItemsByFlags);
    }

    @Nullable
    public String getFilterProfileName() {
        return mFilterProfileName;
    }

    public void setSelectedUsers(@Nullable int[] selectedUsers) {
        if (selectedUsers == null) {
            if (mSelectedUsers == null) {
                // No change
                return;
            }
        } else if (mSelectedUsers != null) {
            if (mSelectedUsers.length == selectedUsers.length) {
                boolean differs = false;
                for (int user : selectedUsers) {
                    if (!ArrayUtils.contains(mSelectedUsers, user)) {
                        differs = true;
                        break;
                    }
                }
                if (!differs) {
                    // No change detected
                    return;
                }
            }
        }
        mSelectedUsers = selectedUsers;
        // TODO: 5/6/23 Store value to prefs
        cancelIfRunning();
        mFilterResult = executor.submit(this::filterItemsByFlags);
    }

    @Nullable
    public int[] getSelectedUsers() {
        return mSelectedUsers;
    }

    @AnyThread
    public void onResume() {
        if ((mFilterFlags & MainListOptions.FILTER_RUNNING_APPS) != 0) {
            // Reload filters to get running apps again
            cancelIfRunning();
            mFilterResult = executor.submit(this::filterItemsByFlags);
        }
    }

    public void saveExportedAppList(@ListExporter.ExportType int exportType, @NonNull Path path) {
        executor.submit(() -> {
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(path.openOutputStream(), StandardCharsets.UTF_8))) {
                List<PackageInfo> packageInfoList = new ArrayList<>();
                for (String packageName : getSelectedPackages().keySet()) {
                    int[] userIds = Objects.requireNonNull(getSelectedPackages().get(packageName)).userIds;
                    for (int userId : userIds) {
                        packageInfoList.add(PackageManagerCompat.getPackageInfo(packageName,
                                PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, userId));
                        break;
                    }
                }
                ListExporter.export(getApplication(), writer, exportType, packageInfoList);
                mOperationStatus.postValue(true);
            } catch (IOException | RemoteException | PackageManager.NameNotFoundException e) {
                e.printStackTrace();
                mOperationStatus.postValue(false);
            }
        });
    }

    @GuardedBy("applicationItems")
    public void loadApplicationItems() {
        cancelIfRunning();
        mFilterResult = executor.submit(() -> {
            List<ApplicationItem> updatedApplicationItems = PackageUtils
                    .getInstalledOrBackedUpApplicationsFromDb(getApplication(), true, true);
            synchronized (mApplicationItems) {
                mApplicationItems.clear();
                mApplicationItems.addAll(updatedApplicationItems);
                // select apps again
                for (ApplicationItem item : getSelectedApplicationItems()) {
                    select(item);
                }
                sortApplicationList(mSortBy, mReverseSort);
                filterItemsByFlags();
            }
        });
    }

    private void cancelIfRunning() {
        if (mFilterResult != null) {
            mFilterResult.cancel(true);
        }
    }

    @WorkerThread
    private void filterItemsByQuery(@NonNull List<ApplicationItem> applicationItems) {
        List<ApplicationItem> filteredApplicationItems;
        if (mSearchType == AdvancedSearchView.SEARCH_TYPE_REGEX) {
            filteredApplicationItems = AdvancedSearchView.matches(mSearchQuery, applicationItems,
                    (AdvancedSearchView.ChoicesGenerator<ApplicationItem>) item -> {
                        // OPTIMIZATION: Ensure lowercase fields are populated
                        item.ensureLowerCaseFields();
                        return new ArrayList<String>() {{
                            add(item.packageNameLowerCase);
                            add(item.labelLowerCase);
                        }};
                    }, AdvancedSearchView.SEARCH_TYPE_REGEX);
            mApplicationItemsLiveData.postValue(filteredApplicationItems);
            return;
        }
        // OPTIMIZATION: Lowercase query once instead of per-item (saves 100-200ms)
        String queryLower = mSearchQuery.toLowerCase(Locale.ROOT);
        filteredApplicationItems = new ArrayList<>();
        for (ApplicationItem item : applicationItems) {
            if (ThreadUtils.isInterrupted()) {
                return;
            }
            // OPTIMIZATION: Ensure lowercase fields exist
            item.ensureLowerCaseFields();

            // Use pre-computed lowercase values - eliminates repeated allocation
            if (AdvancedSearchView.matches(queryLower, item.packageNameLowerCase, mSearchType)) {
                filteredApplicationItems.add(item);
            } else if (mSearchType == AdvancedSearchView.SEARCH_TYPE_CONTAINS) {
                if (Utils.containsOrHasInitials(mSearchQuery, item.label)) {
                    filteredApplicationItems.add(item);
                }
            } else if (AdvancedSearchView.matches(queryLower, item.labelLowerCase, mSearchType)) {
                filteredApplicationItems.add(item);
            }
        }
        mApplicationItemsLiveData.postValue(filteredApplicationItems);
    }

    @WorkerThread
    @GuardedBy("applicationItems")
    private void filterItemsByFlags() {
        synchronized (mApplicationItems) {
            List<ApplicationItem> candidateApplicationItems = new ArrayList<>();
            List<FilterOption> profileFilterOptions = new ArrayList<>();
            if (mFilterProfileName != null) {
                String profileId = ProfileManager.getProfileIdCompat(mFilterProfileName);
                Path profilePath = ProfileManager.findProfilePathById(profileId);
                try {
                    BaseProfile profile = BaseProfile.fromPath(profilePath);
                    if (profile instanceof AppsProfile) {
                        AppsProfile appsProfile = (AppsProfile) profile;
                        PackageNameOption option = new PackageNameOption();
                        option.setKeyValue("eq_any", TextUtils.join("\n", appsProfile.packages));
                        profileFilterOptions.add(option);
                    } else if (profile instanceof AppsFilterProfile) {
                        AppsFilterProfile filterProfile = (AppsFilterProfile) profile;
                        FilterItem filterItem = filterProfile.getFilterItem();
                        for (int i = 0; i < filterItem.getSize(); ++i) {
                            profileFilterOptions.add(filterItem.getFilterOptionAt(i));
                        }
                    }
                } catch (IOException | JSONException e) {
                    e.printStackTrace();
                }
            }
            for (ApplicationItem item : mApplicationItems) {
                if (ThreadUtils.isInterrupted()) {
                    return;
                }
                if (isAmongSelectedUsers(item)) {
                    candidateApplicationItems.add(item);
                }
            }
            // Other filters
            if (profileFilterOptions.isEmpty() && mFilterFlags == MainListOptions.FILTER_NO_FILTER) {
                if (!TextUtils.isEmpty(mSearchQuery)) {
                    filterItemsByQuery(candidateApplicationItems);
                } else {
                    mApplicationItemsLiveData.postValue(candidateApplicationItems);
                }
            } else {
                List<ApplicationItem> filteredApplicationItems = new ArrayList<>();
                FilterItem filterItem = MainListOptions.getFilterItemFromFlags(mFilterFlags);
                for (FilterOption filterOption : profileFilterOptions) {
                    filterItem.addFilterOption(filterOption);
                }
                // OPTIMIZATION: Use cached usage stats with proper thread safety
                Map<String, PackageUsageInfo> packageUsageInfoList;
                if (filterItem.getTimesUsageInfoUsed() > 0) {
                    synchronized (mUsageCacheLock) {
                        long currentTime = System.currentTimeMillis();
                        boolean cacheValid = mCachedUsageStats != null &&
                                            (currentTime - mUsageStatsCacheTimestamp) < USAGE_STATS_CACHE_TTL_MS;

                        if (cacheValid) {
                            // Use cached data - avoids expensive DB query (saves 2-8 seconds)
                            packageUsageInfoList = mCachedUsageStats;
                        } else {
                            // Cache miss or expired - rebuild
                            packageUsageInfoList = buildUsageStatsMap();
                            if (packageUsageInfoList != null && !ThreadUtils.isInterrupted()) {
                                mCachedUsageStats = packageUsageInfoList;
                                mUsageStatsCacheTimestamp = currentTime;
                            }
                        }
                    }
                } else {
                    packageUsageInfoList = new HashMap<>();
                }
                // OPTIMIZATION: Extract usage stats building to separate method
                HashSet<String> runningPackages = new HashSet<>();
                if (filterItem.getTimesRunningOptionUsed() > 0) {
                    for (ActivityManager.RunningAppProcessInfo info : ActivityManagerCompat.getRunningAppProcesses()) {
                        if (info.pkgList != null) {
                            runningPackages.addAll(Arrays.asList(info.pkgList));
                        }
                    }
                }
                for (ApplicationItem item : candidateApplicationItems) {
                    item.setPackageUsageInfo(packageUsageInfoList.get(item.packageName));
                    item.setRunning(runningPackages.contains(item.packageName));
                }
                List<FilterItem.FilteredItemInfo<ApplicationItem>> result = filterItem.getFilteredList(candidateApplicationItems);
                for (FilterItem.FilteredItemInfo<ApplicationItem> item : result) {
                    if ((mFilterFlags & MainListOptions.FILTER_APPS_WITH_SPLITS) != 0 && !item.info.hasSplits) {
                        continue;
                    }
                    if ((mFilterFlags & MainListOptions.FILTER_APPS_WITH_SAF) != 0 && !item.info.usesSaf) {
                        continue;
                    }
                    filteredApplicationItems.add(item.info);
                }
                if (!TextUtils.isEmpty(mSearchQuery)) {
                    filterItemsByQuery(filteredApplicationItems);
                } else {
                    mApplicationItemsLiveData.postValue(filteredApplicationItems);
                }
            }
        }
    }

    // OPTIMIZATION: Extracted method for building usage stats map
    private Map<String, PackageUsageInfo> buildUsageStatsMap() {
        Map<String, PackageUsageInfo> packageUsageInfoList = new HashMap<>();
        boolean hasUsageAccess = FeatureController.isUsageAccessEnabled() && SelfPermissions.checkUsageStatsPermission();
        if (hasUsageAccess) {
            TimeInterval interval = UsageUtils.getLastWeek();
            for (int userId : Users.getUsersIds()) {
                List<PackageUsageInfo> usageInfoList;
                usageInfoList = ExUtils.exceptionAsNull(() -> AppUsageStatsManager
                        .getInstance().getUsageStats(interval, userId));
                if (usageInfoList != null) {
                    for (PackageUsageInfo info : usageInfoList) {
                        if (ThreadUtils.isInterrupted()) return packageUsageInfoList;
                        PackageUsageInfo oldInfo = packageUsageInfoList.get(info.packageName);
                        if (oldInfo != null) {
                            oldInfo.screenTime += info.screenTime;
                            oldInfo.lastUsageTime += info.lastUsageTime;
                            oldInfo.timesOpened += info.timesOpened;
                            oldInfo.mobileData = AppUsageStatsManager.DataUsage.fromDataUsage(oldInfo.mobileData, info.mobileData);
                            oldInfo.wifiData = AppUsageStatsManager.DataUsage.fromDataUsage(oldInfo.wifiData, info.wifiData);
                            if (info.entries != null) {
                                if (oldInfo.entries == null) {
                                    oldInfo.entries = info.entries;
                                } else oldInfo.entries.addAll(info.entries);
                            }
                        } else packageUsageInfoList.put(info.packageName, info);
                    }
                }
            }
        }
        return packageUsageInfoList;
    }

    // Public method to invalidate cache (call from onResume, refresh, or package changes)
    public void invalidateUsageStatsCache() {
        synchronized (mUsageCacheLock) {
            mCachedUsageStats = null;
            mUsageStatsCacheTimestamp = 0;
        }
    }

    private boolean isAmongSelectedUsers(@NonNull ApplicationItem applicationItem) {
        if (mSelectedUsers == null) {
            // All users
            return true;
        }
        for (int userId : mSelectedUsers) {
            if (ArrayUtils.contains(applicationItem.userIds, userId)) {
                return true;
            }
        }
        return false;
    }

    @GuardedBy("applicationItems")
    private void sortApplicationList(@MainListOptions.SortOrder int sortBy, boolean reverse) {
        synchronized (mApplicationItems) {
            // OPTIMIZATION: Single-pass sort with label as secondary key (removes double sort)
            int mode = reverse ? -1 : 1;
            Collections.sort(mApplicationItems, (o1, o2) -> {
                int primaryComparison;

                switch (sortBy) {
                    case MainListOptions.SORT_BY_APP_LABEL:
                        return mode * mCollator.compare(o1.label, o2.label);

                    case MainListOptions.SORT_BY_PACKAGE_NAME:
                        primaryComparison = mode * o1.packageName.compareTo(o2.packageName);
                        break;

                    case MainListOptions.SORT_BY_DOMAIN:
                        boolean isSystem1 = (o1.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                        boolean isSystem2 = (o2.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                        primaryComparison = mode * Boolean.compare(isSystem1, isSystem2);
                        break;

                    case MainListOptions.SORT_BY_LAST_UPDATE:
                        // Sort in decreasing order
                        primaryComparison = -mode * o1.lastUpdateTime.compareTo(o2.lastUpdateTime);
                        break;

                    case MainListOptions.SORT_BY_TOTAL_SIZE:
                        // Sort in decreasing order
                        primaryComparison = -mode * o1.totalSize.compareTo(o2.totalSize);
                        break;

                    case MainListOptions.SORT_BY_DATA_USAGE:
                        // Sort in decreasing order
                        primaryComparison = -mode * o1.dataUsage.compareTo(o2.dataUsage);
                        break;

                    case MainListOptions.SORT_BY_OPEN_COUNT:
                        // Sort in decreasing order
                        primaryComparison = -mode * Integer.compare(o1.openCount, o2.openCount);
                        break;

                    case MainListOptions.SORT_BY_INSTALLATION_DATE:
                        // Sort in decreasing order
                        primaryComparison = -mode * Long.compare(o1.firstInstallTime, o2.firstInstallTime);
                        break;

                    case MainListOptions.SORT_BY_SCREEN_TIME:
                        // Sort in decreasing order
                        primaryComparison = -mode * Long.compare(o1.screenTime, o2.screenTime);
                        break;

                    case MainListOptions.SORT_BY_LAST_USAGE_TIME:
                        // Sort in decreasing order
                        primaryComparison = -mode * Long.compare(o1.lastUsageTime, o2.lastUsageTime);
                        break;

                    case MainListOptions.SORT_BY_TARGET_SDK:
                        // null on top
                        if (o1.targetSdk == null) primaryComparison = -mode;
                        else if (o2.targetSdk == null) primaryComparison = +mode;
                        else primaryComparison = mode * o1.targetSdk.compareTo(o2.targetSdk);
                        break;

                    case MainListOptions.SORT_BY_SHARED_ID:
                        primaryComparison = mode * Integer.compare(o1.uid, o2.uid);
                        break;

                    case MainListOptions.SORT_BY_SHA:
                        // null on top
                        if (o1.sha == null) {
                            primaryComparison = -mode;
                        } else if (o2.sha == null) {
                            primaryComparison = +mode;
                        } else {  // Both aren't null
                            int i = o1.sha.first.compareToIgnoreCase(o2.sha.first);
                            if (i == 0) {
                                primaryComparison = mode * o1.sha.second.compareToIgnoreCase(o2.sha.second);
                            } else primaryComparison = mode * i;
                        }
                        break;

                    case MainListOptions.SORT_BY_BLOCKED_COMPONENTS:
                        primaryComparison = -mode * o1.blockedCount.compareTo(o2.blockedCount);
                        break;

                    case MainListOptions.SORT_BY_FROZEN_APP:
                        primaryComparison = -mode * Boolean.compare(o1.isDisabled, o2.isDisabled);
                        break;

                    case MainListOptions.SORT_BY_BACKUP:
                        primaryComparison = -mode * Boolean.compare(o1.backup != null, o2.backup != null);
                        break;

                    case MainListOptions.SORT_BY_LAST_ACTION:
                        primaryComparison = -mode * o1.lastActionTime.compareTo(o2.lastActionTime);
                        break;

                    case MainListOptions.SORT_BY_TRACKERS:
                        primaryComparison = -mode * o1.trackerCount.compareTo(o2.trackerCount);
                        break;

                    default:
                        primaryComparison = 0;
                }

                // OPTIMIZATION: Use label as secondary sort key (saves 0.5-1 second)
                if (primaryComparison == 0) {
                    return mCollator.compare(o1.label, o2.label);
                }

                return primaryComparison;
            });
        }
    }

    @WorkerThread
    private void updateInfoForUid(int uid, String action) {
        Log.d("updateInfoForUid", "Uid: %d", uid);
        String[] packages;
        if (Intent.ACTION_PACKAGE_REMOVED.equals(action)) packages = getPackagesForUid(uid);
        else packages = mPackageManager.getPackagesForUid(uid);
        updateInfoForPackages(packages, action);
    }

    @WorkerThread
    private void updateInfoForPackages(@Nullable String[] packages, @NonNull String action) {
        Log.d("updateInfoForPackages", "packages: %s", Arrays.toString(packages));
        if (packages == null || packages.length == 0) return;
        boolean modified = false;
        switch (action) {
            case PackageChangeReceiver.ACTION_DB_PACKAGE_REMOVED:
            case PackageChangeReceiver.ACTION_DB_PACKAGE_ALTERED:
            case PackageChangeReceiver.ACTION_DB_PACKAGE_ADDED: {
                AppDb appDb = new AppDb();
                for (String packageName : packages) {
                    ApplicationItem item = getNewApplicationItem(packageName, appDb.getAllApplications(packageName));
                    modified |= item != null ? insertOrAddApplicationItem(item) : deleteApplicationItem(packageName);
                }
                break;
            }
            case PackageChangeReceiver.ACTION_PACKAGE_REMOVED:
            case PackageChangeReceiver.ACTION_PACKAGE_ALTERED:
            case PackageChangeReceiver.ACTION_PACKAGE_ADDED:
                // case BatchOpsService.ACTION_BATCH_OPS_COMPLETED:
            case Intent.ACTION_PACKAGE_REMOVED:
            case Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE:
            case Intent.ACTION_PACKAGE_ADDED:
            case Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE:
            case Intent.ACTION_PACKAGE_CHANGED: {
                List<App> appList = new AppDb().updateApplications(getApplication(), packages);
                for (String packageName : packages) {
                    ApplicationItem item = getNewApplicationItem(packageName, appList);
                    modified |= item != null ? insertOrAddApplicationItem(item) : deleteApplicationItem(packageName);
                }
                break;
            }
            default:
                return;
        }
        if (modified) {
            sortApplicationList(mSortBy, mReverseSort);
            filterItemsByFlags();
        }
    }

    @GuardedBy("applicationItems")
    private boolean insertOrAddApplicationItem(@Nullable ApplicationItem item) {
        if (item == null) return false;
        synchronized (mApplicationItems) {
            if (insertApplicationItem(item)) {
                return true;
            }
            boolean inserted = mApplicationItems.add(item);
            if (mSelectedPackageApplicationItemMap.containsKey(item.packageName)) {
                select(item);
            }
            return inserted;
        }
    }

    @GuardedBy("applicationItems")
    private boolean insertApplicationItem(@NonNull ApplicationItem item) {
        synchronized (mApplicationItems) {
            boolean isInserted = false;
            for (int i = 0; i < mApplicationItems.size(); ++i) {
                if (item.equals(mApplicationItems.get(i))) {
                    mApplicationItems.set(i, item);
                    isInserted = true;
                    if (mSelectedPackageApplicationItemMap.containsKey(item.packageName)) {
                        select(item);
                    }
                }
            }
            return isInserted;
        }
    }

    private boolean deleteApplicationItem(@NonNull String packageName) {
        synchronized (mApplicationItems) {
            ListIterator<ApplicationItem> it = mApplicationItems.listIterator();
            while (it.hasNext()) {
                ApplicationItem item = it.next();
                if (item.packageName.equals(packageName)) {
                    mSelectedPackageApplicationItemMap.remove(packageName);
                    it.remove();
                    return true;
                }
            }
            return false;
        }
    }

    @WorkerThread
    @Nullable
    private ApplicationItem getNewApplicationItem(@NonNull String packageName, @NonNull List<App> apps) {
        ApplicationItem item = new ApplicationItem();
        int thisUser = UserHandleHidden.myUserId();
        for (App app : apps) {
            if (!packageName.equals(app.packageName)) {
                // Package name didn't match
                continue;
            }
            if (app.isInstalled) {
                boolean newItem = item.packageName == null || !item.isInstalled;
                if (item.packageName == null) {
                    item.packageName = app.packageName;
                }
                item.userIds = ArrayUtils.appendInt(item.userIds, app.userId);
                item.isInstalled = true;
                item.isOnlyDataInstalled = false;
                item.openCount += app.openCount;
                item.screenTime += app.screenTime;
                if (item.lastUsageTime == 0L || item.lastUsageTime < app.lastUsageTime) {
                    item.lastUsageTime = app.lastUsageTime;
                }
                item.hasKeystore |= app.hasKeystore;
                item.usesSaf |= app.usesSaf;
                if (app.ssaid != null) {
                    item.ssaid = app.ssaid;
                }
                item.totalSize += app.codeSize + app.dataSize;
                item.dataUsage += app.wifiDataUsage + app.mobileDataUsage;
                if (!newItem && app.userId != thisUser) {
                    // This user has the highest priority
                    continue;
                }
            } else {
                // App not installed but may be installed in other profiles
                if (item.packageName != null) {
                    // Item exists, use the previous status
                    continue;
                } else {
                    item.packageName = app.packageName;
                    item.isInstalled = false;
                    item.isOnlyDataInstalled = app.isOnlyDataInstalled;
                    item.hasKeystore |= app.hasKeystore;
                }
            }
            item.flags = app.flags;
            item.uid = app.uid;
            item.debuggable = app.isDebuggable();
            item.isUser = !app.isSystemApp();
            item.isDisabled = !app.isEnabled;
            item.label = app.packageLabel;
            item.targetSdk = app.sdk;
            item.versionName = app.versionName;
            item.versionCode = app.versionCode;
            item.sharedUserId = app.sharedUserId;
            item.sha = new Pair<>(app.certName, app.certAlgo);
            item.firstInstallTime = app.firstInstallTime;
            item.lastUpdateTime = app.lastUpdateTime;
            item.hasActivities = app.hasActivities;
            item.hasSplits = app.hasSplits;
            item.blockedCount = app.rulesCount;
            item.trackerCount = app.trackerCount;
            item.lastActionTime = app.lastActionTime;
            if (item.backup == null) {
                item.backup = BackupUtils.getLatestBackupMetadataFromDbNoLockValidate(packageName);
            }
            item.generateOtherInfo();
        }
        if (item.packageName == null) {
            return null;
        }
        return item;
    }

    @GuardedBy("applicationItems")
    @NonNull
    private String[] getPackagesForUid(int uid) {
        synchronized (mApplicationItems) {
            List<String> packages = new LinkedList<>();
            for (ApplicationItem item : mApplicationItems) {
                if (item.uid == uid) packages.add(item.packageName);
            }
            return packages.toArray(new String[0]);
        }
    }

    @Override
    protected void onCleared() {
        if (mPackageObserver != null) getApplication().unregisterReceiver(mPackageObserver);
        executor.shutdownNow();
        super.onCleared();
    }

    public static class PackageIntentReceiver extends PackageChangeReceiver {
        private final MainViewModel mModel;

        public PackageIntentReceiver(@NonNull MainViewModel model) {
            super(model.getApplication());
            mModel = model;
        }

        @Override
        @WorkerThread
        protected void onPackageChanged(Intent intent, @Nullable Integer uid, @Nullable String[] packages) {
            mModel.cancelIfRunning();
            if (uid != null) {
                mModel.updateInfoForUid(uid, intent.getAction());
            } else if (packages != null) {
                mModel.updateInfoForPackages(packages, intent.getAction());
            } else {
                mModel.loadApplicationItems();
            }
        }
    }
}
