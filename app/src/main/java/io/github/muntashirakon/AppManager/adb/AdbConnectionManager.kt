// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.adb

import android.os.Build
import androidx.annotation.WorkerThread
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.github.muntashirakon.AppManager.crypto.ks.KeyPair
import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreManager
import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreUtils
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.utils.ThreadUtils
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import java.security.PrivateKey
import java.security.cert.Certificate

class AdbConnectionManager @Throws(Exception::class) constructor() : AbsAdbConnectionManager() {
    private val mKeyPair: KeyPair
    private val mPairingObserver = MutableLiveData<Exception?>()

    init {
        api = Build.VERSION.SDK_INT
        val keyStoreManager = KeyStoreManager.getInstance()
        var keyPair = keyStoreManager.getKeyPairNoThrow(ADB_KEY_ALIAS)
        if (keyPair == null) {
            val subject = "CN=App Manager"
            keyPair = KeyStoreUtils.generateRSAKeyPair(subject, 2048, System.currentTimeMillis() + 86400000)
            keyStoreManager.addKeyPair(ADB_KEY_ALIAS, keyPair, true)
        }
        mKeyPair = keyPair!!
    }

    fun getPairingObserver(): LiveData<Exception?> {
        return mPairingObserver
    }

    @WorkerThread
    @Throws(Exception::class)
    fun pairLiveData(host: String, port: Int, pairingCode: String) {
        try {
            ThreadUtils.ensureWorkerThread()
            pair(host, port, pairingCode)
            mPairingObserver.postValue(null)
        } catch (e: Exception) {
            Log.w(TAG, "Pairing failed.", e)
            mPairingObserver.postValue(e)
            throw e
        }
    }

    override fun getPrivateKey(): PrivateKey {
        return mKeyPair.privateKey
    }

    override fun getCertificate(): Certificate {
        return mKeyPair.certificate
    }

    override fun getDeviceName(): String {
        return "AppManager"
    }

    companion object {
        @JvmField
        val TAG: String = AdbConnectionManager::class.java.simpleName
        const val ADB_KEY_ALIAS = "adb_rsa"

        @Volatile
        private var sInstance: AdbConnectionManager? = null

        @JvmStatic
        @Throws(Exception::class)
        fun getInstance(): AdbConnectionManager {
            return sInstance ?: synchronized(this) {
                sInstance ?: AdbConnectionManager().also { sInstance = it }
            }
        }
    }
}
