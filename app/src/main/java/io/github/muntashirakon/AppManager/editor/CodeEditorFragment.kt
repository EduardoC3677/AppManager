// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.editor

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.appcompat.widget.PopupMenu
import androidx.core.os.BundleCompat
import androidx.core.os.ParcelCompat
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.transition.TransitionManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.elevation.SurfaceColors
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.transition.MaterialSharedAxis
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.app.AndroidFragment
import io.github.muntashirakon.AppManager.fm.FmProvider
import io.github.muntashirakon.AppManager.intercept.IntentCompat
import io.github.muntashirakon.AppManager.utils.UIUtils
import io.github.muntashirakon.io.Paths
import io.github.muntashirakon.util.UiUtils
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.PublishSearchResultEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.LineSeparator
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher.SearchOptions
import io.github.rosemoe.sora.widget.SymbolInputView
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import java.util.regex.PatternSyntaxException

class CodeEditorFragment : AndroidFragment(), MenuProvider {
    class Options : Parcelable {
        val uri: Uri?
        val title: String?
        val subtitle: String?
        val readOnly: Boolean
        val javaSmaliToggle: Boolean
        val enableSharing: Boolean

        private constructor(uri: Uri?, title: String?, subtitle: String?, readOnly: Boolean, javaSmaliToggle: Boolean, enableSharing: Boolean) {
            this.uri = uri
            this.title = title
            this.subtitle = subtitle
            this.readOnly = readOnly
            this.javaSmaliToggle = javaSmaliToggle
            this.enableSharing = enableSharing
        }

        protected constructor(`in`: Parcel) {
            uri = ParcelCompat.readParcelable(`in`, Uri::class.java.classLoader, Uri::class.java)
            title = `in`.readString()
            subtitle = `in`.readString()
            readOnly = `in`.readByte().toInt() != 0
            javaSmaliToggle = `in`.readByte().toInt() != 0
            enableSharing = `in`.readByte().toInt() != 0
        }

        override fun writeToParcel(dest: Parcel, flags: Int) {
            dest.writeParcelable(uri, flags)
            dest.writeString(title)
            dest.writeString(subtitle)
            dest.writeByte((if (readOnly) 1 else 0).toByte())
            dest.writeByte((if (javaSmaliToggle) 1 else 0).toByte())
            dest.writeByte((if (enableSharing) 1 else 0).toByte())
        }

        override fun describeContents(): Int = 0

        class Builder {
            private var uri: Uri? = null
            private var title: String? = null
            private var subtitle: String? = null
            private var readOnly = false
            private var javaSmaliToggle = false
            private var enableSharing = true

            constructor()
            constructor(options: Options) {
                uri = options.uri
                title = options.title
                subtitle = options.subtitle
                readOnly = options.readOnly
                javaSmaliToggle = options.javaSmaliToggle
                enableSharing = options.enableSharing
            }

            fun setUri(uri: Uri?): Builder { this.uri = uri; return this }
            fun setTitle(title: String?): Builder { this.title = title; return this }
            fun setSubtitle(subtitle: String?): Builder { this.subtitle = subtitle; return this }
            fun setReadOnly(readOnly: Boolean): Builder { this.readOnly = readOnly; return this }
            fun setJavaSmaliToggle(javaSmaliToggle: Boolean): Builder { this.javaSmaliToggle = javaSmaliToggle; return this }
            fun setEnableSharing(enableSharing: Boolean): Builder { this.enableSharing = enableSharing; return this }
            fun build(): Options = Options(uri, title, subtitle, readOnly, javaSmaliToggle, enableSharing)
        }

        companion object {
            @JvmField
            val CREATOR: Parcelable.Creator<Options> = object : Parcelable.Creator<Options> {
                override fun createFromParcel(source: Parcel): Options = Options(source)
                override fun newArray(size: Int): Array<Options?> = arrayOfNulls(size)
            }
        }
    }

