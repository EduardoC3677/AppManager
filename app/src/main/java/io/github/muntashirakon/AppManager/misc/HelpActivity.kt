// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.widget.Button
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.appcompat.widget.SearchView
import androidx.transition.TransitionManager
import androidx.webkit.WebViewClientCompat
import com.google.android.material.transition.MaterialSharedAxis
import io.github.muntashirakon.AppManager.BaseActivity
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.utils.ResourceUtil
import io.github.muntashirakon.AppManager.utils.appearance.AppearanceUtils
import io.github.muntashirakon.util.UiUtils
import me.zhanghai.android.fastscroll.FastScrollerBuilder

class HelpActivity : BaseActivity(), SearchView.OnQueryTextListener {
    private lateinit var mContainer: LinearLayoutCompat
    private lateinit var mWebView: WebView
    private lateinit var mSearchContainer: LinearLayoutCompat
    private lateinit var mSearchView: SearchView
    private val mOnBackPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (mWebView.canGoBack()) {
                mWebView.goBack()
                return
            }
            isEnabled = false
            onBackPressedDispatcher.onBackPressed()
        }
    }

    override fun onAuthenticated(savedInstanceState: Bundle?) {
        try {
            setContentView(R.layout.activity_help)
        } catch (th: Throwable) {
            openDocsSite()
            return
        }
        setSupportActionBar(findViewById(R.id.toolbar))
        onBackPressedDispatcher.addCallback(this, mOnBackPressedCallback)
        supportActionBar?.setTitle(R.string.user_manual)
        findViewById<View>(R.id.progress_linear).visibility = View.GONE
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_WEBVIEW) || ResourceUtil.getRawDataId(this, "index") == 0) {
            openDocsSite()
            return
        }
        mContainer = findViewById(R.id.container)
        mWebView = findViewById(R.id.webview)
        UiUtils.applyWindowInsetsAsPaddingNoTop(mContainer)
        AppearanceUtils.applyOnlyLocale(this)
        mWebView.webViewClient = WebViewClientImpl()
        mWebView.setNetworkAvailable(false)
        mWebView.settings.allowContentAccess = false
        mWebView.loadUrl("file:///android_res/raw/index.html")
        mSearchContainer = findViewById(R.id.search_container)
        val nextButton: Button = findViewById(R.id.next_button)
        val previousButton: Button = findViewById(R.id.previous_button)
        mSearchView = findViewById(R.id.search_bar)
        mSearchView.findViewById<View>(com.google.android.material.R.id.search_close_btn).setOnClickListener {
            mWebView.clearMatches()
            mSearchView.setQuery(null, false)
            val sharedAxis = MaterialSharedAxis(MaterialSharedAxis.Y, true)
            TransitionManager.beginDelayedTransition(mContainer, sharedAxis)
            mSearchContainer.visibility = View.GONE
        }
        mSearchView.setOnQueryTextListener(this)
        nextButton.setOnClickListener { mWebView.findNext(true) }
        previousButton.setOnClickListener { mWebView.findNext(false) }
        FastScrollerBuilder(mWebView).useMd2Style().build()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.activity_help_actions, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_search -> {
                if (mSearchContainer.visibility == View.VISIBLE) {
                    mSearchView.setQuery(null, false)
                    val sharedAxis = MaterialSharedAxis(MaterialSharedAxis.Y, true)
                    TransitionManager.beginDelayedTransition(mContainer, sharedAxis)
                    mSearchContainer.visibility = View.GONE
                } else {
                    val sharedAxis = MaterialSharedAxis(MaterialSharedAxis.Y, false)
                    TransitionManager.beginDelayedTransition(mContainer, sharedAxis)
                    mSearchContainer.visibility = View.VISIBLE
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onQueryTextSubmit(query: String?): Boolean = false

    override fun onQueryTextChange(newText: String?): Boolean {
        mWebView.findAllAsync(newText)
        return true
    }

    private fun openDocsSite() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.website_message)))
        startActivity(intent)
        finish()
    }

    private inner class WebViewClientImpl : WebViewClientCompat() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val uri = request.url
            if (uri.toString().startsWith("file:///android_res")) return false
            startActivity(Intent(Intent.ACTION_VIEW, uri))
            return true
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
            mOnBackPressedCallback.isEnabled = view.canGoBack()
        }
    }
}
