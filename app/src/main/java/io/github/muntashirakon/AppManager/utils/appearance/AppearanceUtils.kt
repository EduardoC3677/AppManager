// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils.appearance

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.app.UiModeManager
import android.content.ComponentCallbacks
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import android.os.PowerManager
import android.view.ContextThemeWrapper
import android.view.View
import android.view.Window
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.PublicTwilightManager
import androidx.collection.ArrayMap
import androidx.core.app.ActivityCompat
import androidx.core.view.WindowCompat
import com.google.android.material.color.DynamicColors
import io.github.muntashirakon.AppManager.PerProcessActivity
import io.github.muntashirakon.AppManager.settings.Prefs
import io.github.muntashirakon.AppManager.utils.LangUtils
import java.lang.ref.WeakReference
import java.util.LinkedHashSet
import java.util.Locale

object AppearanceUtils {
    @JvmField
    val TAG: String = AppearanceUtils::class.java.simpleName

    private val sActivityReferences = ArrayMap<Int, WeakReference<Activity>>()

    private class AppearanceOptions {
        var locale: Locale? = null
        var layoutDirection: Int? = null
        var theme: Int? = null
        var nightMode: Int? = null
    }

    @JvmStatic
    fun applyOnlyLocale(context: Context) {
        // Update locale and layout direction for the application
        val options = AppearanceOptions().apply {
            locale = LangUtils.getFromPreference(context)
            layoutDirection = Prefs.Appearance.getLayoutDirection()
        }
        updateConfiguration(context, options)
        if (context != context.getApplicationContext()) {
            updateConfiguration(context.getApplicationContext(), options)
        }
    }

    /**
     * Return a [ContextThemeWrapper] with the default locale, layout direction, theme and night mode.
     */
    @JvmStatic
    fun getThemedContext(context: Context, transparent: Boolean): Context {
        val options = AppearanceOptions().apply {
            locale = LangUtils.getFromPreference(context)
            layoutDirection = Prefs.Appearance.getLayoutDirection()
            theme = if (transparent) Prefs.Appearance.getTransparentAppTheme() else Prefs.Appearance.getAppTheme()
            nightMode = Prefs.Appearance.getNightMode()
        }
        val newCtx = ContextThemeWrapper(context, options.theme!!)
        newCtx.applyOverrideConfiguration(createOverrideConfiguration(context, options))
        return DynamicColors.wrapContextIfAvailable(newCtx)
    }

    /**
     * Return a [ContextThemeWrapper] with the default locale, layout direction, theme and night mode.
     */
    @JvmStatic
    fun getThemedWidgetContext(context: Context, transparent: Boolean): Context {
        val theme = if (transparent) Prefs.Appearance.getTransparentAppTheme() else Prefs.Appearance.getAppTheme()
        val newCtx = ContextThemeWrapper(context, theme)
        return DynamicColors.wrapContextIfAvailable(newCtx)
    }

    /**
     * Return a [ContextWrapper] with system configuration. This is helpful when it is necessary to access system
     * configurations instead of the one used in the app.
     */
    @JvmStatic
    fun getSystemContext(context: Context): Context {
        val res = Resources.getSystem()
        val configuration = res.configuration
        return ContextWrapper(context.createConfigurationContext(configuration))
    }