    private var mColorScheme: EditorColorScheme? = null
    private var mEditor: CodeEditor? = null
    private var mSymbolInputView: SymbolInputView? = null
    private var mPositionButton: TextView? = null
    private var mLockButton: MaterialButton? = null
    private var mSearchWidget: LinearLayoutCompat? = null
    private var mSearchView: TextInputEditText? = null
    private var mReplaceView: TextInputEditText? = null
    private var mReplaceViewContainer: TextInputLayout? = null
    private var mReplaceButton: MaterialButton? = null
    private var mReplaceAllButton: MaterialButton? = null
    private var mSearchResultCount: TextView? = null
    private var mOptions: Options? = null
    private var mSearchOptions = SearchOptions(false, false)
    private var mSaveMenu: MenuItem? = null
    private var mUndoMenu: MenuItem? = null
    private var mRedoMenu: MenuItem? = null
    private var mJavaSmaliToggleMenu: MenuItem? = null
    private var mShareMenu: MenuItem? = null
    private var mViewModel: CodeEditorViewModel? = null
    private var mTextModified = false

    private val mSaveOpenedFile = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        try {
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
            val data = result.data ?: return@registerForActivityResult
            val uri = IntentCompat.getDataUri(data) ?: return@registerForActivityResult
            saveFile(mEditor!!.text, uri)
            if (data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0) {
                mOptions = Options.Builder(mOptions!!).setUri(uri).setSubtitle(Paths.get(uri).name).build()
                mViewModel!!.setOptions(mOptions!!)
            }
        } finally {
            showProgressIndicator(false)
            unlockEditor()
        }
    }

    private val mExitSearchBackPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (mSearchWidget?.visibility == View.VISIBLE) hideSearchWidget()
            else { isEnabled = false; requireActivity().onBackPressedDispatcher.onBackPressed() }
        }
    }

    private val mTextModifiedBackPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (mTextModified) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.exit_confirmation)
                    .setMessage(R.string.file_modified_are_you_sure)
                    .setPositiveButton(R.string.no, null)
                    .setNegativeButton(R.string.yes) { _, _ -> isEnabled = false; requireActivity().onBackPressedDispatcher.onBackPressed() }
                    .setNeutralButton(R.string.save_and_exit) { _, _ -> saveFile(); isEnabled = false; requireActivity().onBackPressedDispatcher.onBackPressed() }
                    .show()
            } else { isEnabled = false; requireActivity().onBackPressedDispatcher.onBackPressed() }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_code_editor, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        mViewModel = ViewModelProvider(this).get(CodeEditorViewModel::class.java)
        mOptions = BundleCompat.getParcelable(requireArguments(), ARG_OPTIONS, Options::class.java)!!
        mViewModel!!.setOptions(mOptions!!)
        mColorScheme = EditorThemes.getColorScheme(requireContext())
        mEditor = view.findViewById(R.id.editor)
        mEditor!!.apply {
            colorScheme = mColorScheme
            typefaceText = Typeface.MONOSPACE
            setTextSize(14f)
            setLineSpacing(2f, 1.1f)
            subscribeEvent(ContentChangeEvent::class.java) { event, _ ->
                if (!mTextModified && event.action != ContentChangeEvent.ACTION_SET_NEW_TEXT) {
                    mTextModified = true
                    mTextModifiedBackPressedCallback.isEnabled = true
                    getActionBar().ifPresent { it.subtitle = "* ${mOptions!!.subtitle}" }
                }
                postDelayed({ updateLiveButtons() }, 50)
            }
            subscribeEvent(SelectionChangeEvent::class.java) { _, _ -> getFragmentActivity().ifPresent { updatePositionText() } }
            subscribeEvent(PublishSearchResultEvent::class.java) { _, _ -> getFragmentActivity().ifPresent { updatePositionText(); updateSearchResult() } }
            props.apply {
                useICULibToSelectWords = false
                symbolPairAutoCompletion = false
                deleteMultiSpaces = -1
                deleteEmptyLineFast = false
            }
        }
        mSymbolInputView = view.findViewById(R.id.symbol_input)
        mSymbolInputView!!.apply {
            addSymbols(arrayOf("⇥", "{", "}", "(", ")", ",", ".", ";", """, "?", "+", "-", "*", "/"), arrayOf("	", "{", "}", "(", ")", ",", ".", ";", """, "?", "+", "-", "*", "/"))
            setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface))
            background = null
            (parent as HorizontalScrollView).setBackgroundColor(SurfaceColors.SURFACE_2.getColor(requireContext()))
            bindEditor(mEditor)
            if (mOptions!!.readOnly) visibility = View.GONE
        }
        mSearchWidget = view.findViewById(R.id.search_container)
        mSearchView = view.findViewById(R.id.search_bar)
        mSearchView!!.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { if (s.isNullOrEmpty()) mEditor!!.searcher.stopSearch() else try { mEditor!!.searcher.search(s.toString(), mSearchOptions) } catch (ignore: PatternSyntaxException) {} }
        })
        view.findViewById<TextInputLayout>(R.id.search_bar_container).setEndIconOnClickListener { v ->
            val popup = PopupMenu(v.context, v)
            popup.menu.apply {
                add(R.string.search_option_match_case).setCheckable(true).setChecked(!mSearchOptions.ignoreCase).setOnMenuItemClickListener { mSearchOptions = SearchOptions(mSearchOptions.type, it.isChecked); search(mSearchView!!.text); true }
                add(R.string.search_option_regex).setCheckable(true).setChecked(mSearchOptions.type == SearchOptions.TYPE_REGULAR_EXPRESSION).setOnMenuItemClickListener { val type = if (it.isChecked) SearchOptions.TYPE_REGULAR_EXPRESSION else SearchOptions.TYPE_NORMAL; mSearchOptions = SearchOptions(type, mSearchOptions.ignoreCase); search(mSearchView!!.text); true }
                add(R.string.search_option_whole_word).setCheckable(true).setChecked(mSearchOptions.type == SearchOptions.TYPE_WHOLE_WORD).setOnMenuItemClickListener { val type = if (it.isChecked) SearchOptions.TYPE_WHOLE_WORD else SearchOptions.TYPE_NORMAL; mSearchOptions = SearchOptions(type, mSearchOptions.ignoreCase); search(mSearchView!!.text); true }
            }
            popup.show()
        }
        mSearchResultCount = view.findViewById(R.id.search_result_count)
        view.findViewById<View>(R.id.previous_button).setOnClickListener { if (mEditor!!.searcher.hasQuery()) mEditor!!.searcher.gotoPrevious() }
        view.findViewById<View>(R.id.next_button).setOnClickListener { if (mEditor!!.searcher.hasQuery()) mEditor!!.searcher.gotoNext() }
        mReplaceView = view.findViewById(R.id.replace_bar)
        mReplaceViewContainer = view.findViewById(R.id.replace_bar_container)
        mReplaceButton = view.findViewById(R.id.replace_button)
        mReplaceAllButton = view.findViewById(R.id.replace_all_button)
        mReplaceButton!!.setOnClickListener { if (mEditor!!.searcher.hasQuery()) mReplaceView!!.text?.let { mEditor!!.searcher.replaceThis(it.toString()) } }
        mReplaceAllButton!!.setOnClickListener { if (mEditor!!.searcher.hasQuery()) mReplaceView!!.text?.let { mEditor!!.searcher.replaceAll(it.toString()) } }
        mLockButton = view.findViewById(R.id.lock)
        mLockButton!!.setOnClickListener { if (mEditor!!.isEditable) lockEditor() else unlockEditor() }
        val languageButton: TextView = view.findViewById(R.id.language)
        val indentSizeButton: TextView = view.findViewById(R.id.tab_size)
        val lineSeparatorButton: TextView = view.findViewById(R.id.line_separator)
        lineSeparatorButton.setOnClickListener { v ->
            val popup = PopupMenu(requireContext(), v)
            popup.menu.apply {
                add(R.string.line_separator).setEnabled(false)
                if (mEditor!!.lineSeparator != LineSeparator.CRLF) add("CRLF - Windows (
)").setOnMenuItemClickListener { mEditor!!.lineSeparator = LineSeparator.CRLF; lineSeparatorButton.text = mEditor!!.lineSeparator.name; true }
                if (mEditor!!.lineSeparator != LineSeparator.CR) add("CR - Classic Mac OS ()").setOnMenuItemClickListener { mEditor!!.lineSeparator = LineSeparator.CR; lineSeparatorButton.text = mEditor!!.lineSeparator.name; true }
                if (mEditor!!.lineSeparator != LineSeparator.LF) add("LF - Unix & Mac OS (
)").setOnMenuItemClickListener { mEditor!!.lineSeparator = LineSeparator.LF; lineSeparatorButton.text = mEditor!!.lineSeparator.name; true }
            }
            popup.show()
        }
        mPositionButton = view.findViewById(R.id.position)
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
        updateLiveButtons()
        updateStartupMenu()
        UiUtils.applyWindowInsetsAsPaddingNoTop(view.findViewById(R.id.editor_container))

        mViewModel!!.contentLiveData.observe(viewLifecycleOwner) { content ->
            showProgressIndicator(false)
            if (content == null) { UIUtils.displayLongToast(R.string.failed); return@observe }
            mEditor!!.setEditorLanguage(getLanguage(mViewModel!!.language))
            if (mViewModel!!.isReadOnly) {
                mLockButton!!.setIconResource(R.drawable.ic_lock); mLockButton!!.isEnabled = false; mEditor!!.isEditable = false
            } else mLockButton!!.isEnabled = true
            languageButton.text = mViewModel!!.language; languageButton.isEnabled = !mViewModel!!.isReadOnly
            indentSizeButton.isEnabled = !mViewModel!!.isReadOnly
            indentSizeButton.text = "${mEditor!!.tabWidth} ${if (mEditor!!.editorLanguage.useTab()) "tabs" else "spaces"}"
            lineSeparatorButton.isEnabled = !mViewModel!!.isReadOnly
            mEditor!!.setText(content)
            lineSeparatorButton.text = mEditor!!.lineSeparator.name
            updatePositionText()
        }
        mViewModel!!.saveFileLiveData.observe(viewLifecycleOwner) {
            if (it) {
                UIUtils.displayShortToast(R.string.saved_successfully)
                mTextModified = false; mTextModifiedBackPressedCallback.isEnabled = false
                getActionBar().ifPresent { ab -> ab.setSubtitle(mOptions!!.subtitle) }
            } else UIUtils.displayLongToast(R.string.saving_failed)
        }
        mViewModel!!.javaFileLiveData.observe(viewLifecycleOwner) { uri ->
            val opts = Options.Builder()
                .setUri(uri).setTitle(mOptions!!.title).setSubtitle(mOptions!!.subtitle)
                .setEnableSharing(true).setJavaSmaliToggle(false).setReadOnly(true).build()
            val fragment = CodeEditorFragment().apply { arguments = Bundle().apply { putParcelable(ARG_OPTIONS, opts) } }
            getFragmentActivity().ifPresent { it.supportFragmentManager.beginTransaction().replace((requireView().parent as ViewGroup).id, fragment).addToBackStack(null).commit() }
        }
        mViewModel!!.loadFileContentIfAvailable()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        requireActivity().onBackPressedDispatcher.addCallback(this, mTextModifiedBackPressedCallback)
        requireActivity().onBackPressedDispatcher.addCallback(this, mExitSearchBackPressedCallback)
    }

    override fun onResume() {
        super.onResume()
        getActionBar().ifPresent { ab ->
            ab.title = mOptions!!.title
            ab.setSubtitle((if (mTextModified) "* " else "") + mOptions!!.subtitle)
        }
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.activity_code_editor_actions, menu)
        mSaveMenu = menu.findItem(R.id.action_save)
        mUndoMenu = menu.findItem(R.id.action_undo)
        mRedoMenu = menu.findItem(R.id.action_redo)
        mJavaSmaliToggleMenu = menu.findItem(R.id.action_java_smali_toggle)
        mShareMenu = menu.findItem(R.id.action_share)
        updateStartupMenu()
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_undo -> { if (mEditor?.canUndo() == true) mEditor!!.undo(); true }
            R.id.action_redo -> { if (mEditor?.canRedo() == true) mEditor!!.redo(); true }
            R.id.action_wrap -> { mEditor?.let { it.isWordwrap = !it.isWordwrap }; true }
            R.id.action_save -> { saveFile(); true }
            R.id.action_save_as -> { launchIntentSaver(); true }
            R.id.action_share -> {
                mViewModel!!.sourceFile?.let { path ->
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = path.type; putExtra(Intent.EXTRA_STREAM, FmProvider.getContentUri(path))
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(Intent.createChooser(intent, getString(R.string.share)))
                }
                true
            }
            R.id.action_java_smali_toggle -> { mViewModel!!.generateJava(mEditor!!.text); true }
            R.id.action_search -> { mSearchWidget?.let { if (it.visibility == View.VISIBLE) hideSearchWidget() else showSearchWidget() }; true }
            else -> false
        }
    }

    private fun showProgressIndicator(show: Boolean) {
        requireActivity().findViewById<LinearProgressIndicator>(R.id.progress_linear)?.let { if (show) it.show() else it.hide() }
    }

    private fun updateLiveButtons() {
        val ro = mViewModel?.isReadOnly == true
        mSaveMenu?.isEnabled = mTextModified && !ro
        mUndoMenu?.isEnabled = mEditor?.canUndo() == true && !ro
        mRedoMenu?.isEnabled = mEditor?.canRedo() == true && !ro
        mReplaceViewContainer?.visibility = if (ro) View.GONE else View.VISIBLE
        mReplaceButton?.visibility = if (ro) View.GONE else View.VISIBLE
        mReplaceAllButton?.visibility = if (ro) View.GONE else View.VISIBLE
    }

    private fun updateStartupMenu() {
        mViewModel?.let { vm ->
            mJavaSmaliToggleMenu?.apply { isVisible = vm.canGenerateJava(); isEnabled = vm.canGenerateJava() }
            mShareMenu?.isEnabled = vm.isBackedByAFile
        }
    }

    @MainThread
    private fun updatePositionText() {
        val cursor = mEditor!!.cursor
        val sb = StringBuilder("${1 + cursor.leftLine}:${cursor.leftColumn}")
        if (cursor.isSelected) sb.append(" (${cursor.right - cursor.left} chars)")
        mPositionButton!!.text = sb
    }

    @MainThread
    private fun updateSearchResult() {
        val count = if (mEditor!!.searcher.hasQuery()) mEditor!!.searcher.matchedPositionCount else 0
        mSearchResultCount!!.text = resources.getQuantityString(R.plurals.search_results, count, count)
    }

    private fun saveFile() {
        if (!mViewModel!!.isBackedByAFile) launchIntentSaver()
        else if (mViewModel!!.canWrite()) saveFile(mEditor!!.text, null)
        else {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.read_only_file)
                .setMessage(R.string.read_only_file_warning)
                .setPositiveButton(R.string.yes) { _, _ -> launchIntentSaver() }
                .setNegativeButton(R.string.no, null)
                .show()
        }
    }

    private fun saveFile(content: Content, uri: Uri?) { mViewModel!!.saveFile(content, uri?.let { Paths.get(it) }) }

    private fun getLanguage(language: String?): Language {
        if (language == null || mColorScheme !is TextMateColorScheme) return EmptyLanguage()
        return Languages.getLanguage(requireContext(), language, (mColorScheme as TextMateColorScheme).themeSource)
    }

    fun showSearchWidget() {
        mSearchWidget?.let {
            mExitSearchBackPressedCallback.isEnabled = true
            TransitionManager.beginDelayedTransition(it, MaterialSharedAxis(MaterialSharedAxis.Y, true))
            it.visibility = View.VISIBLE
            mSearchView!!.requestFocus()
        }
    }

    fun hideSearchWidget() {
        mSearchWidget?.let {
            TransitionManager.beginDelayedTransition(it, MaterialSharedAxis(MaterialSharedAxis.Y, false))
            it.visibility = View.GONE
            mEditor!!.searcher.stopSearch()
            mExitSearchBackPressedCallback.isEnabled = false
        }
    }

    private fun search(s: CharSequence?) {
        if (s.isNullOrEmpty()) mEditor!!.searcher.stopSearch()
        else try { mEditor!!.searcher.search(s.toString(), mSearchOptions) } catch (ignore: PatternSyntaxException) {}
    }

    private fun lockEditor() {
        if (mViewModel?.isReadOnly == true) return
        if (mEditor!!.isEditable) {
            mEditor!!.isEditable = false; mSymbolInputView!!.visibility = View.GONE
            mLockButton!!.setIconResource(R.drawable.ic_lock)
        }
    }

    private fun unlockEditor() {
        if (mViewModel?.isReadOnly == true) return
        if (!mEditor!!.isEditable) {
            mEditor!!.isEditable = true; mSymbolInputView!!.visibility = View.VISIBLE
            mLockButton!!.setIconResource(R.drawable.ic_unlock)
        }
    }

    private fun launchIntentSaver() { showProgressIndicator(true); lockEditor(); mSaveOpenedFile.launch(getSaveIntent()) }

    private fun getSaveIntent(): Intent = Intent(Intent.ACTION_CREATE_DOCUMENT).setType("*/*").putExtra(Intent.EXTRA_TITLE, mViewModel!!.filename)

    companion object {
        const val ARG_OPTIONS = "options"
    }
}
