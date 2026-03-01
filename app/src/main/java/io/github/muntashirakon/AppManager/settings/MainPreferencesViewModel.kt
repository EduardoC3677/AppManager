// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings

import android.app.Application
import android.os.Build
import android.os.PowerManager
import android.os.UserHandleHidden
import androidx.annotation.RequiresApi
import androidx.collection.ArrayMap
import androidx.core.util.Pair
import androidx.documentfile.provider.DocumentFileUtils
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.apk.signing.Signer
import io.github.muntashirakon.AppManager.changelog.Changelog
import io.github.muntashirakon.AppManager.changelog.ChangelogParser
import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreManager
import io.github.muntashirakon.AppManager.db.utils.AppDb
import io.github.muntashirakon.AppManager.misc.DeviceInfo2
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker
import io.github.muntashirakon.AppManager.servermanager.ServerConfig
import io.github.muntashirakon.AppManager.users.UserInfo
import io.github.muntashirakon.AppManager.users.Users
import io.github.muntashirakon.AppManager.utils.CpuUtils
import io.github.muntashirakon.AppManager.utils.DigestUtils
import io.github.muntashirakon.AppManager.utils.StorageUtils
import io.github.muntashirakon.AppManager.utils.ThreadUtils
import io.github.muntashirakon.lifecycle.SingleLiveEvent
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.util.concurrent.Executors

class MainPreferencesViewModel(application: Application) : AndroidViewModel(application), Ops.AdbConnectionInterface {
    private val mRulesLock = Any()
    private val mSelectUsers = SingleLiveEvent<List<UserInfo>>()
    private val mChangeLog = SingleLiveEvent<Changelog>()
    private val mDeviceInfo = SingleLiveEvent<DeviceInfo2>()
    private val mCustomCommand0 = SingleLiveEvent<String?>()
    private val mCustomCommand1 = SingleLiveEvent<String?>()
    private val mModeOfOpsStatus = SingleLiveEvent<Int>()
    private val mOperationCompletedLiveData = SingleLiveEvent<Boolean>()
    private val mStorageVolumesLiveData = SingleLiveEvent<ArrayMap<String, android.net.Uri>>()
    private val mSigningKeySha256HashLiveData = SingleLiveEvent<String?>()
    private val mPackageNameLabelPairLiveData = SingleLiveEvent<List<Pair<String, CharSequence>>>()
    private val mExecutor = Executors.newFixedThreadPool(1)

    fun selectUsers(): LiveData<List<UserInfo>> = mSelectUsers

    fun loadAllUsers() {
        ThreadUtils.postOnBackgroundThread { mSelectUsers.postValue(Users.getAllUsers()) }
    }

    fun getChangeLog(): LiveData<Changelog> = mChangeLog

    fun loadChangeLog() {
        ThreadUtils.postOnBackgroundThread {
            try {
                val changelog = ChangelogParser(getApplication(), R.raw.changelog).parse()
                mChangeLog.postValue(changelog)
            } catch (e: IOException) {
                e.printStackTrace()
            } catch (e: XmlPullParserException) {
                e.printStackTrace()
            }
        }
    }

    fun getDeviceInfo(): LiveData<DeviceInfo2> = mDeviceInfo

    fun loadDeviceInfo(di: DeviceInfo2) {
        ThreadUtils.postOnBackgroundThread {
            di.loadInfo()
            mDeviceInfo.postValue(di)
        }
    }

    fun reloadApps() {
        ThreadUtils.postOnBackgroundThread {
            val wakeLock = CpuUtils.getPartialWakeLock("appDbUpdater")
            try {
                wakeLock.acquire()
                val appDb = AppDb()
                appDb.deleteAllApplications()
                appDb.deleteAllBackups()
                appDb.loadInstalledOrBackedUpApplications(getApplication())
            } finally {
                CpuUtils.releaseWakeLock(wakeLock)
            }
        }
    }

    fun getCustomCommand0(): MutableLiveData<String?> = mCustomCommand0

    fun getCustomCommand1(): MutableLiveData<String?> = mCustomCommand1

    fun loadCustomCommands() {
        mExecutor.submit {
            try {
                ServerConfig.init(getApplication())
                mCustomCommand0.postValue(ServerConfig.getServerRunnerCommand(0))
                mCustomCommand1.postValue(ServerConfig.getServerRunnerCommand(1))
            } catch (e: Throwable) {
                e.printStackTrace()
                mCustomCommand0.postValue(null)
                mCustomCommand1.postValue(null)
            }
        }
    }

