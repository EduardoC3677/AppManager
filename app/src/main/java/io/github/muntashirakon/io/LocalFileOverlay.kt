// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io

import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.RemoteException
import android.os.UserHandleHidden
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat
import java.io.File

internal object LocalFileOverlay {
    private val ROOT_FILES = arrayOf(
        "acct", "apex", "audit_filter_table", "bin", "bugreports",
        "cache", "carrier", "config", "d", "data", "data_mirror", "debug_ramdisk", "default.prop", "dev", "dpolicy",
        "dsp", "efs", "etc", "init", "init.container.rc", "init.environ.rc", "lib", "linkerconfig", "lost+found",
        "metadata", "mnt", "odm", "odm_dklm", "oem", "omr", "oneplus", "op1", "postinstall", "proc", "product",
        "sdcard", "second_state_resources", "sepolicy_version", "spu", "storage", "sys", "system", "system_ext",
        "vendor", "vendor_dlkm"
    )

    // There might be more, but these are what I've got for now (also, /system/apex)
    private val APEX_PKGS = arrayOf(
        "com.android.adbd",
        "com.android.adservices",
        "com.android.appsearch",
        "com.android.art",
        "com.android.art.debug",
        "com.android.art.release",
        "com.android.bluetooth",
        "com.android.bootanimation",
        "com.android.btservices",
        "com.android.car.framework",
        "com.android.cellbroadcast",
        "com.android.conscrypt",
        "com.android.devicelock",
        "com.android.extservices",
        "com.android.extservices.gms",
        "com.android.federatedcompute",
        "com.android.geotz",
        "com.android.gki",
        "com.android.healthfitness",
        "com.android.i18n",
        "com.android.ipsec",
        "com.android.media",
        "com.android.media.swcodec",
        "com.android.mediaprovider",
        "com.android.neuralnetworks",
        "com.android.ondevicepersonalization",
        "com.android.os.statsd",
        "com.android.permission",
        "com.android.permission.gms",
        "com.android.resolv",
        "com.android.rkpd",
        "com.android.runtime",
        "com.android.scheduling",
        "com.android.sdkext",
        "com.android.sepolicy",
        "com.android.telephony",
        "com.android.tethering",
        "com.android.tethering.inprocess",
        "com.android.tzdata",
        "com.android.uwb",
        "com.android.virt",
        "com.android.vndk",
        "com.android.vndk.current",
        "com.android.vndk.v" + Build.VERSION.SDK_INT,
        "com.android.wifi"
    )

    private val DATA_FILES = arrayOf(
        "app", "app-ephemeral", "app-lib", "cache", "dalvik-cache",
        "data", "local", "media", "misc", "misc_ce", "misc_de", "per_boot", "resource-cache", "rollback",
        "rollback-observer", "ss", "system", "system_ce", "system_de", "user", "user_ce", "user_de", "vendor",
        "vendor_ce", "vendor_de"
    )

    // Read-only here means whether this should be accessed by ReadOnlyDirectory, it has nothing to do with the actual mode of the file.
    private val sPathReadOnlyMap = HashMap<String, Array<String>?>()

