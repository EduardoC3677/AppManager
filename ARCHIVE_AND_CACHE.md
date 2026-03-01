# 📦 App Archiving & Storage Management

## Overview

AppManager provides **separate, modular operations** for storage management:

1. **App Archiving** - Preserve APKs while removing apps
2. **Cache Cleaning** - Clear temporary files to free space
3. **Data Cleaning** - Clear app data for maximum space savings

Each operation is **independent** and can be used separately or together based on your needs.

---

## 🗄️ App Archiving

### What It Does
- Uninstalls apps while preserving APK files
- Keeps app data intact (unlike full uninstall)
- Apps can be quickly restored from archive
- Tracked in archived apps database

### Use Cases
- Temporarily remove apps you're not using
- Free up space while keeping reinstall option
- Preserve apps with large APKs but small data

### How to Use
1. Select apps to archive
2. Choose archive method (System/Shizuku/Root/Standard)
3. Confirm - APKs saved, apps removed

### Storage Impact
- **Saves**: APK size (typically 50-500 MB per app)
- **Preserves**: App data and cache

---

## 🧹 Cache Cleaning

### What It Does
- Clears temporary cache files
- Safe - apps rebuild cache as needed
- Available as standalone batch operation
- Can be run before or after archiving

### Use Cases
- Free up space without removing apps
- Clean up accumulated temporary files
- Prepare for archiving (optional)

### How to Use
1. Go to One-Click Ops or Batch Operations
2. Select "Clear Cache"
3. Choose apps or select all
4. Execute - cache cleared

### Storage Impact
- **Saves**: Cache size (50-500 MB per app)
- **Preserves**: App data and APK

---

## 🗑️ Data Cleaning

### What It Does
- Clears all app data (databases, settings, etc.)
- Permanent - data cannot be recovered
- Maximum space savings
- Available as standalone operation

### Use Cases
- Remove apps you won't use again
- Maximum storage cleanup
- Reset apps to fresh state

### ⚠️ Warning
- **Permanent**: Login sessions, settings, databases lost
- **Irreversible**: Cannot recover data
- Use with caution!

### Storage Impact
- **Saves**: Data size (100 MB - 2+ GB per app)
- **Preserves**: APK only

---

## 💡 Recommended Workflows

### Workflow 1: Temporary Cleanup
```
1. Clear Cache (optional)
2. Archive Apps
3. Restore later when needed
```
**Best for**: Apps you'll use again soon

### Workflow 2: Maximum Space Savings
```
1. Clear Cache
2. Clear Data (if sure)
3. Archive Apps
```
**Best for**: Apps you won't use for a long time

### Workflow 3: Quick Cleanup
```
1. Clear Cache (all apps)
```
**Best for**: Regular maintenance without removing apps

---

## 📊 Storage Savings Comparison

| Operation | Typical Savings | Reversible | App Data |
|-----------|----------------|------------|----------|
| **Archive Only** | APK size (50-500 MB) | ✅ Yes | Preserved |
| **Cache Clean** | Cache (50-500 MB) | ✅ Yes | Preserved |
| **Data Clean** | Data (100 MB-2 GB) | ❌ No | Lost |
| **Archive + Cache** | APK + Cache | ✅ Yes | Preserved |
| **Archive + Data** | APK + Data | ❌ No | Lost |

---

## 🎯 App Category Recommendations

| Category | Recommended Operation | Reason |
|----------|----------------------|--------|
| **Games** | Archive + Cache Clean | Large APKs, significant cache |
| **Food Apps** | Cache Clean | Frequent use, cache accumulates |
| **Social Media** | Cache Clean | Heavy cache users |
| **Banking** | Archive Only | Keep data/settings |
| **Productivity** | Archive Only | Preserve work data |
| **Shopping** | Cache Clean | Occasional use |

---

## 🔧 Technical Details

### Batch Operations
Both archiving and cache cleaning support:
- **Batch processing** - Multiple apps at once
- **Progress tracking** - See operation status
- **Root/Shizuku support** - Privileged operations
- **Standard mode** - No root required

### Independence
- Operations can be run separately
- No dependency between archive and clean
- Users have full control
- Modular design for flexibility

---

## 📝 Example Scenarios

### Scenario 1: Gamer with Limited Storage
```
Problem: 50 games installed, only play 10 at a time
Solution:
1. Select unused 40 games
2. Clear Cache (frees 2 GB)
3. Archive Games (frees 6 GB APKs)
Total Freed: 8 GB
Result: Can reinstall anytime, progress saved
```

### Scenario 2: Food App User
```
Problem: 15 food delivery apps, use 2-3 regularly
Solution:
1. Select 12 unused food apps
2. Clear Cache only (frees 500 MB)
Total Freed: 500 MB
Result: Apps ready to use, no data loss
```

### Scenario 3: Maximum Cleanup
```
Problem: Need maximum space, don't need app data
Solution:
1. Select apps to remove
2. Clear Cache (frees 1 GB)
3. Clear Data (frees 3 GB)
4. Archive APKs (frees 2 GB)
Total Freed: 6 GB
Result: Maximum savings, APKs preserved
```

---

## ⚙️ Implementation

### ArchiveHandler
```kotlin
// Simple, focused on archiving only
object ArchiveHandler {
    fun opArchive(info, progressHandler, logger, mode): Result
}
```

### Cache Cleaning
```kotlin
// Separate batch operation
BatchOpsManager.OP_CLEAR_CACHE
```

### Data Cleaning
```kotlin
// Separate batch operation  
BatchOpsManager.OP_CLEAR_DATA
```

---

## ✅ Benefits of Separation

1. **Modularity** - Each operation independent
2. **Flexibility** - Users choose their workflow
3. **Safety** - No accidental data loss
4. **Clarity** - Clear purpose for each operation
5. **Control** - Users decide when to combine

---

**Last Updated**: After refactor commit `9b809e86b`  
**Status**: ✅ Operations separated and simplified
