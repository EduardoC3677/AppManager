// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat

import android.content.Context
import android.net.INetworkPolicyManager
import android.net.NetworkPolicyManager
import android.os.RemoteException
import android.os.UserHandleHidden
import androidx.annotation.IntDef
import androidx.annotation.RequiresPermission
import androidx.collection.ArrayMap
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.ipc.ProxyBinder
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.utils.ExUtils
import java.util.*

object NetworkPolicyManagerCompat {
    @JvmField
    val TAG: String = NetworkPolicyManagerCompat::class.java.simpleName

    /*
     * The policies below are taken from LineageOS
     * Source: https://github.com/LineageOS/android_frameworks_base/blob/lineage-18.1/core/java/android/net/NetworkPolicyManager.java
     */
    /**
     * Reject network usage on Wi-Fi network. {@code POLICY_REJECT_ON_WLAN} up to Lineage 17.1 (Android 10)
     */
    const val POLICY_LOS_REJECT_WIFI: Int = 1 shl 15

    /**
     * Reject network usage on cellular network. {@code POLICY_REJECT_ON_DATA} up to Lineage 17.1 (Android 10)
     */
    const val POLICY_LOS_REJECT_CELLULAR: Int = 1 shl 16

    /**
     * Reject network usage on virtual private network. {@code POLICY_REJECT_ON_VPN} up to Lineage 17.1 (Android 10)
     */
    const val POLICY_LOS_REJECT_VPN: Int = 1 shl 17

    /**
     * Reject network usage on all networks. {@code POLICY_NETWORK_ISOLATED} up to Lineage 17.1 (Android 10)
     */
    const val POLICY_LOS_REJECT_ALL: Int = 1 shl 18

    // The following are taken from Motorola device (Android 12)
    const val POLICY_MOTO_REJECT_METERED: Int = 1 shl 1

    const val POLICY_MOTO_REJECT_BACKGROUND: Int = 1 shl 5

    const val POLICY_MOTO_REJECT_ALL: Int = 1 shl 6

    // The following are taken from Samsung device (Android 10)
    const val POLICY_ONE_UI_ALLOW_METERED_IN_ROAMING: Int = 1001

    const val POLICY_ONE_UI_ALLOW_WHITELIST_IN_ROAMING: Int = 1002

    @IntDef(
        flag = true, value = [
            NetworkPolicyManager.POLICY_NONE,
            NetworkPolicyManager.POLICY_REJECT_METERED_BACKGROUND,
            NetworkPolicyManager.POLICY_ALLOW_METERED_BACKGROUND,
            POLICY_LOS_REJECT_WIFI,
            POLICY_LOS_REJECT_CELLULAR,
            POLICY_LOS_REJECT_VPN,
            POLICY_LOS_REJECT_ALL,
            POLICY_MOTO_REJECT_METERED,
            POLICY_MOTO_REJECT_BACKGROUND,
            POLICY_MOTO_REJECT_ALL,
            POLICY_ONE_UI_ALLOW_METERED_IN_ROAMING,
            POLICY_ONE_UI_ALLOW_WHITELIST_IN_ROAMING
        ]
    )
    @Retention(AnnotationRetention.SOURCE)
    annotation class NetPolicy

    private val sNetworkPolicies = ArrayMap<Int, String>().apply {
        for (field in NetworkPolicyManager::class.java.fields) {
            if (field.name.startsWith("POLICY_")) {
                try {
                    put(field.getInt(null), field.name)
                } catch (ignore: IllegalAccessException) {
                }
            }
        }
    }

    @JvmStatic
    @NetPolicy
    @RequiresPermission(ManifestCompat.permission.MANAGE_NETWORK_POLICY)
    fun getUidPolicy(uid: Int): Int {
        return try {
            netPolicyManager.getUidPolicy(uid)
        } catch (e: RemoteException) {
            ExUtils.rethrowFromSystemServer(e)
        }
    }

