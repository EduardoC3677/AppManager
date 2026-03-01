// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat

import android.os.Build
import android.os.RemoteException
import android.telephony.SubscriptionInfo
import com.android.internal.telephony.IPhoneSubInfo
import com.android.internal.telephony.ISub
import io.github.muntashirakon.AppManager.ipc.ProxyBinder
import io.github.muntashirakon.AppManager.self.SelfPermissions
import io.github.muntashirakon.AppManager.users.Users
import io.github.muntashirakon.AppManager.utils.ExUtils

object SubscriptionManagerCompat {
    @JvmField
    val TAG: String = SubscriptionManagerCompat::class.java.simpleName

    @JvmStatic
    @Suppress("DEPRECATION")
    fun getActiveSubscriptionInfoList(): List<SubscriptionInfo>? {
        return try {
            val sub = sub
            val uid = Users.getSelfOrRemoteUid()
            val callingPackage = SelfPermissions.getCallingPackage(uid)
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                    try {
                        sub.getActiveSubscriptionInfoList(callingPackage, null)
                    } catch (e: NoSuchMethodError) {
                        // Google Pixel
                        sub.getActiveSubscriptionInfoList(callingPackage, null, true)
                    }
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> sub.getActiveSubscriptionInfoList(callingPackage, null)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> sub.getActiveSubscriptionInfoList(callingPackage)
                else -> sub.activeSubscriptionInfoList
            }
        } catch (e: RemoteException) {
            ExUtils.rethrowFromSystemServer(e)
        }
    }

    @JvmStatic
    @Suppress("DEPRECATION")
    fun getSubscriberIdForSubscriber(subId: Long): String? {
        return try {
            val sub = phoneSubInfo
            val uid = Users.getSelfOrRemoteUid()
            val callingPackage = SelfPermissions.getCallingPackage(uid)
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> sub.getSubscriberIdForSubscriber(subId.toInt(), callingPackage, null)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> sub.getSubscriberIdForSubscriber(subId.toInt(), callingPackage)
                Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP_MR1 -> sub.getSubscriberIdForSubscriber(subId.toInt())
                else -> sub.getSubscriberIdForSubscriber(subId)
            }
        } catch (e: RemoteException) {
            ExUtils.rethrowFromSystemServer(e)
        } catch (ignore: NullPointerException) {
            null
        }
    }

    private val sub: ISub
        get() = ISub.Stub.asInterface(ProxyBinder.getService("isub"))

    private val phoneSubInfo: IPhoneSubInfo
        get() = IPhoneSubInfo.Stub.asInterface(ProxyBinder.getService("iphonesubinfo"))
}
