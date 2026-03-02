# Testing Guide for AppManager

## Overview
Comprehensive testing setup for AppManager including unit tests, integration tests, and UI tests.

---

## Test Framework Setup

### Enabled Frameworks
- ✅ **LeakCanary** - Memory leak detection (debug builds)
- ✅ **JUnit** - Unit testing framework
- ✅ **Robolectric** - Android unit testing without emulator
- ✅ **MockK** - Kotlin mocking framework
- ✅ **Coroutines Test** - Coroutine testing utilities
- ✅ **Espresso** - UI testing framework
- ✅ **AndroidX Test** - Android testing libraries

### Dependencies Added
```gradle
// LeakCanary
debugImplementation "com.squareup.leakcanary:leakcanary-android:2.14"

// Unit Testing
testImplementation "org.jetbrains.kotlinx:kotlinx-coroutines-test:${kotlinx_coroutines_version}"
testImplementation "androidx.arch.core:core-testing:2.2.0"
testImplementation "io.mockk:mockk:1.13.9"

// Android Instrumented Tests
androidTestImplementation "androidx.test:core:1.5.0"
androidTestImplementation "androidx.test:runner:1.5.2"
androidTestImplementation "androidx.test:rules:1.5.0"
androidTestImplementation "androidx.test.ext:junit:1.1.5"
androidTestImplementation "androidx.test.espresso:espresso-core:3.5.1"
androidTestImplementation "androidx.test.espresso:espresso-intents:3.5.1"
```

---

## Running Tests

### Unit Tests
```bash
# Run all unit tests
./gradlew testDebugUnitTest

# Run specific test class
./gradlew testDebugUnitTest --tests "io.github.muntashirakon.AppManager.main.MainViewModelTest"

# Run with coverage
./gradlew jacocoTestReport
```

### Instrumented Tests
```bash
# Run all instrumented tests
./gradlew connectedAndroidTest

# Run specific test
./gradlew connectedAndroidTest --tests "io.github.muntashirakon.AppManager.main.MainActivityTest"
```

### Lint Checks
```bash
# Run lint
./gradlew lint

# Run lint with fix
./gradlew lintFix
```

---

## Writing Tests

### Unit Test Example (Kotlin)

```kotlin
package io.github.muntashirakon.AppManager.main

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MainViewModelTest {

    @Test
    fun `loadApps emits success state`() = runTest {
        // Arrange
        val viewModel = MainViewModel(repository)
        
        // Act
        viewModel.loadApps()
        
        // Assert
        val state = viewModel.state.value
        assertTrue(state is MainUiState.Success)
    }

    @Test
    fun `filterApps filters by name`() = runTest {
        // Arrange
        val apps = listOf(
            AppItem("com.example.app1", "App One"),
            AppItem("com.example.app2", "App Two")
        )
        
        // Act
        val filtered = apps.filter { it.label.contains("One", ignoreCase = true) }
        
        // Assert
        assertEquals(1, filtered.size)
        assertEquals("App One", filtered[0].label)
    }
}
```

### UI Test Example (Espresso)

```kotlin
package io.github.muntashirakon.AppManager.main

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun appList_displaysApps() {
        // Verify app list is displayed
        onView(withId(R.id.recycler_view))
            .check(matches(hasMinimumChildCount(1)))
    }

    @Test
    fun searchButton_opensSearch() {
        // Click search button
        onView(withId(R.id.action_search)).perform(click())
        
        // Verify search view is visible
        onView(withId(R.id.search_view))
            .check(matches(isDisplayed()))
    }
}
```

### Coroutine Test Example

```kotlin
package io.github.muntashirakon.AppManager.utils

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class FreezeUtilsTest {

    private val testDispatcher = StandardTestDispatcher()

    @Test
    fun `isFrozen returns true for frozen app`() = runTest(testDispatcher) {
        // Arrange
        val packageName = "com.example.frozen"
        
        // Act
        val result = FreezeUtils.isFrozen(packageName, 0)
        
        // Assert
        assertTrue(result)
    }
}
```

