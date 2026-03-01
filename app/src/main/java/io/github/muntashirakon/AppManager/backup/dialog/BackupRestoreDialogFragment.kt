// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.dialog

import android.annotation.UserIdInt
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.TypedArray
import android.os.Bundle
import android.os.UserHandleHidden
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.IntDef
import androidx.annotation.UiThread
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager
import io.github.muntashirakon.AppManager.batchops.BatchOpsService
import io.github.muntashirakon.AppManager.batchops.BatchQueueItem
import io.github.muntashirakon.AppManager.batchops.struct.BatchBackupOptions
import io.github.muntashirakon.AppManager.types.UserPackagePair
import io.github.muntashirakon.AppManager.users.UserInfo
import io.github.muntashirakon.AppManager.utils.ArrayUtils
import io.github.muntashirakon.AppManager.utils.StoragePermission
import io.github.muntashirakon.dialog.BottomSheetBehavior
import io.github.muntashirakon.dialog.CapsuleBottomSheetDialogFragment
import io.github.muntashirakon.dialog.DialogTitleBuilder
import io.github.muntashirakon.dialog.SearchableMultiChoiceDialogBuilder
import java.util.*

class BackupRestoreDialogFragment : CapsuleBottomSheetDialogFragment() {
    companion object {
        val TAG: String = BackupRestoreDialogFragment::class.java.simpleName

        private const val ARG_PACKAGE_PAIRS = "pkg_pairs"
        private const val ARG_CUSTOM_MODE = "custom_mode"
        private const val ARG_PREFERRED_USER_FOR_RESTORE = "pref_user_restore"

        const val MODE_BACKUP = 1
        const val MODE_RESTORE = 1 shl 1
        const val MODE_DELETE = 1 shl 2

        @JvmStatic
        fun getInstance(userPackagePairs: List<UserPackagePair>): BackupRestoreDialogFragment {
            val fragment = BackupRestoreDialogFragment()
            val args = Bundle()
            args.putParcelableArrayList(ARG_PACKAGE_PAIRS, ArrayList(userPackagePairs))
            fragment.arguments = args
            return fragment
        }

        @JvmStatic
        fun getInstanceWithPref(userPackagePairs: List<UserPackagePair>, @UserIdInt preferredUserForRestore: Int): BackupRestoreDialogFragment {
            val fragment = BackupRestoreDialogFragment()
            val args = Bundle()
            args.putParcelableArrayList(ARG_PACKAGE_PAIRS, ArrayList(userPackagePairs))
            args.putInt(ARG_PREFERRED_USER_FOR_RESTORE, preferredUserForRestore)
            fragment.arguments = args
            return fragment
        }

        @JvmStatic
        fun getInstance(userPackagePairs: List<UserPackagePair>, @ActionMode mode: Int): BackupRestoreDialogFragment {
            val fragment = BackupRestoreDialogFragment()
            val args = Bundle()
            args.putParcelableArrayList(ARG_PACKAGE_PAIRS, ArrayList(userPackagePairs))
            args.putInt(ARG_CUSTOM_MODE, mode)
            fragment.arguments = args
            return fragment
        }
    }

    @IntDef(flag = true, value = [MODE_BACKUP, MODE_RESTORE, MODE_DELETE])
    @Retention(AnnotationRetention.SOURCE)
    annotation class ActionMode

    interface ActionCompleteInterface {
        fun onActionComplete(@ActionMode mode: Int, failedPackages: Array<String>)
    }

    interface ActionBeginInterface {
        fun onActionBegin(@ActionMode mode: Int)
    }

    private var mActionCompleteInterface: ActionCompleteInterface? = null
    private var mActionBeginInterface: ActionBeginInterface? = null
    @ActionMode
    private var mMode = MODE_BACKUP
    private var mActivity: FragmentActivity? = null
    private var mViewModel: BackupRestoreDialogViewModel? = null
    private var mTabFragments: Array<Fragment>? = null
    private var mTabTitles: TypedArray? = null
    private var mDialogTitleBuilder: DialogTitleBuilder? = null
    private var mCustomModes: Int = 0

