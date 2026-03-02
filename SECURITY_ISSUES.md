# 🔴 CRITICAL: Security Issues - March 2026 Audit

## Overview
Comprehensive security audit identified 4 critical issues requiring immediate attention.

**Audit Date**: March 1, 2026  
**Auditor**: MCP Analysis with Google Developer Documentation  
**Severity**: Critical  
**Total Issues**: 4 Critical, 4 High, 4 Medium

---

## Critical Issues

### 1. WebView Security Configuration
**Status**: ⏳ Open  
**File**: `app/src/main/java/io/github/muntashirakon/AppManager/misc/HelpActivity.kt`  
**Severity**: Critical  
**CVSS Score**: 7.5 (High)

#### Issue
WebView loads local content without full security hardening. JavaScript and file access not explicitly disabled.

#### Current Code
```kotlin
mWebView.webViewClient = WebViewClientImpl()
mWebView.setNetworkAvailable(false)
mWebView.settings.allowContentAccess = false
mWebView.loadUrl("file:///android_res/raw/index.html")
```

#### Risk
- Potential XSS attacks if content is compromised
- File system access if `allowFileAccess` defaults to true
- Content provider access vulnerabilities

#### Fix Required
```kotlin
mWebView.settings.apply {
    javaScriptEnabled = false  // Explicitly disable JS
    allowFileAccess = false    // Explicitly disable file access
    allowContentAccess = false // Already set
    allowUniversalAccessFromFileURLs = false
    setGeolocationEnabled(false)
    databaseEnabled = false
    domStorageEnabled = false
    cacheMode = WebSettings.LOAD_NO_CACHE
    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
}
```

#### Implementation Steps
1. Open `HelpActivity.kt`
2. Find line with `mWebView.settings.allowContentAccess = false`
3. Replace with full security configuration above
4. Test Help page still loads correctly
5. Verify no JavaScript execution

