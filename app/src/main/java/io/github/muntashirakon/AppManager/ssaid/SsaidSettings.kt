// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ssaid

import android.annotation.UserIdInt
import android.content.pm.PackageInfo
import android.os.Build
import android.os.UserHandleHidden
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import aosp.libcore.util.HexEncoding
import io.github.muntashirakon.AppManager.apk.signing.SignerInfo
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat
import io.github.muntashirakon.AppManager.misc.OsEnvironment
import io.github.muntashirakon.AppManager.ssaid.SettingsState.Companion.SYSTEM_PACKAGE_NAME
import io.github.muntashirakon.AppManager.utils.PackageUtils
import io.github.muntashirakon.io.Path
import java.io.IOException
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@RequiresApi(Build.VERSION_CODES.O)
class SsaidSettings {
    private val mLock = Any()
    private val mSettingsState: SettingsState

    @WorkerThread
    @Throws(IOException::class)
    constructor(@UserIdInt userId: Int) {
        val ssaidLocation = OsEnvironment.getUserSystemDirectory(userId).findFile("settings_ssaid.xml")
        if (!ssaidLocation.canRead()) throw IOException("settings_ssaid.xml is inaccessible.")
        mSettingsState = init(ssaidLocation, userId)
    }

    @VisibleForTesting
    @Throws(IOException::class)
    constructor(ssaidLocation: Path, @UserIdInt userId: Int) {
        mSettingsState = init(ssaidLocation, userId)
    }

    @Throws(IOException::class)
    private fun init(ssaidLocation: Path, @UserIdInt userId: Int): SettingsState {
        val ssaidKey = SettingsStateV26.makeKey(SettingsState.SETTINGS_TYPE_SSAID, userId)
        return try {
            SettingsStateV26(mLock, ssaidLocation, ssaidKey, SettingsState.MAX_BYTES_PER_APP_PACKAGE_UNLIMITED)
        } catch (e: IllegalStateException) { throw IOException(e) }
    }

    fun getSsaid(packageName: String, uid: Int): String? = mSettingsState.getSettingLocked(getName(packageName, uid))?.value

    fun setSsaid(packageName: String, uid: Int, ssaid: String?): Boolean {
        try { PackageManagerCompat.forceStopPackage(packageName, UserHandleHidden.getUserId(uid)) } catch (ignore: Throwable) {}
        return mSettingsState.insertSettingLocked(getName(packageName, uid), ssaid, null, true, packageName)
    }

    companion object {
        const val SSAID_USER_KEY = "userkey"\nprivate fun getName(packageName: String?, uid: Int): String = if (packageName == SYSTEM_PACKAGE_NAME) SSAID_USER_KEY else uid.toString()

        @JvmStatic
        fun generateSsaid(packageName: String): String {
            val isUserKey = packageName == SYSTEM_PACKAGE_NAME
            val keyBytes = ByteArray(if (isUserKey) 32 else 8)
            SecureRandom().nextBytes(keyBytes)
            return HexEncoding.encodeToString(keyBytes, isUserKey)
        }

        @JvmStatic
        @Throws(IOException::class)
        fun generateSsaid(callingPkg: PackageInfo): String {
            val ssaidSettings = SsaidSettings(UserHandleHidden.getUserId(callingPkg.applicationInfo!!.uid))
            val settingsState = ssaidSettings.mSettingsState
            var userKeySetting = settingsState.getSettingLocked(SSAID_USER_KEY)
            if (userKeySetting == null || userKeySetting.isNull() || userKeySetting.value == null) {
                val userKey = generateSsaid(SYSTEM_PACKAGE_NAME)
                settingsState.insertSettingLocked(SSAID_USER_KEY, userKey, null, true, SYSTEM_PACKAGE_NAME)
                userKeySetting = settingsState.getSettingLocked(SSAID_USER_KEY)
                if (userKeySetting == null || userKeySetting.isNull() || userKeySetting.value == null) throw IllegalStateException("User key not accessible")
            }
            val userKey = userKeySetting.value!!
            if (userKey.length % 2 != 0) throw IllegalStateException("User key invalid")
            val keyBytes = HexEncoding.decode(userKey)
            if (keyBytes.size != 16 && keyBytes.size != 32) throw IllegalStateException("User key invalid")
            val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(keyBytes, algorithm)) }
            PackageUtils.getSignerInfo(callingPkg, false)?.currentSignerCerts?.forEach { cert ->
                try { val sig = cert.encoded; mac.update(ByteBuffer.allocate(4).putInt(sig.size).array(), 0, 4); mac.update(sig) } catch (ignore: Exception) {}
            }
            return HexEncoding.encodeToString(mac.doFinal(), false).substring(0, 16)
        }
    }
}