    private val mStoragePermission = StoragePermission.init(this)
    private val mBatchOpsBroadCastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (mActionCompleteInterface != null) {
                val failedPackages = intent.getStringArrayListExtra(BatchOpsService.EXTRA_FAILED_PKG)
                mActionCompleteInterface!!.onActionComplete(mMode, failedPackages?.toTypedArray() ?: emptyArray())
            }
            mActivity?.unregisterReceiver(this)
        }
    }

    fun setOnActionCompleteListener(actionCompleteInterface: ActionCompleteInterface) {
        mActionCompleteInterface = actionCompleteInterface
    }

    fun setOnActionBeginListener(actionBeginInterface: ActionBeginInterface) {
        mActionBeginInterface = actionBeginInterface
    }

    override fun initRootView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.dialog_backup_restore, container, false)
    }

    override fun displayLoaderByDefault(): Boolean = true

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mActivity = requireActivity()
        mStoragePermission.request()
    }

    override fun onBodyInitialized(bodyView: View, savedInstanceState: Bundle?) {
        mViewModel = ViewModelProvider(this).get(BackupRestoreDialogViewModel::class.java)
        mActivity = requireActivity()
        val args = requireArguments()
        val targetPackages = args.getParcelableArrayList<UserPackagePair>(ARG_PACKAGE_PAIRS)!!
        mCustomModes = args.getInt(ARG_CUSTOM_MODE, MODE_BACKUP or MODE_RESTORE or MODE_DELETE)
        val preferredUserForRestore = args.getInt(ARG_PREFERRED_USER_FOR_RESTORE, -1)
        if (preferredUserForRestore >= 0) {
            mViewModel!!.setPreferredUserForRestore(preferredUserForRestore)
        }

        mDialogTitleBuilder = DialogTitleBuilder(requireContext())
            .setTitle(R.string.backup_restore)
            .setStartIcon(R.drawable.ic_backup_restore)
        setHeader(mDialogTitleBuilder!!.build())

        mViewModel!!.backupInfoStateLiveData.observe(this) { state -> loadBody(state) }
        mViewModel!!.backupOperationLiveData.observe(this) { info -> startOperation(info) }
        mViewModel!!.userSelectionLiveData.observe(this) { info -> handleCustomUsers(info) }
        mViewModel!!.setPackageList(targetPackages)
    }

    private fun loadBody(@BackupInfoState state: Int) {
        val realState = getRealState(state)
        Log.d(TAG, "Backup dialog state: $realState")
        when (realState) {
            BackupInfoState.NONE -> showBackupOptionsUnavailable()
            BackupInfoState.BACKUP_MULTIPLE -> loadMultipleBackupFragment()
            BackupInfoState.RESTORE_MULTIPLE -> loadMultipleRestoreFragment()
            BackupInfoState.BOTH_MULTIPLE -> loadMultipleBackupRestoreViewPager()
            BackupInfoState.BACKUP_SINGLE -> loadSingleBackupFragment()
            BackupInfoState.RESTORE_SINGLE -> loadSingleRestoreFragment()
            BackupInfoState.BOTH_SINGLE -> loadSingleBackupRestoreViewPager()
            else -> showBackupOptionsUnavailable()
        }
    }

    @BackupInfoState
    private fun getRealState(@BackupInfoState state: Int): Int {
        val singleMode = state == BackupInfoState.BACKUP_SINGLE || state == BackupInfoState.RESTORE_SINGLE ||
                state == BackupInfoState.BOTH_SINGLE
        when (state) {
            BackupInfoState.NONE -> return state
            BackupInfoState.BACKUP_MULTIPLE, BackupInfoState.BACKUP_SINGLE -> if (mCustomModes and MODE_BACKUP == 0) {
                return BackupInfoState.NONE
            }
            BackupInfoState.BOTH_MULTIPLE, BackupInfoState.BOTH_SINGLE -> {
                val canBackup = mCustomModes and MODE_BACKUP != 0
                val canRestore = mCustomModes and MODE_RESTORE != 0
                if (!canBackup && !canRestore) {
                    return BackupInfoState.NONE
                }
                if (!canRestore) {
                    return if (singleMode) BackupInfoState.BACKUP_SINGLE else BackupInfoState.BACKUP_MULTIPLE
                }
                if (!canBackup) {
                    return if (singleMode) BackupInfoState.RESTORE_SINGLE else BackupInfoState.RESTORE_MULTIPLE
                }
            }
            BackupInfoState.RESTORE_MULTIPLE, BackupInfoState.RESTORE_SINGLE -> if (mCustomModes and MODE_RESTORE == 0) {
                return BackupInfoState.NONE
            }
        }
        return state
    }

    private fun showBackupOptionsUnavailable() {
        body!!.findViewById<View>(R.id.message).visibility = View.VISIBLE
        body!!.findViewById<View>(R.id.fragment_container_view_tag).visibility = View.GONE
        finishLoading()
    }

    private fun getBackupFragment(): BackupFragment {
        return BackupFragment.getInstance(mViewModel!!.allowCustomUsersInBackup())
    }

    private fun loadMultipleBackupFragment() {
        mDialogTitleBuilder!!.setTitle(R.string.backup)
        setHeader(mDialogTitleBuilder!!.build())
        finishLoading()
        childFragmentManager
            .beginTransaction()
            .replace(R.id.fragment_container_view_tag, getBackupFragment())
            .commit()
    }

    private fun loadMultipleRestoreFragment() {
        mDialogTitleBuilder!!.setTitle(R.string.restore)
        updateMultipleRestoreHeader()
        finishLoading()
        childFragmentManager
            .beginTransaction()
            .replace(R.id.fragment_container_view_tag, RestoreMultipleFragment.getInstance())
            .commit()
    }

    private fun loadMultipleBackupRestoreViewPager() {
        updateMultipleRestoreHeader()

        mTabTitles = resources.obtainTypedArray(R.array.backup_restore_tabs_multiple)
        mTabFragments = arrayOf(getBackupFragment(), RestoreMultipleFragment.getInstance())
        body!!.findViewById<View>(R.id.container).visibility = View.VISIBLE
        val viewPager: ViewPager2 = body!!.findViewById(R.id.pager)
        val tabLayout: TabLayout = body!!.findViewById(R.id.tab_layout)
        viewPager.offscreenPageLimit = 1
        viewPager.registerOnPageChangeCallback(ViewPagerUpdateScrollingChildListener(viewPager, behavior!!))
        finishLoading()
        viewPager.adapter = BackupDialogFragmentPagerAdapter(this)
        TabLayoutMediator(tabLayout, viewPager) { tab, position -> tab.text = mTabTitles!!.getString(position) }
            .attach()
    }

    private fun updateMultipleRestoreHeader() {
        // Display delete button
        mDialogTitleBuilder!!.setEndIcon(R.drawable.ic_trash_can) { handleDeleteBaseBackup() }
            .setEndIconContentDescription(R.string.delete_backup)
        setHeader(mDialogTitleBuilder!!.build())
    }

    private fun loadSingleBackupFragment() {
        mDialogTitleBuilder!!.setTitle(R.string.backup)
        updateSingleBackupHeader()
        finishLoading()
        childFragmentManager
            .beginTransaction()
            .replace(R.id.fragment_container_view_tag, getBackupFragment())
            .commit()
    }

    private fun loadSingleRestoreFragment() {
        mDialogTitleBuilder!!.setTitle(R.string.restore_dots)
        updateSingleBackupHeader()
        finishLoading()
        childFragmentManager
            .beginTransaction()
            .replace(R.id.fragment_container_view_tag, RestoreSingleFragment.getInstance())
            .commit()
    }

    private fun loadSingleBackupRestoreViewPager() {
        updateSingleBackupHeader()

        mTabTitles = resources.obtainTypedArray(R.array.backup_restore_tabs_single)
        mTabFragments = arrayOf(getBackupFragment(), RestoreSingleFragment.getInstance())
        body!!.findViewById<View>(R.id.container).visibility = View.VISIBLE
        val viewPager: ViewPager2 = body!!.findViewById(R.id.pager)
        val tabLayout: TabLayout = body!!.findViewById(R.id.tab_layout)
        viewPager.offscreenPageLimit = 1
        viewPager.registerOnPageChangeCallback(ViewPagerUpdateScrollingChildListener(viewPager, behavior!!))
        finishLoading()
        viewPager.adapter = BackupDialogFragmentPagerAdapter(this)
        TabLayoutMediator(tabLayout, viewPager) { tab, position -> tab.text = mTabTitles!!.getString(position) }
            .attach()
    }

    private fun updateSingleBackupHeader() {
        mDialogTitleBuilder!!.setSubtitle(mViewModel!!.backupInfo.appLabel)
        setHeader(mDialogTitleBuilder!!.build())
    }

    private fun handleCustomUsers(operationInfo: BackupRestoreDialogViewModel.OperationInfo) {
        val users = operationInfo.userInfoList!!
        val userNames = Array<CharSequence>(users.size) { i -> users[i].toLocalizedString(requireContext()) }
        val userHandles = users.map { it.id }

        SearchableMultiChoiceDialogBuilder(mActivity!!, userHandles, userNames)
            .setTitle(R.string.select_user)
            .addSelections(listOf(UserHandleHidden.myUserId()))
            .showSelectAll(false)
            .setPositiveButton(R.string.ok) { _, _, selectedUsers ->
                if (selectedUsers.isNotEmpty()) {
                    operationInfo.selectedUsers = ArrayUtils.convertToIntArray(selectedUsers)
                }
                mViewModel!!.prepareForOperation(operationInfo)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun handleDeleteBaseBackup() {
        MaterialAlertDialogBuilder(mActivity!!)
            .setTitle(R.string.delete_backup)
            .setMessage(R.string.are_you_sure)
            .setPositiveButton(R.string.yes) { _, _ ->
                val operationInfo = BackupRestoreDialogViewModel.OperationInfo()
                operationInfo.mode = MODE_DELETE
                operationInfo.op = BatchOpsManager.OP_DELETE_BACKUP
                mViewModel!!.prepareForOperation(operationInfo)
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    @UiThread
    private fun startOperation(operationInfo: BackupRestoreDialogViewModel.OperationInfo) {
        mMode = operationInfo.mode
        mActionBeginInterface?.onActionBegin(operationInfo.mode)
        ContextCompat.registerReceiver(
            mActivity!!, mBatchOpsBroadCastReceiver,
            IntentFilter(BatchOpsService.ACTION_BATCH_OPS_COMPLETED), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        // Start batch ops service
        val options = BatchBackupOptions(operationInfo.flags, operationInfo.backupNames, operationInfo.uuids)
        val queueItem = BatchQueueItem.getBatchOpQueue(
            operationInfo.op,
            operationInfo.packageList, operationInfo.userIdListMappedToPackageList, options
        )
        val intent = BatchOpsService.getServiceIntent(mActivity!!, queueItem)
        ContextCompat.startForegroundService(mActivity!!, intent)
        dismiss()
    }

    private inner class BackupDialogFragmentPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        override fun createFragment(position: Int): Fragment = mTabFragments!![position]
        override fun getItemCount(): Int = mTabTitles!!.length()
    }

    private class ViewPagerUpdateScrollingChildListener(
        private val mViewPager: ViewPager2,
        private val mBehavior: BottomSheetBehavior<FrameLayout>
    ) : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            mViewPager.post { mBehavior.updateScrollingChild() }
        }
    }
}
