// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.annotation.StringDef
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import io.github.muntashirakon.AppManager.BaseActivity
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.profiles.struct.BaseProfile
import io.github.muntashirakon.AppManager.utils.ThreadUtils
import io.github.muntashirakon.dialog.DialogTitleBuilder
import io.github.muntashirakon.dialog.SearchableSingleChoiceDialogBuilder
import org.json.JSONException
import java.io.IOException
import java.util.*

class ProfileApplierActivity : BaseActivity() {
    @StringDef(ST_SIMPLE, ST_ADVANCED)
    @Retention(AnnotationRetention.SOURCE)
    annotation class ShortcutType

    class ProfileApplierInfo {
        var profile: BaseProfile? = null
        var profileId: String = ""
        @ShortcutType
        var shortcutType: String = ST_SIMPLE
        var state: String? = null
        var notify: Boolean = true
    }

    private val mQueue: Queue<Intent> = LinkedList()
    private var mViewModel: ProfileApplierViewModel? = null

    override fun getTransparentBackground(): Boolean = true

    override fun onAuthenticated(savedInstanceState: Bundle?) {
        mViewModel = ViewModelProvider(this).get(ProfileApplierViewModel::class.java)
        synchronized(mQueue) {
            mQueue.add(intent)
        }
        mViewModel!!.mProfileLiveData.observe(this) { handleShortcut(it) }
        next()
    }

    override fun onNewIntent(intent: Intent) {
        synchronized(mQueue) {
            mQueue.add(intent)
        }
        super.onNewIntent(intent)
    }

    private fun next() {
        val intent = synchronized(mQueue) {
            mQueue.poll()
        }
        if (intent == null) {
            finish()
            return
        }
        val shortcutType = intent.getStringExtra(EXTRA_SHORTCUT_TYPE)
        val profileId = intent.getStringExtra(EXTRA_PROFILE_ID)
        val profileState = intent.getStringExtra(EXTRA_STATE)
        val notify = intent.getBooleanExtra(EXTRA_NOTIFY, true)
        if (shortcutType == null || profileId == null) {
            return
        }
        val info = ProfileApplierInfo().apply {
            this.profileId = profileId
            this.shortcutType = shortcutType
            this.state = profileState
            this.notify = notify
        }
        mViewModel!!.loadProfile(info)
    }

    private fun handleShortcut(info: ProfileApplierInfo?) {
        if (info?.profile == null) {
            next()
            return
        }
        info.state = info.state ?: info.profile!!.state
        when (info.shortcutType) {
            ST_SIMPLE -> {
                val intent = ProfileApplierService.getIntent(this,
                    ProfileQueueItem.fromProfiledApplierInfo(info), info.notify)
                ContextCompat.startForegroundService(this, intent)
                next()
            }
            ST_ADVANCED -> {
                val statesL = arrayOf(getString(R.string.on), getString(R.string.off))
                val states = listOf(BaseProfile.STATE_ON, BaseProfile.STATE_OFF)
                val titleBuilder = DialogTitleBuilder(this)
                    .setTitle(getString(R.string.apply_profile, info.profile!!.name))
                    .setSubtitle(R.string.choose_a_profile_state)
                SearchableSingleChoiceDialogBuilder(this, states, statesL)
                    .setTitle(titleBuilder.build())
                    .setSelection(info.state)
                    .setPositiveButton(R.string.ok) { _, _, selectedState ->
                        info.state = selectedState
                        val aIntent = ProfileApplierService.getIntent(this,
                            ProfileQueueItem.fromProfiledApplierInfo(info), info.notify)
                        ContextCompat.startForegroundService(this, aIntent)
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .setOnDismissListener { next() }
                    .show()
            }
            else -> next()
        }
    }

    class ProfileApplierViewModel(application: Application) : AndroidViewModel(application) {
        val mProfileLiveData = MutableLiveData<ProfileApplierInfo>()

        fun loadProfile(info: ProfileApplierInfo) {
            ThreadUtils.postOnBackgroundThread {
                val profilePath = ProfileManager.findProfilePathById(info.profileId)
                try {
                    info.profile = BaseProfile.fromPath(profilePath)
                    mProfileLiveData.postValue(info)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    companion object {
        private const val EXTRA_SHORTCUT_TYPE = "shortcut"
        const val EXTRA_PROFILE_ID = "prof"
        const val EXTRA_STATE = "state"
        private const val EXTRA_NOTIFY = "notify"

        const val ST_SIMPLE = "simple"
        const val ST_ADVANCED = "advanced"

        @JvmStatic
        fun getShortcutIntent(context: Context, profileId: String, @ShortcutType shortcutType: String?, state: String?): Intent {
            val realProfileId = ProfileManager.getProfileIdCompat(profileId)
            val intent = Intent(context, ProfileApplierActivity::class.java)
            intent.putExtra(EXTRA_PROFILE_ID, realProfileId)
            if (shortcutType == null) {
                if (state != null) {
                    intent.putExtra(EXTRA_SHORTCUT_TYPE, ST_SIMPLE)
                    intent.putExtra(EXTRA_STATE, state)
                } else {
                    intent.putExtra(EXTRA_SHORTCUT_TYPE, ST_ADVANCED)
                }
            } else {
                intent.putExtra(EXTRA_SHORTCUT_TYPE, shortcutType)
                if (state != null) {
                    intent.putExtra(EXTRA_STATE, state)
                }
            }
            return intent
        }

        @JvmStatic
        fun getAutomationIntent(context: Context, profileId: String, state: String?): Intent {
            val realProfileId = ProfileManager.getProfileIdCompat(profileId)
            val intent = Intent(context, ProfileApplierActivity::class.java)
            intent.putExtra(EXTRA_PROFILE_ID, realProfileId)
            if (state != null) {
                intent.putExtra(EXTRA_SHORTCUT_TYPE, ST_SIMPLE)
                intent.putExtra(EXTRA_STATE, state)
                intent.putExtra(EXTRA_NOTIFY, false)
            } else {
                intent.putExtra(EXTRA_SHORTCUT_TYPE, ST_ADVANCED)
            }
            return intent
        }

        @JvmStatic
        fun getApplierIntent(context: Context, profileId: String): Intent {
            val realProfileId = ProfileManager.getProfileIdCompat(profileId)
            val intent = Intent(context, ProfileApplierActivity::class.java)
            intent.putExtra(EXTRA_PROFILE_ID, realProfileId)
            intent.putExtra(EXTRA_SHORTCUT_TYPE, ST_ADVANCED)
            return intent
        }
    }
}
