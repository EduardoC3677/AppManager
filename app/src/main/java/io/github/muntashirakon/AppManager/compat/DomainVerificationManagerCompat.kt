// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.verify.domain.DomainVerificationUserState
import android.content.pm.verify.domain.IDomainVerificationManager
import android.os.Build
import android.os.RemoteException
import android.os.ServiceSpecificException
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import io.github.muntashirakon.AppManager.ipc.ProxyBinder

@RequiresApi(Build.VERSION_CODES.S)
object DomainVerificationManagerCompat {
    @JvmStatic
    fun getDomainVerificationUserState(packageName: String?, userId: Int): DomainVerificationUserState? {
        return try {
            domainVerificationManager.getDomainVerificationUserState(packageName, userId)
        } catch (ignore: Throwable) {
            null
        }
    }

    @JvmStatic
    @RequiresPermission(ManifestCompat.permission.UPDATE_DOMAIN_VERIFICATION_USER_SELECTION)
    @Throws(RemoteException::class, PackageManager.NameNotFoundException::class)
    fun setDomainVerificationLinkHandlingAllowed(packageName: String?, allowed: Boolean, userId: Int) {
        try {
            domainVerificationManager.setDomainVerificationLinkHandlingAllowed(packageName, allowed, userId)
        } catch (e: ServiceSpecificException) {
            val serviceSpecificErrorCode = e.errorCode
            val finalPackageName = packageName ?: e.message

            if (serviceSpecificErrorCode == 1) {
                throw PackageManager.NameNotFoundException(finalPackageName)
            }
            throw e
        }
    }

    @get:JvmStatic
    val domainVerificationManager: IDomainVerificationManager
        get() = IDomainVerificationManager.Stub.asInterface(ProxyBinder.getService(Context.DOMAIN_VERIFICATION_SERVICE))
}
