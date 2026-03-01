// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.shortcut

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.os.BundleCompat
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textview.MaterialTextView
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.details.IconPickerDialogFragment
import io.github.muntashirakon.AppManager.utils.ResourceUtil
import io.github.muntashirakon.AppManager.utils.UIUtils
import io.github.muntashirakon.lifecycle.SoftInputLifeCycleObserver
import io.github.muntashirakon.view.TextInputLayoutCompat
import java.lang.ref.WeakReference
import java.util.*

class CreateShortcutDialogFragment : DialogFragment() {
    private var mValidName = true
    private var mShortcutInfo: ShortcutInfo? = null
    private var mDialogView: View? = null
    private var mShortcutNameField: TextInputEditText? = null
    private var mShortcutIconField: TextInputEditText? = null
    private var mShortcutIconLayout: TextInputLayout? = null
    private var mShortcutIconPreview: ShapeableImageView? = null
    private var mShortcutNamePreview: MaterialTextView? = null
    private var mPm: PackageManager? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        mPm = requireActivity().packageManager
        mShortcutInfo = BundleCompat.getParcelable(requireArguments(), ARG_SHORTCUT_INFO, ShortcutInfo::class.java)!!
        mDialogView = View.inflate(requireActivity(), R.layout.dialog_create_shortcut, null)
        mShortcutNameField = mDialogView!!.findViewById(R.id.shortcut_name)
        mShortcutIconField = mDialogView!!.findViewById(R.id.insert_icon)
        mShortcutIconLayout = TextInputLayoutCompat.fromTextInputEditText(mShortcutIconField!!)
        mShortcutIconPreview = mDialogView!!.findViewById(R.id.icon)
        mShortcutNamePreview = mDialogView!!.findViewById(R.id.name)

        mShortcutNameField!!.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!TextUtils.isEmpty(s)) {
                    mValidName = true
                    mShortcutInfo!!.name = s
                    mShortcutNamePreview!!.text = s
                } else mValidName = false
            }
        })
        mShortcutIconField!!.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val drawable = getDrawable(s?.toString())
                if (drawable != null) {
                    mShortcutInfo!!.icon = UIUtils.getBitmapFromDrawable(drawable)
                    mShortcutIconPreview!!.setImageDrawable(drawable)
                }
            }
        })
        mShortcutIconLayout!!.setEndIconOnClickListener {
            val dialog = IconPickerDialogFragment()
            dialog.attachIconPickerListener { icon ->
                mShortcutIconField!!.setText(icon.name)
                val drawable = icon.loadIcon(mPm!!)
                mShortcutInfo!!.icon = UIUtils.getBitmapFromDrawable(drawable)
                mShortcutIconPreview!!.setImageDrawable(drawable)
            }
            dialog.show(parentFragmentManager, IconPickerDialogFragment.TAG)
        }
        mShortcutNameField!!.setText(mShortcutInfo!!.name)
        mShortcutNamePreview!!.text = mShortcutInfo!!.name
        mShortcutIconPreview!!.setImageBitmap(mShortcutInfo!!.icon)

        return MaterialAlertDialogBuilder(requireActivity())
            .setTitle(R.string.create_shortcut)
            .setView(mDialogView)
            .setPositiveButton(R.string.ok) { _, _ -> if (mValidName) requestPinShortcut(mShortcutInfo!!) }
            .setNegativeButton(R.string.cancel, null)
            .create()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? = mDialogView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        lifecycle.addObserver(SoftInputLifeCycleObserver(WeakReference(mShortcutNameField)))
    }

    private fun getDrawable(iconResString: String?): Drawable? {
        if (TextUtils.isEmpty(iconResString)) return null
        try {
            return ResourceUtil.getResourceFromName(mPm!!, iconResString!!).getDrawable(requireActivity().theme)
        } catch (ignore: Exception) {}
        return null
    }

    private fun requestPinShortcut(shortcutInfo: ShortcutInfo) {
        val context = requireContext().applicationContext
        val name = shortcutInfo.name ?: return
        val shortcutId = shortcutInfo.id ?: UUID.randomUUID().toString()
        val shortcutIntent = shortcutInfo.toShortcutIntent(context).apply { action = Intent.ACTION_CREATE_SHORTCUT }
        val sic = ShortcutInfoCompat.Builder(context, shortcutId)
            .setShortLabel(name.toString()).setLongLabel(name)
            .setIcon(IconCompat.createWithBitmap(shortcutInfo.icon)).setIntent(shortcutIntent).build()
        if (!ShortcutManagerCompat.requestPinShortcut(context, sic, null)) {
            MaterialAlertDialogBuilder(context).setTitle(R.string.error_creating_shortcut).setMessage(R.string.error_verbose_pin_shortcut).setPositiveButton(R.string.ok) { d, _ -> d.cancel() }.show()
        }
    }

    companion object {
        val TAG: String = CreateShortcutDialogFragment::class.java.simpleName
        private const val ARG_SHORTCUT_INFO = "info"

        @JvmStatic
        fun getInstance(shortcutInfo: ShortcutInfo): CreateShortcutDialogFragment {
            return CreateShortcutDialogFragment().apply { arguments = Bundle().apply { putParcelable(ARG_SHORTCUT_INFO, shortcutInfo) } }
        }
    }
}
