# RunBlocking to Suspend Conversion Guide

## Overview
22 instances of `runBlocking` found. Converting to proper suspend functions prevents ANR.

## Priority Files

### HIGH Priority (Called from UI)
1. **FreezeUtils.kt** - 3 instances
2. **OpHistoryManager.kt** - 3 instances
3. **FmFavoritesManager.kt** - 4 instances

### MEDIUM Priority (Background operations)
4. **LogFilterManager.kt** - 4 instances
5. **TagHandler.kt** - 1 instance
6. **ArchiveHandler.kt** - 1 instance (already in coroutine context)

---

## Conversion Pattern

### Before (runBlocking)
```kotlin
fun someFunction(): Result {
    runBlocking {
        val result = dao.someOperation()
        return result
    }
}
```

### After (Suspend)
```kotlin
suspend fun someFunction(): Result = withContext(Dispatchers.IO) {
    val result = dao.someOperation()
    return@withContext result
}
```

### Alternative (Callback)
```kotlin
fun someFunction(callback: (Result) -> Unit) {
    CoroutineScope(Dispatchers.IO).launch {
        val result = dao.someOperation()
        withContext(Dispatchers.Main) {
            callback(result)
        }
    }
}
```

---

## File-by-File Conversions

### 1. FreezeUtils.kt

**Location**: `app/src/main/java/io/github/muntashirakon/AppManager/utils/FreezeUtils.kt`

#### Instance 1 (Line 43)
```kotlin
// BEFORE
fun isFrozen(packageName: String, userId: Int): Boolean {
    runBlocking {
        // database operation
    }
}

// AFTER
suspend fun isFrozen(packageName: String, userId: Int): Boolean = withContext(Dispatchers.IO) {
    // database operation
}
```

#### Instance 2 (Line 51)
Similar conversion needed.

#### Instance 3 (Line 61)
Similar conversion needed.

---

### 2. OpHistoryManager.kt

**Location**: `app/src/main/java/io/github/muntashirakon/AppManager/history/ops/OpHistoryManager.kt`

All 3 instances follow same pattern - convert to suspend.

---

### 3. FmFavoritesManager.kt

**Location**: `app/src/main/java/io/github/muntashirakon/AppManager/fm/FmFavoritesManager.kt`

All 4 instances need conversion.

---

### 4. LogFilterManager.kt

**Location**: `app/src/main/java/io/github/muntashirakon/AppManager/logcat/LogFilterManager.kt`

All 4 instances need conversion.

---

### 5. TagHandler.kt

**Location**: `app/src/main/java/io/github/muntashirakon/AppManager/batchops/TagHandler.kt`

Already in batch operation context - lower priority.

---

### 6. ArchiveHandler.kt

**Location**: `app/src/main/java/io/github/muntashirakon/AppManager/batchops/ArchiveHandler.kt`

Line 107: Already within coroutine context (BatchOpsService) - lowest priority.

---

## Implementation Steps

### Phase 1: HIGH Priority (2-3 hours)
1. Convert FreezeUtils.kt (3 instances)
2. Convert OpHistoryManager.kt (3 instances)
3. Convert FmFavoritesManager.kt (4 instances)
4. Update all call sites to use coroutines

### Phase 2: MEDIUM Priority (1-2 hours)
1. Convert LogFilterManager.kt (4 instances)
2. Convert TagHandler.kt (1 instance)
3. Verify ArchiveHandler.kt (1 instance - may not need change)

### Phase 3: Testing (1 hour)
1. Test all affected flows
2. Verify no ANR
3. Check UI responsiveness

---

## Call Site Updates

After converting to suspend, update call sites:

### Before
```kotlin
val result = someFunction() // Blocking call
```

### After
```kotlin
// In coroutine scope
lifecycleScope.launch {
    val result = someFunction() // Non-blocking
}

// Or with viewModelScope
viewModelScope.launch {
    val result = someFunction()
}
```

---

## Testing Checklist

After conversion:
- [ ] No ANR in affected flows
- [ ] All database operations complete
- [ ] UI remains responsive
- [ ] No crashes from coroutine context issues
- [ ] LeakCanary shows no leaks

---

## Estimated Time
- **Conversion**: 3-4 hours
- **Testing**: 1-2 hours
- **Total**: 4-6 hours

---

**Status**: ⏳ In Progress  
**Priority**: High  
**Due**: March 8, 2026
