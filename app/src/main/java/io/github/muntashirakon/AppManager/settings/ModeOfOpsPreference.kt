// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings

import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textview.MaterialTextView
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.ipc.LocalServices
import io.github.muntashirakon.AppManager.servermanager.LocalServer
import io.github.muntashirakon.AppManager.servermanager.ServerConfig
import io.github.muntashirakon.AppManager.users.Users
import io.github.muntashirakon.AppManager.utils.UIUtils
import io.github.muntashirakon.AppManager.utils.Utils
import io.github.muntashirakon.util.UiUtils
import io.github.muntashirakon.view.TextInputLayoutCompat
import io.github.muntashirakon.widget.TextInputTextView
import java.util.*

class ModeOfOpsPreference : Fragment() {
    companion object {
        private val MODE_NAMES = listOf(
            Ops.MODE_AUTO,
            Ops.MODE_ROOT,
            Ops.MODE_ADB_OVER_TCP,
            Ops.MODE_ADB_WIFI,
            Ops.MODE_SHIZUKU,
            Ops.MODE_NO_ROOT
        )

        private fun requireRemoteServer(mode: String): Boolean {
            return Ops.MODE_ADB_OVER_TCP == mode || Ops.MODE_ADB_WIFI == mode
        }

        private fun requireRemoteServices(mode: String): Boolean {
            return (Ops.MODE_AUTO != mode
                    && Ops.MODE_NO_ROOT != mode
                    && Ops.MODE_SHIZUKU != mode) // Shizuku doesn't need remote services
        }

        private fun badInferredMode(mode: String, uid: Int): Boolean {
            return when (mode) {
                Ops.MODE_ROOT -> uid != Ops.ROOT_UID
                Ops.MODE_ADB_OVER_TCP, Ops.MODE_ADB_WIFI -> uid > Ops.SHELL_UID
                Ops.MODE_SHIZUKU ->                         // Shizuku manages its own elevated permissions, always good if selected
                    false
                else -> false
            }
        }
    }