    fun getModeOfOpsStatus(): LiveData<Int> = mModeOfOpsStatus

    fun setModeOfOps() {
        mExecutor.submit {
            val status = Ops.init(getApplication(), true)
            mModeOfOpsStatus.postValue(status)
        }
    }

    fun getOperationCompletedLiveData(): LiveData<Boolean> = mOperationCompletedLiveData

    fun applyAllRules() {
        ThreadUtils.postOnBackgroundThread {
            synchronized(mRulesLock) {
                // TODO: 13/8/22 Synchronise in ComponentsBlocker instead of here
                ComponentsBlocker.applyAllRules(getApplication(), UserHandleHidden.myUserId())
            }
        }
    }

    fun removeAllRules() {
        ThreadUtils.postOnBackgroundThread {
            val userHandles = Users.getUsersIds()
            val packages = ComponentUtils.getAllPackagesWithRules(getApplication())
            for (userHandle in userHandles) {
                for (packageName in packages) {
                    ComponentUtils.removeAllRules(packageName, userHandle)
                }
            }
            mOperationCompletedLiveData.postValue(true)
        }
    }

    fun getStorageVolumesLiveData(): LiveData<ArrayMap<String, android.net.Uri>> = mStorageVolumesLiveData

    fun loadStorageVolumes() {
        ThreadUtils.postOnBackgroundThread {
            val locations = StorageUtils.getAllStorageLocations(getApplication())
            val newLocations = ArrayMap<String, android.net.Uri>(locations.size)
            val pm = getApplication<Application>().packageManager
            for (i in 0 until locations.size) {
                val uri = locations.valueAt(i)
                val authority = uri.authority
                if (authority != null) {
                    val resolveInfo = DocumentFileUtils.getUriSource(getApplication(), uri)
                    val readableName = if (resolveInfo != null) {
                        resolveInfo.loadLabel(pm).toString()
                    } else {
                        locations.keyAt(i)
                    }
                    newLocations[readableName] = locations.valueAt(i)
                } else {
                    newLocations[locations.keyAt(i)] = locations.valueAt(i)
                }
            }
            mStorageVolumesLiveData.postValue(newLocations)
        }
    }

    fun getSigningKeySha256HashLiveData(): LiveData<String?> = mSigningKeySha256HashLiveData

    fun loadSigningKeySha256Hash() {
        mExecutor.submit {
            var hash: String? = null
            try {
                val keyStoreManager = KeyStoreManager.getInstance()
                if (keyStoreManager.containsKey(Signer.SIGNING_KEY_ALIAS)) {
                    val keyPair = keyStoreManager.getKeyPair(Signer.SIGNING_KEY_ALIAS)
                    if (keyPair != null) {
                        val certificate = keyPair.certificate
                        hash = DigestUtils.getHexDigest(DigestUtils.SHA_256, certificate.encoded)
                        try {
                            keyPair.destroy()
                        } catch (ignore: Exception) {
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            mSigningKeySha256HashLiveData.postValue(hash)
        }
    }

    fun getPackageNameLabelPairLiveData(): LiveData<List<Pair<String, CharSequence>>> = mPackageNameLabelPairLiveData

    fun loadPackageNameLabelPair() {
        mExecutor.submit {
            val appList = AppDb().allApplications
            val packageNameLabelMap = HashMap<String, CharSequence>(appList.size)
            for (app in appList) {
                packageNameLabelMap[app.packageName] = app.packageLabel
            }
            val appInfo = ArrayList<Pair<String, CharSequence>>()
            for (packageName in packageNameLabelMap.keys) {
                appInfo.add(Pair(packageName, packageNameLabelMap[packageName]))
            }
            appInfo.sortWith { o1, o2 -> o1.second.toString().compareTo(o2.second.toString()) }
            mPackageNameLabelPairLiveData.postValue(appInfo)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun autoConnectWirelessDebugging() {
        mExecutor.submit {
            val status = Ops.autoConnectWirelessDebugging(getApplication())
            mModeOfOpsStatus.postValue(status)
        }
    }

    override fun connectAdb(port: Int) {
        mExecutor.submit {
            val status = Ops.connectAdb(getApplication(), port, Ops.STATUS_FAILURE)
            mModeOfOpsStatus.postValue(status)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun pairAdb() {
        mExecutor.submit {
            val status = Ops.pairAdb(getApplication())
            mModeOfOpsStatus.postValue(status)
        }
    }

    override fun onStatusReceived(status: Int) {
        mModeOfOpsStatus.postValue(status)
    }
}