    /**
     * Initialize appearance in the app. Must be called from [Application.onCreate].
     */
    @JvmStatic
    fun init(application: Application) {
        application.registerActivityLifecycleCallbacks(ActivityAppearanceCallback())
        application.registerComponentCallbacks(object : ComponentCallbacks2, ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) {
                applyOnlyLocale(application)
            }

            override fun onLowMemory() {}

            override fun onTrimMemory(level: Int) {}
        })
        applyOnlyLocale(application)
        if (Prefs.Appearance.useSystemFont()) {
            TypefaceUtil.replaceFontsWithSystem(application)
        }
    }

    /**
     * This is similar to what the delegate methods such as [AppCompatDelegate.setDefaultNightMode] does.
     * This is required because simply calling [ActivityCompat.recreate] cannot apply the changes to
     * all the active activities.
     */
    @JvmStatic
    fun applyConfigurationChangesToActivities() {
        for (activityRef in sActivityReferences.values) {
            val activity = activityRef.get()
            if (activity != null) {
                ActivityCompat.recreate(activity)
            }
        }
    }

    private class ActivityAppearanceCallback : Application.ActivityLifecycleCallbacks {
        override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
            if (activity is PerProcessActivity) {
                val transparentBackground = activity.getTransparentBackground()
                activity.setTheme(
                    if (transparentBackground)
                        Prefs.Appearance.getTransparentAppTheme()
                    else
                        Prefs.Appearance.getAppTheme()
                )
            }
            // Theme must be set first because the method below will add dynamic attributes to the theme
            DynamicColors.applyToActivityIfAvailable(activity)
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                onActivityPreCreated(activity, savedInstanceState)
            }
            val window: Window = activity.window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                onActivityPostCreated(activity, savedInstanceState)
            }
        }

        override fun onActivityPostCreated(activity: Activity, savedInstanceState: Bundle?) {
            applyOnlyLocale(activity)
            AppCompatDelegate.setDefaultNightMode(Prefs.Appearance.getNightMode())

            sActivityReferences[activity.hashCode()] = WeakReference(activity)
        }

        override fun onActivityStarted(activity: Activity) {
        }

        override fun onActivityResumed(activity: Activity) {
        }

        override fun onActivityPaused(activity: Activity) {
        }

        override fun onActivityStopped(activity: Activity) {
        }

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        }

        override fun onActivityPreDestroyed(activity: Activity) {
            sActivityReferences.remove(activity.hashCode())
        }

        override fun onActivityDestroyed(activity: Activity) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                onActivityPreDestroyed(activity)
            }
        }
    }

    private fun updateConfiguration(context: Context, options: AppearanceOptions) {
        val res = context.resources
        // Set theme
        options.theme?.let { context.setTheme(it) }
        // Update configuration
        val overrideConf = createOverrideConfiguration(context, options)
        @Suppress("DEPRECATION")
        res.updateConfiguration(overrideConf, res.displayMetrics)
    }

    private fun createOverrideConfiguration(context: Context, options: AppearanceOptions): Configuration {
        return createOverrideConfiguration(context, options, null, false)
    }

    @SuppressLint("AppBundleLocaleChanges") // We don't use Play Store
    private fun createOverrideConfiguration(
        context: Context,
        options: AppearanceOptions,
        configOverlay: Configuration?,
        ignoreFollowSystem: Boolean
    ): Configuration {
        // Order matters!
        val res = context.resources
        val oldConf = res.configuration
        val overrideConf = Configuration(oldConf)

        // Set locale
        options.locale?.let { locale ->
            Locale.setDefault(locale)
            @Suppress("DEPRECATION")
            val currentLocale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                oldConf.locales[0]
            } else {
                oldConf.locale
            }
            if (currentLocale != locale) {
                // Locale has changed
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    setLocaleApi24(overrideConf, locale)
                } else {
                    overrideConf.setLocale(locale)
                    overrideConf.setLayoutDirection(locale)
                }
            }
        }

        // Set layout direction
        options.layoutDirection?.let { layoutDirection ->
            val currentLayoutDirection = overrideConf.layoutDirection
            if (currentLayoutDirection != layoutDirection) {
                when (layoutDirection) {
                    View.LAYOUT_DIRECTION_RTL ->
                        overrideConf.setLayoutDirection(Locale.forLanguageTag("ar"))
                    View.LAYOUT_DIRECTION_LTR ->
                        overrideConf.setLayoutDirection(Locale.ENGLISH)
                }
            }
        }

        // Set night mode
        options.nightMode?.let { nightMode ->
            // Follow AppCompatDelegateImpl
            val nightModeToUse = if (nightMode != AppCompatDelegate.MODE_NIGHT_UNSPECIFIED) {
                nightMode
            } else {
                AppCompatDelegate.getDefaultNightMode()
            }
            val modeToApply = mapNightModeOnce(context, nightModeToUse)
            val newNightMode = when (modeToApply) {
                AppCompatDelegate.MODE_NIGHT_YES ->
                    Configuration.UI_MODE_NIGHT_YES
                AppCompatDelegate.MODE_NIGHT_NO ->
                    Configuration.UI_MODE_NIGHT_NO
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM -> {
                    if (ignoreFollowSystem) {
                        // We're generating an overlay to be used on top of the system configuration,
                        // so use whatever's already there.
                        Configuration.UI_MODE_NIGHT_UNDEFINED
                    } else {
                        // If we're following the system, we just use the system default from the
                        // application context
                        val sysConf = Resources.getSystem().configuration
                        sysConf.uiMode and Configuration.UI_MODE_NIGHT_MASK
                    }
                }
                else -> {
                    if (ignoreFollowSystem) {
                        Configuration.UI_MODE_NIGHT_UNDEFINED
                    } else {
                        val sysConf = Resources.getSystem().configuration
                        sysConf.uiMode and Configuration.UI_MODE_NIGHT_MASK
                    }
                }
            }
            overrideConf.uiMode = newNightMode or (overrideConf.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv())
        }

        // Apply overlay
        configOverlay?.let { overrideConf.setTo(it) }
        return overrideConf
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun setLocaleApi24(config: Configuration, locale: Locale) {
        val defaultLocales = LocaleList.getDefault()
        val locales = LinkedHashSet<Locale>(defaultLocales.size() + 1)
        // Bring the target locale to the front of the list
        // There's a hidden API, but it's not currently used here.
        locales.add(locale)
        for (i in 0 until defaultLocales.size()) {
            locales.add(defaultLocales[i])
        }
        config.setLocales(LocaleList(*locales.toTypedArray()))
    }

    @Suppress("DEPRECATION")
    private fun mapNightModeOnce(context: Context, @AppCompatDelegate.NightMode mode: Int): Int {
        return when (mode) {
            AppCompatDelegate.MODE_NIGHT_NO,
            AppCompatDelegate.MODE_NIGHT_YES,
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM ->
                // $FALLTHROUGH since these are all valid modes to return
                mode
            AppCompatDelegate.MODE_NIGHT_AUTO_TIME -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val uiModeManager = context.applicationContext
                        .getSystemService(UiModeManager::class.java)!!
                    if (uiModeManager.nightMode == UiModeManager.MODE_NIGHT_AUTO) {
                        // If we're set to AUTO and the system's auto night mode is already enabled,
                        // we'll just let the system handle it by returning FOLLOW_SYSTEM
                        return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    }
                }
                // Unlike AppCompatDelegateImpl, we don't need to change it based on configuration
                if (PublicTwilightManager.isNight(context)) {
                    AppCompatDelegate.MODE_NIGHT_YES
                } else {
                    AppCompatDelegate.MODE_NIGHT_NO
                }
            }
            AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY -> {
                // Unlike AppCompatDelegateImpl, we don't need to change it based on configuration
                val pm = context.applicationContext
                    .getSystemService(PowerManager::class.java)!!
                if (pm.isPowerSaveMode) {
                    AppCompatDelegate.MODE_NIGHT_YES
                } else {
                    AppCompatDelegate.MODE_NIGHT_NO
                }
            }
            AppCompatDelegate.MODE_NIGHT_UNSPECIFIED ->
                // If we don't have a mode specified, let the system handle it
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            else ->
                throw IllegalStateException(
                    "Unknown value set for night mode. Please use one" +
                            " of the MODE_NIGHT values from AppCompatDelegate."
                )
        }
    }
}
