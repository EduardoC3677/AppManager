// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ssaid

import android.os.Build
import android.os.RemoteException
import android.os.SystemClock
import android.text.TextUtils
import android.util.ArrayMap
import android.util.Base64
import androidx.annotation.GuardedBy
import androidx.annotation.RequiresApi
import io.github.muntashirakon.AppManager.BuildConfig
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.ssaid.SettingsState.Companion.MAX_BYTES_PER_APP_PACKAGE_LIMITED
import io.github.muntashirakon.AppManager.ssaid.SettingsState.Companion.MAX_BYTES_PER_APP_PACKAGE_UNLIMITED
import io.github.muntashirakon.AppManager.ssaid.SettingsState.Companion.SETTINGS_TYPE_CONFIG
import io.github.muntashirakon.AppManager.ssaid.SettingsState.Companion.SETTINGS_TYPE_GLOBAL
import io.github.muntashirakon.AppManager.ssaid.SettingsState.Companion.SETTINGS_TYPE_SECURE
import io.github.muntashirakon.AppManager.ssaid.SettingsState.Companion.SETTINGS_TYPE_SSAID
import io.github.muntashirakon.AppManager.ssaid.SettingsState.Companion.SETTINGS_TYPE_SYSTEM
import io.github.muntashirakon.AppManager.ssaid.SettingsState.Companion.SYSTEM_PACKAGE_NAME
import io.github.muntashirakon.compat.xml.TypedXmlPullParser
import io.github.muntashirakon.compat.xml.TypedXmlSerializer
import io.github.muntashirakon.compat.xml.Xml
import io.github.muntashirakon.io.AtomicExtendedFile
import io.github.muntashirakon.io.IoUtils
import io.github.muntashirakon.io.Path
import io.github.muntashirakon.io.Paths
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.*
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.*
import java.security.cert.CertificateException
import java.util.*
import javax.crypto.*
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.security.auth.DestroyFailedException
import javax.security.auth.x500.X500Principal

@RequiresApi(Build.VERSION_CODES.O)
class SettingsStateV26(private val mLock: Any, file: Path, key: Int, maxBytesPerAppPackage: Int) : SettingsState {
    private val mWriteLock = Any()

    @GuardedBy("mLock")
    private val mSettings = ArrayMap<String, Setting>()
    @GuardedBy("mLock")
    private val mNamespaceBannedHashes = ArrayMap<String, String>()
    @GuardedBy("mLock")
    private val mPackageToMemoryUsage: ArrayMap<String, Int>?
    @GuardedBy("mLock")
    private val mMaxBytesPerAppPackage: Int = maxBytesPerAppPackage
    @GuardedBy("mLock")
    private val mStatePersistFile: Path = file
    private val mNullSetting: Setting = object : Setting(null, null, false, null, null) {
        override fun isNull(): Boolean = true
    }
    @GuardedBy("mLock")
    private val mHistoricalOperations: MutableList<HistoricalOperation>?
    @GuardedBy("mLock")
    val mKey: Int = key
    @GuardedBy("mLock")
    private var mVersion = VERSION_UNDEFINED
    @GuardedBy("mLock")
    private var mDirty: Boolean = false
    @GuardedBy("mLock")
    private var mNextId: Long = 0
    @GuardedBy("mLock")
    private var mNextHistoricalOpIdx: Int = 0

    init {
        mPackageToMemoryUsage = if (maxBytesPerAppPackage == MAX_BYTES_PER_APP_PACKAGE_LIMITED) ArrayMap() else null
        mHistoricalOperations = if (BuildConfig.DEBUG) ArrayList(HISTORICAL_OPERATION_COUNT) else null
        synchronized(mLock) { readStateSyncLocked() }
    }

    @GuardedBy("mLock")
    fun getVersionLocked(): Int = mVersion

    fun getNullSetting(): Setting = mNullSetting

    @GuardedBy("mLock")
    fun setVersionLocked(version: Int) {
        if (version == mVersion) return
        mVersion = version
        scheduleWriteIfNeededLocked()
    }

    @GuardedBy("mLock")
    fun removeSettingsForPackageLocked(packageName: String) {
        var removedSomething = false
        for (i in mSettings.size - 1 downTo 0) {
            val setting = mSettings.valueAt(i)
            if (packageName == setting.packageName) {
                mSettings.removeAt(i)
                removedSomething = true
            }
        }
        if (removedSomething) scheduleWriteIfNeededLocked()
    }

