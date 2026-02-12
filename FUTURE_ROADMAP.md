# App Manager Modernization Roadmap (MAD Standards)

This document tracks high-level architectural and UI enhancements based on Google's Modern Android Development (MAD) standards.

## [ISSUE-01] Implement Hilt for Dependency Injection
**Priority:** High
**Status:** TODO
**Description:**
The project currently relies on manual `ViewModelProvider` instantiation and manual passing of dependencies (Database, Shizuku shells, etc.). This makes the codebase brittle and hard to test.
**Tasks:**
- [ ] Add Hilt Gradle dependencies.
- [ ] Annotate `App.java` with `@HiltAndroidApp`.
- [ ] Migrate `AppsDb` to a Hilt Module.
- [ ] Refactor `MainActivity` and `AppInfoFragment` to use `@AndroidEntryPoint`.

## [ISSUE-02] Gradual Migration to Kotlin
**Priority:** Medium
**Status:** TODO
**Description:**
To leverage modern features like Coroutines, Flow, and Jetpack Compose, the project needs to start adopting Kotlin.
**Tasks:**
- [ ] Configure Kotlin in `build.gradle`.
- [ ] Convert utility classes (e.g., `FileUtils`, `LangUtils`) to Kotlin.
- [ ] Start writing new ViewModels in Kotlin.

## [ISSUE-03] Migrate Asynchronous Logic to Coroutines & Flow
**Priority:** High
**Status:** TODO
**Description:**
Replace legacy Java `Threads`, `Executors`, and `LiveData` with Kotlin Coroutines and `StateFlow`.
**Tasks:**
- [ ] Replace `ThreadUtils` background posts with `viewModelScope.launch`.
- [ ] Migrate `AppDao` to return `Flow<List<App>>` instead of `LiveData`.
- [ ] Use `collectLatest` in UI to handle list updates efficiently.

## [ISSUE-04] Jetpack Compose Transition (Settings First)
**Priority:** Medium
**Status:** TODO
**Description:**
The XML-based Preference system is complex and difficult to customize for M3 Expressive standards.
**Tasks:**
- [ ] Add Compose dependencies.
- [ ] Create a "Compose Settings" activity.
- [ ] Re-implement `AppearancePreferences` as a Compose `@Composable`.
- [ ] Gradually replace leaf fragments with Compose.

## [ISSUE-05] Implement Full Edge-to-Edge & Predictive Back
**Priority:** Low
**Status:** TODO
**Description:**
Enhance the M3 design by supporting system-wide gestures and immersive displays.
**Tasks:**
- [ ] Call `WindowCompat.setDecorFitsSystemWindows(window, false)` in `BaseActivity`.
- [ ] Implement `WindowInsets` handling for Top/Bottom bars.
- [ ] Migrate all `onBackPressed` overrides to `OnBackPressedDispatcher`.

## [ISSUE-06] Paging 3 for Large App Lists
**Priority:** Medium
**Status:** TODO
**Description:**
While `AppListCache` helps, the UI still binds the entire list. `Paging 3` would allow the app to handle 10,000+ apps with minimal memory usage.
**Tasks:**
- [ ] Implement `PagingSource` for `AppDao`.
- [ ] Update `MainRecyclerAdapter` to `PagingDataAdapter`.

## [ISSUE-07] WorkManager for Batch Operations
**Priority:** Medium
**Status:** TODO
**Description:**
Move long-running operations like backups and deep scans to `WorkManager` to ensure reliability and battery efficiency.
**Tasks:**
- [ ] Refactor `BatchOpsService` logic into `Worker` classes.
- [ ] Use `ForegroundInfo` for persistent notifications during tasks.