    private lateinit var mInferredModeView: MaterialTextView
    private lateinit var mRemoteServerStatusView: MaterialTextView
    private lateinit var mRemoteServicesStatusView: MaterialTextView
    private lateinit var mModeOfOpsView: MaterialTextView
    private lateinit var mModel: MainPreferencesViewModel
    private lateinit var mModeOfOpsAlertDialog: AlertDialog
    private lateinit var mModes: Array<String>
    private lateinit var mCurrentMode: String
    private var mConnecting = false
    private var mColorActive: ColorStateList? = null
    private var mColorInactive: ColorStateList? = null
    private var mColorError: ColorStateList? = null
    @DrawableRes
    private var mIconActive = 0
    @DrawableRes
    private var mIconInactive = 0
    @DrawableRes
    private var mIconProgress = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mModel = ViewModelProvider(requireActivity())[MainPreferencesViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_mode_of_ops, container, false)
        var secondary = false
        val args = arguments
        if (args != null) {
            secondary = args.getBoolean(PreferenceFragment.PREF_SECONDARY)
            args.remove(PreferenceFragment.PREF_KEY)
            args.remove(PreferenceFragment.PREF_SECONDARY)
        }
        if (secondary) {
            UiUtils.applyWindowInsetsAsPadding(view, false, true, false, true)
        } else {
            UiUtils.applyWindowInsetsAsPaddingNoTop(view)
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        mColorActive = MaterialColors.getColorStateListOrNull(
            view.context,
            com.google.android.material.R.attr.colorOnPrimaryContainer
        )
        mColorInactive =
            MaterialColors.getColorStateListOrNull(view.context, com.google.android.material.R.attr.colorOutline)
        mColorError = MaterialColors.getColorStateListOrNull(
            view.context,
            com.google.android.material.R.attr.colorOnErrorContainer
        )
        mIconActive = R.drawable.ic_check_circle
        mIconInactive = io.github.muntashirakon.ui.R.drawable.ic_caution
        mIconProgress = R.drawable.ic_sync
        mModeOfOpsAlertDialog = UIUtils.getProgressDialog(requireActivity(), getString(R.string.loading), true)
        mModes = resources.getStringArray(R.array.modes)
        mCurrentMode = Ops.getMode()
        mInferredModeView = view.findViewById(R.id.inferred_mode)
        mRemoteServerStatusView = view.findViewById(R.id.remote_server_status)
        mRemoteServicesStatusView = view.findViewById(R.id.remote_services_status)
        mModeOfOpsView = view.findViewById(R.id.op_name)
        val changeModeView = view.findViewById<MaterialButton>(R.id.action_settings)
        val disableWifiMode = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Utils.isTv(requireContext())
        changeModeView.setOnClickListener { v: View? ->
            val currentIndex = MODE_NAMES.indexOf(mCurrentMode)
            MaterialAlertDialogBuilder(requireActivity())
                .setTitle(R.string.pref_mode_of_operations)
                .setSingleChoiceItems(mModes, currentIndex) { dialog, which ->
                    // Disable WiFi mode selection if not supported
                    if (disableWifiMode && which == MODE_NAMES.indexOf(Ops.MODE_ADB_WIFI)) {
                        return@setSingleChoiceItems  // Don't allow selection
                    }
                    val selectedMode = MODE_NAMES[which]
                    if (selectedMode != null) {
                        mCurrentMode = selectedMode
                        if (Ops.MODE_ADB_OVER_TCP == mCurrentMode) {
                            ServerConfig.setAdbPort(ServerConfig.DEFAULT_ADB_PORT)
                        }
                        Ops.setMode(mCurrentMode)
                        mModeOfOpsAlertDialog.show()
                        mConnecting = true
                        updateViews()
                        mModel.setModeOfOps()
                        dialog.dismiss()
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
        val customCommand0 = view.findViewById<TextInputTextView>(android.R.id.text1)
        val customCommand0Layout = TextInputLayoutCompat.fromTextInputEditText(customCommand0)
        customCommand0Layout.setEndIconOnClickListener { v: View? ->
            val command = customCommand0.text
            if (!TextUtils.isEmpty(command)) {
                Utils.copyToClipboard(requireContext(), "command", command)
            }
        }
        val customCommand1 = view.findViewById<TextInputTextView>(android.R.id.text2)
        val customCommand1Layout = TextInputLayoutCompat.fromTextInputEditText(customCommand1)
        customCommand1Layout.setEndIconOnClickListener { v: View? ->
            val command = customCommand1.text
            if (!TextUtils.isEmpty(command)) {
                Utils.copyToClipboard(requireContext(), "command", command)
            }
        }
        mModel.loadCustomCommands()
        updateViews()
        // Mode of ops
        mModel.getModeOfOpsStatus().observe(viewLifecycleOwner) { status ->
            when (status) {
                Ops.STATUS_AUTO_CONNECT_WIRELESS_DEBUGGING -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    updateViews()
                    mModel.autoConnectWirelessDebugging()
                    return@observe
                }
                Ops.STATUS_WIRELESS_DEBUGGING_CHOOSER_REQUIRED -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    mModeOfOpsAlertDialog.dismiss()
                    updateViews()
                    Ops.connectWirelessDebugging(requireActivity(), mModel)
                    return@observe
                }
                Ops.STATUS_ADB_CONNECT_REQUIRED -> {
                    mModeOfOpsAlertDialog.dismiss()
                    updateViews()
                    Ops.connectAdbInput(requireActivity(), mModel)
                    return@observe
                }
                Ops.STATUS_ADB_PAIRING_REQUIRED -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    mModeOfOpsAlertDialog.dismiss()
                    updateViews()
                    Ops.pairAdbInput(requireActivity(), mModel)
                    return@observe
                }
                Ops.STATUS_FAILURE_ADB_NEED_MORE_PERMS -> Ops.displayIncompleteUsbDebuggingMessage(
                    requireActivity()
                )
                Ops.STATUS_SUCCESS, Ops.STATUS_FAILURE -> {
                    mConnecting = false
                    mModeOfOpsAlertDialog.dismiss()
                    mCurrentMode = Ops.getMode()
                    updateViews()
                }
            }
        }
        mModel.getCustomCommand0().observe(viewLifecycleOwner) { text: String? -> customCommand0.setText(text) }
        mModel.getCustomCommand1().observe(viewLifecycleOwner) { text: String? -> customCommand1.setText(text) }
    }

