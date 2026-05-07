// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager

import android.app.Application
import android.content.Context
import android.os.Build
import android.sun.security.provider.JavaKeyStoreProvider
import androidx.annotation.Keep
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.HiltAndroidApp
import io.github.muntashirakon.AppManager.ipc.LocalServices
import io.github.muntashirakon.AppManager.misc.AMExceptionHandler
import io.github.muntashirakon.AppManager.utils.AppPref
import io.github.muntashirakon.AppManager.utils.ThreadUtils
import io.github.muntashirakon.AppManager.utils.Utils
import io.github.muntashirakon.AppManager.utils.appearance.AppearanceUtils
import io.github.muntashirakon.AppManager.utils.appearance.TypefaceUtil
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.security.Security
import dalvik.system.ZipPathValidator

@HiltAndroidApp
class AppManager : Application() {
    companion object {
        init {
            Shell.enableVerboseLogging = BuildConfig.DEBUG
            Shell.setDefaultBuilder(Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(10))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // We don't rely on the system to detect a zip slip attack
                ZipPathValidator.clearCallback()
            }
        }
    }

    @Keep
    override fun onCreate() {
        super.onCreate()
        Thread.setDefaultUncaughtExceptionHandler(AMExceptionHandler(this))
        AppearanceUtils.init(this)
        TypefaceUtil.replaceFontsWithSystem(this)
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        Security.addProvider(JavaKeyStoreProvider())
        Security.addProvider(BouncyCastleProvider())

        // Initialize AppPref on a background thread to prevent UI freezes on first access
        ThreadUtils.postOnBackgroundThread { AppPref.getInstance() }

        // Bind LocalServices early on a background thread to prevent UI blocking IPC calls
        ThreadUtils.postOnBackgroundThread {
            try {
                LocalServices.bindServices()
            } catch (e: Throwable) {
                android.util.Log.e("AppManager", "Failed to bind LocalServices early", e)
            }
        }
    }

    @Keep
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !Utils.isRoboUnitTest()) {
            HiddenApiBypass.addHiddenApiExemptions("L")
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_CRITICAL) {
            StaticDataset.cleanup()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        StaticDataset.cleanup()
    }

    override fun onTerminate() {
        super.onTerminate()
        StaticDataset.cleanup()
    }
}