---

## Test Directories

### Unit Tests
```
app/src/test/java/io/github/muntashirakon/AppManager/
├── main/
│   ├── MainViewModelTest.kt
│   └── MainRecyclerAdapterTest.kt
├── utils/
│   ├── FreezeUtilsTest.kt
│   └── PackageUtilsTest.kt
├── backup/
│   └── BackupManagerTest.kt
└── batchops/
    └── BatchOpsManagerTest.kt
```

### Instrumented Tests
```
app/src/androidTest/java/io/github/muntashirakon/AppManager/
├── main/
│   └── MainActivityTest.kt
├── details/
│   └── AppDetailsActivityTest.kt
└── utils/
    └── UiTestUtils.kt
```

---

## LeakCanary Setup

### Automatic Detection
LeakCanary is automatically enabled in debug builds. Memory leaks will be detected and reported.

### Viewing Leak Reports
1. Run debug build
2. Use the app normally
3. If a leak is detected, a notification appears
4. Tap notification to view leak trace

### Manual Leak Check
```kotlin
// In your test
import leakcanary.DetectLeaks

@Test
fun noLeaksDetected() {
    DetectLeaks.detectLeaks()
}
```

---

## Test Coverage

### Generate Coverage Report
```bash
# Generate coverage
./gradlew jacocoTestReport

# View coverage report
open app/build/reports/jacoco/jacocoTestReport/html/index.html
```

### Coverage Goals
- **Overall**: 60% minimum
- **Critical Paths**: 80% minimum
- **ViewModels**: 70% minimum
- **Utils**: 90% minimum

---

## Best Practices

### 1. Test Naming
```kotlin
// Use descriptive names
@Test
fun `loadApps when repository returns data emits success`() = runTest { }

@Test
fun `archiveApp when called removes from database`() = runTest { }
```

### 2. Arrange-Act-Assert Pattern
```kotlin
@Test
fun `test example`() = runTest {
    // Arrange
    val input = "test"
    
    // Act
    val result = processor.process(input)
    
    // Assert
    assertEquals("expected", result)
}
```

### 3. Use Coroutines Test
```kotlin
// Use runTest for coroutine tests
@Test
fun `async operation`() = runTest {
    // Test code
}

// Use test dispatcher for control
private val testDispatcher = StandardTestDispatcher()
```

### 4. Mock External Dependencies
```kotlin
// Use MockK for mocking
val mockRepository = mockk<AppRepository>()
every { mockRepository.getApps() } returns listOf(app1, app2)
```

---

## CI/CD Integration

### GitHub Actions
Tests run automatically on:
- Push to main/master
- Pull requests
- Manual trigger

### Required Checks
- [ ] Unit tests pass
- [ ] Instrumented tests pass
- [ ] Lint passes
- [ ] No memory leaks detected

---

## Troubleshooting

### Test Not Running
```bash
# Clean and rebuild
./gradlew clean build

# Invalidate caches
./gradlew --stop
rm -rf ~/.gradle/caches/
```

### Flaky Tests
```kotlin
// Use test rules for stability
@get:Rule
val coroutinesRule = CoroutinesTestRule()
```

### Memory Issues
```bash
# Increase heap size
export GRADLE_OPTS="-Xmx4g -XX:MaxMetaspaceSize=512m"
```

---

## Resources

- [Android Testing Guide](https://developer.android.com/training/testing)
- [Kotlin Coroutines Testing](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-test/)
- [MockK Documentation](https://mockk.io/)
- [LeakCanary Documentation](https://square.github.io/leakcanary/)
- [Espresso Guide](https://developer.android.com/training/testing/espresso)

---

**Last Updated**: March 1, 2026  
**Status**: ✅ Test Framework Enabled
