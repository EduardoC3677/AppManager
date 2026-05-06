// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat

import android.annotation.SuppressLint
import android.annotation.UserIdInt
import android.app.AppOpsManager
import android.app.AppOpsManagerHidden
import android.content.Context
import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import android.os.RemoteException
import androidx.annotation.IntRange
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.collection.SparseArrayCompat
import androidx.core.os.ParcelCompat
import com.android.internal.app.IAppOpsService
import dev.rikka.tools.refine.Refine
import io.github.muntashirakon.AppManager.ipc.ProxyBinder
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.utils.ExUtils
import io.github.muntashirakon.AppManager.utils.MiuiUtils
import java.lang.reflect.Field
import java.util.*

@SuppressLint("SoonBlockedPrivateApi")
class AppOpsManagerCompat {
    @IntRange(from = -1, to = 5)
    @Retention(AnnotationRetention.SOURCE)
    annotation class Mode

    @Retention(AnnotationRetention.SOURCE)
    annotation class OpFlags

    @Retention(AnnotationRetention.SOURCE)
    annotation class UidState

    class PackageOps : Parcelable {
        private val mPackageName: String?
        private val mUid: Int
        private val mEntries: List<OpEntry>

        constructor(packageName: String?, uid: Int, entries: List<OpEntry>) {
            mPackageName = packageName
            mUid = uid
            mEntries = entries
        }

        fun getPackageName(): String? = mPackageName
        fun getUid(): Int = mUid
        fun getOps(): List<OpEntry> = mEntries

        override fun toString(): String {
            return "PackageOps{" +
                    "mPackageName='" + mPackageName + '\'' +
                    ", mUid=" + mUid +
                    ", mEntries=" + mEntries +
                    '}'
        }

        override fun describeContents(): Int = 0

        override fun writeToParcel(dest: Parcel, flags: Int) {
            dest.writeString(mPackageName)
            dest.writeInt(mUid)
            dest.writeTypedList(mEntries)
        }

        protected constructor(`in`: Parcel) {
            mPackageName = `in`.readString()
            mUid = `in`.readInt()
            val entries = ArrayList<OpEntry>()
            `in`.readTypedList(entries, OpEntry.CREATOR)
            mEntries = entries
        }

        companion object {
            @JvmField
            val CREATOR: Parcelable.Creator<PackageOps> = object : Parcelable.Creator<PackageOps> {
                override fun createFromParcel(source: Parcel): PackageOps = PackageOps(source)
                override fun newArray(size: Int): Array<PackageOps?> = arrayOfNulls(size)
            }
        }
    }

    class OpEntry : Parcelable {
        private val mOpEntry: AppOpsManagerHidden.OpEntry

        constructor(opEntry: Parcelable) {
            mOpEntry = Refine.unsafeCast(opEntry)
        }

        protected constructor(`in`: Parcel) {
            mOpEntry = ParcelCompat.readParcelable(`in`, AppOpsManagerHidden.OpEntry::class.java.classLoader, AppOpsManagerHidden.OpEntry::class.java)!!
        }

        fun getOp(): Int = mOpEntry.op

        fun getName(): String = opToName(getOp())

        fun getPermission(): String? = opToPermission(getOp())

        @Mode
        fun getMode(): Int = mOpEntry.mode

        @Mode
        fun getDefaultMode(): Int = opToDefaultMode(getOp())

        fun getTime(): Long = getLastAccessTime(OP_FLAGS_ALL)

        fun getLastAccessTime(@OpFlags flags: Int): Long {
            return when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> mOpEntry.getLastAccessTime(flags)
                Build.VERSION.SDK_INT == Build.VERSION_CODES.P -> mOpEntry.lastAccessTime
                else -> mOpEntry.time
            }
        }