#### Testing
- [ ] Help page loads correctly
- [ ] No JavaScript console errors
- [ ] File access blocked (try to load file:// URLs)
- [ ] Content access blocked

#### References
- [Android WebView Security](https://developer.android.com/guide/webapps/managing-webview)
- [OWASP WebView Guidelines](https://owasp.org/www-project-mobile-security/)

---

### 2. Hardcoded Debug Credentials
**Status**: ⏳ Open  
**File**: `app/build.gradle`  
**Severity**: Critical  
**CVSS Score**: 6.5 (Medium-High)

#### Issue
Debug keystore credentials hardcoded in version control.

#### Current Code
```gradle
signingConfigs {
    debug {
        storeFile file('dev_keystore.jks')
        storePassword 'kJCp!Bda#PBdN2RLK%yMK@hatq&69E'
        keyPassword 'kJCp!Bda#PBdN2RLK%yMK@hatq&69E'
        keyAlias 'key0'
    }
}
```

#### Risk
- Credentials exposed in git history
- Debug builds can be impersonated
- Potential supply chain attack vector

#### Fix Required
```gradle
signingConfigs {
    debug {
        storeFile file('dev_keystore.jks')
        storePassword System.getenv('DEBUG_STORE_PASSWORD') ?: ''
        keyPassword System.getenv('DEBUG_KEY_PASSWORD') ?: ''
        keyAlias 'key0'
    }
}
```

#### Implementation Steps
1. Open `app/build.gradle`
2. Replace hardcoded passwords with environment variables
3. Add `.env.example` with placeholder values
4. Update CI/CD to set environment variables
5. Rotate debug keystore (optional but recommended)

#### Testing
- [ ] Debug build succeeds with environment variables set
- [ ] Debug build fails gracefully without credentials
- [ ] No passwords in git history (verify with `git log -p`)

#### References
- [Android Signing Config](https://developer.android.com/studio/build/building-cmdline#configure_signing)
- [Gradle Environment Variables](https://docs.gradle.org/current/userguide/build_environment.html)

---

### 3. GlobalScope Memory Leak Risk
**Status**: ⏳ Open  
**File**: `app/src/main/java/io/github/muntashirakon/AppManager/main/PackageInstallReceiver.kt`  
**Severity**: Critical  
**CVSS Score**: 5.5 (Medium)

#### Issue
Using `GlobalScope` which can lead to memory leaks and outlives proper lifecycle.

#### Current Code
```kotlin
GlobalScope.launch {
    // Database operations
    val appDb = AppsDb.getInstance()
    // ... operations
}
```

#### Risk
- Coroutine outlives its context
- Memory leaks in long-running processes
- Potential OOM crashes
- No structured concurrency

#### Fix Required
```kotlin
// Option 1: Application scope
object AppScope : CoroutineScope by SupervisorJob() + Dispatchers.Default

// In receiver
AppScope.launch {
    // Database operations
}

// Option 2: Lifecycle-aware scope
receiverScope.launch {
    // Database operations
}
```

#### Implementation Steps
1. Create `AppScope` object in `AppManager.kt`
2. Replace all `GlobalScope` usages with `AppScope`
3. Ensure proper cleanup in `onTerminate()`
4. Test for memory leaks with LeakCanary

#### Files Affected
- [ ] `PackageInstallReceiver.kt`
- [ ] Search for other `GlobalScope` usages

#### Testing
- [ ] No memory leaks detected by LeakCanary
- [ ] App terminates cleanly
- [ ] No coroutine-related crashes

#### References
- [Kotlin Coroutines Best Practices](https://developer.android.com/kotlin/coroutines/coroutines-best-practices)
- [GlobalScope Dangers](https://elizarov.medium.com/global-scope-is-evil-610398958b69)

---

### 4. runBlocking on Main Thread Risk
**Status**: ⏳ Open  
**Files**: Multiple  
**Severity**: Critical  
**CVSS Score**: 5.0 (Medium)

#### Issue
`runBlocking` used in utility methods that might be called from main thread, causing ANR.

#### Affected Files
- `app/src/main/java/io/github/muntashirakon/AppManager/utils/FreezeUtils.kt`
- `app/src/main/java/io/github/muntashirakon/AppManager/history/ops/OpHistoryManager.kt`
- `app/src/main/java/io/github/muntashirakon/AppManager/fm/FmFavoritesManager.kt`

#### Current Code Pattern
```kotlin
fun someFunction(): Result {
    runBlocking {
        // Database operations
        val result = dao.someOperation()
        return result
    }
}
```

#### Risk
- ANR if called from main thread
- Blocks UI thread
- Poor user experience

#### Fix Required
```kotlin
// Convert to suspend function
suspend fun someFunction(): Result = withContext(Dispatchers.IO) {
    // Database operations
    val result = dao.someOperation()
    return result
}

// Or use callback pattern
fun someFunction(callback: (Result) -> Unit) {
    CoroutineScope(Dispatchers.IO).launch {
        val result = dao.someOperation()
        withContext(Dispatchers.Main) {
            callback(result)
        }
    }
}
```

#### Implementation Steps
1. Search for all `runBlocking` usages
2. Convert to suspend functions
3. Update call sites to use coroutines
4. Test all affected flows

#### Testing
- [ ] No ANR in affected flows
- [ ] All database operations complete successfully
- [ ] UI remains responsive

#### References
- [runBlocking Documentation](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/run-blocking.html)
- [Android ANR Guide](https://developer.android.com/topic/performance/vitals/anr)

---

## High Priority Issues

### 5. Edge-to-Edge Implementation
**Status**: ⏳ Open  
**Severity**: High  
**Android 15 Requirement**: Yes

### 6. Foreground Service Type Verification
**Status**: ⏳ Open  
**Severity**: High  
**Android 14 Requirement**: Yes

### 7. Test Coverage Expansion
**Status**: ⏳ Open  
**Severity**: High  
**Current Coverage**: ~30%

### 8. Kotlin Version Update
**Status**: ⏳ Open  
**Severity**: High  
**Current Version**: 1.9.25  
**Target Version**: 2.0+

---

## Implementation Timeline

### Week 1: Critical Security (March 4-8)
- [x] Create issues (March 1)
- [ ] Fix WebView security (15 min)
- [ ] Remove hardcoded credentials (10 min)
- [ ] Replace GlobalScope (30 min)
- [ ] Audit runBlocking calls (2-3 hours)

### Week 2: Android 15 Compliance (March 11-15)
- [ ] Implement full edge-to-edge
- [ ] Verify foreground service types
- [ ] Test on Android 15 DP

### Week 3-4: Testing & Modernization (March 18-29)
- [ ] Re-enable LeakCanary
- [ ] Add Kotlin unit tests
- [ ] Enable Espresso tests
- [ ] Update Kotlin to 2.0+

---

## Progress Tracking

| Issue | Status | Assigned | Due Date |
|-------|--------|----------|----------|
| WebView Security | ⏳ Open | - | March 8 |
| Hardcoded Credentials | ⏳ Open | - | March 8 |
| GlobalScope | ⏳ Open | - | March 8 |
| runBlocking | ⏳ Open | - | March 8 |
| Edge-to-Edge | ⏳ Open | - | March 15 |
| Test Coverage | ⏳ Open | - | March 29 |

---

## Verification Checklist

After all fixes:
- [ ] Security audit passes
- [ ] No hardcoded credentials in git
- [ ] No GlobalScope usage
- [ ] No runBlocking on main thread
- [ ] All tests pass
- [ ] No memory leaks
- [ ] Android 15 compatible

---

**Last Updated**: March 1, 2026  
**Next Review**: March 8, 2026
