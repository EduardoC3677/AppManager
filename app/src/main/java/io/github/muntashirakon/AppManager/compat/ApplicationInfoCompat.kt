// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat

import android.content.pm.ApplicationInfo
import android.content.pm.ApplicationInfoHidden
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.IntDef
import dev.rikka.tools.refine.Refine
import io.github.muntashirakon.io.Paths

object ApplicationInfoCompat {
    /**
     * Value for {@code #privateFlags}: true if the application is hidden via restrictions and for
     * most purposes is considered as not installed.
     */
    const val PRIVATE_FLAG_HIDDEN: Int = 1

    /**
     * Value for {@code #privateFlags}: set to <code>true</code> if the application
     * has reported that it is heavy-weight, and thus can not participate in
     * the normal application lifecycle.
     *
     * <p>Comes from the
     * android.R.styleable#AndroidManifestApplication_cantSaveState
     * attribute of the &lt;application&gt; tag.
     */
    const val PRIVATE_FLAG_CANT_SAVE_STATE: Int = 1 shl 1

    /**
     * Value for {@code #privateFlags}: set to {@code true} if the application
     * is permitted to hold privileged permissions.
     */
    const val PRIVATE_FLAG_PRIVILEGED: Int = 1 shl 3

    /**
     * Value for {@code #privateFlags}: {@code true} if the application has any IntentFiler
     * with some data URI using HTTP or HTTPS with an associated VIEW action.
     */
    const val PRIVATE_FLAG_HAS_DOMAIN_URLS: Int = 1 shl 4

    /**
     * When set, the default data storage directory for this app is pointed at
     * the device-protected location.
     */
    const val PRIVATE_FLAG_DEFAULT_TO_DEVICE_PROTECTED_STORAGE: Int = 1 shl 5

    /**
     * When set, assume that all components under the given app are direct boot
     * aware, unless otherwise specified.
     */
    const val PRIVATE_FLAG_DIRECT_BOOT_AWARE: Int = 1 shl 6

    /**
     * Value for {@code #privateFlags}: {@code true} if the application is installed
     * as instant app.
     */
    const val PRIVATE_FLAG_INSTANT: Int = 1 shl 7

    /**
     * When set, at least one component inside this application is direct boot
     * aware.
     */
    const val PRIVATE_FLAG_PARTIALLY_DIRECT_BOOT_AWARE: Int = 1 shl 8

    /**
     * When set, signals that the application is required for the system user and should not be
     * uninstalled.
     */
    const val PRIVATE_FLAG_REQUIRED_FOR_SYSTEM_USER: Int = 1 shl 9

    /**
     * When set, the application explicitly requested that its activities be resizeable by default.
     * {@code android.R.styleable#AndroidManifestActivity_resizeableActivity}
     */
    const val PRIVATE_FLAG_ACTIVITIES_RESIZE_MODE_RESIZEABLE: Int = 1 shl 10

    /**
     * When set, the application explicitly requested that its activities *not* be resizeable by
     * default.
     * {@code android.R.styleable#AndroidManifestActivity_resizeableActivity}
     */
    const val PRIVATE_FLAG_ACTIVITIES_RESIZE_MODE_UNRESIZEABLE: Int = 1 shl 11

    /**
     * The application isn't requesting explicitly requesting for its activities to be resizeable or
     * non-resizeable by default. So, we are making it activities resizeable by default based on the
     * target SDK version of the app.
     * {@code android.R.styleable#AndroidManifestActivity_resizeableActivity}
     * <p>
     * NOTE: This only affects apps with target SDK >= N where the resizeableActivity attribute was
     * introduced. It shouldn't be confused with {@code ActivityInfo#RESIZE_MODE_FORCE_RESIZEABLE}
     * where certain pre-N apps are forced to the resizeable.
     */
    const val PRIVATE_FLAG_ACTIVITIES_RESIZE_MODE_RESIZEABLE_VIA_SDK_VERSION: Int = 1 shl 12