    @GuardedBy("mLock")
    fun getSettingNamesLocked(): List<String> = mSettings.keys.toList()

    @GuardedBy("mLock")
    override fun getSettingLocked(name: String): Setting {
        if (TextUtils.isEmpty(name)) return mNullSetting
        return mSettings[name]?.let { Setting(it) } ?: mNullSetting
    }

    fun updateSettingLocked(name: String, value: String?, tag: String?, makeValue: Boolean, packageName: String): Boolean {
        if (!hasSettingLocked(name)) return false
        return insertSettingLocked(name, value, tag, makeValue, packageName)
    }

    @GuardedBy("mLock")
    fun resetSettingDefaultValueLocked(name: String) {
        val old = getSettingLocked(name)
        if (!old.isNull() && old.defaultValue != null) {
            val oldValue = old.value
            val oldDefault = old.defaultValue
            val new = Setting(name, old.value, null, old.packageName, old.tag, false, old.id)
            mSettings[name] = new
            updateMemoryUsagePerPackageLocked(new.packageName!!, oldValue, new.value, oldDefault, new.defaultValue)
            scheduleWriteIfNeededLocked()
        }
    }

    @GuardedBy("mLock")
    fun insertSettingOverrideableByRestoreLocked(name: String, value: String?, tag: String?, makeDefault: Boolean, packageName: String): Boolean {
        return insertSettingLocked(name, value, tag, makeDefault, false, packageName, true)
    }

    @GuardedBy("mLock")
    override fun insertSettingLocked(name: String, value: String?, tag: String?, makeDefault: Boolean, packageName: String): Boolean {
        return insertSettingLocked(name, value, tag, makeDefault, false, packageName, false)
    }

    @GuardedBy("mLock")
    fun insertSettingLocked(name: String, value: String?, tag: String?, makeDefault: Boolean, forceNonSystemPackage: Boolean, packageName: String, overrideableByRestore: Boolean): Boolean {
        if (TextUtils.isEmpty(name)) return false
        val oldState = mSettings[name]
        val oldValue = oldState?.value
        val oldDefaultValue = oldState?.defaultValue
        val newState: Setting
        if (oldState != null) {
            if (!oldState.update(value, makeDefault, packageName, tag, forceNonSystemPackage, overrideableByRestore)) return false
            newState = oldState
        } else {
            newState = Setting(name, value, makeDefault, packageName, tag, forceNonSystemPackage)
            mSettings[name] = newState
        }
        addHistoricalOperationLocked(HISTORICAL_OPERATION_UPDATE, newState)
        updateMemoryUsagePerPackageLocked(packageName, oldValue, value, oldDefaultValue, newState.defaultValue)
        scheduleWriteIfNeededLocked()
        return true
    }

    @GuardedBy("mLock")
    fun isNewConfigBannedLocked(prefix: String, keyValues: Map<String, String>): Boolean {
        val cleaned = removeNullValueOldStyle(keyValues)
        val bannedHash = mNamespaceBannedHashes[prefix] ?: return false
        return bannedHash == cleaned.hashCode().toString()
    }

    @GuardedBy("mLock")
    fun unbanAllConfigIfBannedConfigUpdatedLocked(prefix: String) {
        if (mNamespaceBannedHashes.containsKey(prefix)) {
            mNamespaceBannedHashes.clear()
            scheduleWriteIfNeededLocked()
        }
    }

    @GuardedBy("mLock")
    fun banConfigurationLocked(prefix: String?, keyValues: Map<String, String>) {
        if (prefix == null || keyValues.isEmpty()) return
        mNamespaceBannedHashes[prefix] = keyValues.hashCode().toString()
    }

    @GuardedBy("mLock")
    fun getAllConfigPrefixesLocked(): Set<String> {
        val prefixes = HashSet<String>()
        for (i in 0 until mSettings.size) prefixes.add(mSettings.keyAt(i).split("/")[0] + "/")
        return prefixes
    }