        fun getLastAccessForegroundTime(@OpFlags flags: Int): Long {
            return when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> mOpEntry.getLastAccessForegroundTime(flags)
                Build.VERSION.SDK_INT == Build.VERSION_CODES.P -> mOpEntry.lastAccessForegroundTime
                else -> mOpEntry.time
            }
        }

        fun getLastAccessBackgroundTime(@OpFlags flags: Int): Long {
            return when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> mOpEntry.getLastAccessBackgroundTime(flags)
                Build.VERSION.SDK_INT == Build.VERSION_CODES.P -> mOpEntry.lastAccessBackgroundTime
                else -> mOpEntry.time
            }
        }

        fun getLastAccessTime(@UidState fromUidState: Int, @UidState toUidState: Int, @OpFlags flags: Int): Long {
            return when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> mOpEntry.getLastAccessTime(fromUidState, toUidState, flags)
                Build.VERSION.SDK_INT == Build.VERSION_CODES.P -> mOpEntry.getLastTimeFor(fromUidState)
                else -> mOpEntry.time
            }
        }

        fun getRejectTime(): Long = getLastRejectTime(OP_FLAGS_ALL)

        fun getLastRejectTime(@OpFlags flags: Int): Long {
            return when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> mOpEntry.getLastRejectTime(flags)
                Build.VERSION.SDK_INT == Build.VERSION_CODES.P -> mOpEntry.lastRejectTime
                else -> mOpEntry.rejectTime
            }
        }

        fun getLastRejectForegroundTime(@OpFlags flags: Int): Long {
            return when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> mOpEntry.getLastRejectForegroundTime(flags)
                Build.VERSION.SDK_INT == Build.VERSION_CODES.P -> mOpEntry.lastRejectForegroundTime
                else -> mOpEntry.rejectTime
            }
        }

        fun getLastRejectBackgroundTime(@OpFlags flags: Int): Long {
            return when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> mOpEntry.getLastRejectBackgroundTime(flags)
                Build.VERSION.SDK_INT == Build.VERSION_CODES.P -> mOpEntry.lastRejectBackgroundTime
                else -> mOpEntry.rejectTime
            }
        }

        fun getLastRejectTime(@UidState fromUidState: Int, @UidState toUidState: Int, @OpFlags flags: Int): Long {
            return when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> mOpEntry.getLastRejectTime(fromUidState, toUidState, flags)
                Build.VERSION.SDK_INT == Build.VERSION_CODES.P -> mOpEntry.getLastRejectTimeFor(fromUidState)
                else -> mOpEntry.rejectTime
            }
        }

        fun isRunning(): Boolean = mOpEntry.isRunning

        fun getDuration(): Long = getLastDuration(MAX_PRIORITY_UID_STATE, MIN_PRIORITY_UID_STATE, OP_FLAGS_ALL)

        @RequiresApi(Build.VERSION_CODES.R)
        fun getLastDuration(@OpFlags flags: Int): Long = mOpEntry.getLastDuration(flags)

        fun getLastForegroundDuration(@OpFlags flags: Int): Long {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) mOpEntry.getLastForegroundDuration(flags) else mOpEntry.duration
        }

        fun getLastBackgroundDuration(@OpFlags flags: Int): Long {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) mOpEntry.getLastBackgroundDuration(flags) else mOpEntry.duration
        }

        fun getLastDuration(@UidState fromUidState: Int, @UidState toUidState: Int, @OpFlags flags: Int): Long {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) mOpEntry.getLastDuration(fromUidState, toUidState, flags) else mOpEntry.duration
        }

        @Deprecated("Deprecated in R")
        @RequiresApi(Build.VERSION_CODES.M)
        fun getProxyUid(): Int = mOpEntry.proxyUid

        @Deprecated("Deprecated in R")
        fun getProxyUid(@UidState uidState: Int, @OpFlags flags: Int): Int {
            return when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> mOpEntry.getProxyUid(uidState, flags)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> mOpEntry.proxyUid
                else -> 0
            }
        }

        @Deprecated("Deprecated in R")
        @RequiresApi(Build.VERSION_CODES.M)
        fun getProxyPackageName(): String? = mOpEntry.proxyPackageName

        @Deprecated("Deprecated in R")
        fun getProxyPackageName(@UidState uidState: Int, @OpFlags flags: Int): String? {
            return when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> mOpEntry.getProxyPackageName(uidState, flags)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> mOpEntry.proxyPackageName
                else -> null
            }
        }

        override fun describeContents(): Int = 0

        override fun writeToParcel(dest: Parcel, flags: Int) {
            dest.writeParcelable(mOpEntry, flags)
        }

        companion object {
            @JvmField
            val CREATOR: Parcelable.Creator<OpEntry> = object : Parcelable.Creator<OpEntry> {
                override fun createFromParcel(source: Parcel): OpEntry = OpEntry(source)
                override fun newArray(size: Int): Array<OpEntry?> = arrayOfNulls(size)
            }
        }
    }

    private val mAppOpsService: IAppOpsService = IAppOpsService.Stub.asInterface(ProxyBinder.getService(Context.APP_OPS_SERVICE))

    @Mode
    @Throws(RemoteException::class)
    fun checkOperation(op: Int, uid: Int, packageName: String?): Int {
        return mAppOpsService.checkOperation(op, uid, packageName)
    }

    @Mode
    fun checkOpNoThrow(op: Int, uid: Int, packageName: String?): Int {
        return try {
            val mode = mAppOpsService.checkOperation(op, uid, packageName)
            if (mode == AppOpsManager.MODE_FOREGROUND) AppOpsManager.MODE_ALLOWED else mode
        } catch (e: RemoteException) {
            ExUtils.rethrowFromSystemServer(e)
        }
    }

    @RequiresPermission(ManifestCompat.permission.GET_APP_OPS_STATS)
    @Throws(RemoteException::class)
    fun getOpsForPackage(uid: Int, packageName: String, ops: IntArray?): List<PackageOps> {
        val opEntries = ArrayList<OpEntry>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                addAllRelevantOpEntriesWithNoOverride(opEntries, mAppOpsService.getUidOps(uid, ops))
            } catch (e: NullPointerException) {
                Log.e("AppOpsManagerCompat", "Could not get app ops for UID %d", e, uid)
            }
        }
        addAllRelevantOpEntriesWithNoOverride(opEntries, mAppOpsService.getOpsForPackage(uid, packageName, ops))
        return listOf(PackageOps(packageName, uid, opEntries))
    }

    @RequiresPermission(ManifestCompat.permission.GET_APP_OPS_STATS)
    @Throws(RemoteException::class)
    fun getPackagesForOps(ops: IntArray?): List<PackageOps> {
        val opsForPackage = mAppOpsService.getPackagesForOps(ops)
        val packageOpsList = ArrayList<PackageOps>()
        if (opsForPackage != null) {
            for (o in opsForPackage) {
                packageOpsList.add(opsConvert(Refine.unsafeCast(o)))
            }
        }
        return packageOpsList
    }

    @RequiresPermission("android.permission.MANAGE_APP_OPS_MODES")
    @Throws(RemoteException::class)
    fun setMode(op: Int, uid: Int, packageName: String?, @Mode mode: Int) {
        if (isMiuiOp(op) || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            mAppOpsService.setMode(op, uid, packageName, mode)
        } else {
            mAppOpsService.setUidMode(op, uid, mode)
        }
    }

    @RequiresPermission("android.permission.MANAGE_APP_OPS_MODES")
    @Throws(RemoteException::class)
    fun resetAllModes(@UserIdInt reqUserId: Int, reqPackageName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            mAppOpsService.resetAllModes(reqUserId, reqPackageName)
        }
    }

    companion object {
        @JvmField val OP_FLAG_SELF: Int
        @JvmField val OP_FLAG_TRUSTED_PROXY: Int
        @JvmField val OP_FLAG_UNTRUSTED_PROXY: Int
        @JvmField val OP_FLAG_TRUSTED_PROXIED: Int
        @JvmField val OP_FLAG_UNTRUSTED_PROXIED: Int
        @JvmField val OP_FLAGS_ALL: Int
        @JvmField val OP_FLAGS_ALL_TRUSTED: Int

        @JvmField val UID_STATE_PERSISTENT: Int
        @JvmField val UID_STATE_TOP: Int
        @JvmField val UID_STATE_FOREGROUND_SERVICE_LOCATION: Int
        @JvmField val UID_STATE_FOREGROUND_SERVICE: Int
        @JvmField val UID_STATE_FOREGROUND: Int
        @JvmField val UID_STATE_BACKGROUND: Int
        @JvmField val UID_STATE_CACHED: Int

        @JvmField val MAX_PRIORITY_UID_STATE: Int
        @JvmField val MIN_PRIORITY_UID_STATE: Int

        private val sModes = SparseArrayCompat<String>()
        private val sOpToString: Array<String>

        const val OP_NONE = AppOpsManagerHidden.OP_NONE
        @JvmField val OP_RUN_IN_BACKGROUND: Int
        @JvmField val OP_RUN_ANY_IN_BACKGROUND: Int
        const val _NUM_OP = AppOpsManagerHidden._NUM_OP

        private val sPermToOp = HashMap<String, Int>()
        @JvmField val sOpWithoutPerms: List<Int>

        init {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                OP_FLAG_SELF = AppOpsManagerHidden.OP_FLAG_SELF
                OP_FLAG_TRUSTED_PROXY = AppOpsManagerHidden.OP_FLAG_TRUSTED_PROXY
                OP_FLAG_UNTRUSTED_PROXY = AppOpsManagerHidden.OP_FLAG_UNTRUSTED_PROXY
                OP_FLAG_TRUSTED_PROXIED = AppOpsManagerHidden.OP_FLAG_TRUSTED_PROXIED
                OP_FLAG_UNTRUSTED_PROXIED = AppOpsManagerHidden.OP_FLAG_UNTRUSTED_PROXIED
                OP_FLAGS_ALL = AppOpsManagerHidden.OP_FLAGS_ALL
                OP_FLAGS_ALL_TRUSTED = AppOpsManagerHidden.OP_FLAGS_ALL_TRUSTED
            } else {
                OP_FLAG_SELF = 0
                OP_FLAG_TRUSTED_PROXY = 0
                OP_FLAG_UNTRUSTED_PROXY = 0
                OP_FLAG_TRUSTED_PROXIED = 0
                OP_FLAG_UNTRUSTED_PROXIED = 0
                OP_FLAGS_ALL = 0
                OP_FLAGS_ALL_TRUSTED = 0
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                UID_STATE_PERSISTENT = AppOpsManagerHidden.UID_STATE_PERSISTENT
                UID_STATE_TOP = AppOpsManagerHidden.UID_STATE_TOP
                UID_STATE_FOREGROUND_SERVICE = AppOpsManagerHidden.UID_STATE_FOREGROUND_SERVICE
                UID_STATE_FOREGROUND = AppOpsManagerHidden.UID_STATE_FOREGROUND
                UID_STATE_BACKGROUND = AppOpsManagerHidden.UID_STATE_BACKGROUND
                UID_STATE_CACHED = AppOpsManagerHidden.UID_STATE_CACHED
            } else {
                UID_STATE_PERSISTENT = 0
                UID_STATE_TOP = 0
                UID_STATE_FOREGROUND_SERVICE = 0
                UID_STATE_FOREGROUND = 0
                UID_STATE_BACKGROUND = 0
                UID_STATE_CACHED = 0
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                UID_STATE_FOREGROUND_SERVICE_LOCATION = AppOpsManagerHidden.UID_STATE_FOREGROUND_SERVICE_LOCATION
                MAX_PRIORITY_UID_STATE = AppOpsManagerHidden.MAX_PRIORITY_UID_STATE
                MIN_PRIORITY_UID_STATE = AppOpsManagerHidden.MIN_PRIORITY_UID_STATE
            } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P) {
                UID_STATE_FOREGROUND_SERVICE_LOCATION = 0
                MAX_PRIORITY_UID_STATE = UID_STATE_PERSISTENT
                MIN_PRIORITY_UID_STATE = UID_STATE_CACHED
            } else {
                UID_STATE_FOREGROUND_SERVICE_LOCATION = 0
                MAX_PRIORITY_UID_STATE = 0
                MIN_PRIORITY_UID_STATE = 0
            }

            OP_RUN_IN_BACKGROUND = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) AppOpsManagerHidden.OP_RUN_IN_BACKGROUND else 0
            OP_RUN_ANY_IN_BACKGROUND = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) AppOpsManagerHidden.OP_RUN_ANY_IN_BACKGROUND else 0

            var opToString = aosp.libcore.util.EmptyArray.STRING
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                try {
                    val sOpToStringField = AppOpsManagerHidden::class.java.getDeclaredField("sOpToString")
                    sOpToStringField.isAccessible = true
                    opToString = sOpToStringField[null] as Array<String>
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            sOpToString = opToString

            for (field in AppOpsManager::class.java.declaredFields) {
                field.isAccessible = true
                if (field.type == Int::class.javaPrimitiveType && field.name.startsWith("MODE_")) {
                    try {
                        sModes.put(field.getInt(null), field.name)
                    } catch (ignore: Exception) {
                    }
                }
            }

            val opWithoutPerms = HashSet<Int>()
            for (i in 0 until _NUM_OP) {
                val permission = AppOpsManagerHidden.opToPermission(i)
                if (permission != null) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                        sPermToOp[permission] = i
                    }
                } else {
                    opWithoutPerms.add(AppOpsManagerHidden.opToSwitch(i))
                }
            }
            sOpWithoutPerms = ArrayList(opWithoutPerms)
        }

        @JvmStatic
        fun isMiuiOp(op: Int): Boolean {
            return try {
                MiuiUtils.isMiui() && op > AppOpsManagerHidden.MIUI_OP_START
            } catch (e: Throwable) {
                false
            }
        }

        @JvmStatic
        fun getAllOps(): List<Int> {
            val appOps = ArrayList<Int>()
            for (i in 0 until _NUM_OP) {
                appOps.add(i)
            }
            if (MiuiUtils.isMiui()) {
                try {
                    for (op in AppOpsManagerHidden.MIUI_OP_START + 1 until AppOpsManagerHidden.MIUI_OP_END) {
                        appOps.add(op)
                    }
                } catch (ignore: Exception) {
                }
            }
            return appOps
        }

        @JvmStatic
        fun getOpsWithoutPermissions(): List<Int> = sOpWithoutPerms

        @JvmStatic
        fun getModeConstants(): List<Int> {
            val list = ArrayList<Int>(sModes.size())
            for (i in 0 until sModes.size()) {
                list.add(sModes.keyAt(i))
            }
            return list
        }

        @JvmStatic
        fun modeToName(@IntRange(from = -1) mode: Int): String {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return AppOpsManagerHidden.modeToName(mode)
            }
            val fieldName = sModes.get(mode) ?: return "mode=$mode"\nreturn when (mode) {
                AppOpsManager.MODE_ALLOWED -> "allow"\nAppOpsManager.MODE_IGNORED -> "ignore"\nAppOpsManager.MODE_ERRORED -> "deny"
                else -> fieldName.substring(5).toLowerCase(Locale.ROOT)
            }
        }

        @JvmStatic
        fun opToSwitch(op: Int): Int = AppOpsManagerHidden.opToSwitch(op)

        @JvmStatic
        fun opToName(op: Int): String = AppOpsManagerHidden.opToName(op)

        @JvmStatic
        fun opToPermission(op: Int): String? = AppOpsManagerHidden.opToPermission(op)

        @JvmStatic
        fun opToDefaultMode(op: Int): Int {
            return try {
                AppOpsManagerHidden.opToDefaultMode(op)
            } catch (e: NoSuchMethodError) {
                AppOpsManagerHidden.opToDefaultMode(op, false)
            }
        }

        @JvmStatic
        fun permissionToOpCode(permission: String): Int {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                return AppOpsManagerHidden.permissionToOpCode(permission)
            }
            val boxedOpCode = sPermToOp[permission]
            return if (boxedOpCode == null || boxedOpCode >= _NUM_OP) OP_NONE else boxedOpCode
        }

        @JvmStatic
        fun permissionToOp(permission: String): String? {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                return AppOpsManagerHidden.permissionToOp(permission)
            }
            val opCode = permissionToOpCode(permission)
            return if (opCode == OP_NONE) null else sOpToString[opCode]
        }

        @JvmStatic
        fun getModeFromOpEntriesOrDefault(op: Int, opEntries: List<OpEntry>?): Int {
            if (op <= OP_NONE || op >= _NUM_OP || opEntries == null) {
                return AppOpsManager.MODE_IGNORED
            }
            for (opEntry in opEntries) {
                if (opEntry.getOp() == op) {
                    return opEntry.getMode()
                }
            }
            return opToDefaultMode(op)
        }

        @JvmStatic
        @Throws(RemoteException::class)
        fun getConfiguredOpsForPackage(appOpsManager: AppOpsManagerCompat, packageName: String, uid: Int): List<OpEntry> {
            val packageOpsList = appOpsManager.getOpsForPackage(uid, packageName, null)
            return if (packageOpsList.size == 1) packageOpsList[0].getOps() else emptyList()
        }

        private fun addAllRelevantOpEntriesWithNoOverride(opEntries: MutableList<OpEntry>, opsForPackage: List<Parcelable>?) {
            if (opsForPackage != null) {
                for (o in opsForPackage) {
                    val packageOps = opsConvert(Refine.unsafeCast(o))
                    for (opEntry in packageOps.getOps()) {
                        if (!opEntries.contains(opEntry)) {
                            opEntries.add(opEntry)
                        }
                    }
                }
            }
        }

        private fun opsConvert(packageOps: AppOpsManagerHidden.PackageOps): PackageOps {
            val packageName = packageOps.packageName
            val uid = packageOps.uid
            val opEntries = ArrayList<OpEntry>()
            for (opEntry in packageOps.ops) {
                opEntries.add(OpEntry(opEntry))
            }
            return PackageOps(packageName, uid, opEntries)
        }
    }
}
