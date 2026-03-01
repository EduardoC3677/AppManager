// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles

import android.app.Dialog
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.TextUtils
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.profiles.struct.AppsProfile
import io.github.muntashirakon.AppManager.utils.ExUtils
import io.github.muntashirakon.AppManager.utils.ThreadUtils
import io.github.muntashirakon.AppManager.utils.UIUtils
import io.github.muntashirakon.AppManager.utils.UIUtils.getSecondaryText
import io.github.muntashirakon.AppManager.utils.UIUtils.getSmallerText
import io.github.muntashirakon.dialog.DialogTitleBuilder
import io.github.muntashirakon.dialog.SearchableMultiChoiceDialogBuilder
import io.github.muntashirakon.dialog.TextInputDialogBuilder
import java.util.*
import java.util.concurrent.atomic.AtomicReference

class AddToProfileDialogFragment : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val packages = requireArguments().getStringArray(ARG_PKGS)!!
        val profiles = ExUtils.requireNonNullElse({ ProfileManager.getProfiles<AppsProfile>(AppsProfile.PROFILE_TYPE_APPS) }, emptyList())
        val profileNames = ArrayList<CharSequence>(profiles.size)
        for (profile in profiles) {
            profileNames.add(SpannableStringBuilder(profile.name).append("
")
                .append(getSecondaryText(requireContext(), getSmallerText(
                    profile.toLocalizedString(requireContext())))))
        }
        val dialogRef = AtomicReference<AlertDialog>()
        val titleBuilder = DialogTitleBuilder(requireContext())
            .setTitle(R.string.add_to_profile)
            .setEndIconContentDescription(R.string.new_profile)
            .setEndIcon(R.drawable.ic_add) {
                TextInputDialogBuilder(requireContext(), R.string.input_profile_name)
                    .setTitle(R.string.new_profile)
                    .setHelperText(R.string.input_profile_name_description)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.go) { _, _, profName, _ ->
                        if (!TextUtils.isEmpty(profName)) {
                            startActivity(AppsProfileActivity.getNewProfileIntent(requireContext(),
                                profName.toString(), packages))
                            dialogRef.get()?.dismiss()
                        }
                    }
                    .show()
            }
        val alertDialog = SearchableMultiChoiceDialogBuilder(requireContext(), profiles, profileNames)
            .setTitle(titleBuilder.build())
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.add) { _, _, selectedItems ->
                ThreadUtils.postOnBackgroundThread {
                    var isSuccess = true
                    for (profile in selectedItems) {
                        val profilePath = ProfileManager.findProfilePathById(profile.profileId)
                        if (profilePath == null) {
                            isSuccess = false
                            continue
                        }
                        try {
                            profilePath.openOutputStream().use { os ->
                                profile.appendPackages(packages)
                                profile.write(os)
                            }
                        } catch (e: Throwable) {
                            isSuccess = false
                            e.printStackTrace()
                        }
                    }
                    ThreadUtils.postOnMainThread {
                        UIUtils.displayShortToast(if (isSuccess) R.string.done else R.string.failed)
                    }
                }
            }
            .create()
        dialogRef.set(alertDialog)
        return alertDialog
    }

    companion object {
        val TAG: String = AddToProfileDialogFragment::class.java.simpleName
        private const val ARG_PKGS = "pkgs"

        @JvmStatic
        fun getInstance(packages: Array<String>): AddToProfileDialogFragment {
            val fragment = AddToProfileDialogFragment()
            val args = Bundle()
            args.putStringArray(ARG_PKGS, packages)
            fragment.arguments = args
            return fragment
        }
    }
}
