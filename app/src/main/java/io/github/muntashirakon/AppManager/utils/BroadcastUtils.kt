// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils

import android.content.Context
import android.content.Intent
import io.github.muntashirakon.AppManager.types.PackageChangeReceiver

object BroadcastUtils {
    @JvmStatic
    fun sendPackageAdded(context: Context, packageNames: Array<String>) {
        val intent = Intent(PackageChangeReceiver.ACTION_PACKAGE_ADDED)
        intent.setPackage(context.getPackageName())
        intent.putExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST, packageNames)
        context.sendBroadcast(intent)
    }

    @JvmStatic
    fun sendPackageAltered(context: Context, packageNames: Array<String>) {
        val intent = Intent(PackageChangeReceiver.ACTION_PACKAGE_ALTERED)
        intent.setPackage(context.getPackageName())
        intent.putExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST, packageNames)
        context.sendBroadcast(intent)
    }

    @JvmStatic
    fun sendPackageRemoved(context: Context, packageNames: Array<String>) {
        val intent = Intent(PackageChangeReceiver.ACTION_PACKAGE_REMOVED)
        intent.setPackage(context.getPackageName())
        intent.putExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST, packageNames)
        context.sendBroadcast(intent)
    }

    @JvmStatic
    fun sendDbPackageAdded(context: Context, packageNames: Array<String>) {
        val intent = Intent(PackageChangeReceiver.ACTION_DB_PACKAGE_ADDED)
        intent.setPackage(context.getPackageName())
        intent.putExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST, packageNames)
        context.sendBroadcast(intent)
    }

    @JvmStatic
    fun sendDbPackageAltered(context: Context, packageNames: Array<String>) {
        val intent = Intent(PackageChangeReceiver.ACTION_DB_PACKAGE_ALTERED)
        intent.setPackage(context.getPackageName())
        intent.putExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST, packageNames)
        context.sendBroadcast(intent)
    }

    @JvmStatic
    fun sendDbPackageRemoved(context: Context, packageNames: Array<String>) {
        val intent = Intent(PackageChangeReceiver.ACTION_DB_PACKAGE_REMOVED)
        intent.setPackage(context.getPackageName())
        intent.putExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST, packageNames)
        context.sendBroadcast(intent)
    }
}