    init {
        val userId = UserHandleHidden.myUserId()
        var appId: String
        try {
            appId = Class.forName("io.github.muntashirakon.AppManager.BuildConfig").getDeclaredField("APPLICATION_ID")[null] as String
        } catch (e: Exception) {
            appId = "io.github.muntashirakon.AppManager" + if (BuildConfig.DEBUG) ".debug" else ""
        }
        sPathReadOnlyMap["/"] = ROOT_FILES // Permission denied
        sPathReadOnlyMap["/apex"] = APEX_PKGS // Permission denied
        sPathReadOnlyMap["/data"] = DATA_FILES // Permission denied
        sPathReadOnlyMap["/data/app"] = null // Permission denied
        sPathReadOnlyMap["/data/data"] = arrayOf(appId) // Permission denied
        sPathReadOnlyMap["/data/local"] = arrayOf("tmp") // Permission denied
        sPathReadOnlyMap["/data/misc"] = arrayOf("ethernet", "gcov", "keychain", "profiles", "textclassifier", "user", "zoneinfo")
        sPathReadOnlyMap["/data/misc/user"] = arrayOf(appId)
        sPathReadOnlyMap["/data/misc/profiles"] = arrayOf("cur")
        sPathReadOnlyMap["/data/misc/profiles/cur"] = arrayOf(appId)
        sPathReadOnlyMap["/data/misc_ce"] = arrayOf(userId.toString()) // Permission denied
        sPathReadOnlyMap["/data/misc_de"] = arrayOf(userId.toString()) // Permission denied
        sPathReadOnlyMap["/data/user"] = arrayOf(userId.toString()) // Permission denied
        sPathReadOnlyMap["/data/user/$userId"] = arrayOf(appId) // Permission denied
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            sPathReadOnlyMap["/data/user_de"] = arrayOf(userId.toString()) // Permission denied
            sPathReadOnlyMap["/data/user_de/$userId"] = arrayOf(appId) // Permission denied
        }
        sPathReadOnlyMap["/mnt/sdcard"] = arrayOf("/storage/self/primary") // Permission denied, but redirects to /storage/self/primary
        sPathReadOnlyMap["/storage"] = arrayOf("emulated", "self") // Permission denied
        sPathReadOnlyMap["/storage/emulated"] = arrayOf(userId.toString()) // Permission denied
    }

    @JvmStatic
    fun getOverlayFile(file: ExtendedFile): ExtendedFile {
        return getOverlayFileOrNull(file) ?: file
    }

    @JvmStatic
    fun getOverlayFileOrNull(file: ExtendedFile): ExtendedFile? {
        val path = Paths.sanitize(file.absolutePath, false) ?: return null
        val children = listChildrenInternal(path)
        if (children != null) {
            // Check for potential alias
            for (child in children) {
                if (child.startsWith(File.separator)) {
                    return ReadOnlyLocalFile.getAliasInstance(path, child)
                }
            }
            // No alias found
            return ReadOnlyLocalFile(path)
        }
        // No overlay needed
        return null
    }

    @JvmStatic
    fun listChildren(file: File): Array<String>? {
        return listChildrenInternal(Paths.sanitize(file.absolutePath, false))
    }

    @JvmStatic
    private fun listChildrenInternal(path: String?): Array<String>? {
        if (path == null) {
            return null
        }
        if (path == "/data/app") {
            fetchDataAppPaths()
        }
        return sPathReadOnlyMap[path]
    }

    @JvmStatic
    @Suppress("SuspiciousRegexArgument") // Not on windows
    fun fetchDataAppPaths() {
        if (sPathReadOnlyMap["/data/app"] != null) {
            return
        }
        var applicationInfoList: List<ApplicationInfo>? = null
        try {
            applicationInfoList = PackageManagerCompat.getInstalledApplications(
                PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES or PackageManagerCompat.MATCH_UNINSTALLED_PACKAGES,
                UserHandleHidden.myUserId()
            )
        } catch (ignore: RemoteException) {
        }
        if (applicationInfoList == null) {
            return
        }
        val paths = HashMap<String, MutableList<String>?>()
        for (info in applicationInfoList) {
            if (info.publicSourceDir == null) {
                continue
            }
            val path = File(info.publicSourceDir).parent ?: continue
            if (!path.startsWith("/data/app/")) {
                continue
            }
            val relativePath = path.substring(10) // "/data/app/".length()
            // Remaining path is required one. It can contain either two or one paths
            val pathParts = relativePath.split(File.separator.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            when (pathParts.size) {
                1 -> paths[pathParts[0]] = null
                2 -> {
                    val part1 = pathParts[0]
                    var part2 = paths[part1]
                    if (part2 == null) {
                        part2 = ArrayList()
                        paths[part1] = part2
                    }
                    part2.add(pathParts[1])
                }
            }
        }
        // Update pathReadOnlyMap
        sPathReadOnlyMap["/data/app"] = paths.keys.toTypedArray()
        for (part1 in paths.keys) {
            val part2 = paths[part1] ?: continue
            sPathReadOnlyMap["/data/app/$part1"] = part2.toTypedArray()
        }
    }
}