    /**
     * Value for {@code #privateFlags}: {@code true} means the OS should go ahead and
     * run full-data backup operations for the app even when it is in a
     * foreground-equivalent run state.  Defaults to {@code false} if unspecified.
     */
    const val PRIVATE_FLAG_BACKUP_IN_FOREGROUND: Int = 1 shl 13

    /**
     * Value for {@code #privateFlags}: {@code true} means this application
     * contains a static shared library. Defaults to {@code false} if unspecified.
     */
    const val PRIVATE_FLAG_STATIC_SHARED_LIBRARY: Int = 1 shl 14

    /**
     * Value for {@code #privateFlags}: When set, the application will only have its splits loaded
     * if they are required to load a component. Splits can be loaded on demand using the
     * {@code Context#createContextForSplit(String)} API.
     */
    const val PRIVATE_FLAG_ISOLATED_SPLIT_LOADING: Int = 1 shl 15

    /**
     * Value for {@code #privateFlags}: When set, the application was installed as
     * a virtual preload.
     */
    const val PRIVATE_FLAG_VIRTUAL_PRELOAD: Int = 1 shl 16

    /**
     * Value for {@code #privateFlags}: whether this app is pre-installed on the
     * OEM partition of the system image.
     */
    const val PRIVATE_FLAG_OEM: Int = 1 shl 17

    /**
     * Value for {@code #privateFlags}: whether this app is pre-installed on the
     * vendor partition of the system image.
     */
    const val PRIVATE_FLAG_VENDOR: Int = 1 shl 18

    /**
     * Value for {@code #privateFlags}: whether this app is pre-installed on the
     * product partition of the system image.
     */
    const val PRIVATE_FLAG_PRODUCT: Int = 1 shl 19

    /**
     * Value for {@code #privateFlags}: whether this app is signed with the
     * platform key.
     */
    const val PRIVATE_FLAG_SIGNED_WITH_PLATFORM_KEY: Int = 1 shl 20

    /**
     * Value for {@code #privateFlags}: whether this app is pre-installed on the
     * system_ext partition of the system image.
     */
    const val PRIVATE_FLAG_SYSTEM_EXT: Int = 1 shl 21

    /**
     * Indicates whether this package requires access to non-SDK APIs.
     * Only system apps and tests are allowed to use this property.
     */
    const val PRIVATE_FLAG_USES_NON_SDK_API: Int = 1 shl 22

    /**
     * Indicates whether this application can be profiled by the shell user,
     * even when running on a device that is running in user mode.
     */
    const val PRIVATE_FLAG_PROFILEABLE_BY_SHELL: Int = 1 shl 23

    /**
     * Indicates whether this package requires access to non-SDK APIs.
     * Only system apps and tests are allowed to use this property.
     */
    const val PRIVATE_FLAG_HAS_FRAGILE_USER_DATA: Int = 1 shl 24

    /**
     * Indicates whether this application wants to use the embedded dex in the APK, rather than
     * extracted or locally compiled variants. This keeps the dex code protected by the APK
     * signature. Such apps will always run in JIT mode (same when they are first installed), and
     * the system will never generate ahead-of-time compiled code for them. Depending on the app's
     * workload, there may be some run time performance change, noteably the cold start time.
     */
    const val PRIVATE_FLAG_USE_EMBEDDED_DEX: Int = 1 shl 25

    /**
     * Value for {@code #privateFlags}: indicates whether this application's data will be cleared
     * on a failed restore.
     *
     * <p>Comes from the
     * android.R.styleable#AndroidManifestApplication_allowClearUserDataOnFailedRestore attribute
     * of the &lt;application&gt; tag.
     */
    const val PRIVATE_FLAG_ALLOW_CLEAR_USER_DATA_ON_FAILED_RESTORE: Int = 1 shl 26

    /**
     * Value for {@code #privateFlags}: true if the application allows its audio playback
     * to be captured by other apps.
     */
    const val PRIVATE_FLAG_ALLOW_AUDIO_PLAYBACK_CAPTURE: Int = 1 shl 27

    /**
     * Indicates whether this package is in fact a runtime resource overlay.
     */
    const val PRIVATE_FLAG_IS_RESOURCE_OVERLAY: Int = 1 shl 28