    @GuardedBy("mLock")
    fun setSettingsLocked(prefix: String, keyValues: Map<String, String>, packageName: String): List<String> {
        val changed = mutableListOf<String>()
        val it = mSettings.entries.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            val key = entry.key
            if (key != null && key.startsWith(prefix) && !keyValues.containsKey(key)) {
                it.remove()
                addHistoricalOperationLocked(HISTORICAL_OPERATION_DELETE, entry.value)
                changed.add(key)
            }
        }
        for ((key, value) in keyValues) {
            val state = mSettings[key]
            if (state == null) {
                val newState = Setting(key, value, false, packageName, null)
                mSettings[key] = newState
                changed.add(key)
                addHistoricalOperationLocked(HISTORICAL_OPERATION_UPDATE, newState)
            } else if (state.value != value) {
                state.update(value, false, packageName, null, true, false)
                changed.add(key)
                addHistoricalOperationLocked(HISTORICAL_OPERATION_UPDATE, state)
            }
        }
        if (changed.isNotEmpty()) scheduleWriteIfNeededLocked()
        return changed
    }

    fun persistSyncLocked() { doWriteState() }

    @GuardedBy("mLock")
    fun deleteSettingLocked(name: String): Boolean {
        if (TextUtils.isEmpty(name) || !hasSettingLocked(name)) return false
        val old = mSettings.remove(name)!!
        updateMemoryUsagePerPackageLocked(old.packageName!!, old.value, null, old.defaultValue, null)
        addHistoricalOperationLocked(HISTORICAL_OPERATION_DELETE, old)
        scheduleWriteIfNeededLocked()
        return true
    }

    @GuardedBy("mLock")
    fun resetSettingLocked(name: String): Boolean {
        if (TextUtils.isEmpty(name) || !hasSettingLocked(name)) return false
        val setting = mSettings[name]!!
        val old = Setting(setting)
        val oldVal = setting.value; val oldDef = setting.defaultValue
        if (!setting.reset()) return false
        updateMemoryUsagePerPackageLocked(setting.packageName!!, oldVal, setting.value, oldDef, setting.defaultValue)
        addHistoricalOperationLocked(HISTORICAL_OPERATION_RESET, old)
        scheduleWriteIfNeededLocked()
        return true
    }

    @GuardedBy("mLock")
    fun destroyLocked(callback: Runnable?) {
        if (callback != null) {
            if (mDirty) doWriteState()
            callback.run()
        }
    }

    @GuardedBy("mLock")
    private fun addHistoricalOperationLocked(type: String, setting: Setting?) {
        if (mHistoricalOperations == null) return
        val op = HistoricalOperation(SystemClock.elapsedRealtime(), type, setting?.let { Setting(it) })
        if (mNextHistoricalOpIdx >= mHistoricalOperations.size) mHistoricalOperations.add(op)
        else mHistoricalOperations[mNextHistoricalOpIdx] = op
        mNextHistoricalOpIdx = (mNextHistoricalOpIdx + 1) % HISTORICAL_OPERATION_COUNT
    }

    @GuardedBy("mLock")
    private fun updateMemoryUsagePerPackageLocked(packageName: String, oldValue: String?, newValue: String?, oldDefaultValue: String?, newDefaultValue: String?) {
        if (mMaxBytesPerAppPackage == MAX_BYTES_PER_APP_PACKAGE_UNLIMITED) return
        if (SYSTEM_PACKAGE_NAME == packageName) return
        val ovs = oldValue?.length ?: 0; val nvs = newValue?.length ?: 0; val odvs = oldDefaultValue?.length ?: 0; val ndvs = newDefaultValue?.length ?: 0
        val delta = nvs + ndvs - ovs - odvs
        val currentSize = mPackageToMemoryUsage!![packageName] ?: 0
        val newSize = Math.max(currentSize + delta, 0)
        if (newSize > mMaxBytesPerAppPackage) throw IllegalStateException("Too many settings for package $packageName")
        mPackageToMemoryUsage[packageName] = newSize
    }

    @GuardedBy("mLock")
    private fun hasSettingLocked(name: String): Boolean = mSettings.containsKey(name)

    @GuardedBy("mLock")
    private fun scheduleWriteIfNeededLocked() {
        if (!mDirty) {
            mDirty = true
            writeStateAsyncLocked()
        }
    }

    @GuardedBy("mLock")
    private fun writeStateAsyncLocked() { doWriteState() }

    private fun doWriteState() {
        var wroteState = false
        val version: Int; val settings: ArrayMap<String, Setting>; val hashes: ArrayMap<String, String>
        synchronized(mLock) {
            version = mVersion; settings = ArrayMap(mSettings); hashes = ArrayMap(mNamespaceBannedHashes)
            mDirty = false
        }
        synchronized(mWriteLock) {
            val destination = AtomicExtendedFile(mStatePersistFile.file!!)
            var out: FileOutputStream? = null
            try {
                out = destination.startWrite()
                val serializer = Xml.resolveSerializer(out)
                serializer.startDocument(null, true)
                serializer.startTag(null, TAG_SETTINGS).attributeInt(null, ATTR_VERSION, version)
                for (i in 0 until settings.size) {
                    val s = settings.valueAt(i)
                    writeSingleSetting(mVersion, serializer, s.id!!, s.name!!, s.value, s.defaultValue, s.packageName!!, s.tag, s.isDefaultFromSystem, s.isValuePreservedInRestore)
                }
                serializer.endTag(null, TAG_SETTINGS)
                serializer.startTag(null, TAG_NAMESPACE_HASHES)
                for (i in 0 until hashes.size) {
                    writeSingleNamespaceHash(serializer, hashes.keyAt(i), hashes.valueAt(i))
                }
                serializer.endTag(null, TAG_NAMESPACE_HASHES).endDocument()
                destination.finishWrite(out)
                wroteState = true
            } catch (t: Throwable) {
                Log.e(LOG_TAG, "Failed to write settings", t)
                destination.failWrite(out)
            } finally { IoUtils.closeQuietly(out) }
        }
        if (wroteState) synchronized(mLock) { addHistoricalOperationLocked(HISTORICAL_OPERATION_PERSIST, null) }
    }

    @GuardedBy("mLock")
    private fun readStateSyncLocked() {
        var isStream: FileInputStream? = null
        val file = AtomicExtendedFile(mStatePersistFile.file!!)
        try {
            isStream = file.openRead()
            if (parseStateFromXmlStreamLocked(isStream)) return
        } catch (e: Exception) {
            Log.w(LOG_TAG, "No settings state $mStatePersistFile")
            addHistoricalOperationLocked(HISTORICAL_OPERATION_INITIALIZE, null)
            return
        }
        val fallback = Paths.get(mStatePersistFile.filePath + FALLBACK_FILE_SUFFIX)
        try {
            isStream = AtomicExtendedFile(fallback.file!!).openRead()
            if (parseStateFromXmlStreamLocked(isStream)) {
                try { IoUtils.copy(fallback, mStatePersistFile) } catch (ignore: IOException) {}
            } else throw IllegalStateException("Failed parsing settings file $mStatePersistFile")
        } catch (e: Exception) { throw IllegalStateException("No fallback file found for $mStatePersistFile", e) }
    }

    @GuardedBy("mLock")
    private fun parseStateFromXmlStreamLocked(isStream: InputStream): Boolean {
        return try {
            val parser = Xml.resolvePullParser(isStream)
            parseStateLocked(parser)
            true
        } catch (e: Exception) { false } finally { IoUtils.closeQuietly(isStream) }
    }

    private fun parseStateLocked(parser: TypedXmlPullParser) {
        val depth = parser.depth
        var type: Int
        while (parser.next().also { type = it } != XmlPullParser.END_DOCUMENT && (type != XmlPullParser.END_TAG || parser.depth > depth)) {
            if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) continue
            when (parser.name) {
                TAG_SETTINGS -> parseSettingsLocked(parser)
                TAG_NAMESPACE_HASHES -> parseNamespaceHash(parser)
            }
        }
    }

    @GuardedBy("mLock")
    private fun parseSettingsLocked(parser: TypedXmlPullParser) {
        mVersion = parser.getAttributeInt(null, ATTR_VERSION)
        val depth = parser.depth
        var type: Int
        while (parser.next().also { type = it } != XmlPullParser.END_DOCUMENT && (type != XmlPullParser.END_TAG || parser.depth > depth)) {
            if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) continue
            if (parser.name == TAG_SETTING) {
                val id = parser.getAttributeValue(null, ATTR_ID)
                val name = parser.getAttributeValue(null, ATTR_NAME)
                val value = getValueAttribute(parser, ATTR_VALUE, ATTR_VALUE_BASE64)
                val pkg = parser.getAttributeValue(null, ATTR_PACKAGE)
                val def = getValueAttribute(parser, ATTR_DEFAULT_VALUE, ATTR_DEFAULT_VALUE_BASE64)
                val preserved = parser.getAttributeBoolean(null, ATTR_PRESERVE_IN_RESTORE, false)
                var tag: String? = null; var sys = false
                if (def != null) {
                    sys = parser.getAttributeBoolean(null, ATTR_DEFAULT_SYS_SET)
                    tag = getValueAttribute(parser, ATTR_TAG, ATTR_TAG_BASE64)
                }
                mSettings[name] = Setting(name, value, def, pkg, tag, sys, id, preserved)
            }
        }
    }

    @GuardedBy("mLock")
    private fun parseNamespaceHash(parser: TypedXmlPullParser) {
        val depth = parser.depth
        var type: Int
        while (parser.next().also { type = it } != XmlPullParser.END_DOCUMENT && (type != XmlPullParser.END_TAG || parser.depth > depth)) {
            if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) continue
            if (parser.name == TAG_NAMESPACE_HASH) {
                mNamespaceBannedHashes[parser.getAttributeValue(null, ATTR_NAMESPACE)] = parser.getAttributeValue(null, ATTR_BANNED_HASH)
            }
        }
    }

    private fun getValueAttribute(parser: TypedXmlPullParser, attr: String, base64Attr: String): String? {
        return if (mVersion >= SETTINGS_VERSION_NEW_ENCODING) {
            parser.getAttributeValue(null, attr) ?: parser.getAttributeValue(null, base64Attr)?.let { base64Decode(it) }
        } else parser.getAttributeValue(null, attr).takeIf { it != NULL_VALUE_OLD_STYLE }
    }

    private class HistoricalOperation(val mTimestamp: Long, val mOperation: String, val mSetting: Setting?)

    inner class Setting : SettingsState.Setting {
        var name: String? = null; override var value: String? = null; var defaultValue: String? = null; var packageName: String? = null; var id: String? = null; var tag: String? = null
        var isDefaultFromSystem: Boolean = false; var isValuePreservedInRestore: Boolean = false

        constructor(other: Setting) {
            name = other.name; value = other.value; defaultValue = other.defaultValue; packageName = other.packageName; id = other.id; isDefaultFromSystem = other.isDefaultFromSystem; tag = other.tag; isValuePreservedInRestore = other.isValuePreservedInRestore
        }

        constructor(name: String, value: String?, makeDefault: Boolean, packageName: String, tag: String?, forceNonSystemPackage: Boolean = false) {
            this.name = name; update(value, makeDefault, packageName, tag, forceNonSystemPackage, true)
        }

        constructor(name: String, value: String?, defaultValue: String?, packageName: String, tag: String?, fromSystem: Boolean, id: String, isPreserved: Boolean = false) {
            this.name = name; this.value = if (NULL_VALUE == value) null else value; this.tag = tag; this.defaultValue = defaultValue; this.packageName = packageName; this.id = id; this.isDefaultFromSystem = fromSystem; this.isValuePreservedInRestore = isPreserved
            mNextId = Math.max(mNextId, id.toLong() + 1)
        }

        override fun isNull(): Boolean = false

        fun reset(): Boolean = update(defaultValue, false, packageName!!, null, true, true, true)

        fun update(value: String?, setDefault: Boolean, packageName: String, tag: String?, forceNonSystemPackage: Boolean, overrideableByRestore: Boolean, resetToDefault: Boolean = false): Boolean {
            val newVal = if (NULL_VALUE == value) null else value
            var newDef = defaultValue; var newSys = isDefaultFromSystem
            if (setDefault) {
                if (newVal != defaultValue) { newDef = newVal; if (newDef == null) { tag == null; newSys = false } }
                if (!newSys && newVal != null) newSys = true
            }
            val preserved = if (resetToDefault) false else if (newVal != null && newVal == this.value && SYSTEM_PACKAGE_NAME == packageName) false else isValuePreservedInRestore || !overrideableByRestore
            if (newVal == this.value && newDef == this.defaultValue && packageName == this.packageName && tag == this.tag && newSys == isDefaultFromSystem && preserved == isValuePreservedInRestore) return false
            this.value = newVal; this.tag = tag; this.defaultValue = newDef; this.packageName = packageName; this.id = (mNextId++).toString(); this.isDefaultFromSystem = newSys; this.isValuePreservedInRestore = preserved
            return true
        }
    }

    companion object {
        private const val LOG_TAG = "SettingsStateV26"; const val SETTINGS_VERSION_NEW_ENCODING = 121; const val VERSION_UNDEFINED = -1; const val FALLBACK_FILE_SUFFIX = ".fallback"; private const val TAG_SETTINGS = "settings"; private const val TAG_SETTING = "setting"; private const val ATTR_PACKAGE = "package"; private const val ATTR_DEFAULT_SYS_SET = "defaultSysSet"; private const val ATTR_TAG = "tag"; private const val ATTR_TAG_BASE64 = "tagBase64"; private const val ATTR_VERSION = "version"; private const val ATTR_ID = "id"; private const val ATTR_NAME = "name"; private const val TAG_NAMESPACE_HASHES = "namespaceHashes"; private const val TAG_NAMESPACE_HASH = "namespaceHash"; private const val ATTR_NAMESPACE = "namespace"; private const val ATTR_BANNED_HASH = "bannedHash"; private const val ATTR_PRESERVE_IN_RESTORE = "preserve_in_restore"; private const val ATTR_VALUE = "value"; private const val ATTR_DEFAULT_VALUE = "defaultValue"; private const val ATTR_VALUE_BASE64 = "valueBase64"; private const val ATTR_DEFAULT_VALUE_BASE64 = "defaultValueBase64"; private const val NULL_VALUE_OLD_STYLE = "null"; private const val HISTORICAL_OPERATION_COUNT = 20; private const val HISTORICAL_OPERATION_UPDATE = "update"; private const val HISTORICAL_OPERATION_DELETE = "delete"; private const val HISTORICAL_OPERATION_PERSIST = "persist"; private const val HISTORICAL_OPERATION_INITIALIZE = "initialize"; private const val HISTORICAL_OPERATION_RESET = "reset"; private const val NULL_VALUE = "null"
        const val SETTINGS_TYPE_MASK = -0x10000000; const val SETTINGS_TYPE_SHIFT = 28
        @JvmStatic fun makeKey(type: Int, userId: Int): Int = (type shl SETTINGS_TYPE_SHIFT) or userId
        @JvmStatic fun getTypeFromKey(key: Int): Int = key ushr SETTINGS_TYPE_SHIFT
        @JvmStatic fun getUserIdFromKey(key: Int): Int = key and SETTINGS_TYPE_MASK.inv()

        @JvmStatic fun writeSingleSetting(version: Int, serializer: TypedXmlSerializer, id: String, name: String, value: String?, defaultValue: String?, packageName: String, tag: String?, defaultSysSet: Boolean, isValuePreservedInRestore: Boolean) {
            serializer.startTag(null, TAG_SETTING).attribute(null, ATTR_ID, id).attribute(null, ATTR_NAME, name)
            setValueAttribute(ATTR_VALUE, ATTR_VALUE_BASE64, version, serializer, value)
            serializer.attribute(null, ATTR_PACKAGE, packageName)
            if (defaultValue != null) {
                setValueAttribute(ATTR_DEFAULT_VALUE, ATTR_DEFAULT_VALUE_BASE64, version, serializer, defaultValue)
                serializer.attributeBoolean(null, ATTR_DEFAULT_SYS_SET, defaultSysSet)
                setValueAttribute(ATTR_TAG, ATTR_TAG_BASE64, version, serializer, tag)
            }
            if (isValuePreservedInRestore) serializer.attributeBoolean(null, ATTR_PRESERVE_IN_RESTORE, true)
            serializer.endTag(null, TAG_SETTING)
        }

        private fun setValueAttribute(attr: String, attrBase64: String, version: Int, serializer: TypedXmlSerializer, value: String?) {
            if (version >= SETTINGS_VERSION_NEW_ENCODING) {
                if (value == null) return
                if (isBinary(value)) serializer.attribute(null, attrBase64, base64Encode(value))
                else serializer.attribute(null, attr, value)
            } else serializer.attribute(null, attr, value ?: NULL_VALUE_OLD_STYLE)
        }

        private fun writeSingleNamespaceHash(serializer: TypedXmlSerializer, ns: String, hash: String) {
            serializer.startTag(null, TAG_NAMESPACE_HASH).attribute(null, ATTR_NAMESPACE, ns).attribute(null, ATTR_BANNED_HASH, hash).endTag(null, TAG_NAMESPACE_HASH)
        }

        @JvmStatic fun isBinary(s: String): Boolean = s.any { c -> !((c in '\u0020'..'\ud7ff') || (c in '\ue000'..'\ufffd')) }
        private fun base64Encode(s: String): String = Base64.encodeToString(toBytes(s), Base64.NO_WRAP)
        private fun base64Decode(s: String): String = fromBytes(Base64.decode(s, Base64.DEFAULT))
        private fun toBytes(s: String): ByteArray { val res = ByteArray(s.length * 2); for (i in s.indices) { val c = s[i].toInt(); res[i * 2] = (c shr 8).toByte(); res[i * 2 + 1] = c.toByte() }; return res }
        private fun fromBytes(b: ByteArray): String { val sb = StringBuilder(b.size / 2); for (i in 0 until b.size - 1 step 2) sb.append(((b[i].toInt() and 0xFF shl 8) or (b[i + 1].toInt() and 0xFF)).toChar()); return sb.toString() }
    }
}
