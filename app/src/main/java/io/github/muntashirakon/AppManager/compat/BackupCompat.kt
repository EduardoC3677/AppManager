// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat

import android.annotation.UserIdInt
import android.app.backup.IBackupManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import android.os.UserHandleHidden
import aosp.libcore.util.EmptyArray
import io.github.muntashirakon.AppManager.ipc.ProxyBinder
import io.github.muntashirakon.AppManager.utils.ArrayUtils
import io.github.muntashirakon.AppManager.utils.ExUtils

/**
 * A complete recreation of the `bu` command (i.e. com.android.commands.bu.Backup class) with support for setting a
 * file location. Although the help page of the command include an -f switch for file, it actually does not work with
 * the command and only intended for ADB itself.
 */
object BackupCompat {
    /**
     * @see IBackupManager.setBackupEnabledForUser
     */
    @JvmStatic
    fun setBackupEnabledForUser(@UserIdInt userId: Int, isEnabled: Boolean) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                backupManager.setBackupEnabledForUser(userId, isEnabled)
            } else {
                @Suppress("DEPRECATION")
                backupManager.setBackupEnabled(isEnabled)
            }
        } catch (e: RemoteException) {
            ExUtils.rethrowFromSystemServer(e)
        }
    }

    /**
     * @see IBackupManager.isBackupEnabledForUser
     */
    @JvmStatic
    fun isBackupEnabledForUser(@UserIdInt userId: Int): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return backupManager.isBackupEnabledForUser(userId)
            }
            if (UserHandleHidden.myUserId() == userId) {
                @Suppress("DEPRECATION")
                return backupManager.isBackupEnabled
            }
            // Multiuser backup only available since Android 10
            false
        } catch (e: RemoteException) {
            ExUtils.rethrowFromSystemServer(e)
        }
    }

    @JvmStatic
    fun setBackupPassword(currentPw: String?, newPw: String?): Boolean {
        return try {
            backupManager.setBackupPassword(currentPw, newPw)
        } catch (e: RemoteException) {
            ExUtils.rethrowFromSystemServer(e)
        }
    }

    @JvmStatic
    fun hasBackupPassword(): Boolean {
        return try {
            backupManager.hasBackupPassword()
        } catch (e: RemoteException) {
            ExUtils.rethrowFromSystemServer(e)
        }
    }

    @JvmStatic
    @Throws(RemoteException::class)
    fun adbBackup(
        @UserIdInt userId: Int, fd: ParcelFileDescriptor?, includeApks: Boolean, includeObbs: Boolean,
        includeShared: Boolean, doWidgets: Boolean, allApps: Boolean, allIncludesSystem: Boolean,
        doCompress: Boolean, doKeyValue: Boolean, packageNames: Array<String>?
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            backupManager.adbBackup(
                userId, fd, includeApks, includeObbs, includeShared, doWidgets, allApps, allIncludesSystem, doCompress, doKeyValue, packageNames
            )
        } else {
            if (UserHandleHidden.myUserId() != userId) {
                throw RemoteException("Backup only allowed for current user")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                backupManager.adbBackup(
                    fd, includeApks, includeObbs, includeShared, doWidgets, allApps, allIncludesSystem, doCompress, doKeyValue, packageNames
                )
            } else {
                @Suppress("DEPRECATION")
                backupManager.fullBackup(
                    fd, includeApks, includeObbs, includeShared, doWidgets, allApps, allIncludesSystem, doCompress, packageNames
                )
            }
        }
    }

    @JvmStatic
    @Throws(RemoteException::class)
    fun adbRestore(@UserIdInt userId: Int, fd: ParcelFileDescriptor?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            backupManager.adbRestore(userId, fd)
        } else {
            if (UserHandleHidden.myUserId() != userId) {
                throw RemoteException("Backup only allowed for current user")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                backupManager.adbRestore(fd)
            } else {
                @Suppress("DEPRECATION")
                backupManager.fullRestore(fd)
            }
        }
    }

    @JvmStatic
    fun isAppEligibleForBackupForUser(@UserIdInt userId: Int, packageName: String?): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                backupManager.isAppEligibleForBackupForUser(userId, packageName)
            } else {
                if (UserHandleHidden.myUserId() != userId) {
                    // Multiuser support unavailable
                    return false
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    @Suppress("DEPRECATION")
                    return backupManager.isAppEligibleForBackup(packageName)
                }
                // In API 23 and earlier, set it to eligible by default
                true
            }
        } catch (e: RemoteException) {
            ExUtils.rethrowFromSystemServer(e)
        }
    }

    @JvmStatic
    fun filterAppsEligibleForBackupForUser(@UserIdInt userId: Int, packages: Array<String>): Array<String> {
        val manager = backupManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return ArrayUtils.defeatNullable(ExUtils.exceptionAsNull<Array<String>> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    manager.filterAppsEligibleForBackupForUser(userId, packages)
                } else {
                    @Suppress("DEPRECATION")
                    manager.filterAppsEligibleForBackup(packages)
                }
                null
            })
        }
        if (UserHandleHidden.myUserId() != userId) {
            // Multiuser support unavailable
            return EmptyArray.STRING
        }
        // Check individually
        val eligibleApps: MutableList<String> = ArrayList(packages.size)
        for (packageName in packages) {
            val isEligible = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                java.lang.Boolean.TRUE == ExUtils.exceptionAsNull<Boolean> {
                    @Suppress("DEPRECATION")
                    manager.isAppEligibleForBackup(packageName)
                }
            } else {
                true
            }
            if (isEligible) {
                eligibleApps.add(packageName)
            }
        }
        return eligibleApps.toTypedArray()
    }

    @get:JvmStatic
    val backupManager: IBackupManager
        get() = IBackupManager.Stub.asInterface(ProxyBinder.getService("backup"))
}
