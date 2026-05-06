// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.behavior

import android.annotation.UserIdInt
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.UserHandleHidden
import android.text.SpannableStringBuilder
import androidx.annotation.IntDef
import androidx.core.app.PendingIntentCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat
import io.github.muntashirakon.AppManager.utils.ArrayUtils
import io.github.muntashirakon.AppManager.utils.FreezeUtils
import io.github.muntashirakon.AppManager.utils.NotificationUtils
import io.github.muntashirakon.AppManager.utils.UIUtils
import io.github.muntashirakon.dialog.SearchableSingleChoiceDialogBuilder

object FreezeUnfreeze {
    @IntDef(flag = true, value = [FLAG_ON_UNFREEZE_OPEN_APP, FLAG_ON_OPEN_APP_NO_TASK, FLAG_FREEZE_ON_PHONE_LOCKED])
    @Retention(AnnotationRetention.SOURCE)
    annotation class FreezeFlags

    const val FLAG_ON_UNFREEZE_OPEN_APP = 1 shl 0
    const val FLAG_ON_OPEN_APP_NO_TASK = 1 shl 1
    const val FLAG_FREEZE_ON_PHONE_LOCKED = 1 shl 2

    const val PRIVATE_FLAG_FREEZE_FORCE = 1 shl 0

    private const val EXTRA_PACKAGE_NAME = "pkg"\nprivate const val EXTRA_USER_ID = "user"\nprivate const val EXTRA_FLAGS = "flags"\nprivate const val EXTRA_FORCE_FREEZE = "force"\n@JvmStatic
    fun getShortcutIntent(context: Context, shortcutInfo: FreezeUnfreezeShortcutInfo): Intent {
        return Intent(context, FreezeUnfreezeActivity::class.java).apply {
            putExtra(EXTRA_PACKAGE_NAME, shortcutInfo.packageName)
            putExtra(EXTRA_USER_ID, shortcutInfo.userId)
            putExtra(EXTRA_FLAGS, shortcutInfo.flags)
            addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    @JvmStatic
    fun getShortcutIntent(context: Context, packageName: String, @UserIdInt userId: Int, flags: Int): Intent {
        return Intent(context, FreezeUnfreezeActivity::class.java).apply {
            putExtra(EXTRA_PACKAGE_NAME, packageName)
            putExtra(EXTRA_USER_ID, userId)
            putExtra(EXTRA_FLAGS, flags)
            addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    @JvmStatic
    fun getShortcutInfo(intent: Intent): FreezeUnfreezeShortcutInfo? {
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: return null
        val userId = intent.getIntExtra(EXTRA_USER_ID, UserHandleHidden.myUserId())
        val flags = intent.getIntExtra(EXTRA_FLAGS, 0)
        val force = intent.getBooleanExtra(EXTRA_FORCE_FREEZE, false)
        return FreezeUnfreezeShortcutInfo(packageName, userId, flags).apply {
            if (force) addPrivateFlags(PRIVATE_FLAG_FREEZE_FORCE)
        }
    }

    private val FREEZING_METHODS = arrayOf(FreezeUtils.FREEZE_SUSPEND, FreezeUtils.FREEZE_ADV_SUSPEND, FreezeUtils.FREEZE_DISABLE, FreezeUtils.FREEZE_HIDE)
    private val FREEZING_METHOD_TITLES = arrayOf(R.string.suspend_app, R.string.advanced_suspend_app, R.string.disable, R.string.hide_app)
    private val FREEZING_METHOD_DESCRIPTIONS = arrayOf(R.string.suspend_app_description, R.string.advanced_suspend_app_description, R.string.disable_app_description, R.string.hide_app_description)

    @JvmStatic
    fun getFreezeDialog(context: Context, @FreezeUtils.FreezeMethod selectedType: Int): SearchableSingleChoiceDialogBuilder<Int> {
        val itemDescription = Array<CharSequence>(FREEZING_METHODS.size) { i ->
            SpannableStringBuilder()
                .append(context.getString(FREEZING_METHOD_TITLES[i]))
                .append("\n")
                .append(UIUtils.getSmallerText(context.getString(FREEZING_METHOD_DESCRIPTIONS[i])))
        }
        return SearchableSingleChoiceDialogBuilder(context, FREEZING_METHODS.toList(), itemDescription)
            .setSelectionIndex(ArrayUtils.indexOf(FREEZING_METHODS, selectedType))
    }

    @JvmStatic
    fun launchApp(activity: FragmentActivity, shortcutInfo: FreezeUnfreezeShortcutInfo) {
        val launchIntent = PackageManagerCompat.getLaunchIntentForPackage(shortcutInfo.packageName, shortcutInfo.userId) ?: return
        if (shortcutInfo.flags and FLAG_ON_OPEN_APP_NO_TASK != 0) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        }
        try {
            activity.startActivity(launchIntent)
            val intent = getShortcutIntent(activity, shortcutInfo).apply { putExtra(EXTRA_FORCE_FREEZE, true) }
            val requestCode = shortcutInfo.hashCode()
            val pendingIntent = PendingIntentCompat.getActivity(activity, requestCode, intent, PendingIntent.FLAG_ONE_SHOT, false)
            NotificationUtils.displayFreezeUnfreezeNotification(activity, requestCode.toString()) { builder ->
                builder.setDefaults(Notification.DEFAULT_ALL)
                    .setWhen(System.currentTimeMillis())
                    .setSmallIcon(R.drawable.ic_default_notification)
                    .setTicker(activity.getText(R.string.freeze))
                    .setContentTitle(shortcutInfo.name)
                    .setContentText(activity.getString(R.string.tap_to_freeze_app))
                    .setContentIntent(pendingIntent)
                    .build()
            }
            if (shortcutInfo.flags and FLAG_FREEZE_ON_PHONE_LOCKED != 0) {
                val service = Intent(intent).setClassName(activity, FreezeUnfreezeService::class.java.name)
                ContextCompat.startForegroundService(activity, service)
            }
        } catch (th: Throwable) {
            UIUtils.displayLongToast(th.localizedMessage)
        }
    }
}