    override fun onStart() {
        super.onStart()
        requireActivity().setTitle(R.string.pref_mode_of_operations)
    }

    private fun updateViews() {
        val serverActive = LocalServer.alive(requireContext())
        val serverRequired = requireRemoteServer(mCurrentMode)
        val servicesActive = LocalServices.alive()
        val servicesRequired = requireRemoteServices(mCurrentMode)
        // Mode
        if (mConnecting) {
            mInferredModeView.setText(R.string.status_connecting)
            mInferredModeView.setTextColor(mColorActive)
            TextViewCompat.setCompoundDrawableTintList(mModeOfOpsView, mColorActive)
            mModeOfOpsView.setTextColor(mColorActive)
            mModeOfOpsView.setCompoundDrawablesRelativeWithIntrinsicBounds(mIconProgress, 0, 0, 0)
            mModeOfOpsView.text = getString(R.string.status_connecting_via_mode, mModes[MODE_NAMES.indexOf(mCurrentMode)])
        } else {
            val uid = Users.getSelfOrRemoteUid()
            val goodMode = !badInferredMode(mCurrentMode, uid)
            mInferredModeView.text = Ops.getInferredMode(requireContext())
            if (goodMode) {
                mInferredModeView.setTextColor(mColorActive)
                TextViewCompat.setCompoundDrawableTintList(mModeOfOpsView, mColorActive)
                mModeOfOpsView.setTextColor(mColorActive)
                mModeOfOpsView.setCompoundDrawablesRelativeWithIntrinsicBounds(mIconActive, 0, 0, 0)
                val mode: CharSequence = if (serverActive && uid != Process.myUid()) {
                    "remote service"
                } else {
                    mModes[MODE_NAMES.indexOf(mCurrentMode)]
                }
                mModeOfOpsView.text = getString(R.string.status_connected_via_mode, mode)
            } else {
                mInferredModeView.setTextColor(mColorError)
                TextViewCompat.setCompoundDrawableTintList(mModeOfOpsView, mColorError)
                mModeOfOpsView.setTextColor(mColorError)
                mModeOfOpsView.setCompoundDrawablesRelativeWithIntrinsicBounds(mIconInactive, 0, 0, 0)
                mModeOfOpsView.text = getString(R.string.status_not_connected_via_mode, mModes[MODE_NAMES.indexOf(mCurrentMode)])
            }
        }
        // Server
        if (serverRequired) {
            mRemoteServerStatusView.setTextColor(if (serverActive) mColorActive else mColorError)
            TextViewCompat.setCompoundDrawableTintList(
                mRemoteServerStatusView,
                if (serverActive) mColorActive else mColorError
            )
        } else {
            mRemoteServerStatusView.setTextColor(mColorInactive)
            TextViewCompat.setCompoundDrawableTintList(mRemoteServerStatusView, mColorInactive)
        }
        mRemoteServerStatusView.setCompoundDrawablesRelativeWithIntrinsicBounds(if (serverActive) mIconActive else mIconInactive, 0, 0, 0)
        mRemoteServerStatusView.setText(if (serverActive) R.string.status_remote_server_active else R.string.status_remote_server_inactive)
        // Services
        if (servicesRequired) {
            mRemoteServicesStatusView.setTextColor(if (servicesActive) mColorActive else mColorError)
            TextViewCompat.setCompoundDrawableTintList(
                mRemoteServicesStatusView,
                if (servicesActive) mColorActive else mColorError
            )
        } else {
            mRemoteServicesStatusView.setTextColor(mColorInactive)
            TextViewCompat.setCompoundDrawableTintList(mRemoteServicesStatusView, mColorInactive)
        }
        mRemoteServicesStatusView.setCompoundDrawablesRelativeWithIntrinsicBounds(if (servicesActive) mIconActive else mIconInactive, 0, 0, 0)
        mRemoteServicesStatusView.setText(if (servicesActive) R.string.status_remote_services_active else R.string.status_remote_services_inactive)
    }
}
