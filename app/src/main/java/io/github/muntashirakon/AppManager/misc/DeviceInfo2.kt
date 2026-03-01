// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.FeatureInfo
import android.content.pm.PackageManager
import android.opengl.GLES20
import android.os.*
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.format.DateFormat
import android.text.format.Formatter
import android.util.DisplayMetrics
import android.view.Display
import androidx.annotation.RequiresApi
import androidx.annotation.WorkerThread
import androidx.collection.ArrayMap
import androidx.core.os.LocaleListCompat
import androidx.core.util.Pair
import androidx.fragment.app.FragmentActivity
import com.android.internal.os.PowerProfile
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.StaticDataset
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat
import io.github.muntashirakon.AppManager.compat.UserHandleHidden
import io.github.muntashirakon.AppManager.misc.gles.EglCore
import io.github.muntashirakon.AppManager.misc.gles.OffscreenSurface
import io.github.muntashirakon.AppManager.runner.Runner
import io.github.muntashirakon.AppManager.runner.RunnerUtils
import io.github.muntashirakon.AppManager.users.UserInfo
import io.github.muntashirakon.AppManager.users.Users
import io.github.muntashirakon.AppManager.utils.*
import io.github.muntashirakon.AppManager.utils.UIUtils.getStyledKeyValue
import io.github.muntashirakon.AppManager.utils.UIUtils.getTitleText
import io.github.muntashirakon.proc.ProcFs
import io.github.muntashirakon.util.LocalizedString
import java.security.Provider
import java.security.Security
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DeviceInfo2(private val mActivity: FragmentActivity) : LocalizedString {
    val osVersion: String = Build.VERSION.RELEASE
    val bootloader: String = Build.BOOTLOADER
    val vm: String = getVmVersion()
    val kernel: String = getKernel()
    val brandName: String = Build.BRAND
    val model: String = Build.MODEL
    val board: String = Build.BOARD
    val manufacturer: String = Build.MANUFACTURER
    val maxSdk: Int = Build.VERSION.SDK_INT
    val minSdk: Int = SystemProperties.getInt("ro.build.version.min_supported_target_sdk", 0)

    var hasRoot: Boolean = false
    var selinux: Int = 0
    var encryptionStatus: String = ""
    var dmVerity: String = ""
    var verifiedBootState: String? = null
    var verifiedBootStateString: String = ""
    var avbVersion: String = ""
    var bootloaderState: String = ""
    var debuggable: Boolean = false
    val patchLevel: String?
    val securityProviders: Array<Provider> = Security.getProviders()
    val hardwareBackedFeatures: String?
    val strongBoxBackedFeatures: String?

    var cpuHardware: String? = null
    val supportedAbis: Array<String> = Build.SUPPORTED_ABIS
    var availableProcessors: Int = 0
    var openGlEsVersion: String = ""
    var vulkanVersion: String? = null

    val memoryInfo: ActivityManager.MemoryInfo = ActivityManager.MemoryInfo()

    var batteryPresent: Boolean = false
    var batteryCapacityMAh: Double = 0.0
    var batteryCapacityMAhAlt: Double = 0.0
    var batteryTechnology: String? = null
    var batteryCycleCount: Int = 0
    var batteryHealth: String = ""

    val displayDensityDpi: Int = StaticDataset.DEVICE_DENSITY
    val displayDensity: String = getDensity()
    var scalingFactor: Float = 0f
    var actualWidthPx: Int = 0
    var actualHeightPx: Int = 0
    var windowWidthPx: Int = 0
    var windowHeightPx: Int = 0
    var refreshRate: Float = 0f

    val systemLocales: LocaleListCompat = LocaleListCompat.getDefault()
    var users: List<UserInfo>? = null
    var userPackages: ArrayMap<Int, Pair<Int, Int>> = ArrayMap(1)
    var features: Array<FeatureInfo>

    private val mActivityManager: ActivityManager = mActivity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val mPm: PackageManager = mActivity.packageManager
    private val mDisplay: Display = getDisplay()

    init {
        patchLevel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) getSecurityPatch() else null
        features = mPm.systemAvailableFeatures
        hardwareBackedFeatures = getHardwareBackedFeatures()
        strongBoxBackedFeatures = getStrongBoxBackedFeatures()
    }

    @WorkerThread
    fun loadInfo() {
        hasRoot = RunnerUtils.isRootAvailable()
        selinux = getSelinuxStatus()
        encryptionStatus = getEncryptionStatus()
        if (mPm.hasSystemFeature(PackageManager.FEATURE_VERIFIED_BOOT)) {
            verifiedBootState = SystemProperties.get("ro.boot.verifiedbootstate", "")
            verifiedBootStateString = getVerifiedBootStateString(verifiedBootState!!)
            dmVerity = SystemProperties.get("ro.boot.veritymode", "")
            avbVersion = SystemProperties.get("ro.boot.avb_version", "")
            bootloaderState = SystemProperties.get("ro.boot.vbmeta.device_state", "")
        } else verifiedBootState = null
        debuggable = "1" == SystemProperties.get("ro.debuggable", "0")
        cpuHardware = getCpuHardware()
        availableProcessors = Runtime.getRuntime().availableProcessors()
        openGlEsVersion = Utils.getGlEsVersion(mActivityManager.deviceConfigurationInfo.reqGlEsVersion)
        vulkanVersion = Utils.getVulkanVersion(mPm)
        mActivityManager.getMemoryInfo(memoryInfo)
        getBatteryStats(mActivity)
        val displayMetrics = DisplayMetrics()
        mDisplay.getRealMetrics(displayMetrics)
        scalingFactor = displayMetrics.density
        actualWidthPx = displayMetrics.widthPixels
        actualHeightPx = displayMetrics.heightPixels
        mDisplay.getMetrics(displayMetrics)
        windowWidthPx = displayMetrics.widthPixels
        windowHeightPx = displayMetrics.heightPixels
        refreshRate = mDisplay.refreshRate
        users = Users.getAllUsers()
        users?.forEach { userPackages[it.id] = getPackageStats(it.id) }
    }

    override fun toLocalizedString(ctx: Context): CharSequence {
        val builder = SpannableStringBuilder()
        builder.append(getStyledKeyValue(ctx, R.string.os_version, osVersion)).append(", ")
            .append(getStyledKeyValue(ctx, "Build", Build.DISPLAY)).append("
")
            .append(getStyledKeyValue(ctx, R.string.bootloader, bootloader)).append(", ")
            .append(getStyledKeyValue(ctx, "VM", vm)).append("
")
            .append(getStyledKeyValue(ctx, R.string.kernel, kernel)).append("
")
            .append(getStyledKeyValue(ctx, R.string.brand_name, brandName)).append(", ")
            .append(getStyledKeyValue(ctx, R.string.model, model)).append("
")
            .append(getStyledKeyValue(ctx, R.string.board_name, board)).append(", ")
            .append(getStyledKeyValue(ctx, R.string.manufacturer, manufacturer)).append("
")
        builder.append("
").append(getTitleText(ctx, R.string.sdk)).append("
")
            .append(getStyledKeyValue(ctx, R.string.sdk_max, String.format(Locale.getDefault(), "%d", maxSdk)))
        if (minSdk != 0) {
            builder.append(", ").append(getStyledKeyValue(ctx, R.string.sdk_min, String.format(Locale.getDefault(), "%d", minSdk)))
        }
        builder.append("
")
        builder.append("
").append(getTitleText(ctx, R.string.security)).append("
")
        patchLevel?.let { builder.append(getStyledKeyValue(ctx, R.string.patch_level, it)).append("
") }
        builder.append(getStyledKeyValue(ctx, R.string.root, hasRoot.toString())).append(", ")
            .append(getStyledKeyValue(ctx, R.string.debuggable, debuggable.toString()))
            .append("
")
        if (selinux != 2) {
            builder.append(getStyledKeyValue(ctx, R.string.selinux, getString(if (selinux == 1) R.string.enforcing else R.string.permissive))).append(", ")
        }
        builder.append(getStyledKeyValue(ctx, R.string.encryption, encryptionStatus)).append("
")
        var verifiedBoot = false
        if (!TextUtils.isEmpty(verifiedBootState)) {
            verifiedBoot = true
            builder.append(getStyledKeyValue(ctx, R.string.verified_boot, verifiedBootState!!))
                .append(" ($verifiedBootStateString)")
        }
        if (!TextUtils.isEmpty(avbVersion)) {
            if (verifiedBoot) builder.append(", ")
            builder.append(getStyledKeyValue(ctx, R.string.android_verified_bootloader_version, avbVersion)).append("
")
        } else if (verifiedBoot) builder.append("
")
        var isDmVerity = false
        if (!TextUtils.isEmpty(dmVerity)) {
            isDmVerity = true
            builder.append(getStyledKeyValue(ctx, "dm-verity", dmVerity))
        }
        if (!TextUtils.isEmpty(bootloaderState)) {
            if (isDmVerity) builder.append(", ")
            builder.append(getStyledKeyValue(ctx, R.string.bootloader, bootloaderState)).append("
")
        } else if (isDmVerity) builder.append("
")
        val securityProvidersList = mutableListOf<CharSequence>()
        var hasAndroidKeyStore = false
        for (provider in securityProviders) {
            if ("AndroidKeyStore" == provider.name) hasAndroidKeyStore = true
            securityProvidersList.add("${provider.name} (v${provider.version})")
        }
        builder.append(getStyledKeyValue(ctx, R.string.security_providers, TextUtilsCompat.joinSpannable(", ", securityProvidersList))).append(".
")
        if (hasAndroidKeyStore) builder.append("
").append(getTitleText(ctx, "Android KeyStore")).append("
")
        val featuresSb = StringBuilder("Software")
        hardwareBackedFeatures?.let { featuresSb.append(", Hardware") }
        strongBoxBackedFeatures?.let { featuresSb.append(", StrongBox") }
        builder.append(getStyledKeyValue(ctx, R.string.features, featuresSb)).append("
")
        hardwareBackedFeatures?.let { builder.append("   ").append(getStyledKeyValue(ctx, "Hardware", it)).append("
") }
        strongBoxBackedFeatures?.let { builder.append("   ").append(getStyledKeyValue(ctx, "StrongBox", it)).append("
") }
        builder.append("
").append(getTitleText(ctx, R.string.cpu)).append("
")
        cpuHardware?.let { builder.append(getStyledKeyValue(ctx, R.string.hardware, it)).append("
") }
        builder.append(getStyledKeyValue(ctx, R.string.support_architectures, TextUtils.join(", ", supportedAbis))).append("
")
            .append(getStyledKeyValue(ctx, R.string.no_of_cores, String.format(Locale.getDefault(), "%d", availableProcessors))).append("
")
        builder.append("
").append(getTitleText(ctx, R.string.graphics)).append("
")
            .append(getGlInfo(ctx))
            .append(getStyledKeyValue(ctx, R.string.gles_version, openGlEsVersion)).append("
")
        vulkanVersion?.let { builder.append(getStyledKeyValue(ctx, R.string.vulkan_version, it)).append("
") }
        builder.append("
").append(getTitleText(ctx, R.string.memory)).append("
")
            .append(Formatter.formatFileSize(ctx, memoryInfo.totalMem)).append("
")
        if (batteryPresent || batteryCapacityMAh > 0) {
            builder.append("
").append(getTitleText(ctx, R.string.battery)).append("
")
            batteryTechnology?.let { builder.append(getStyledKeyValue(ctx, R.string.battery_technology, it)).append("
") }
            if (batteryCapacityMAh > 0) {
                builder.append(getStyledKeyValue(ctx, R.string.battery_capacity, batteryCapacityMAh.toString())).append(" mAh")
                if (batteryCapacityMAhAlt > 0) {
                    builder.append(" (est. ").append(String.format(Locale.ROOT, "%.1f", batteryCapacityMAhAlt)).append(" mAh)")
                }
                builder.append("
")
            } else if (batteryCapacityMAhAlt > 0) {
                builder.append(getStyledKeyValue(ctx, R.string.battery_capacity, String.format(Locale.ROOT, "%.1f", batteryCapacityMAhAlt)))
                    .append(" mAh (est.)").append("
")
            }
            if (batteryHealth.isNotEmpty()) {
                builder.append(getStyledKeyValue(ctx, R.string.battery_health, batteryHealth))
                if (batteryCycleCount > 0) {
                    builder.append(" ($batteryCycleCount cycles)")
                }
                builder.append("
")
            }
        }
        builder.append("
").append(getTitleText(ctx, R.string.screen)).append("
")
            .append(getStyledKeyValue(ctx, R.string.density, String.format(Locale.getDefault(), "%s (%d DPI)", displayDensity, displayDensityDpi))).append("
")
        builder.append(getStyledKeyValue(ctx, R.string.scaling_factor, scalingFactor.toString())).append("
")
            .append(getStyledKeyValue(ctx, R.string.size, "$actualWidthPx" + "px × $actualHeightPx" + "px
"))
        builder.append(getStyledKeyValue(ctx, R.string.window_size, "$windowWidthPx" + "px × $windowHeightPx" + "px
"))
        builder.append(getStyledKeyValue(ctx, R.string.refresh_rate, String.format(Locale.getDefault(), "%.1f Hz", refreshRate))).append("
")
        val localeStrings = mutableListOf<String>()
        for (i in 0 until systemLocales.size()) {
            localeStrings.add(systemLocales[i]!!.displayName)
        }
        builder.append("
").append(getTitleText(ctx, R.string.languages))
            .append("
").append(TextUtilsCompat.joinSpannable(", ", localeStrings)).append("
")
        users?.let {
            builder.append("
").append(getTitleText(ctx, R.string.users)).append("
")
            val userNames = it.map { u -> u.name ?: u.id.toString() }
            builder.append(String.format(Locale.getDefault(), "%d", it.size)).append(" (")
                .append(TextUtilsCompat.joinSpannable(", ", userNames)).append(")
")
            builder.append("
").append(getTitleText(ctx, R.string.apps)).append("
")
            for (user in it) {
                val packageSizes = userPackages[user.id] ?: continue
                if (packageSizes.first!! + packageSizes.second!! == 0) continue
                builder.append(getStyledKeyValue(ctx, R.string.user, user.toLocalizedString(ctx))).append("
   ")
                    .append(getStyledKeyValue(ctx, R.string.total_size, String.format(Locale.getDefault(), "%d", packageSizes.first!! + packageSizes.second!!))).append(", ")
                    .append(getStyledKeyValue(ctx, R.string.user, String.format(Locale.getDefault(), "%d", packageSizes.first))).append(", ")
                    .append(getStyledKeyValue(ctx, R.string.system, String.format(Locale.getDefault(), "%d", packageSizes.second)))
                    .append("
")
            }
        } ?: run {
            builder.append("
").append(getTitleText(ctx, R.string.apps)).append("
")
            userPackages[UserHandleHidden.myUserId()]?.let { packageSizes ->
                builder.append(getStyledKeyValue(ctx, R.string.total_size, String.format(Locale.getDefault(), "%d", packageSizes.first!! + packageSizes.second!!))).append(", ")
                    .append(getStyledKeyValue(ctx, R.string.user, String.format(Locale.getDefault(), "%d", packageSizes.first))).append(", ")
                    .append(getStyledKeyValue(ctx, R.string.system, String.format(Locale.getDefault(), "%d", packageSizes.second)))
                    .append("
")
            }
        }
        builder.append("
").append(getTitleText(ctx, R.string.features)).append("
")
        val featureStrings = mutableListOf<CharSequence>()
        for (info in features) {
            info.name?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && info.version != 0) {
                    featureStrings.add("$it (v${info.version})")
                } else featureStrings.add(it)
            }
        }
        featureStrings.sortWith { o1, o2 -> o1.toString().compareTo(o2.toString(), ignoreCase = true) }
        builder.append(TextUtilsCompat.joinSpannable("
", featureStrings)).append("
")
        return builder
    }

    private fun getGlInfo(ctx: Context): Spannable {
        val eglCore = EglCore()
        val surface = OffscreenSurface(eglCore, 1, 1)
        surface.makeCurrent()
        val gpu = "${GLES20.glGetString(GLES20.GL_VENDOR)} ${GLES20.glGetString(GLES20.GL_RENDERER)}"
        val sb = SpannableStringBuilder()
        sb.append(getStyledKeyValue(ctx, "GPU", gpu)).append("
")
        surface.release()
        eglCore.release()
        return sb
    }

    private var mBatteryStatusLock: CountDownLatch? = null
    private var mBatteryStatusBundle: Bundle? = null
    private val mBatteryStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            mBatteryStatusLock?.countDown()
            mBatteryStatusBundle = intent.extras
        }
    }

    @WorkerThread
    private fun getBatteryStats(ctx: Context) {
        batteryCapacityMAh = PowerProfile(ContextUtils.getContext()).batteryCapacity
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val data = ctx.registerReceiver(mBatteryStatusReceiver, filter)
        if (data != null) mBatteryStatusBundle = data.extras
        if (mBatteryStatusBundle == null) {
            mBatteryStatusLock = CountDownLatch(1)
            if (!mBatteryStatusLock!!.await(10, TimeUnit.SECONDS)) throw InterruptedException()
        }
        ctx.unregisterReceiver(mBatteryStatusReceiver)
        mBatteryStatusBundle?.let {
            batteryPresent = it.getBoolean(BatteryManager.EXTRA_PRESENT)
            batteryTechnology = it.getString(BatteryManager.EXTRA_TECHNOLOGY)
            val batteryCapacityUAh = it.getInt("charge_counter", 0)
            if (batteryCapacityUAh != 0) {
                batteryCapacityMAhAlt = batteryCapacityUAh / 1000.0
                val level = it.getInt(BatteryManager.EXTRA_LEVEL, 0)
                val scale = it.getInt(BatteryManager.EXTRA_SCALE, 0)
                val batteryPercent = if (scale > 0) (level * 100.0 / scale) else 0.0
                if (batteryPercent > 0) batteryCapacityMAhAlt = (batteryCapacityMAhAlt * 100.0 / batteryPercent)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                batteryCycleCount = it.getInt(BatteryManager.EXTRA_CYCLE_COUNT, 0)
            }
            batteryHealth = when (it.getInt(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
                BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
                BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "failure"
                BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
                else -> "Unknown"
            }
        }
    }

    private fun getDisplay(): Display {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) mActivity.display!!
        else mActivity.windowManager.defaultDisplay
    }

    private fun getDensity(): String {
        val dpi = StaticDataset.DEVICE_DENSITY
        var smallestDiff = Int.MAX_VALUE
        var density = StaticDataset.XXXHDPI
        for (i in 0 until StaticDataset.DENSITY_NAME_TO_DENSITY.size()) {
            val diff = Math.abs(dpi - StaticDataset.DENSITY_NAME_TO_DENSITY.valueAt(i))
            if (diff < smallestDiff) {
                smallestDiff = diff
                density = StaticDataset.DENSITY_NAME_TO_DENSITY.keyAt(i)
            }
        }
        return density
    }

    private fun getVmVersion(): String {
        var vm = "Dalvik"
        val vmVersion = System.getProperty("java.vm.version")
        if (vmVersion != null && vmVersion.startsWith("2")) vm = "ART"
        return vm
    }

    private fun getKernel(): String = System.getProperty("os.version") ?: ""

    @WorkerThread
    private fun getSelinuxStatus(): Int {
        if (SELinux.isSELinuxEnabled()) {
            val result = Runner.runCommand("getenforce")
            if (result.isSuccessful && result.output.trim() == "Permissive") return 0
            return 1
        }
        return 2
    }

    private fun getEncryptionStatus(): String {
        val state = SystemProperties.get("ro.crypto.state", "")
        return if ("encrypted" == state) {
            val encryptedMsg = getString(R.string.encrypted)
            when (SystemProperties.get("ro.crypto.type", "")) {
                "file" -> "$encryptedMsg (FBE)"
                "block" -> "$encryptedMsg (FDE)"
                else -> encryptedMsg
            }
        } else if ("unencrypted" == state) getString(R.string.unencrypted)
        else getString(R.string.state_unknown)
    }

    private fun getVerifiedBootStateString(color: String): String {
        return when (color) {
            "green" -> "verified"
            "yellow" -> "self-signed"
            "red" -> "failed"
            else -> "unverified"
        }
    }

    private fun getHardwareBackedFeatures(): String? {
        val f = getFeature("android.hardware.hardware_keystore") ?: return null
        val version = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) f.version else 0
        if (version < 40) return getString(R.string.state_unknown)
        val sb = StringBuilder("AES, HMAC, ECDSA, RSA")
        if (version >= 100) sb.append(", ECDH")
        if (version >= 200) sb.append(", Curve 25519")
        return sb.toString()
    }

    private fun getStrongBoxBackedFeatures(): String? {
        val f = getFeature("android.hardware.strongbox_keystore") ?: return null
        val version = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) f.version else 0
        if (version < 40) return getString(R.string.state_unknown)
        val sb = StringBuilder("AES, HMAC, ECDSA, RSA")
        if (version >= 100) sb.append(", ECDH")
        return sb.toString()
    }

    private fun getFeature(feature: String): FeatureInfo? = features.find { it.name == feature }

    private fun getCpuHardware(): String? {
        var model = CpuUtils.getCpuModel() ?: ProcFs.getInstance().cpuInfoHardware
        if (model == null) {
            val part1 = SystemProperties.get("ro.soc.manufacturer", "")
            val part2 = SystemProperties.get("ro.soc.model", "")
            if (part2.isNotEmpty()) return if (part1.isNotEmpty()) "$part1 $part2" else part2
            model = SystemProperties.get("ro.board.platform", "")
        }
        return if (model?.isNotEmpty() == true) model else null
    }

    private fun getPackageStats(userHandle: Int): Pair<Int, Int> {
        var systemApps = 0
        var userApps = 0
        try {
            val applicationInfoList = PackageManagerCompat.getInstalledApplications(PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, userHandle)
            for (info in applicationInfoList) {
                if ((info.flags and ApplicationInfo.FLAG_SYSTEM) == 1) systemApps++
                else userApps++
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        return Pair(userApps, systemApps)
    }

    private fun getString(strRes: Int): String = mActivity.getString(strRes)

    companion object {
        @JvmStatic
        @RequiresApi(Build.VERSION_CODES.M)
        fun getSecurityPatch(): String? {
            var patch = Build.VERSION.SECURITY_PATCH
            if (patch.isNotEmpty()) {
                try {
                    val template = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
                    val patchDate = template.parse(patch)!!
                    val format = DateFormat.getBestDateTimePattern(Locale.getDefault(), "dMMMMyyyy")
                    patch = DateFormat.format(format, patchDate).toString()
                } catch (ignore: ParseException) {}
                return patch
            }
            return null
        }
    }
}
