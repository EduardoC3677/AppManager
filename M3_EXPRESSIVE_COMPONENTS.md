# 🎨 M3 Expressive Components Implementation Guide

## Official Google Developer Implementation

This document provides implementation details for **Material 3 Expressive components** in AppManager, following official Google developer guidelines from [developer.android.com](https://developer.android.com) and [m3.material.io](https://m3.material.io).

---

## 📦 Implemented Components

### 1. **FAB Menu** ✅

**Status:** Complete  
**Guidelines:** [developer.android.com/design/ui/mobile/guides/components/fab-menu](https://developer.android.com/design/ui/mobile/guides/components/fab-menu)

#### Features
- ✅ 2-6 related actions (official limit)
- ✅ Single size compatible with any FAB (Regular, Medium, Large)
- ✅ Replaces speed dial and stacked small FABs
- ✅ Contrasting colors for close button and items
- ✅ Spring animations for open/close
- ✅ Haptic feedback on interactions
- ✅ Staggered item animations

#### Implementation
```kotlin
// Create FAB Menu
val fabMenu = findViewById<FabMenu>(R.id.fab_menu)

// Add menu items (2-6 items recommended)
fabMenu.setMenuItems(listOf(
    FabMenu.MenuItem(
        id = 1,
        icon = R.drawable.ic_archive,
        label = getString(R.string.fab_menu_archive),
        enabled = true
    ),
    FabMenu.MenuItem(
        id = 2,
        icon = R.drawable.ic_clean,
        label = getString(R.string.fab_menu_clean_cache),
        enabled = true
    ),
    FabMenu.MenuItem(
        id = 3,
        icon = R.drawable.ic_backup,
        label = getString(R.string.fab_menu_backup),
        enabled = true
    )
))

// Handle item clicks
fabMenu.setOnMenuItemClickListener { itemId ->
    when (itemId) {
        1 -> // Archive action
        2 -> // Clean cache action
        3 -> // Backup action
    }
}
```

#### XML Layout
```xml
<io.github.muntashirakon.AppManager.view.FabMenu
    android:id="@+id/fab_menu"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:layout_gravity="bottom|end" />
```

#### M3 Expressive Specifications
| Property | Value |
|----------|-------|
| **Max Items** | 6 |
| **Min Items** | 2 |
| **FAB Size** | Normal (56dp) |
| **Menu Width** | 200dp min |
| **Corner Radius** | 16dp |
| **Elevation** | 8dp |
| **Animation Duration** | 300ms |
| **Easing** | Emphasized (0.05, 0.7, 0.1, 1.0) |

---

### 2. **Loading Indicator** ✅

**Status:** Complete  
**Guidelines:** [developer.android.com/design/ui/mobile/guides/components/loading-indicator](https://developer.android.com/design/ui/mobile/guides/components/loading-indicator)

#### Features
- ✅ Shows progress for loads under 5 seconds
- ✅ Replaces indeterminate circular progress indicators
- ✅ Pull-to-refresh support
- ✅ Smooth, fluid animation
- ✅ Success/error state handling
- ✅ M3 styling with system colors

#### Implementation
```kotlin
// Create Loading Indicator
val loadingIndicator = findViewById<LoadingIndicator>(R.id.loading_indicator)

// Start indeterminate loading
loadingIndicator.startLoading()

// Start determinate loading
loadingIndicator.startLoading(maxProgress = 100)
loadingIndicator.updateProgress(currentProgress = 50)

// Complete with success
loadingIndicator.completeLoading()

// Complete with error
loadingIndicator.completeLoadingWithError()

// Hide
loadingIndicator.hideLoading()
```

#### Pull-to-Refresh Integration
```kotlin
// For swipe refresh operations
loadingIndicator.startPullToRefresh()

// On refresh complete
loadingIndicator.completePullToRefresh()
```

#### M3 Expressive Specifications
| Property | Value |
|----------|-------|
| **Track Thickness** | 4dp |
| **Animation Mode** | Continuous |
| **Duration** | 200-300ms |
| **Easing** | Emphasized Decelerate |
| **Color** | System accent1_500 |
| **Background** | System neutral1_100 |

---

### 3. **Split Button** ⏳

**Status:** Planned  
**Guidelines:** [developer.android.com/reference/com/google/android/material/button/MaterialSplitButton](https://developer.android.com/reference/com/google/android/material/button/MaterialSplitButton)

#### Planned Features
- Two-button container (leading + trailing)
- Leading button: Icon and/or label
- Trailing button: Animated vector drawable
- Shape morphing on activation
- 5 recommended sizes
- Elevated, filled, tonal, outlined styles

#### Implementation (Planned)
```xml
<com.google.android.material.button.MaterialSplitButton
    android:id="@+id/split_button"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content">

    <!-- Leading button -->
    <Button
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/archive"
        app:icon="@drawable/ic_archive"
        app:iconGravity="start"/>

    <!-- Trailing button with animated chevron -->
    <Button
        style="?attr/materialIconSplitButtonFilledStyle"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        app:icon="@drawable/m3_split_button_chevron_avd"/>

</com.google.android.material.button.MaterialSplitButton>
```

---

### 4. **Docked Toolbar** ⏳

**Status:** Planned  
**Guidelines:** [m3.material.io/components/toolbars/guidelines](https://m3.material.io/components/toolbars/guidelines)

#### Planned Features
- Spans full width of window
- Global actions that remain same across pages
- Replaces bottom app bar
- Shorter, more flexible than bottom app bar
- FAB contained within toolbar container

#### Use Cases
- Global navigation actions
- Persistent actions across screens
- Bottom placement for thumb reachability

---

### 5. **Floating Toolbar** ⏳

**Status:** Planned  
**Guidelines:** [m3.material.io/components/toolbars/guidelines](https://m3.material.io/components/toolbars/guidelines)

#### Planned Features
- Versatile toolbar placement
- Greater number of actions
- Pairs with FAB
- Can be docked or floating

---

### 6. **Button Groups** ⏳

**Status:** Planned  
**Guidelines:** [m3.material.io/components/button-groups](https://m3.material.io/components/button-groups)

#### Planned Features
- Container holding buttons of different shapes/sizes
- Works with all button sizes (XS-XL)
- Applies shape, motion, width changes
- Single-line display

---

## 🎨 Design Tokens Used

### Color Tokens
```kotlin
// Primary colors
@color/m3_sys_color_light_primary
@color/m3_sys_color_light_on_primary
@color/m3_sys_color_light_primary_container
@color/m3_sys_color_light_on_primary_container

// Secondary colors
@color/m3_sys_color_light_secondary_container
@color/m3_sys_color_light_on_secondary_container

// Error colors
@color/m3_sys_color_light_error
@color/m3_sys_color_light_on_error

// Surface colors
@color/m3_sys_color_light_surface
@color/m3_sys_color_light_on_surface
```

### Motion Tokens
```kotlin
// Easing curves
val EMPHASIZED = PathInterpolator(0.05f, 0.7f, 0.1f, 1.0f)
val EMPHASIZED_DECELERERATE = PathInterpolator(0.05f, 0.7f, 0.1f, 1.0f)
val EMPHASIZED_ACCELERATE = PathInterpolator(0.3f, 0.0f, 0.8f, 0.15f)
val SPRING = PathInterpolator(0.08f, 0.82f, 0.17f, 1.05f)

// Duration
const val DURATION_SHORT = 200L
const val DURATION_MEDIUM = 300L
const val DURATION_LONG = 400L
```

### Shape Tokens
```kotlin
// Corner radius
const val CORNER_SMALL = 8dp
const val CORNER_MEDIUM = 12dp
const val CORNER_LARGE = 16dp
const val CORNER_EXTRA_LARGE = 28dp
const val CORNER_FULL = 50% // Fully rounded
```

---

## 📱 Accessibility Requirements

### Touch Targets
- **Minimum size:** 48dp x 48dp
- **Recommended spacing:** 8dp between targets

### Content Descriptions
```kotlin
// Always provide content descriptions for screen readers
fab.contentDescription = getString(R.string.open_menu)
closeButton.contentDescription = getString(R.string.close_menu)
menuItemIcon.contentDescription = null // Decorative
```

### Color Contrast
- **Normal text:** WCAG AA (4.5:1 minimum)
- **Large text:** WCAG AA (3:1 minimum)
- **M3 tokens:** All meet WCAG AA by default

---

## 🎯 Best Practices

### 1. **FAB Menu**
- ✅ Limit to 2-6 items (official recommendation)
- ✅ Use clear, action-oriented labels
- ✅ Include icons for all items
- ✅ Provide content descriptions
- ✅ Use contrasting colors for close button

### 2. **Loading Indicator**
- ✅ Use for loads under 5 seconds
- ✅ Show determinate progress when possible
- ✅ Provide success/error feedback
- ✅ Smooth fade in/out animations
- ✅ Don't use for long operations (use progress dialog)

### 3. **Motion & Animation**
- ✅ Use emphasized easing for important transitions
- ✅ Keep animations under 400ms
- ✅ Provide haptic feedback for key interactions
- ✅ Respect system animation preferences
- ✅ Use spring physics for natural feel

### 4. **Haptic Feedback**
- ✅ LIGHT: Toggles, subtle interactions
- ✅ MEDIUM: Buttons, standard actions
- ✅ HEAVY: Destructive actions, confirmations
- ✅ SUCCESS: Positive confirmations
- ✅ ERROR: Negative feedback

---

## 📊 Performance Guidelines

### Animation Performance
- **Frame time:** <16ms (60 FPS)
- **Animation duration:** 200-400ms
- **Stagger delay:** 30-50ms between items

### Memory Usage
- **FAB Menu:** Max 6 items
- **Loading Indicator:** Single instance per screen
- **Haptics:** Reuse vibrator instance

### Battery Impact
- **Animations:** Hardware accelerated
- **Haptics:** Use sparingly
- **Loading states:** Timeout after 5 seconds

---

## 🔗 Official References

### Google Developer Documentation
- [Material 3 Expressive Components](https://developer.android.com/design/ui/mobile/guides/components)
- [FAB Menu Guidelines](https://developer.android.com/design/ui/mobile/guides/components/fab-menu)
- [Loading Indicator](https://developer.android.com/design/ui/mobile/guides/components/loading-indicator)
- [MaterialSplitButton API](https://developer.android.com/reference/com/google/android/material/button/MaterialSplitButton)

### Material Design Documentation
- [M3 Components](https://m3.material.io/components)
- [Design Tokens](https://m3.material.io/design-tokens)
- [Motion Guidelines](https://m3.material.io/styles/motion)
- [Color System](https://m3.material.io/styles/color)

### Android Developer Resources
- [Jetpack Compose Material 3](https://developer.android.com/jetpack/androidx/releases/compose-material3)
- [Material Components for Android](https://github.com/material-components/material-components-android)
- [Haptic Feedback Guidelines](https://source.android.com/docs/core/interaction/haptics)

---

## 📈 Implementation Status

| Component | Status | Version | Documentation |
|-----------|--------|---------|---------------|
| **FAB Menu** | ✅ Complete | 4.0.6 | [FabMenu.kt](app/src/main/java/io/github/muntashirakon/AppManager/view/FabMenu.kt) |
| **Loading Indicator** | ✅ Complete | 4.0.6 | [LoadingIndicator.kt](app/src/main/java/io/github/muntashirakon/AppManager/view/LoadingIndicator.kt) |
| **Split Button** | ⏳ Planned | - | - |
| **Docked Toolbar** | ⏳ Planned | - | - |
| **Floating Toolbar** | ⏳ Planned | - | - |
| **Button Groups** | ⏳ Planned | - | - |
| **Shape Morphing** | ⏳ Planned | - | - |

---

**Last Updated:** March 1, 2026  
**Version:** 4.0.6  
**Compliance:** ✅ **M3 Expressive 2026**
