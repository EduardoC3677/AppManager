// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.behavior

import android.app.Application
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.os.RemoteException
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.util.Pair
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.muntashirakon.AppManager.BaseActivity
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat
import io.github.muntashirakon.AppManager.self.SelfPermissions
import io.github.muntashirakon.AppManager.settings.Prefs
import io.github.muntashirakon.AppManager.utils.*
import io.github.muntashirakon.AppManager.utils.UIUtils.getBitmapFromDrawable
import io.github.muntashirakon.AppManager.utils.UIUtils.getDimmedBitmap
import java.util.*

class FreezeUnfreezeActivity : BaseActivity() {
    private var mViewModel: FreezeUnfreezeViewModel? = null

    override fun onAuthenticated(savedInstanceState: Bundle?) {
        mViewModel = ViewModelProvider(this).get(FreezeUnfreezeViewModel::class.java)
        if (!SelfPermissions.canFreezeUnfreezePackages()) {
            UIUtils.displayShortToast(R.string.only_works_in_root_or_adb_mode)
            finish()
            return
        }
        val i = FreezeUnfreeze.getShortcutInfo(intent)
        if (i != null) {
            hideNotification(i)
            mViewModel!!.addToPendingShortcuts(i)
            mViewModel!!.checkNextFrozen()
        } else {
            finish()
            return
        }
        mViewModel!!.mIsFrozenLiveData.observe(this) { pair ->
            if (pair == null) { finish(); return@observe }
            val si = pair.first
            val intent = FreezeUnfreeze.getShortcutIntent(this, si).apply { action = Intent.ACTION_CREATE_SHORTCUT }
            val sic = ShortcutInfoCompat.Builder(this, si.id!!)
                .setShortLabel(si.name!!).setLongLabel(si.name!!)
                .setIcon(IconCompat.createWithBitmap(si.icon!!)).setIntent(intent).build()
            ShortcutManagerCompat.updateShortcuts(this, listOf(sic))
            if (!pair.second && si.flags and FreezeUnfreeze.FLAG_ON_UNFREEZE_OPEN_APP != 0) {
                FreezeUnfreeze.launchApp(this, si)
            }
            mViewModel!!.checkNextFrozen()
        }
        mViewModel!!.mOpenAppOrFreeze.observe(this) { si ->
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.freeze_unfreeze).setMessage(R.string.choose_what_to_do)
                .setPositiveButton(R.string.open) { _, _ -> FreezeUnfreeze.launchApp(this, si) }
                .setNegativeButton(R.string.freeze) { _, _ -> mViewModel!!.freezeFinal(si) }
                .setOnDismissListener { mViewModel!!.checkNextFrozen() }.show()
        }
    }

    override fun getTransparentBackground(): Boolean = true

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (!SelfPermissions.canFreezeUnfreezePackages()) {
            UIUtils.displayShortToast(R.string.only_works_in_root_or_adb_mode)
            finish()
            return
        }
        val si = FreezeUnfreeze.getShortcutInfo(intent)
        if (mViewModel != null && si != null) {
            hideNotification(si)
            mViewModel!!.addToPendingShortcuts(si)
        }
    }

    private fun hideNotification(si: FreezeUnfreezeShortcutInfo?) {
        si?.let { NotificationUtils.getFreezeUnfreezeNotificationManager(this).cancel(it.hashCode().toString(), 1) }
    }

    class FreezeUnfreezeViewModel(application: Application) : AndroidViewModel(application) {
        val mIsFrozenLiveData = MutableLiveData<Pair<FreezeUnfreezeShortcutInfo, Boolean>?>()
        val mOpenAppOrFreeze = MutableLiveData<FreezeUnfreezeShortcutInfo>()
        private val mPendingShortcuts: Queue<FreezeUnfreezeShortcutInfo> = LinkedList()

        fun addToPendingShortcuts(si: FreezeUnfreezeShortcutInfo) {
            synchronized(mPendingShortcuts) { mPendingShortcuts.add(si) }
        }

        fun checkNextFrozen() {
            ThreadUtils.postOnBackgroundThread {
                val si = synchronized(mPendingShortcuts) { mPendingShortcuts.poll() }
                if (si == null) { mIsFrozenLiveData.postValue(null); return@postOnBackgroundThread }
                val force = (si.privateFlags and FreezeUnfreeze.PRIVATE_FLAG_FREEZE_FORCE) != 0
                try {
                    val ai = PackageManagerCompat.getApplicationInfo(si.packageName,
                        PackageManagerCompat.MATCH_UNINSTALLED_PACKAGES or PackageManagerCompat.MATCH_DISABLED_COMPONENTS or PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, si.userId)
                    val pm = getApplication<Application>().packageManager
                    val icon = getBitmapFromDrawable(ai.loadIcon(pm))
                    si.name = ai.loadLabel(pm)
                    val isFrozen = !force && FreezeUtils.isFrozen(ai)
                    if (isFrozen) {
                        FreezeUtils.unfreeze(si.packageName, si.userId)
                        si.icon = icon
                    } else {
                        si.icon = getDimmedBitmap(icon)
                        if (!force && si.flags and FreezeUnfreeze.FLAG_ON_UNFREEZE_OPEN_APP != 0) {
                            mOpenAppOrFreeze.postValue(si)
                            return@postOnBackgroundThread
                        }
                        val type = FreezeUtils.loadFreezeMethod(si.packageName) ?: Prefs.Blocking.getDefaultFreezingMethod()
                        FreezeUtils.freeze(si.packageName, si.userId, type)
                    }
                    mIsFrozenLiveData.postValue(Pair(si, !isFrozen))
                } catch (ignore: Exception) {}
            }
        }

        fun freezeFinal(si: FreezeUnfreezeShortcutInfo) {
            ThreadUtils.postOnBackgroundThread {
                try {
                    val type = FreezeUtils.loadFreezeMethod(si.packageName) ?: Prefs.Blocking.getDefaultFreezingMethod()
                    FreezeUtils.freeze(si.packageName, si.userId, type)
                    mIsFrozenLiveData.postValue(Pair(si, true))
                } catch (ignore: RemoteException) {}
            }
        }
    }
}