    /**
     * Value for {@code #privateFlags}: If {@code true} this app requests
     * full external storage access. The request may not be honored due to
     * policy or other reasons.
     */
    const val PRIVATE_FLAG_REQUEST_LEGACY_EXTERNAL_STORAGE: Int = 1 shl 29

    /**
     * Value for {@code #privateFlags}: whether this app is pre-installed on the
     * ODM partition of the system image.
     */
    const val PRIVATE_FLAG_ODM: Int = 1 shl 30

    /**
     * Value for {@code #privateFlags}: If {@code true} this app allows heap tagging.
     * {@code com.android.server.am.ProcessList#NATIVE_HEAP_POINTER_TAGGING}
     */
    const val PRIVATE_FLAG_ALLOW_NATIVE_HEAP_POINTER_TAGGING: Int = 1 shl 31

    @IntDef(
        flag = true, value = [
            PRIVATE_FLAG_ACTIVITIES_RESIZE_MODE_RESIZEABLE,
            PRIVATE_FLAG_ACTIVITIES_RESIZE_MODE_RESIZEABLE_VIA_SDK_VERSION,
            PRIVATE_FLAG_ACTIVITIES_RESIZE_MODE_UNRESIZEABLE,
            PRIVATE_FLAG_BACKUP_IN_FOREGROUND,
            PRIVATE_FLAG_CANT_SAVE_STATE,
            PRIVATE_FLAG_DEFAULT_TO_DEVICE_PROTECTED_STORAGE,
            PRIVATE_FLAG_DIRECT_BOOT_AWARE,
            PRIVATE_FLAG_HAS_DOMAIN_URLS,
            PRIVATE_FLAG_HIDDEN,
            PRIVATE_FLAG_INSTANT,
            PRIVATE_FLAG_IS_RESOURCE_OVERLAY,
            PRIVATE_FLAG_ISOLATED_SPLIT_LOADING,
            PRIVATE_FLAG_OEM,
            PRIVATE_FLAG_PARTIALLY_DIRECT_BOOT_AWARE,
            PRIVATE_FLAG_USE_EMBEDDED_DEX,
            PRIVATE_FLAG_PRIVILEGED,
            PRIVATE_FLAG_PRODUCT,
            PRIVATE_FLAG_SYSTEM_EXT,
            PRIVATE_FLAG_PROFILEABLE_BY_SHELL,
            PRIVATE_FLAG_REQUIRED_FOR_SYSTEM_USER,
            PRIVATE_FLAG_SIGNED_WITH_PLATFORM_KEY,
            PRIVATE_FLAG_STATIC_SHARED_LIBRARY,
            PRIVATE_FLAG_VENDOR,
            PRIVATE_FLAG_VIRTUAL_PRELOAD,
            PRIVATE_FLAG_HAS_FRAGILE_USER_DATA,
            PRIVATE_FLAG_ALLOW_CLEAR_USER_DATA_ON_FAILED_RESTORE,
            PRIVATE_FLAG_ALLOW_AUDIO_PLAYBACK_CAPTURE,
            PRIVATE_FLAG_REQUEST_LEGACY_EXTERNAL_STORAGE,
            PRIVATE_FLAG_ODM,
            PRIVATE_FLAG_ALLOW_NATIVE_HEAP_POINTER_TAGGING,
        ]
    )
    @Retention(AnnotationRetention.SOURCE)
    annotation class ApplicationInfoPrivateFlags

    /**
     * Represents the default policy. The actual policy used will depend on other properties of
     * the application, e.g. the target SDK version.
     */
    const val HIDDEN_API_ENFORCEMENT_DEFAULT: Int = -1

    /**
     * No API enforcement; the app can access the entire internal private API. Only for use by
     * system apps.
     */
    const val HIDDEN_API_ENFORCEMENT_DISABLED: Int = 0

    /**
     * No API enforcement, but enable the detection logic and warnings. Observed behaviour is the
     * same as {@link #HIDDEN_API_ENFORCEMENT_DISABLED} but you may see warnings in the log when
     * APIs are accessed.
     */
    const val HIDDEN_API_ENFORCEMENT_JUST_WARN: Int = 1

