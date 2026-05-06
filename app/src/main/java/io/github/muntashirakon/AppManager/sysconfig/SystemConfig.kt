// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sysconfig

import android.content.ComponentName
import android.content.pm.FeatureInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.Process
import android.text.TextUtils
import android.util.ArrayMap
import android.util.SparseArray
import android.util.Xml
import androidx.annotation.IntRange
import androidx.annotation.WorkerThread
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.misc.OsEnvironment
import io.github.muntashirakon.AppManager.misc.SystemProperties
import io.github.muntashirakon.AppManager.utils.ArrayUtils
import io.github.muntashirakon.AppManager.utils.TextUtilsCompat
import io.github.muntashirakon.compat.xml.XmlUtils
import io.github.muntashirakon.io.IoUtils
import io.github.muntashirakon.io.Path
import io.github.muntashirakon.io.Paths
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.io.StringReader
import java.util.*

class SystemConfig {
    var mGlobalGids: IntArray? = null
    val mSystemPermissions = SparseArray<MutableSet<String>>()

    class SplitPermissionInfo(
        val splitPermission: String,
        val newPermissions: List<String>,
        @IntRange(from = 0) val targetSdk: Int
    )

    val mSplitPermissions = ArrayList<SplitPermissionInfo>()

    class SharedLibraryEntry(val name: String, val filename: String, val dependencies: Array<String>)

    val mSharedLibraries = ArrayMap<String, SharedLibraryEntry>()
    val mAvailableFeatures = ArrayMap<String, FeatureInfo>()
    val mUnavailableFeatures: MutableSet<String> = HashSet()

    class PermissionEntry(val name: String, val perUser: Boolean) {
        var gids: IntArray? = null
    }

    val mPermissions = ArrayMap<String, PermissionEntry>()
    val mAllowInPowerSaveExceptIdle: MutableSet<String> = HashSet()
    val mAllowInPowerSave: MutableSet<String> = HashSet()
    val mAllowInDataUsageSave: MutableSet<String> = HashSet()
    val mAllowUnthrottledLocation: MutableSet<String> = HashSet()
    val mAllowIgnoreLocationSettings: MutableSet<String> = HashSet()
    val mAllowImplicitBroadcasts: MutableSet<String> = HashSet()
    val mLinkedApps: MutableSet<String> = HashSet()
    val mSystemUserWhitelistedApps: MutableSet<String> = HashSet()
    val mSystemUserBlacklistedApps: MutableSet<String> = HashSet()
    val mDefaultVrComponents: MutableSet<ComponentName> = HashSet()
    val mBackupTransportWhitelist: MutableSet<ComponentName> = HashSet()
    val mPackageComponentEnabledState = ArrayMap<String, ArrayMap<String, Boolean>>()
    val mHiddenApiPackageWhitelist: MutableSet<String> = HashSet()
    val mDisabledUntilUsedPreinstalledCarrierApps: MutableSet<String> = HashSet()

    class CarrierAssociatedAppEntry(val packageName: String, val addedInSdk: Int) {
        companion object {
            const val SDK_UNSPECIFIED = -1
        }
    }

    val mDisabledUntilUsedPreinstalledCarrierAssociatedApps = ArrayMap<String, MutableList<CarrierAssociatedAppEntry>>()
    val mPrivAppPermissions = ArrayMap<String, MutableSet<String>>()
    val mPrivAppDenyPermissions = ArrayMap<String, MutableSet<String>>()
    val mVendorPrivAppPermissions = ArrayMap<String, MutableSet<String>>()
    val mVendorPrivAppDenyPermissions = ArrayMap<String, MutableSet<String>>()
    val mProductPrivAppPermissions = ArrayMap<String, MutableSet<String>>()
    val mProductPrivAppDenyPermissions = ArrayMap<String, MutableSet<String>>()
    val mSystemExtPrivAppPermissions = ArrayMap<String, MutableSet<String>>()
    val mSystemExtPrivAppDenyPermissions = ArrayMap<String, MutableSet<String>>()
    val mOemPermissions = ArrayMap<String, ArrayMap<String, Boolean>>()
    val mAllowedAssociations = ArrayMap<String, MutableSet<String>>()
    private val mBugreportWhitelistedPackages: MutableSet<String> = HashSet()
    private val mAppDataIsolationWhitelistedApps: MutableSet<String> = HashSet()
    var mPackageToUserTypeWhitelist = ArrayMap<String, MutableSet<String>>()
    var mPackageToUserTypeBlacklist = ArrayMap<String, MutableSet<String>>()
    private val mRollbackWhitelistedPackages: MutableSet<String> = HashSet()
    private val mWhitelistedStagedInstallers: MutableSet<String> = HashSet()
    private val mNamedActors = ArrayMap<String, ArrayMap<String, String>>()

