// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules

import android.annotation.UserIdInt
import android.content.Context
import android.os.RemoteException
import androidx.annotation.GuardedBy
import androidx.annotation.WorkerThread
import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat
import io.github.muntashirakon.AppManager.compat.NetworkPolicyManagerCompat
import io.github.muntashirakon.AppManager.compat.PermissionCompat
import io.github.muntashirakon.AppManager.magisk.MagiskProcess
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils
import io.github.muntashirakon.AppManager.rules.struct.*
import io.github.muntashirakon.AppManager.uri.UriManager
import io.github.muntashirakon.AppManager.utils.ContextUtils
import io.github.muntashirakon.AppManager.utils.FreezeUtils
import io.github.muntashirakon.io.Path
import io.github.muntashirakon.io.PathReader
import io.github.muntashirakon.io.Paths
import java.io.BufferedReader
import java.io.Closeable
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

open class RulesStorageManager(@NonNull protected var packageName: String, @UserIdInt protected var userId: Int) : Closeable {
    private val mEntries = ArrayList<RuleEntry>()

    @get:GuardedBy("entries")
    protected var readOnly: Boolean = true
        private set

    init {
        try {
            loadEntries(getDesiredFile(false), false)
        } catch (ignored: Throwable) {
        }
    }

    fun setReadOnly() {
        this.readOnly = true
    }

    open fun setMutable() {
        this.readOnly = false
    }

    override fun close() {
        if (!readOnly) commit()
    }

    fun <T : RuleEntry> getAll(type: Class<T>): List<T> {
        synchronized(mEntries) {
            val newEntries = ArrayList<T>()
            for (entry in mEntries) if (type.isInstance(entry)) newEntries.add(type.cast(entry)!!)
            return newEntries
        }
    }

    fun getAll(types: List<RuleType>): List<RuleEntry> {
        synchronized(mEntries) {
            val newEntries = ArrayList<RuleEntry>()
            for (entry in mEntries) if (types.contains(entry.type)) newEntries.add(entry)
            return newEntries
        }
    }

    fun getAllComponents(): List<ComponentRule> = getAll(ComponentRule::class.java)

    fun getAll(): List<RuleEntry> {
        synchronized(mEntries) {
            return ArrayList(mEntries)
        }
    }

    fun entryCount(): Int {
        synchronized(mEntries) {
            return mEntries.size
        }
    }

    fun removeEntry(entry: RuleEntry) {
        synchronized(mEntries) {
            mEntries.remove(entry)
        }
    }

    protected fun removeEntries(name: String, type: RuleType): RuleEntry? {
        synchronized(mEntries) {
            val entryIterator = mEntries.iterator()
            var entry: RuleEntry? = null
            while (entryIterator.hasNext()) {
                val e = entryIterator.next()
                if (e.name == name && e.type == type) {
                    entry = e
                    entryIterator.remove()
                }
            }
            return entry
        }
    }

    protected fun setComponent(name: String, componentType: RuleType, @ComponentRule.ComponentStatus componentStatus: String) {
        val newRule = ComponentRule(packageName, name, componentType, componentStatus)
        val oldRule = addUniqueEntry(newRule)
        if (oldRule is ComponentRule) {
            newRule.lastComponentStatus = oldRule.componentStatus
        }
    }

    fun setAppOp(op: Int, @AppOpsManagerCompat.Mode mode: Int) {
        addUniqueEntry(AppOpRule(packageName, op, mode))
    }

    fun setPermission(name: String, isGranted: Boolean, @PermissionCompat.PermissionFlags flags: Int) {
        addUniqueEntry(PermissionRule(packageName, name, isGranted, flags))
    }

    fun setNotificationListener(name: String, isGranted: Boolean) {
        addUniqueEntry(NotificationListenerRule(packageName, name, isGranted))
    }

    fun setMagiskHide(magiskProcess: MagiskProcess) {
        addUniqueEntry(MagiskHideRule(magiskProcess))
    }

    fun setMagiskDenyList(magiskProcess: MagiskProcess) {
        addUniqueEntry(MagiskDenyListRule(magiskProcess))
    }

    fun setBatteryOptimization(willOptimize: Boolean) {
        addUniqueEntry(BatteryOptimizationRule(packageName, willOptimize))
    }

    fun setNetPolicy(@NetworkPolicyManagerCompat.NetPolicy netPolicy: Int) {
        addUniqueEntry(NetPolicyRule(packageName, netPolicy))
    }

    fun setUriGrant(uriGrant: UriManager.UriGrant) {
        addEntryInternal(UriGrantRule(packageName, uriGrant))
    }

    fun setSsaid(ssaid: String) {
        addUniqueEntry(SsaidRule(packageName, ssaid))
    }

    fun setFreezeType(@FreezeUtils.FreezeMethod freezeType: Int) {
        addUniqueEntry(FreezeRule(packageName, freezeType))
    }

    fun addEntry(entry: RuleEntry) {
        synchronized(mEntries) {
            if (entry.type == RuleType.URI_GRANT) {
                addEntryInternal(entry)
            } else addUniqueEntry(entry)
        }
    }

    private fun addEntryInternal(entry: RuleEntry) {
        synchronized(mEntries) {
            removeEntry(entry)
            mEntries.add(entry)
        }
    }

    private fun addUniqueEntry(entry: RuleEntry): RuleEntry? {
        synchronized(mEntries) {
            val previousEntry = removeEntries(entry.name, entry.type)
            mEntries.add(entry)
            return previousEntry
        }
    }

    @Throws(IOException::class)
    protected fun loadEntries(file: Path, isExternal: Boolean) {
        try {
            BufferedReader(PathReader(file)).use { reader ->
                var dataRow: String?
                while (reader.readLine().also { dataRow = it } != null) {
                    val entry = RuleEntry.unflattenFromString(packageName, dataRow!!, isExternal)
                    synchronized(mEntries) {
                        mEntries.add(entry)
                    }
                }
            }
        } catch (e: Exception) {
            if (e !is FileNotFoundException) throw e
        }
    }

    @WorkerThread
    fun commit() {
        try {
            saveEntries(getDesiredFile(true), false)
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    @WorkerThread
    fun commitExternal(tsvRulesFile: Path) {
        try {
            saveEntries(tsvRulesFile, true)
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    @WorkerThread
    @Throws(IOException::class, RemoteException::class)
    protected fun saveEntries(tsvRulesFile: Path, isExternal: Boolean) {
        synchronized(mEntries) {
            if (mEntries.isEmpty()) {
                tsvRulesFile.delete()
                return
            }
            tsvRulesFile.openOutputStream().use { os ->
                ComponentUtils.storeRules(os, mEntries, isExternal)
            }
        }
    }

    protected open fun getDesiredFile(create: Boolean): Path {
        val confDir = getConfDir(ContextUtils.getContext())
        if (!confDir.exists()) {
            confDir.mkdirs()
        }
        return if (create) {
            confDir.findOrCreateFile("$packageName.tsv", null)
        } else confDir.findFile("$packageName.tsv")
    }

    companion object {
        @JvmStatic
        fun getConfDir(context: Context): Path {
            return Paths.build(context.filesDir, "conf")!!
        }
    }
}