    /**
     * Dark grey list enforcement. Enforces the dark grey and black lists
     */
    const val HIDDEN_API_ENFORCEMENT_ENABLED: Int = 2

    /**
     * Blacklist enforcement only.
     */
    const val HIDDEN_API_ENFORCEMENT_BLACK: Int = 3

    @JvmStatic
    @ApplicationInfoPrivateFlags
    fun getPrivateFlags(info: ApplicationInfo): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Refine.unsafeCast<ApplicationInfoHidden>(info).privateFlags
        }
        return 0
    }

    @JvmStatic
    fun getSeInfo(info: ApplicationInfo): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Refine.unsafeCast<ApplicationInfoHidden>(info).seInfo + Refine.unsafeCast<ApplicationInfoHidden>(info).seInfoUser
        } else {
            @Suppress("DEPRECATION")
            Refine.unsafeCast<ApplicationInfoHidden>(info).seinfo
        }
    }

    @JvmStatic
    fun getPrimaryCpuAbi(info: ApplicationInfo): String? {
        return Refine.unsafeCast<ApplicationInfoHidden>(info).primaryCpuAbi
    }

    @JvmStatic
    fun getZygotePreloadName(info: ApplicationInfo): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return Refine.unsafeCast<ApplicationInfoHidden>(info).zygotePreloadName
        }
        return null
    }

    @JvmStatic
    fun getHiddenApiEnforcementPolicy(info: ApplicationInfo): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Refine.unsafeCast<ApplicationInfoHidden>(info).hiddenApiEnforcementPolicy
        }
        return HIDDEN_API_ENFORCEMENT_DISABLED
    }

    @JvmStatic
    fun isSystemApp(info: ApplicationInfo): Boolean {
        return (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
    }

    @JvmStatic
    fun isStopped(info: ApplicationInfo): Boolean {
        return (info.flags and ApplicationInfo.FLAG_STOPPED) != 0
    }

    @JvmStatic
    fun isInstalled(info: ApplicationInfo): Boolean {
        return (info.flags and ApplicationInfo.FLAG_INSTALLED) != 0 && info.processName != null && Paths.exists(info.publicSourceDir)
    }

    @JvmStatic
    fun isOnlyDataInstalled(info: ApplicationInfo): Boolean {
        return (info.flags and ApplicationInfo.FLAG_INSTALLED) == 0 && !(info.processName != null && Paths.exists(info.publicSourceDir))
    }

    @JvmStatic
    fun isTestOnly(info: ApplicationInfo): Boolean {
        return (info.flags and ApplicationInfo.FLAG_TEST_ONLY) != 0
    }

    @JvmStatic
    fun isSuspended(info: ApplicationInfo): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            (info.flags and ApplicationInfo.FLAG_SUSPENDED) != 0
        } else {
            // Not supported
            false
        }
    }

    @JvmStatic
    fun isHidden(info: ApplicationInfo): Boolean {
        return (getPrivateFlags(info) and PRIVATE_FLAG_HIDDEN) != 0
    }

    @JvmStatic
    fun isStaticSharedLibrary(info: ApplicationInfo): Boolean {
        // Android 8+
        return (getPrivateFlags(info) and PRIVATE_FLAG_STATIC_SHARED_LIBRARY) != 0
    }

    @JvmStatic
    fun isPrivileged(info: ApplicationInfo): Boolean {
        return (getPrivateFlags(info) and PRIVATE_FLAG_PRIVILEGED) != 0
    }

    @JvmStatic
    fun hasDomainUrls(info: ApplicationInfo): Boolean {
        return (getPrivateFlags(info) and PRIVATE_FLAG_HAS_DOMAIN_URLS) != 0
    }

    /**
     * {@link ApplicationInfo#loadLabel(PackageManager)} can throw NPE for uninstalled apps in unprivileged mode.
     *
     * @return App label or package name if an error is occurred.
     */
    @JvmStatic
    fun loadLabelSafe(info: ApplicationInfo, pm: PackageManager): CharSequence {
        if (Paths.exists(info.publicSourceDir)) {
            return info.loadLabel(pm)
        }
        return info.packageName
    }
}
