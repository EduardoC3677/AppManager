// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.intercept

import android.content.ComponentName
import android.content.Intent
import android.content.pm.*
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.os.*
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.*
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBar
import androidx.collection.LruCache
import androidx.collection.SimpleArrayMap
import androidx.collection.SparseArrayCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import io.github.muntashirakon.AppManager.BaseActivity
import io.github.muntashirakon.AppManager.BuildConfig
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.compat.ActivityManagerCompat
import io.github.muntashirakon.AppManager.compat.IntegerCompat
import io.github.muntashirakon.AppManager.compat.ManifestCompat
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat
import io.github.muntashirakon.AppManager.compat.UserHandleHidden
import io.github.muntashirakon.AppManager.crypto.auth.AuthManager
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.runner.RunnerUtils
import io.github.muntashirakon.AppManager.self.SelfPermissions
import io.github.muntashirakon.AppManager.self.SelfUriManager
import io.github.muntashirakon.AppManager.self.imagecache.ImageLoader
import io.github.muntashirakon.AppManager.settings.Ops
import io.github.muntashirakon.AppManager.shortcut.CreateShortcutDialogFragment
import io.github.muntashirakon.AppManager.utils.*
import io.github.muntashirakon.AppManager.utils.appearance.ColorCodes
import io.github.muntashirakon.dialog.TextInputDropdownDialogBuilder
import io.github.muntashirakon.util.AccessibilityUtils
import io.github.muntashirakon.util.AdapterUtils
import io.github.muntashirakon.util.UiUtils
import io.github.muntashirakon.widget.MaterialAutoCompleteTextView
import java.util.*

class ActivityInterceptor : BaseActivity() {
    private lateinit var mActionView: MaterialAutoCompleteTextView
    private lateinit var mDataView: MaterialAutoCompleteTextView
    private lateinit var mTypeView: MaterialAutoCompleteTextView
    private lateinit var mUriView: MaterialAutoCompleteTextView
    private lateinit var mPackageNameView: MaterialAutoCompleteTextView
    private lateinit var mClassNameView: MaterialAutoCompleteTextView
    private lateinit var mIdView: TextInputEditText
    private lateinit var mUserIdEdit: TextInputEditText

    private var mHistory: HistoryEditText? = null
    private var mCategoriesAdapter: CategoriesRecyclerViewAdapter? = null
    private var mFlagsAdapter: FlagsRecyclerViewAdapter? = null
    private var mExtrasAdapter: ExtrasRecyclerViewAdapter? = null
    private var mMatchingActivitiesAdapter: MatchingActivitiesRecyclerViewAdapter? = null
    private var mActivitiesHeader: TextView? = null
    private var mResendIntentButton: Button? = null
    private var mResetIntentButton: Button? = null

    private var mOriginalIntent: String? = null
    private var mAdditionalExtras: Bundle? = null
    private var mMutableIntent: Intent? = null
    private var mRequestedComponent: ComponentName? = null

    private var mUseRoot: Boolean = false
    private var mUserHandle: Int = UserHandleHidden.myUserId()

    private var mLastResultCode: Int? = null
    private var mLastResultIntent: Intent? = null

    private val mPackageLabelMap = LruCache<String, CharSequence>(16)
    @Volatile private var mAreTextWatchersActive: Boolean = false