    @WorkerThread
    constructor() { readAllPermissions() }

    @WorkerThread
    constructor(readPermissions: Boolean) {
        if (readPermissions) {
            Log.w(TAG, "Constructing a test SystemConfig")
            readAllPermissions()
        } else Log.w(TAG, "Constructing an empty test SystemConfig")
    }

    private fun readAllPermissions() {
        readPermissions(Paths.build(Environment.getRootDirectory(), "etc", "sysconfig"), ALLOW_ALL)
        readPermissions(Paths.build(Environment.getRootDirectory(), "etc", "permissions"), ALLOW_ALL)
        var vendorFlag = ALLOW_LIBS or ALLOW_FEATURES or ALLOW_PRIVAPP_PERMISSIONS or ALLOW_ASSOCIATIONS
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1) vendorFlag = vendorFlag or ALLOW_PERMISSIONS or ALLOW_APP_CONFIGS
        readPermissions(Paths.build(OsEnvironment.getVendorDirectory(), "etc", "sysconfig"), vendorFlag)
        readPermissions(Paths.build(OsEnvironment.getVendorDirectory(), "etc", "permissions"), vendorFlag)
        SystemProperties.get(VENDOR_SKU_PROPERTY, "").takeIf { it.isNotEmpty() }?.let { sku ->
            val dir = "sku_$sku"\nreadPermissions(Paths.build(OsEnvironment.getVendorDirectory(), "etc", "sysconfig", dir), vendorFlag)
            readPermissions(Paths.build(OsEnvironment.getVendorDirectory(), "etc", "permissions", dir), vendorFlag)
        }
        val odmFlag = vendorFlag
        readPermissions(Paths.build(OsEnvironment.getOdmDirectory(), "etc", "sysconfig"), odmFlag)
        readPermissions(Paths.build(OsEnvironment.getOdmDirectory(), "etc", "permissions"), odmFlag)
        SystemProperties.get(SKU_PROPERTY, "").takeIf { it.isNotEmpty() }?.let { sku ->
            val dir = "sku_$sku"\nreadPermissions(Paths.build(OsEnvironment.getOdmDirectory(), "etc", "sysconfig", dir), odmFlag)
            readPermissions(Paths.build(OsEnvironment.getOdmDirectory(), "etc", "permissions", dir), odmFlag)
        }
        val oemFlag = ALLOW_FEATURES or ALLOW_OEM_PERMISSIONS or ALLOW_ASSOCIATIONS
        readPermissions(Paths.build(OsEnvironment.getOemDirectory(), "etc", "sysconfig"), oemFlag)
        readPermissions(Paths.build(OsEnvironment.getOemDirectory(), "etc", "permissions"), oemFlag)
        readPermissions(Paths.build(OsEnvironment.getProductDirectory(), "etc", "sysconfig"), ALLOW_ALL)
        readPermissions(Paths.build(OsEnvironment.getProductDirectory(), "etc", "permissions"), ALLOW_ALL)
        readPermissions(Paths.build(OsEnvironment.getSystemExtDirectory(), "etc", "sysconfig"), ALLOW_ALL)
        readPermissions(Paths.build(OsEnvironment.getSystemExtDirectory(), "etc", "permissions"), ALLOW_ALL)
    }

    fun readPermissions(libraryDir: Path?, permissionFlag: Int) {
        if (libraryDir == null || !libraryDir.exists() || !libraryDir.isDirectory) {
            if (permissionFlag == ALLOW_ALL) Log.w(TAG, "No directory $libraryDir, skipping")
            return
        }
        var platformFile: Path? = null
        libraryDir.listFiles().forEach { f ->
            if (!f.isFile) return@forEach
            if (f.uri.path?.endsWith("etc/permissions/platform.xml") == true) { platformFile = f; return@forEach }
            if (f.uri.path?.endsWith(".xml") != true) { Log.i(TAG, "Non-xml file $f in $libraryDir directory, ignoring"); return@forEach }
            if (!f.canRead()) { Log.w(TAG, "Permissions library file $f cannot be read"); return@forEach }
            readPermissionsFromXml(f, permissionFlag)
        }
        platformFile?.let { readPermissionsFromXml(it, permissionFlag) }
    }

    private fun readPermissionsFromXml(permFile: Path, permissionFlag: Int) {
        val permReader = StringReader(permFile.contentAsString)
        try {
            val parser = Xml.newPullParser()
            parser.setInput(permReader)
            var type: Int
            while (parser.next().also { type = it } != XmlPullParser.START_TAG && type != XmlPullParser.END_DOCUMENT) {}
            if (type != XmlPullParser.START_TAG) throw XmlPullParserException("No start tag found")
            if (parser.name != "permissions" && parser.name != "config") throw XmlPullParserException("Unexpected start tag in $permFile: found ${parser.name}, expected 'permissions' or 'config'")
            val allowAll = permissionFlag == ALLOW_ALL
            val allowLibs = (permissionFlag and ALLOW_LIBS) != 0
            val allowFeatures = (permissionFlag and ALLOW_FEATURES) != 0
            val allowPermissions = (permissionFlag and ALLOW_PERMISSIONS) != 0
            val allowAppConfigs = (permissionFlag and ALLOW_APP_CONFIGS) != 0
            val allowPrivappPermissions = (permissionFlag and ALLOW_PRIVAPP_PERMISSIONS) != 0
            val allowOemPermissions = (permissionFlag and ALLOW_OEM_PERMISSIONS) != 0
            val allowApiWhitelisting = (permissionFlag and ALLOW_HIDDENAPI_WHITELISTING) != 0
            val allowAssociations = (permissionFlag and ALLOW_ASSOCIATIONS) != 0
            while (true) {
                XmlUtils.nextElement(parser)
                if (parser.eventType == XmlPullParser.END_DOCUMENT) break
                val name = parser.name ?: { XmlUtils.skipCurrentTag(parser); continue }()
                when (name) {
                    SysConfigType.TYPE_GROUP -> {
                        if (allowAll) {
                            parser.getAttributeValue(null, "gid")?.let { mGlobalGids = ArrayUtils.appendInt(mGlobalGids, Process.getGidForName(it)) }
                                ?: Log.w(TAG, "<$name> without gid in $permFile at ${parser.positionDescription}")
                        } else Log.w(TAG, "<$name> not allowed in partition of $permFile at ${parser.positionDescription}")
                        XmlUtils.skipCurrentTag(parser)
                    }
                    SysConfigType.TYPE_PERMISSION -> {
                        if (allowPermissions) {
                            val perm = parser.getAttributeValue(null, "name")
                            if (perm == null) { Log.w(TAG, "<$name> without name in $permFile at ${parser.positionDescription}"); XmlUtils.skipCurrentTag(parser) }
                            else readPermission(parser, perm.intern())
                        } else { Log.w(TAG, "<$name> not allowed in partition of $permFile at ${parser.positionDescription}"); XmlUtils.skipCurrentTag(parser) }
                    }
                    SysConfigType.TYPE_ASSIGN_PERMISSION -> {
                        if (allowPermissions) {
                            val p = parser.getAttributeValue(null, "name")
                            val u = parser.getAttributeValue(null, "uid")
                            if (p == null || u == null) { Log.w(TAG, "<$name> without name or uid in $permFile at ${parser.positionDescription}"); XmlUtils.skipCurrentTag(parser) }
                            else {
                                val uid = Process.getUidForName(u)
                                if (uid < 0) Log.w(TAG, "<$name> with unknown uid "$u" in $permFile at ${parser.positionDescription}")
                                else mSystemPermissions.get(uid, HashSet()).apply { add(p.intern()); if (mSystemPermissions[uid] == null) mSystemPermissions.put(uid, this) }
                                XmlUtils.skipCurrentTag(parser)
                            }
                        } else { Log.w(TAG, "<$name> not allowed in partition of $permFile at ${parser.positionDescription}"); XmlUtils.skipCurrentTag(parser) }
                    }
                    SysConfigType.TYPE_SPLIT_PERMISSION -> if (allowPermissions) readSplitPermission(parser, permFile) else { Log.w(TAG, "<$name> not allowed in partition of $permFile at ${parser.positionDescription}"); XmlUtils.skipCurrentTag(parser) }
                    SysConfigType.TYPE_LIBRARY -> {
                        if (allowLibs) {
                            val ln = parser.getAttributeValue(null, "name"); val lf = parser.getAttributeValue(null, "file"); val ld = parser.getAttributeValue(null, "dependency")
                            if (ln == null || lf == null) Log.w(TAG, "<$name> without name or file in $permFile at ${parser.positionDescription}")
                            else mSharedLibraries[ln] = SharedLibraryEntry(ln, lf, ld?.split(":")?.toTypedArray() ?: emptyArray())
                        } else Log.w(TAG, "<$name> not allowed in partition of $permFile at ${parser.positionDescription}")
                        XmlUtils.skipCurrentTag(parser)
                    }
                    SysConfigType.TYPE_FEATURE -> {
                        if (allowFeatures) {
                            parser.getAttributeValue(null, "name")?.let { addFeature(it, XmlUtils.readIntAttribute(parser, "version", 0)) }
                                ?: Log.w(TAG, "<$name> without name in $permFile at ${parser.positionDescription}")
                        } else Log.w(TAG, "<$name> not allowed in partition of $permFile at ${parser.positionDescription}")
                        XmlUtils.skipCurrentTag(parser)
                    }
                    SysConfigType.TYPE_UNAVAILABLE_FEATURE -> {
                        if (allowFeatures) parser.getAttributeValue(null, "name")?.let { mUnavailableFeatures.add(it) } ?: Log.w(TAG, "<$name> without name in $permFile at ${parser.positionDescription}")
                        else Log.w(TAG, "<$name> not allowed in partition of $permFile at ${parser.positionDescription}")
                        XmlUtils.skipCurrentTag(parser)
                    }
                    SysConfigType.TYPE_ALLOW_IN_POWER_SAVE_EXCEPT_IDLE -> { if (allowAll) parser.getAttributeValue(null, "package")?.let { mAllowInPowerSaveExceptIdle.add(it) } ?: Log.w(TAG, "<$name> without package in $permFile at ${parser.positionDescription}"); else Log.w(TAG, "<$name> not allowed in partition of $permFile at ${parser.positionDescription}"); XmlUtils.skipCurrentTag(parser) }
                    SysConfigType.TYPE_ALLOW_IN_POWER_SAVE -> { if (allowAll) parser.getAttributeValue(null, "package")?.let { mAllowInPowerSave.add(it) } ?: Log.w(TAG, "<$name> without package in $permFile at ${parser.positionDescription}"); else Log.w(TAG, "<$name> not allowed in partition of $permFile at ${parser.positionDescription}"); XmlUtils.skipCurrentTag(parser) }
                    SysConfigType.TYPE_ALLOW_IN_DATA_USAGE_SAVE -> { if (allowAll) parser.getAttributeValue(null, "package")?.let { mAllowInDataUsageSave.add(it) } ?: Log.w(TAG, "<$name> without package in $permFile at ${parser.positionDescription}"); else Log.w(TAG, "<$name> not allowed in partition of $permFile at ${parser.positionDescription}"); XmlUtils.skipCurrentTag(parser) }
                    SysConfigType.TYPE_ALLOW_UNTHROTTLED_LOCATION -> { if (allowAll) parser.getAttributeValue(null, "package")?.let { mAllowUnthrottledLocation.add(it) } ?: Log.w(TAG, "<$name> without package in $permFile at ${parser.positionDescription}"); else Log.w(TAG, "<$name> not allowed in partition of $permFile at ${parser.positionDescription}"); XmlUtils.skipCurrentTag(parser) }
                    SysConfigType.TYPE_ALLOW_IGNORE_LOCATION_SETTINGS -> { if (allowAll) parser.getAttributeValue(null, "package")?.let { mAllowIgnoreLocationSettings.add(it) } ?: Log.w(TAG, "<$name> without package in $permFile at ${parser.positionDescription}"); else Log.w(TAG, "<$name> not allowed in partition of $permFile at ${parser.positionDescription}"); XmlUtils.skipCurrentTag(parser) }
                    SysConfigType.TYPE_ALLOW_IMPLICIT_BROADCAST -> { if (allowAll) parser.getAttributeValue(null, "action")?.let { mAllowImplicitBroadcasts.add(it) } ?: Log.w(TAG, "<$name> without action in $permFile at ${parser.positionDescription}"); else Log.w(TAG, "<$name> not allowed in partition of $permFile at ${parser.positionDescription}"); XmlUtils.skipCurrentTag(parser) }
                    SysConfigType.TYPE_APP_LINK -> { if (allowAppConfigs) parser.getAttributeValue(null, "package")?.let { mLinkedApps.add(it) } ?: Log.w(TAG, "<$name> without package in $permFile at ${parser.positionDescription}"); else Log.w(TAG, "<$name> not allowed in partition of $permFile at ${parser.positionDescription}"); XmlUtils.skipCurrentTag(parser) }
                    SysConfigType.TYPE_SYSTEM_USER_WHITELISTED_APP -> { if (allowAppConfigs) parser.getAttributeValue(null, "package")?.let { mSystemUserWhitelistedApps.add(it) } ?: Log.w(TAG, "<$name> without package in $permFile at ${parser.positionDescription}"); else Log.w(TAG, "<$name> not allowed in partition of $permFile at ${parser.positionDescription}"); XmlUtils.skipCurrentTag(parser) }
                    SysConfigType.TYPE_SYSTEM_USER_BLACKLISTED_APP -> { if (allowAppConfigs) parser.getAttributeValue(null, "package")?.let { mSystemUserBlacklistedApps.add(it) } ?: Log.w(TAG, "<$name> without package in $permFile at ${parser.positionDescription}"); else Log.w(TAG, "<$name> not allowed in partition of $permFile at ${parser.positionDescription}"); XmlUtils.skipCurrentTag(parser) }
                    SysConfigType.TYPE_DEFAULT_ENABLED_VR_APP -> {
                        if (allowAppConfigs) {
                            val pkg = parser.getAttributeValue(null, "package"); val cls = parser.getAttributeValue(null, "class")
                            if (pkg == null || cls == null) Log.w(TAG, "<$name> without package or class in $permFile at ${parser.positionDescription}")
                            else mDefaultVrComponents.add(ComponentName(pkg, cls))
                        } else Log.w(TAG, "<$name> not allowed in partition of $permFile at ${parser.positionDescription}")
                        XmlUtils.skipCurrentTag(parser)
                    }
                    SysConfigType.TYPE_COMPONENT_OVERRIDE -> readComponentOverrides(parser, permFile)
                    SysConfigType.TYPE_BACKUP_TRANSPORT_WHITELISTED_SERVICE -> {
                        if (allowFeatures) {
                            parser.getAttributeValue(null, "service")?.let { s -> ComponentName.unflattenFromString(s)?.let { mBackupTransportWhitelist.add(it) } ?: Log.w(TAG, "<$name> with invalid service name $s in $permFile at ${parser.positionDescription}") } ?: Log.w(TAG, "<$name> without service in $permFile at ${parser.positionDescription}")
                        } else Log.w(TAG, "<$name> not allowed in partition of $permFile at ${parser.positionDescription}")
                        XmlUtils.skipCurrentTag(parser)
                    }
                    SysConfigType.TYPE_DISABLED_UNTIL_USED_PREINSTALLED_CARRIER_ASSOCIATED_APP -> {
                        if (allowAppConfigs) {
                            val pkg = parser.getAttributeValue(null, "package"); val cp = parser.getAttributeValue(null, "carrierAppPackage")
                            if (pkg == null || cp == null) Log.w(TAG, "<$name> without package or carrierAppPackage in $permFile at ${parser.positionDescription}")
                            else {
                                val sdk = parser.getAttributeValue(null, "addedInSdk")?.toIntOrNull() ?: CarrierAssociatedAppEntry.SDK_UNSPECIFIED
                                mDisabledUntilUsedPreinstalledCarrierAssociatedApps.getOrPut(cp) { mutableListOf() }.add(CarrierAssociatedAppEntry(pkg, sdk))
                            }
                        } else Log.w(TAG, "<$name> not allowed in partition of $permFile at ${parser.positionDescription}")
                        XmlUtils.skipCurrentTag(parser)
                    }
                    SysConfigType.TYPE_DISABLED_UNTIL_USED_PREINSTALLED_CARRIER_APP -> { if (allowAppConfigs) parser.getAttributeValue(null, "package")?.let { mDisabledUntilUsedPreinstalledCarrierApps.add(it) } ?: Log.w(TAG, "<$name> without package in $permFile at ${parser.positionDescription}"); else Log.w(TAG, "<$name> not allowed in partition of $permFile at ${parser.positionDescription}"); XmlUtils.skipCurrentTag(parser) }
                    SysConfigType.TYPE_PRIVAPP_PERMISSIONS -> {
                        if (allowPrivappPermissions) {
                            val path = permFile.filePath
                            when {
                                path.startsWith(OsEnvironment.getVendorDirectory().filePath + "/") || path.startsWith(OsEnvironment.getOdmDirectory().filePath + "/") -> readPrivAppPermissions(parser, mVendorPrivAppPermissions, mVendorPrivAppDenyPermissions)
                                path.startsWith(OsEnvironment.getProductDirectory().filePath + "/") -> readPrivAppPermissions(parser, mProductPrivAppPermissions, mProductPrivAppDenyPermissions)
                                path.startsWith(OsEnvironment.getSystemExtDirectory().filePath + "/") -> readPrivAppPermissions(parser, mSystemExtPrivAppPermissions, mSystemExtPrivAppDenyPermissions)
                                else -> readPrivAppPermissions(parser, mPrivAppPermissions, mPrivAppDenyPermissions)
                            }
                        } else { Log.w(TAG, "<$name> not allowed in partition of $permFile at ${parser.positionDescription}"); XmlUtils.skipCurrentTag(parser) }
                    }
                    SysConfigType.TYPE_OEM_PERMISSIONS -> if (allowOemPermissions) readOemPermissions(parser) else { Log.w(TAG, "<$name> not allowed in partition of $permFile at ${parser.positionDescription}"); XmlUtils.skipCurrentTag(parser) }
                    SysConfigType.TYPE_HIDDEN_API_WHITELISTED_APP -> { if (allowApiWhitelisting) parser.getAttributeValue(null, "package")?.let { mHiddenApiPackageWhitelist.add(it) } ?: Log.w(TAG, "<$name> without package in $permFile at ${parser.positionDescription}"); else Log.w(TAG, "<$name> not allowed in partition of $permFile at ${parser.positionDescription}"); XmlUtils.skipCurrentTag(parser) }
                    SysConfigType.TYPE_ALLOW_ASSOCIATION -> {
                        if (allowAssociations) {
                            val t = parser.getAttributeValue(null, "target"); val a = parser.getAttributeValue(null, "allowed")
                            if (t == null || a == null) { Log.w(TAG, "<$name> without target or allowed in $permFile at ${parser.positionDescription}"); XmlUtils.skipCurrentTag(parser) }
                            else { mAllowedAssociations.getOrPut(t.intern()) { HashSet() }.add(a.intern()); XmlUtils.skipCurrentTag(parser) }
                        } else { Log.w(TAG, "<$name> not allowed in partition of $permFile at ${parser.positionDescription}"); XmlUtils.skipCurrentTag(parser) }
                    }
                    SysConfigType.TYPE_APP_DATA_ISOLATION_WHITELISTED_APP -> { parser.getAttributeValue(null, "package")?.let { mAppDataIsolationWhitelistedApps.add(it) } ?: Log.w(TAG, "<$name> without package in $permFile at ${parser.positionDescription}"); XmlUtils.skipCurrentTag(parser) }
                    SysConfigType.TYPE_BUGREPORT_WHITELISTED -> { parser.getAttributeValue(null, "package")?.let { mBugreportWhitelistedPackages.add(it) } ?: Log.w(TAG, "<$name> without package in $permFile at ${parser.positionDescription}"); XmlUtils.skipCurrentTag(parser) }
                    SysConfigType.TYPE_INSTALL_IN_USER_TYPE -> readInstallInUserType(parser, mPackageToUserTypeWhitelist, mPackageToUserTypeBlacklist)
                    SysConfigType.TYPE_NAMED_ACTOR -> {
                        val ns = TextUtilsCompat.safeIntern(parser.getAttributeValue(null, "namespace")); val an = parser.getAttributeValue(null, "name"); val pn = TextUtilsCompat.safeIntern(parser.getAttributeValue(null, "package"))
                        if (TextUtils.isEmpty(ns)) Log.e(TAG, "<$name> without namespace in $permFile at ${parser.positionDescription}")
                        else if (TextUtils.isEmpty(an)) Log.e(TAG, "<$name> without actor name in $permFile at ${parser.positionDescription}")
                        else if (TextUtils.isEmpty(pn)) Log.e(TAG, "<$name> without package name in $permFile at ${parser.positionDescription}")
                        else if ("android".equals(ns, ignoreCase = true)) throw IllegalStateException("Defining $an as $pn for android namespace is not allowed")
                        else mNamedActors.getOrPut(ns) { ArrayMap() }.apply { if (containsKey(an)) throw IllegalStateException("Duplicate actor definition for $ns/$an; defined as both ${get(an)} and $pn"); put(an, pn) }
                        XmlUtils.skipCurrentTag(parser)
                    }
                    SysConfigType.TYPE_ROLLBACK_WHITELISTED_APP -> { parser.getAttributeValue(null, "package")?.let { mRollbackWhitelistedPackages.add(it) } ?: Log.w(TAG, "<$name> without package in $permFile at ${parser.positionDescription}"); XmlUtils.skipCurrentTag(parser) }
                    SysConfigType.TYPE_WHITELISTED_STAGED_INSTALLER -> { if (allowAppConfigs) parser.getAttributeValue(null, "package")?.let { mWhitelistedStagedInstallers.add(it) } ?: Log.w(TAG, "<$name> without package in $permFile at ${parser.positionDescription}"); else Log.w(TAG, "<$name> not allowed in partition of $permFile at ${parser.positionDescription}"); XmlUtils.skipCurrentTag(parser) }
                    else -> { Log.w(TAG, "Tag $name is unknown in $permFile at ${parser.positionDescription}"); XmlUtils.skipCurrentTag(parser) }
                }
            }
        } catch (e: Exception) { Log.w(TAG, "Got exception parsing permissions.", e) } finally { IoUtils.closeQuietly(permReader) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) addFeature(PackageManager.FEATURE_IPSEC_TUNNELS, 0)
        mUnavailableFeatures.forEach { removeFeature(it) }
    }

    private fun addFeature(name: String, version: Int) {
        val fi = mAvailableFeatures.getOrPut(name) { FeatureInfo().apply { this.name = name } }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) fi.version = Math.max(fi.version, version)
    }

    private fun removeFeature(name: String) { mAvailableFeatures.remove(name)?.let { Log.d(TAG, "Removed unavailable feature $name") } }

    private fun readPermission(parser: XmlPullParser, name: String) {
        if (mPermissions.containsKey(name)) throw IllegalStateException("Duplicate permission definition for $name")
        val perm = PermissionEntry(name, XmlUtils.readBooleanAttribute(parser, "perUser", false))
        mPermissions[name] = perm
        val depth = parser.depth
        var type: Int
        while (parser.next().also { type = it } != XmlPullParser.END_DOCUMENT && (type != XmlPullParser.END_TAG || parser.depth > depth)) {
            if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) continue
            if (parser.name == "group") {
                parser.getAttributeValue(null, "gid")?.let { perm.gids = ArrayUtils.appendInt(perm.gids, Process.getGidForName(it)) }
                    ?: Log.w(TAG, "<group> without gid at ${parser.positionDescription}")
            }
            XmlUtils.skipCurrentTag(parser)
        }
    }

    private fun readPrivAppPermissions(parser: XmlPullParser, grantMap: ArrayMap<String, MutableSet<String>>, denyMap: ArrayMap<String, MutableSet<String>>) {
        val pkg = parser.getAttributeValue(null, "package") ?: { Log.w(TAG, "package required for <privapp-permissions> at ${parser.positionDescription}"); return }()
        val grants = grantMap.getOrPut(pkg) { HashSet() }
        var denies: MutableSet<String>? = denyMap[pkg]
        val depth = parser.depth
        while (XmlUtils.nextElementWithin(parser, depth)) {
            val name = parser.name
            val p = parser.getAttributeValue(null, "name")
            if (name == "permission") p?.let { grants.add(it) } ?: Log.w(TAG, "name required for <permission> at ${parser.positionDescription}")
            else if (name == "deny-permission") p?.let { if (denies == null) denies = denyMap.getOrPut(pkg) { HashSet() }; denies!!.add(it) } ?: Log.w(TAG, "name required for <deny-permission> at ${parser.positionDescription}")
        }
    }

    private fun readInstallInUserType(parser: XmlPullParser, whitelist: ArrayMap<String, MutableSet<String>>, blacklist: ArrayMap<String, MutableSet<String>>) {
        val pkg = parser.getAttributeValue(null, "package") ?: { Log.w(TAG, "package required for <install-in-user-type> at ${parser.positionDescription}"); return }()
        val depth = parser.depth
        while (XmlUtils.nextElementWithin(parser, depth)) {
            val name = parser.name; val ut = parser.getAttributeValue(null, "user-type")
            if (ut == null) { Log.w(TAG, "user-type required for <install-in-user-type> at ${parser.positionDescription}"); continue }
            if (name == "install-in") whitelist.getOrPut(pkg) { HashSet() }.add(ut)
            else if (name == "do-not-install-in") blacklist.getOrPut(pkg) { HashSet() }.add(ut)
            else Log.w(TAG, "unrecognized tag in <install-in-user-type> at ${parser.positionDescription}")
        }
    }

    private fun readOemPermissions(parser: XmlPullParser) {
        val pkg = parser.getAttributeValue(null, "package") ?: { Log.w(TAG, "package required for <oem-permissions> at ${parser.positionDescription}"); return }()
        val perms = mOemPermissions.getOrPut(pkg) { ArrayMap() }
        val depth = parser.depth
        while (XmlUtils.nextElementWithin(parser, depth)) {
            val name = parser.name; val p = parser.getAttributeValue(null, "name")
            if (name == "permission") p?.let { perms[it] = true } ?: Log.w(TAG, "name required for <permission> at ${parser.positionDescription}")
            else if (name == "deny-permission") p?.let { perms[it] = false } ?: Log.w(TAG, "name required for <deny-permission> at ${parser.positionDescription}")
        }
    }

    private fun readSplitPermission(parser: XmlPullParser, permFile: Path) {
        val split = parser.getAttributeValue(null, "name") ?: { Log.w(TAG, "<split-permission> without name in $permFile at ${parser.positionDescription}"); XmlUtils.skipCurrentTag(parser); return }()
        val target = parser.getAttributeValue(null, "targetSdk")?.toIntOrNull() ?: (Build.VERSION_CODES.CUR_DEVELOPMENT + 1)
        val depth = parser.depth
        val newPerms = mutableListOf<String>()
        while (XmlUtils.nextElementWithin(parser, depth)) {
            if (parser.name == "new-permission") parser.getAttributeValue(null, "name")?.let { newPerms.add(it) } ?: Log.w(TAG, "name required for <new-permission> at ${parser.positionDescription}")
            else XmlUtils.skipCurrentTag(parser)
        }
        if (newPerms.isNotEmpty()) mSplitPermissions.add(SplitPermissionInfo(split, newPerms, target))
    }

    private fun readComponentOverrides(parser: XmlPullParser, permFile: Path) {
        val pkg = parser.getAttributeValue(null, "package") ?: { Log.w(TAG, "<component-override> without package in $permFile at ${parser.positionDescription}"); return }()
        val depth = parser.depth
        while (XmlUtils.nextElementWithin(parser, depth)) {
            if (parser.name == "component") {
                val cls = parser.getAttributeValue(null, "class"); val enabled = parser.getAttributeValue(null, "enabled")
                if (cls == null || enabled == null) { Log.w(TAG, "<component> without class or enabled in $permFile at ${parser.positionDescription}"); return }
                val finalCls = if (cls.startsWith(".")) pkg + cls else cls
                mPackageComponentEnabledState.getOrPut(pkg.intern()) { ArrayMap() }[finalCls.intern()] = enabled != "false"\n}
        }
    }

    companion object {
        const val TAG = "SystemConfig"\nprivate const val ALLOW_FEATURES = 0x01; private const val ALLOW_LIBS = 0x02; private const val ALLOW_PERMISSIONS = 0x04; private const val ALLOW_APP_CONFIGS = 0x08; private const val ALLOW_PRIVAPP_PERMISSIONS = 0x10; private const val ALLOW_OEM_PERMISSIONS = 0x20; private const val ALLOW_HIDDENAPI_WHITELISTING = 0x40; private const val ALLOW_ASSOCIATIONS = 0x80; private const val ALLOW_ALL = -1
        private const val SKU_PROPERTY = "ro.boot.product.hardware.sku"; private const val VENDOR_SKU_PROPERTY = "ro.boot.product.vendor.sku"
        private var sInstance: SystemConfig? = null
        @JvmStatic fun getInstance(): SystemConfig = synchronized(SystemConfig::class.java) { if (sInstance == null) sInstance = SystemConfig(); sInstance!! }
    }
}
