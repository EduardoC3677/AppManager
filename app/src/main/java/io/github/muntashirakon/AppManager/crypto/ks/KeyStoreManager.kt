// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.crypto.ks

import android.annotation.SuppressLint
import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.text.TextUtils
import android.util.Base64
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import io.github.muntashirakon.AppManager.BuildConfig
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.adb.AdbConnectionManager
import io.github.muntashirakon.AppManager.apk.signing.Signer
import io.github.muntashirakon.AppManager.crypto.AESCrypto
import io.github.muntashirakon.AppManager.crypto.RSACrypto
import io.github.muntashirakon.AppManager.crypto.RandomChar
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.utils.*
import java.io.*
import java.security.*
import java.security.cert.Certificate
import java.security.cert.CertificateException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.SecretKey

class KeyStoreManager private constructor() {
    private val mContext: Context = ContextUtils.getContext()
    private val mAmKeyStore: KeyStore = getAmKeyStore()
    private val mReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_KS_INTERACTION_END == intent.action) releaseLock()
        }
    }

    @Throws(KeyStoreException::class, IOException::class, CertificateException::class, NoSuchAlgorithmException::class)
    fun addKeyPair(alias: String, keyPair: KeyPair, isOverride: Boolean) {
        val prefAlias = getPrefAlias(alias)
        if (sSharedPreferences.contains(prefAlias) && mAmKeyStore.containsAlias(alias)) {
            if (isOverride) removeItemInternal(alias) else return
        }
        val password = getAmKeyStorePassword()
        mAmKeyStore.setKeyEntry(alias, keyPair.privateKey, password, arrayOf(keyPair.certificate))
        val encryptedPass = getEncryptedPassword(mContext, password) ?: throw KeyStoreException("Password for $alias could not be saved.")
        sSharedPreferences.edit().putString(prefAlias, encryptedPass).apply()
        FileOutputStream(AM_KEYSTORE_FILE).use { mAmKeyStore.store(it, password) }
        Utils.clearChars(password)
    }

    @Throws(KeyStoreException::class, IOException::class, CertificateException::class, NoSuchAlgorithmException::class)
    fun addSecretKey(alias: String, secretKey: SecretKey, isOverride: Boolean) {
        val prefAlias = getPrefAlias(alias)
        if (sSharedPreferences.contains(prefAlias) && mAmKeyStore.containsAlias(alias)) {
            if (!isOverride) throw KeyStoreException("Alias $alias exists.")
            else Log.w(TAG, "Alias $alias exists.")
        }
        val password = getAmKeyStorePassword()
        mAmKeyStore.setEntry(alias, KeyStore.SecretKeyEntry(secretKey), KeyStore.PasswordProtection(password))
        val encryptedPass = getEncryptedPassword(mContext, password) ?: throw KeyStoreException("Password for $alias could not be saved.")
        sSharedPreferences.edit().putString(prefAlias, encryptedPass).apply()
        try { FileOutputStream(AM_KEYSTORE_FILE).use { mAmKeyStore.store(it, password) } } finally { Utils.clearChars(password) }
    }

    @Throws(KeyStoreException::class, IOException::class, CertificateException::class, NoSuchAlgorithmException::class)
    fun removeItem(alias: String) {
        removeItemInternal(alias)
        val password = getAmKeyStorePassword()
        try { FileOutputStream(AM_KEYSTORE_FILE).use { mAmKeyStore.store(it, password) } } finally { Utils.clearChars(password) }
    }

    private fun removeItemInternal(alias: String) {
        mAmKeyStore.deleteEntry(alias)
        val prefAlias = getPrefAlias(alias)
        if (sSharedPreferences.contains(prefAlias)) sSharedPreferences.edit().remove(prefAlias).apply()
    }

    @Throws(UnrecoverableKeyException::class, NoSuchAlgorithmException::class, KeyStoreException::class)
    private fun getKey(alias: String): Key? {
        val password = getAmKeyStorePassword()
        return try { mAmKeyStore.getKey(alias, password) } finally { Utils.clearChars(password) }
    }

    @Throws(UnrecoverableKeyException::class, NoSuchAlgorithmException::class, KeyStoreException::class)
    fun getSecretKey(alias: String): SecretKey? {
        val key = getKey(alias)
        if (key is SecretKey) return key
        throw KeyStoreException("The alias $alias does not have a SecretKey.")
    }

    @Throws(UnrecoverableKeyException::class, NoSuchAlgorithmException::class, KeyStoreException::class)
    fun getKeyPair(alias: String): KeyPair? {
        val key = getKey(alias)
        if (key is PrivateKey) return KeyPair(key, mAmKeyStore.getCertificate(alias))
        throw KeyStoreException("The alias $alias does not have a KeyPair.")
    }

    fun getKeyPairNoThrow(alias: String): KeyPair? = try { getKeyPair(alias) } catch (e: Exception) { null }

    fun containsKey(alias: String): Boolean = mAmKeyStore.containsAlias(alias)

    private fun getAmKeyStore(): KeyStore {
        val keyStore = KeyStore.getInstance(AM_KEYSTORE)
        val password = getAmKeyStorePassword()
        try {
            if (AM_KEYSTORE_FILE.exists()) FileInputStream(AM_KEYSTORE_FILE).use { keyStore.load(it, password) }
            else keyStore.load(null)
        } finally { Utils.clearChars(password) }
        return keyStore
    }

    @Throws(KeyStoreException::class)
    fun getAmKeyStorePassword(): CharArray {
        val encryptedPass = sSharedPreferences.getString(PREF_AM_KEYSTORE_PASS, null) ?: throw KeyStoreException("No saved password for KeyStore.")
        return getDecryptedPassword(mContext, encryptedPass) ?: throw KeyStoreException("Could not decrypt encrypted password.")
    }

    private var mInteractionWatcher: CountDownLatch? = null
    private fun releaseLock() { mInteractionWatcher?.countDown() }
    private fun acquireLock() {
        mInteractionWatcher = CountDownLatch(1)
        try { mInteractionWatcher!!.await(100, TimeUnit.SECONDS) }
        catch (e: InterruptedException) { Log.e(TAG, "waitForResult: interrupted", e) }
    }

    companion object {
        const val TAG = "KSManager"
        const val AM_KEYSTORE_FILE_NAME = "am_keystore.bks"
        val AM_KEYSTORE_FILE: File
        private const val AM_KEYSTORE = "BKS"
        private const val PREF_AM_KEYSTORE_PREFIX = "ks_"
        private const val PREF_AM_KEYSTORE_PASS = "kspass"
        private val sSharedPreferences: android.content.SharedPreferences
        const val ACTION_KS_INTERACTION_BEGIN = "${BuildConfig.APPLICATION_ID}.action.KS_INTERACTION_BEGIN"
        const val ACTION_KS_INTERACTION_END = "${BuildConfig.APPLICATION_ID}.action.KS_INTERACTION_END"

        init {
            val ctx = ContextUtils.getContext()
            AM_KEYSTORE_FILE = File(ctx.filesDir, AM_KEYSTORE_FILE_NAME)
            sSharedPreferences = ctx.getSharedPreferences("keystore", Context.MODE_PRIVATE)
        }

        @SuppressLint("StaticFieldLeak")
        private var sInstance: KeyStoreManager? = null

        @JvmStatic
        @Throws(Exception::class)
        fun getInstance(): KeyStoreManager {
            if (sInstance == null) sInstance = KeyStoreManager()
            return sInstance!!
        }

        @JvmStatic
        @Throws(Exception::class)
        fun reloadKeyStore() { sInstance = KeyStoreManager() }

        @JvmStatic
        fun generateAndDisplayKeyStorePassword(activity: FragmentActivity, dismissListener: Runnable?): AlertDialog {
            val password = CharArray(30)
            RandomChar().nextChars(password)
            savePass(activity, PREF_AM_KEYSTORE_PASS, password)
            return displayKeyStorePassword(activity, password, dismissListener)
        }

        @JvmStatic
        fun displayKeyStorePassword(activity: FragmentActivity, password: CharArray, dismissListener: Runnable?): AlertDialog {
            val view = activity.layoutInflater.inflate(R.layout.dialog_keystore_password, null)
            view.findViewById<TextInputEditText>(R.id.ks_pass).setText(password, 0, password.size)
            return MaterialAlertDialogBuilder(activity).setTitle(R.string.keystore).setView(view).setNegativeButton(R.string.close, null).setCancelable(false).setOnDismissListener { Utils.clearChars(password); dismissListener?.run() }.create()
        }

        @JvmStatic
        fun inputKeyStorePassword(activity: FragmentActivity, dismissListener: Runnable?): AlertDialog {
            val dismiss = AtomicBoolean(true)
            val view = activity.layoutInflater.inflate(R.layout.dialog_keystore_password, null)
            val editText = view.findViewById<TextInputEditText>(R.id.ks_pass).apply { isCursorVisible = true }
            view.findViewById<View>(android.R.id.text1).visibility = View.GONE
            view.findViewById<com.google.android.material.textfield.TextInputLayout>(android.R.id.text2).setHint(R.string.input_keystore_pass)
            val dialog = MaterialAlertDialogBuilder(activity).setTitle(R.string.keystore).setView(view).setPositiveButton(R.string.ok, null).setNegativeButton(R.string.delete, null).setCancelable(false).setOnDismissListener { if (dismiss.get()) dismissListener?.run() }.create()
            dialog.setOnShowListener { d ->
                val ad = d as AlertDialog
                ad.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val text = editText.text
                    if (text.isNullOrEmpty()) { editText.error = activity.getString(R.string.keystore_pass_cannot_be_empty); return@setOnClickListener }
                    val pass = CharArray(text.length)
                    text.getChars(0, text.length, pass, 0)
                    savePass(activity, PREF_AM_KEYSTORE_PASS, pass)
                    Utils.clearChars(pass)
                    try { getInstance() } catch (e: Exception) { editText.error = activity.getString(R.string.invalid_password); return@setOnClickListener }
                    ad.dismiss()
                }
                ad.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                    AM_KEYSTORE_FILE.delete()
                    sSharedPreferences.edit().remove(PREF_AM_KEYSTORE_PASS).apply()
                    dismiss.set(false)
                    generateAndDisplayKeyStorePassword(activity, dismissListener).show()
                    ad.dismiss()
                }
            }
            return dialog
        }

        @JvmStatic fun hasKeyStore(): Boolean = AM_KEYSTORE_FILE.exists()
        @JvmStatic fun hasKeyStorePassword(): Boolean = try { reloadKeyStore(); true } catch (e: Exception) { false }

        @JvmStatic fun savePass(context: Context, prefAlias: String, password: CharArray) { sSharedPreferences.edit().putString(prefAlias, getEncryptedPassword(context, password)).apply() }

        private fun getDecryptedPassword(context: Context, encryptedPass: String): CharArray? {
            return try { Utils.bytesToChars(CompatUtil.decryptData(context, Base64.decode(encryptedPass, Base64.NO_WRAP))) }
            catch (e: Exception) { Log.e("KS", "Could not get decrypted password", e); null }
        }

        private fun getEncryptedPassword(context: Context, realPass: CharArray): String? {
            return try {
                ByteArrayOutputStream().use { bos ->
                    val data = CompatUtil.getEncryptedData(Utils.charsToBytes(realPass), context)
                    bos.write(data.iv.size)
                    bos.write(data.iv)
                    bos.write(data.encryptedData)
                    Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
                }
            } catch (e: Exception) { Log.e("KS", "Could not get encrypted password", e); null }
        }

        @JvmStatic fun getPrefAlias(alias: String): String = PREF_AM_KEYSTORE_PREFIX + alias
    }
}