    private val mIntentLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        mLastResultCode = result.resultCode
        mLastResultIntent = result.data
        setResult(result.resultCode, result.data)
        refreshUI()
        UIUtils.displayLongToast("${getString(R.string.activity_result)}: (${result.data?.data})")
    }

    private val mOnBackPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (mMutableIntent != null && isModified()) {
                MaterialAlertDialogBuilder(this@ActivityInterceptor)
                    .setTitle(R.string.exit_confirmation)
                    .setMessage(R.string.file_modified_are_you_sure)
                    .setCancelable(false)
                    .setPositiveButton(R.string.no, null)
                    .setNegativeButton(R.string.yes) { _, _ -> isEnabled = false; onBackPressedDispatcher.onBackPressed() }
                    .setNeutralButton(R.string.save_and_exit) { _, _ -> writeAndExit(); isEnabled = false }
                    .show()
                return
            }
            isEnabled = false
            onBackPressedDispatcher.onBackPressed()
        }
    }

    override fun getTransparentBackground(): Boolean = true

    override fun onAuthenticated(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_interceptor)
        setSupportActionBar(findViewById(R.id.toolbar))
        onBackPressedDispatcher.addCallback(this, mOnBackPressedCallback)
        findViewById<View>(R.id.progress_linear).visibility = View.GONE
        val intent = Intent(intent)
        mUseRoot = Ops.isWorkingUidRoot() && intent.getBooleanExtra(EXTRA_ROOT, false)
        mUserHandle = intent.getIntExtra(EXTRA_USER_HANDLE, UserHandleHidden.myUserId())
        intent.removeExtra(EXTRA_ROOT); intent.removeExtra(EXTRA_USER_HANDLE)
        intent.`package` = null; intent.component = null
        intent.getStringExtra(EXTRA_PACKAGE_NAME)?.let { pkg ->
            intent.`package` = pkg; updateTitle(pkg)
            intent.getStringExtra(EXTRA_CLASS_NAME)?.let { cls ->
                mRequestedComponent = ComponentName(pkg, cls)
                intent.component = mRequestedComponent
                updateSubtitle(mRequestedComponent)
            }
        }
        intent.getStringExtra(EXTRA_ACTION)?.let { intent.action = it }
        if (intent.getBooleanExtra(EXTRA_TRIGGER_ON_START, false) && AuthManager.getKey() == intent.getStringExtra(EXTRA_AUTH)) {
            intent.removeExtra(EXTRA_TRIGGER_ON_START); intent.removeExtra(EXTRA_AUTH)
            launchIntent(intent, mRequestedComponent == null)
        }
        val isEdited = savedInstanceState?.getBoolean(INTENT_EDITED) ?: false
        init(intent, isEdited)
    }

    private fun init(intent: Intent, isEdited: Boolean) {
        storeOriginalIntent(intent)
        showInitialIntent(isEdited)
        if (mRequestedComponent == null) mHistory?.saveHistory()
    }

    private fun storeOriginalIntent(intent: Intent) {
        mOriginalIntent = getUri(intent)
        val copy = cloneIntent(mOriginalIntent)
        intent.extras?.let { orig ->
            val add = Bundle(orig)
            copy?.extras?.keySet()?.forEach { add.remove(it) }
            if (!add.isEmpty) mAdditionalExtras = add
        }
    }

    private fun showInitialIntent(isEdited: Boolean) {
        mMutableIntent = cloneIntent(mOriginalIntent)
        setupVariables()
        setupTextWatchers()
        showAllIntentData(null)
        showResetIntentButton(isEdited)
    }

    private fun showAllIntentData(ignore: TextView?) {
        showTextViewIntentData(ignore)
        mCategoriesAdapter?.setDefaultList(mMutableIntent?.categories)
        mFlagsAdapter?.setDefaultList(getFlags())
        mExtrasAdapter?.setDefaultList(getExtras())
        refreshUI()
    }

    private fun updateTitle(pkg: String?) {
        supportActionBar?.let { ab ->
            if (pkg != null) {
                mPackageLabelMap[pkg]?.let { ab.title = it } ?: ThreadUtils.postOnBackgroundThread {
                    val label = PackageUtils.getPackageLabel(packageManager, pkg, mUserHandle)
                    ThreadUtils.postOnMainThread { if (pkg == label.toString()) ab.setTitle(R.string.interceptor) else { ab.title = label; mPackageLabelMap.put(pkg, label) } }
                }
            } else ab.setTitle(R.string.interceptor)
        }
    }

    private fun updateSubtitle(cn: ComponentName?) {
        supportActionBar?.let { ab ->
            if (cn == null) { ab.subtitle = null; return }
            try { ab.subtitle = packageManager.getActivityInfo(cn, 0).loadLabel(packageManager) }
            catch (e: Exception) { ab.subtitle = cn.className }
        }
    }

    private fun getExtras(): SimpleArrayMap<String, Any> {
        val res = SimpleArrayMap<String, Any>()
        mMutableIntent?.extras?.let { b -> b.keySet().forEach { k -> b.get(it)?.let { v -> res.put(k, v) } } }
        return res
    }

    private fun showTextViewIntentData(ignore: TextView?) {
        val m = mMutableIntent ?: return
        mAreTextWatchersActive = false
        try {
            if (ignore != mActionView) mActionView.setText(m.action)
            if (ignore != mDataView) mDataView.setText(m.dataString)
            if (ignore != mTypeView) mTypeView.setText(m.type)
            if (ignore != mPackageNameView) mPackageNameView.setText(m.`package`)
            if (ignore != mClassNameView) mClassNameView.setText(m.component?.className)
            if (ignore != mUriView) mUriView.setText(getUri(m))
            if (ignore != mIdView && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) mIdView.setText(m.identifier)
        } finally { mAreTextWatchersActive = true }
    }

    private fun getFlags(): List<String> {
        val f = mMutableIntent?.flags ?: return emptyList()
        val res = mutableListOf<String>()
        for (i in 0 until INTENT_FLAG_TO_STRING.size()) {
            if (f and INTENT_FLAG_TO_STRING.keyAt(i) != 0) res.add(INTENT_FLAG_TO_STRING.valueAt(i))
        }
        return res
    }

    private fun checkAndShowMatchingActivities() {
        val m = mMutableIntent ?: return
        val list = getMatchingActivities()
        mResendIntentButton?.isEnabled = list.isNotEmpty()
        mActivitiesHeader?.visibility = if (list.isNotEmpty()) View.VISIBLE else View.GONE
        mActivitiesHeader?.setText(R.string.matching_activities)
        mMatchingActivitiesAdapter?.setDefaultList(list)
    }

    private fun getMatchingActivities(): List<ResolveInfo> {
        val m = mMutableIntent ?: return emptyList()
        return if (mUseRoot || SelfPermissions.checkCrossUserPermission(mUserHandle, false)) {
            try { PackageManagerCompat.queryIntentActivities(this, m, PackageManager.MATCH_ALL, mUserHandle) }
            catch (e: RemoteException) { emptyList() }
        } else packageManager.queryIntentActivities(m, 0)
    }

    private fun setupVariables() {
        mActionView = findViewById(R.id.action_edit)
        mDataView = findViewById(R.id.data_edit)
        mTypeView = findViewById(R.id.type_edit)
        mUriView = findViewById(R.id.uri_edit)
        mPackageNameView = findViewById(R.id.package_edit)
        mClassNameView = findViewById(R.id.class_edit)
        mIdView = findViewById(R.id.type_id)
        mHistory = HistoryEditText(this, mActionView, mDataView, mTypeView, mUriView, mPackageNameView, mClassNameView)
        mUserIdEdit = findViewById<TextInputEditText>(R.id.user_id_edit).apply {
            setText(mUserHandle.toString())
            isEnabled = SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.INTERACT_ACROSS_USERS) || SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.INTERACT_ACROSS_USERS_FULL)
        }
        findViewById<MaterialCheckBox>(R.id.use_root).apply {
            isChecked = mUseRoot
            visibility = if (Ops.isWorkingUidRoot()) View.VISIBLE else View.GONE
            setOnCheckedChangeListener { _, isChecked -> if (mUseRoot != isChecked) { mUseRoot = isChecked; refreshUI() } }
        }
        findViewById<TextInputLayout>(R.id.type_id_layout).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) setEndIconOnClickListener { mIdView.setText(UUID.randomUUID().toString()); mIdView.requestFocus() }
            else visibility = View.GONE
        }
        findViewById<MaterialButton>(R.id.intent_categories_add_btn).setOnClickListener { b ->
            UiUtils.fixFocus(b)
            TextInputDropdownDialogBuilder(this, R.string.category).setTitle(R.string.category).setDropdownItems(INTENT_CATEGORIES, -1, true).setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.ok) { _, _, text, _ ->
                if (!text.isNullOrEmpty()) { mMutableIntent?.addCategory(text.toString().trim()); mCategoriesAdapter?.setDefaultList(mMutableIntent?.categories); showTextViewIntentData(null); showResetIntentButton(true) }
            }.show()
        }
        mCategoriesAdapter = CategoriesRecyclerViewAdapter(this)
        findViewById<io.github.muntashirakon.widget.RecyclerView>(R.id.intent_categories).apply { layoutManager = UIUtils.getGridLayoutAt450Dp(this@ActivityInterceptor); adapter = mCategoriesAdapter }
        findViewById<MaterialButton>(R.id.intent_flags_add_btn).setOnClickListener { b ->
            UiUtils.fixFocus(b)
            TextInputDropdownDialogBuilder(this, R.string.flags).setTitle(R.string.flags).setDropdownItems(getAllFlags(), -1, true).setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.ok) { _, _, text, _ ->
                if (!text.isNullOrEmpty() && mMutableIntent != null) {
                    val trim = text.toString().trim()
                    val idx = getFlagIndex(trim)
                    if (idx >= 0) mMutableIntent!!.addFlags(INTENT_FLAG_TO_STRING.keyAt(idx))
                    else try { mMutableIntent!!.addFlags(IntegerCompat.decode(trim)) } catch (e: Exception) { return@setPositiveButton }
                    mFlagsAdapter!!.setDefaultList(getFlags()); showTextViewIntentData(null); showResetIntentButton(true)
                }
            }.show()
        }
        mFlagsAdapter = FlagsRecyclerViewAdapter(this)
        findViewById<io.github.muntashirakon.widget.RecyclerView>(R.id.intent_flags).apply { layoutManager = UIUtils.getGridLayoutAt450Dp(this@ActivityInterceptor); adapter = mFlagsAdapter }
        findViewById<MaterialButton>(R.id.intent_extras_add_btn).setOnClickListener { b ->
            UiUtils.fixFocus(b)
            AddIntentExtraFragment().apply {
                setOnSaveListener(object : AddIntentExtraFragment.OnSaveListener {
                    override fun onSave(mode: Int, item: AddIntentExtraFragment.ExtraItem) { mMutableIntent?.let { IntentCompat.addToIntent(it, item); mExtrasAdapter?.setDefaultList(getExtras()); showResetIntentButton(true) } }
                })
                arguments = Bundle().apply { putInt(AddIntentExtraFragment.ARG_MODE, AddIntentExtraFragment.MODE_CREATE) }
                show(supportFragmentManager, AddIntentExtraFragment.TAG)
            }
        }
        mExtrasAdapter = ExtrasRecyclerViewAdapter(this)
        findViewById<io.github.muntashirakon.widget.RecyclerView>(R.id.intent_extras).apply { layoutManager = UIUtils.getGridLayoutAt450Dp(this@ActivityInterceptor); adapter = mExtrasAdapter }
        mActivitiesHeader = findViewById(R.id.intent_matching_activities_header)
        if (mRequestedComponent != null) mActivitiesHeader?.visibility = View.GONE
        mMatchingActivitiesAdapter = MatchingActivitiesRecyclerViewAdapter(this)
        findViewById<io.github.muntashirakon.widget.RecyclerView>(R.id.intent_matching_activities).apply { layoutManager = UIUtils.getGridLayoutAt450Dp(this@ActivityInterceptor); adapter = mMatchingActivitiesAdapter }
        mResendIntentButton = findViewById<Button>(R.id.resend_intent_button).apply { setOnClickListener { UiUtils.fixFocus(it); mMutableIntent?.let { i -> launchIntent(i, mRequestedComponent == null) } } }
        mResetIntentButton = findViewById<Button>(R.id.reset_intent_button).apply { setOnClickListener { UiUtils.fixFocus(it); mAreTextWatchersActive = false; showInitialIntent(false); mAreTextWatchersActive = true; refreshUI() } }
    }

    private fun setupTextWatchers() {
        mActionView.addTextChangedListener(object : IntentUpdateTextWatcher(mActionView) { override fun onUpdateIntent(mod: String) { mMutableIntent?.action = mod } })
        mDataView.addTextChangedListener(object : IntentUpdateTextWatcher(mDataView) { override fun onUpdateIntent(mod: String) { mMutableIntent?.let { val t = it.type; it.setDataAndType(Uri.parse(mod), t) } } })
        mTypeView.addTextChangedListener(object : IntentUpdateTextWatcher(mTypeView) { override fun onUpdateIntent(mod: String) { mMutableIntent?.let { val d = it.dataString; it.setDataAndType(Uri.parse(d), mod) } } })
        mPackageNameView.addTextChangedListener(object : IntentUpdateTextWatcher(mPackageNameView) { override fun onUpdateIntent(mod: String) { mMutableIntent?.`package` = if (mod.isEmpty()) null else mod } })
        mClassNameView.addTextChangedListener(object : IntentUpdateTextWatcher(mClassNameView) {
            override fun onUpdateIntent(mod: String) {
                val m = mMutableIntent ?: return
                if (mod.isEmpty()) { mRequestedComponent = null; m.component = null; return }
                val p = m.`package` ?: run { UIUtils.displayShortToast(R.string.set_package_name_first); mAreTextWatchersActive = false; mClassNameView.text = null; mAreTextWatchersActive = true; return }
                mRequestedComponent = ComponentName(p, (if (mod.startsWith(".")) p else "") + mod)
                m.component = mRequestedComponent
            }
        })
        mUriView.addTextChangedListener(object : IntentUpdateTextWatcher(mUriView) { override fun onUpdateIntent(mod: String) { mMutableIntent = cloneIntent(mod); showAllIntentData(mUriView) } })
        mIdView.addTextChangedListener(object : IntentUpdateTextWatcher(mIdView) { override fun onUpdateIntent(mod: String) { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) mMutableIntent?.identifier = mod } })
        mUserIdEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { try { mUserHandle = Integer.decode(s.toString()); refreshUI() } catch (ignore: Exception) {} }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun showResetIntentButton(visible: Boolean) {
        mResendIntentButton?.setText(R.string.send_edited_intent)
        mResetIntentButton?.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun isModified(): Boolean = mResetIntentButton?.visibility == View.VISIBLE

    private fun writeAndExit() {
        // Dummy implementation for OnBackPressedCallback requirement
    }

    private fun copyIntentDetails() { Utils.copyToClipboard(this, "Intent Details", getIntentDetailsString()) }
    private fun copyIntentAsCommand() { mMutableIntent?.let { val args = IntentCompat.flattenToCommand(it); Utils.copyToClipboard(this, "am command", "${RunnerUtils.CMD_AM} start --user $mUserHandle ${args.joinToString(" ")}") } }
    private fun pasteIntentDetails() {
        val clip = ClipboardUtils.readClipboard(this) ?: return
        val text = clip.toString()
        val lines = text.split("
")
        mUseRoot = false; mUserHandle = UserHandleHidden.myUserId()
        var count = 0
        for (line in lines) {
            if (line.isEmpty()) continue
            val tok = StringTokenizer(line, "	")
            when (tok.nextToken()) {
                "ROOT" -> { mUseRoot = Ops.isWorkingUidRoot() && tok.nextToken().toBoolean(); count++ }
                "USER" -> { val u = Integer.decode(tok.nextToken()); if (SelfPermissions.checkCrossUserPermission(u, false)) mUserHandle = u; count++ }
            }
            if (count == 2) break
        }
        IntentCompat.unflattenFromString(text)?.let { mRequestedComponent = null; init(it, false) }
    }

    private fun refreshUI() {
        val m = mMutableIntent ?: return
        if (mRequestedComponent == null) checkAndShowMatchingActivities()
        else { mActivitiesHeader?.visibility = View.GONE; mResendIntentButton?.isEnabled = true }
        updateTitle(m.`package`)
        updateSubtitle(m.component)
    }

    private fun getIntentDetailsString(): String {
        val m = mMutableIntent ?: return ""
        val list = getMatchingActivities()
        val count = list.size
        val res = StringBuilder()
        res.append("URI	${getUri(m)}
")
        if (mUseRoot) res.append("ROOT	$mUseRoot
")
        if (mUserHandle != UserHandleHidden.myUserId()) res.append("USER	$mUserHandle
")
        res.append("
${IntentCompat.flattenToString(m)}
MATCHING ACTIVITIES	$count
")
        val spaces = " ".repeat(count.toString().length)
        list.forEachIndexed { i, ri ->
            val ai = ri.activityInfo
            res.append("$i	LABEL  	${ai.loadLabel(packageManager)}
")
            res.append("$spaces	NAME   	${ai.name}
")
            res.append("$spaces	PACKAGE	${ai.packageName}
")
        }
        mLastResultCode?.let {
            res.append("
ACTIVITY RESULT	$it
")
            mLastResultIntent?.let { li -> res.append(IntentCompat.describeIntent(li, "RESULT")) }
        }
        return res.toString()
    }

    fun launchIntent(intent: Intent, createChooser: Boolean) {
        val needPriv = mUseRoot || mUserHandle != UserHandleHidden.myUserId()
        try {
            if (createChooser) {
                val chooser = Intent.createChooser(intent, mResendIntentButton?.text ?: getString(R.string.open))
                if (needPriv) ActivityManagerCompat.startActivity(chooser, mUserHandle) else mIntentLauncher.launch(chooser)
            } else {
                if (needPriv) ActivityManagerCompat.startActivity(intent, mUserHandle)
                else try { intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); mIntentLauncher.launch(intent) }
                catch (e: SecurityException) { ActivityManagerCompat.startActivity(intent, mUserHandle) }
            }
        } catch (th: Throwable) {
            Log.e(TAG, th); UIUtils.displayLongToast(R.string.error_with_details, "${th.javaClass.name}: ${th.message}")
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_activity_interceptor_actions, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
            R.id.action_copy_as_default -> { copyIntentDetails(); true }
            R.id.action_copy_as_command -> { copyIntentAsCommand(); true }
            R.id.action_paste -> { pasteIntentDetails(); true }
            R.id.action_shortcut -> {
                try {
                    val name = supportActionBar?.subtitle ?: title ?: ""
                    val icon = ContextCompat.getDrawable(this, R.drawable.ic_launcher_foreground)!!
                    val i = Intent(mMutableIntent).apply {
                        putExtra(EXTRA_AUTH, AuthManager.getKey())
                        putExtra(EXTRA_TRIGGER_ON_START, true)
                        putExtra(EXTRA_ACTION, action)
                        if (mUseRoot) putExtra(EXTRA_ROOT, true)
                        if (mUserHandle != UserHandleHidden.myUserId()) putExtra(EXTRA_USER_HANDLE, mUserHandle)
                        mRequestedComponent?.let { putExtra(EXTRA_PACKAGE_NAME, it.packageName); putExtra(EXTRA_CLASS_NAME, it.className) }
                        setClass(applicationContext, ActivityInterceptor::class.java)
                    }
                    val si = InterceptorShortcutInfo(i).apply { this.name = name; this.icon = UIUtils.getBitmapFromDrawable(icon) }
                    CreateShortcutDialogFragment.getInstance(si).show(supportFragmentManager, CreateShortcutDialogFragment.TAG)
                } catch (th: Throwable) {
                    Log.e(TAG, th); UIUtils.displayLongToast(R.string.error_with_details, "${th.javaClass.name}: ${th.message}")
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onPause() { super.onPause(); mAreTextWatchersActive = false }
    override fun onResume() { super.onResume(); overridePendingTransition(0, 0); mAreTextWatchersActive = true }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mResetIntentButton?.let { outState.putBoolean(INTENT_EDITED, it.visibility == View.VISIBLE) }
        mHistory?.saveHistory()
    }

    private fun getUri(src: Intent?): String? = try { src?.let { IntentCompat.toUri(it, Intent.URI_INTENT_SCHEME) } } catch (e: Exception) { e.printStackTrace(); null }

    private fun cloneIntent(uri: String?): Intent? {
        if (uri == null) return null
        return try {
            val res = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) Intent.parseUri(uri, Intent.URI_INTENT_SCHEME or Intent.URI_ANDROID_APP_SCHEME or Intent.URI_ALLOW_UNSAFE) else Intent.parseUri(uri, Intent.URI_INTENT_SCHEME)
            mAdditionalExtras?.let { res.putExtras(it) }
            res
        } catch (e: Exception) { null }
    }

    private fun getAllFlags(): List<String> = (0 until INTENT_FLAG_TO_STRING.size()).map { INTENT_FLAG_TO_STRING.valueAt(it) }
    private fun getFlagIndex(s: String): Int = (0 until INTENT_FLAG_TO_STRING.size()).find { INTENT_FLAG_TO_STRING.valueAt(it) == s } ?: -1

    private abstract inner class IntentUpdateTextWatcher(private val mTextView: TextView) : TextWatcher {
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            if (mAreTextWatchersActive) {
                try {
                    onUpdateIntent(mTextView.text.toString())
                    showTextViewIntentData(mTextView)
                    showResetIntentButton(true)
                    refreshUI()
                } catch (e: Exception) { UIUtils.displayShortToast(e.message) }
            }
        }
        abstract fun onUpdateIntent(mod: String)
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun afterTextChanged(s: Editable?) {}
    }

    private class CategoriesRecyclerViewAdapter(private val mActivity: ActivityInterceptor) : RecyclerView.Adapter<CategoriesRecyclerViewAdapter.ViewHolder>() {
        private val mCategories = mutableListOf<String>()
        fun setDefaultList(list: Collection<String>?) { AdapterUtils.notifyDataSetChanged(this, mCategories, list?.toList()) }
        override fun onCreateViewHolder(p: ViewGroup, t: Int) = ViewHolder(LayoutInflater.from(p.context).inflate(R.layout.item_title_action, p, false))
        override fun onBindViewHolder(h: ViewHolder, p: Int) {
            val cat = mCategories[p]
            h.title.text = cat; h.title.setTextIsSelectable(true)
            h.actionIcon.setOnClickListener { UiUtils.fixFocus(it); mActivity.mMutableIntent?.let { i -> i.removeCategory(cat); setDefaultList(i.categories); mActivity.showTextViewIntentData(null); mActivity.showResetIntentButton(true) } }
        }
        override fun getItemCount(): Int = mCategories.size
        class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val title: TextView = v.findViewById(R.id.item_title)
            val actionIcon: MaterialButton = v.findViewById(R.id.item_action).apply { contentDescription = v.context.getString(R.string.item_remove) }
        }
    }

    private class FlagsRecyclerViewAdapter(private val mActivity: ActivityInterceptor) : RecyclerView.Adapter<FlagsRecyclerViewAdapter.ViewHolder>() {
        private val mFlags = mutableListOf<String>()
        fun setDefaultList(list: List<String>?) { AdapterUtils.notifyDataSetChanged(this, mFlags, list) }
        override fun onCreateViewHolder(p: ViewGroup, t: Int) = ViewHolder(LayoutInflater.from(p.context).inflate(R.layout.item_title_action, p, false))
        override fun onBindViewHolder(h: ViewHolder, p: Int) {
            val flag = mFlags[p]
            h.title.text = flag; h.title.setTextIsSelectable(true)
            h.actionIcon.setOnClickListener { UiUtils.fixFocus(it); val i = INTENT_FLAG_TO_STRING.indexOfValue(flag); if (i >= 0) { mActivity.mMutableIntent?.let { im -> IntentCompat.removeFlags(im, INTENT_FLAG_TO_STRING.keyAt(i)); setDefaultList(mActivity.getFlags()); mActivity.showTextViewIntentData(null); mActivity.showResetIntentButton(true) } } }
        }
        override fun getItemCount(): Int = mFlags.size
        class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val title: TextView = v.findViewById(R.id.item_title)
            val actionIcon: MaterialButton = v.findViewById(R.id.item_action).apply { contentDescription = v.context.getString(R.string.item_remove) }
        }
    }

    private class ExtrasRecyclerViewAdapter(private val mActivity: ActivityInterceptor) : RecyclerView.Adapter<ExtrasRecyclerViewAdapter.ViewHolder>() {
        private val mExtras = SimpleArrayMap<String, Any>(0)
        fun setDefaultList(map: SimpleArrayMap<String, Any>?) { AdapterUtils.notifyDataSetChanged(this, mExtras, map) }
        override fun onCreateViewHolder(p: ViewGroup, t: Int) = ViewHolder(LayoutInflater.from(p.context).inflate(R.layout.item_icon_title_subtitle, p, false))
        override fun onBindViewHolder(h: ViewHolder, p: Int) {
            val k = mExtras.keyAt(p); val v = mExtras.valueAt(p)
            h.title.text = k; h.title.setTextIsSelectable(true)
            h.subtitle.text = v.toString(); h.subtitle.setTextIsSelectable(true)
            h.actionIcon.setOnClickListener { UiUtils.fixFocus(it); mActivity.mMutableIntent?.let { im -> im.removeExtra(k); mActivity.showTextViewIntentData(null); val pos = mExtras.indexOfKey(k); if (pos >= 0) { mExtras.removeAt(pos); notifyItemRemoved(pos) }; mActivity.showResetIntentButton(true) } }
        }
        override fun getItemCount(): Int = mExtras.size()
        class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val title: TextView = v.findViewById(R.id.item_title)
            val subtitle: TextView = v.findViewById(R.id.item_subtitle)
            val actionIcon: MaterialButton = v.findViewById(R.id.item_open).apply { setIconResource(R.drawable.ic_trash_can); contentDescription = v.context.getString(R.string.item_remove) }
            init { v.findViewById<View>(R.id.item_icon).visibility = View.GONE }
        }
    }

    private class MatchingActivitiesRecyclerViewAdapter(private val mActivity: ActivityInterceptor) : RecyclerView.Adapter<MatchingActivitiesRecyclerViewAdapter.ViewHolder>() {
        private val mList = mutableListOf<ResolveInfo>()
        private val mPm = mActivity.packageManager
        fun setDefaultList(list: List<ResolveInfo>?) { AdapterUtils.notifyDataSetChanged(this, mList, list) }
        override fun onCreateViewHolder(p: ViewGroup, t: Int) = ViewHolder(LayoutInflater.from(p.context).inflate(R.layout.item_icon_title_subtitle, p, false))
        override fun onBindViewHolder(h: ViewHolder, p: Int) {
            val ri = mList[p]; val ai = ri.activityInfo
            h.title.text = ai.loadLabel(mPm)
            h.subtitle.text = "${ai.packageName}
${ai.name}"; h.subtitle.setTextIsSelectable(true)
            val tag = "${ai.packageName}_${ai.name}"; h.icon.tag = tag
            ImageLoader.getInstance().displayImage(tag, ai, h.icon)
            h.actionIcon.setOnClickListener { UiUtils.fixFocus(it); val i = Intent(mActivity.mMutableIntent); i.setClassName(ai.packageName, ai.name); IntentCompat.removeFlags(i, Intent.FLAG_ACTIVITY_FORWARD_RESULT); mActivity.launchIntent(i, false) }
        }
        override fun getItemCount(): Int = mList.size
        class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val title: TextView = v.findViewById(R.id.item_title)
            val subtitle: TextView = v.findViewById(R.id.item_subtitle)
            val icon: ImageView = v.findViewById(R.id.item_icon)
            val actionIcon: MaterialButton = v.findViewById(R.id.item_open).apply { contentDescription = v.context.getString(R.string.open) }
        }
    }

    companion object {
        val TAG: String = ActivityInterceptor::class.java.simpleName
        const val EXTRA_PACKAGE_NAME = "${BuildConfig.APPLICATION_ID}.intent.extra.PACKAGE_NAME"
        const val EXTRA_CLASS_NAME = "${BuildConfig.APPLICATION_ID}.intent.extra.CLASS_NAME"
        const val EXTRA_ACTION = "${BuildConfig.APPLICATION_ID}.intent.extra.ACTION"
        const val EXTRA_ROOT = "${BuildConfig.APPLICATION_ID}.intent.extra.ROOT"
        const val EXTRA_USER_HANDLE = "${BuildConfig.APPLICATION_ID}.intent.extra.USER_HANDLE"
        const val EXTRA_TRIGGER_ON_START = "${BuildConfig.APPLICATION_ID}.intent.extra.TRIGGER_ON_START"
        const val EXTRA_AUTH = "${BuildConfig.APPLICATION_ID}.intent.extra.AUTH"
        private const val INTENT_EDITED = "intent_edited"
        private val INTENT_FLAG_TO_STRING = SparseArrayCompat<String>().apply {
            put(Intent.FLAG_GRANT_READ_URI_PERMISSION, "FLAG_GRANT_READ_URI_PERMISSION")
            put(Intent.FLAG_GRANT_WRITE_URI_PERMISSION, "FLAG_GRANT_WRITE_URI_PERMISSION")
            put(Intent.FLAG_FROM_BACKGROUND, "FLAG_FROM_BACKGROUND")
            put(Intent.FLAG_DEBUG_LOG_RESOLUTION, "FLAG_DEBUG_LOG_RESOLUTION")
            put(Intent.FLAG_EXCLUDE_STOPPED_PACKAGES, "FLAG_EXCLUDE_STOPPED_PACKAGES")
            put(Intent.FLAG_INCLUDE_STOPPED_PACKAGES, "FLAG_INCLUDE_STOPPED_PACKAGES")
            put(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION, "FLAG_GRANT_PERSISTABLE_URI_PERMISSION")
            put(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION, "FLAG_GRANT_PREFIX_URI_PERMISSION")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(Intent.FLAG_DIRECT_BOOT_AUTO, "FLAG_DIRECT_BOOT_AUTO")
            put(0x00000200, "FLAG_IGNORE_EPHEMERAL")
            put(Intent.FLAG_ACTIVITY_NO_HISTORY, "FLAG_ACTIVITY_NO_HISTORY")
            put(Intent.FLAG_ACTIVITY_SINGLE_TOP, "FLAG_ACTIVITY_SINGLE_TOP")
            put(Intent.FLAG_ACTIVITY_NEW_TASK, "FLAG_ACTIVITY_NEW_TASK")
            put(Intent.FLAG_ACTIVITY_MULTIPLE_TASK, "FLAG_ACTIVITY_MULTIPLE_TASK")
            put(Intent.FLAG_ACTIVITY_CLEAR_TOP, "FLAG_ACTIVITY_CLEAR_TOP")
            put(Intent.FLAG_ACTIVITY_FORWARD_RESULT, "FLAG_ACTIVITY_FORWARD_RESULT")
            put(Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP, "FLAG_ACTIVITY_PREVIOUS_IS_TOP")
            put(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS, "FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS")
            put(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT, "FLAG_ACTIVITY_BROUGHT_TO_FRONT")
            put(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED, "FLAG_ACTIVITY_RESET_TASK_IF_NEEDED")
            put(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY, "FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY")
            put(Intent.FLAG_ACTIVITY_NEW_DOCUMENT, "FLAG_ACTIVITY_NEW_DOCUMENT")
            put(Intent.FLAG_ACTIVITY_NO_USER_ACTION, "FLAG_ACTIVITY_NO_USER_ACTION")
            put(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT, "FLAG_ACTIVITY_REORDER_TO_FRONT")
            put(Intent.FLAG_ACTIVITY_NO_ANIMATION, "FLAG_ACTIVITY_NO_ANIMATION")
            put(Intent.FLAG_ACTIVITY_CLEAR_TASK, "FLAG_ACTIVITY_CLEAR_TASK")
            put(Intent.FLAG_ACTIVITY_TASK_ON_HOME, "FLAG_ACTIVITY_TASK_ON_HOME")
            put(Intent.FLAG_ACTIVITY_RETAIN_IN_RECENTS, "FLAG_ACTIVITY_RETAIN_IN_RECENTS")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) put(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT, "FLAG_ACTIVITY_LAUNCH_ADJACENT")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) put(Intent.FLAG_ACTIVITY_MATCH_EXTERNAL, "FLAG_ACTIVITY_MATCH_EXTERNAL")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) put(Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER, "FLAG_ACTIVITY_REQUIRE_NON_BROWSER")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) put(Intent.FLAG_ACTIVITY_REQUIRE_DEFAULT, "FLAG_ACTIVITY_REQUIRE_DEFAULT")
        }
        private val INTENT_CATEGORIES = listOf(Intent.CATEGORY_DEFAULT, Intent.CATEGORY_BROWSABLE, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Intent.CATEGORY_VOICE else null, Intent.CATEGORY_ALTERNATIVE, Intent.CATEGORY_SELECTED_ALTERNATIVE, Intent.CATEGORY_TAB, Intent.CATEGORY_LAUNCHER, Intent.CATEGORY_LEANBACK_LAUNCHER, "android.intent.category.CAR_LAUNCHER", "android.intent.category.LEANBACK_SETTINGS", Intent.CATEGORY_INFO, Intent.CATEGORY_HOME, "android.intent.category.HOME_MAIN", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Intent.CATEGORY_SECONDARY_HOME else null, "android.intent.category.SETUP_WIZARD", "android.intent.category.LAUNCHER_APP", Intent.CATEGORY_PREFERENCE, Intent.CATEGORY_DEVELOPMENT_PREFERENCE, Intent.CATEGORY_EMBED, Intent.CATEGORY_APP_MARKET, Intent.CATEGORY_MONKEY, Intent.CATEGORY_TEST, Intent.CATEGORY_UNIT_TEST, Intent.CATEGORY_SAMPLE_CODE, Intent.CATEGORY_OPENABLE, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Intent.CATEGORY_TYPED_OPENABLE else null, Intent.CATEGORY_FRAMEWORK_INSTRUMENTATION_TEST, Intent.CATEGORY_CAR_DOCK, Intent.CATEGORY_DESK_DOCK, Intent.CATEGORY_LE_DESK_DOCK, Intent.CATEGORY_HE_DESK_DOCK, Intent.CATEGORY_CAR_MODE, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Intent.CATEGORY_VR_HOME else null, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Intent.CATEGORY_ACCESSIBILITY_SHORTCUT_TARGET else null, Intent.CATEGORY_APP_BROWSER, Intent.CATEGORY_APP_CALCULATOR, Intent.CATEGORY_APP_CALENDAR, Intent.CATEGORY_APP_CONTACTS, Intent.CATEGORY_APP_EMAIL, Intent.CATEGORY_APP_GALLERY, Intent.CATEGORY_APP_MAPS, Intent.CATEGORY_APP_MESSAGING, Intent.CATEGORY_APP_MUSIC, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Intent.CATEGORY_APP_FILES else null).filterNotNull()
    }
}