    @JvmStatic
    @RequiresPermission(ManifestCompat.permission.MANAGE_NETWORK_POLICY)
    fun setUidPolicy(uid: Int, policies: Int) {
        if (UserHandleHidden.isApp(uid)) {
            try {
                netPolicyManager.setUidPolicy(uid, policies)
            } catch (e: RemoteException) {
                ExUtils.rethrowFromSystemServer(e)
            }
        } else {
            Log.w(TAG, "Cannot set policy %d to uid %d", policies, uid)
        }
    }

    @JvmStatic
    fun getReadablePolicies(context: Context, policies: Int): ArrayMap<Int, String> {
        val readablePolicies = ArrayMap<Int, String>()
        if (policies == 0) {
            readablePolicies.put(NetworkPolicyManager.POLICY_NONE, context.getString(R.string.none))
            return readablePolicies
        }
        for (i in 0 until sNetworkPolicies.size) {
            val policy = sNetworkPolicies.keyAt(i)
            if (!hasPolicy(policies, policy)) {
                continue
            }
            val policyName = sNetworkPolicies.valueAt(i)
            val readablePolicyName = getReadablePolicyName(context, policy, policyName)
            readablePolicies.put(policy, readablePolicyName)
        }
        return readablePolicies
    }

    @JvmStatic
    fun getAllReadablePolicies(context: Context): ArrayMap<Int, String> {
        val readablePolicies = ArrayMap<Int, String>()
        for (i in 0 until sNetworkPolicies.size) {
            val policy = sNetworkPolicies.keyAt(i)
            val policyName = sNetworkPolicies.valueAt(i)
            val readablePolicyName = getReadablePolicyName(context, policy, policyName)
            readablePolicies.put(policy, readablePolicyName)
        }
        return readablePolicies
    }

    private val netPolicyManager: INetworkPolicyManager
        get() = INetworkPolicyManager.Stub.asInterface(ProxyBinder.getService("netpolicy"))

    private fun hasPolicy(policies: Int, policy: Int): Boolean {
        return (policies and policy) != 0
    }

    private fun getReadablePolicyName(context: Context, policy: Int, policyName: String): String {
        when (policy) {
            NetworkPolicyManager.POLICY_NONE -> return context.getString(R.string.none)
            NetworkPolicyManager.POLICY_REJECT_METERED_BACKGROUND -> return context.getString(R.string.netpolicy_reject_metered_background_data)
            NetworkPolicyManager.POLICY_ALLOW_METERED_BACKGROUND -> return context.getString(R.string.netpolicy_allow_metered_background_data)
            POLICY_LOS_REJECT_WIFI -> if (policyName == "POLICY_REJECT_ON_WLAN" || policyName == "POLICY_REJECT_WIFI") {
                return context.getString(R.string.netpolicy_reject_wifi_data)
            }
            POLICY_LOS_REJECT_CELLULAR -> if (policyName == "POLICY_REJECT_ON_DATA" || policyName == "POLICY_REJECT_CELLULAR") {
                return context.getString(R.string.netpolicy_reject_cellular_data)
            }
            POLICY_LOS_REJECT_VPN -> if (policyName == "POLICY_REJECT_ON_VPN" || policyName == "POLICY_REJECT_VPN") {
                return context.getString(R.string.netpolicy_reject_vpn_data)
            }
            POLICY_LOS_REJECT_ALL -> if (policyName == "POLICY_NETWORK_ISOLATED" || policyName == "POLICY_REJECT_ALL") {
                return context.getString(R.string.netpolicy_disable_network_access)
            }
            POLICY_MOTO_REJECT_METERED -> if (policyName == "POLICY_REJECT_METERED") {
                return context.getString(R.string.netpolicy_reject_metered_data)
            }
            POLICY_MOTO_REJECT_BACKGROUND -> if (policyName == "POLICY_REJECT_BACKGROUND") {
                return context.getString(R.string.netpolicy_reject_background_data)
            }
            POLICY_MOTO_REJECT_ALL -> if (policyName == "POLICY_REJECT_ALL") {
                return context.getString(R.string.netpolicy_disable_network_access)
            }
        }
        return context.getString(R.string.unknown_net_policy, policyName, policy)
    }
}
