// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Build
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.LeadingMarginSpan
import android.util.TypedValue
import android.view.*
import android.view.inputmethod.InputMethodManager
import androidx.annotation.*
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.internal.ViewUtils
import io.github.muntashirakon.text.style.ListSpan
import java.util.*

object UiUtils {
    @JvmStatic
    @Px
    fun dpToPx(context: Context, @Dimension(unit = Dimension.DP) dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }

    @JvmStatic
    @Px
    fun dpToPx(context: Context, @Dimension(unit = Dimension.DP) dp: Float): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.resources.displayMetrics).toInt()
    }

    @JvmStatic
    @Px
    fun spToPx(context: Context, @Dimension(unit = Dimension.SP) sp: Float): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, context.resources.displayMetrics).toInt()
    }

    @JvmStatic
    @Dimension(unit = Dimension.DP)
    fun pxToDp(context: Context, @Px pixel: Int): Int {
        return (pixel.toFloat() / context.resources.displayMetrics.density).toInt()
    }

    @JvmStatic
    @StyleRes
    fun getStyle(context: Context, @AttrRes resId: Int): Int {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(resId, typedValue, true)
        return typedValue.data
    }

    @JvmStatic
    fun getDrawable(context: Context, @AttrRes resId: Int): Drawable? {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(resId, typedValue, true)
        return ContextCompat.getDrawable(context, typedValue.resourceId)
    }

    @JvmStatic
    fun getColumnCount(v: View, @Dimension(unit = Dimension.DP) columnWidth: Int, defaultCount: Int): Int {
        val width = v.width
        if (width == 0) {
            return defaultCount
        }
        val widthDp = pxToDp(v.context, width)
        return (widthDp.toFloat() / columnWidth + 0.5).toInt()
    }

    @JvmStatic
    fun showKeyboard(v: View) {
        val imm = v.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT)
    }

    @JvmStatic
    fun hideKeyboard(v: View) {
        val imm = v.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(v.windowToken, 0)
    }

    /**
     * Get a well-formatted list from the given list of CharSequences. Example:
     * <pre>
     * ["A single-line list-item", "A multi-line
list-item"]
     * </pre>
     * The above will be translated as follows:
     * <pre>
     * 1  A single-line list-item
     * 2  A multi-line
     *    list-item
     * </pre>
     *
     * @param list List of CharSequences
     * @return Formatted list
     */
    @JvmStatic
    fun <T : CharSequence> getOrderedList(list: Iterable<T>): Spanned {
        val spannableStringBuilder = SpannableStringBuilder()
        val locale = Locale.getDefault()
        var j = 0
        for (charSequence in list) {
            val len = charSequence.length
            val spannable = SpannableString(charSequence)
            val finish = spannable.toString().indexOf("\n")
            spannable.setSpan(
                ListSpan(40, 30, ++j, locale), 0, if (finish == -1) len else finish,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            if (finish != -1) {
                spannable.setSpan(
                    LeadingMarginSpan.Standard(40 + 30), finish + 1, len,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            spannableStringBuilder.append(spannable).append("\n")
        }
        return spannableStringBuilder
    }

    /**
     * Wrapper around [androidx.core.view.OnApplyWindowInsetsListener] which also passes the
     * initial padding/margin set on the view. Used with [.doOnApplyWindowInsets].
     */
    fun interface OnApplyWindowInsetsListener {
        /**
         * When [View.setOnApplyWindowInsetsListener] set on a
         * View, this listener method will be called instead of the view's own [View.onApplyWindowInsets] method. The `initial*` is the view's
         * original padding/margin which can be updated and will be applied to the view automatically. This
         * method should return a new [WindowInsetsCompat] with any insets consumed.
         */
        fun onApplyWindowInsets(view: View, insets: WindowInsetsCompat, initialPadding: Rect, initialMargin: Rect?): WindowInsetsCompat
    }

    @JvmStatic
    fun applyWindowInsetsAsPaddingNoTop(v: View) {
        applyWindowInsetsAsPadding(v, false, true, true, true)
    }

    @JvmStatic
    fun applyWindowInsetsNone(v: View) {
        applyWindowInsetsAsPadding(v, false, false, false, false)
    }

    @JvmStatic
    fun applyWindowInsetsAsPadding(v: View, applyVertical: Boolean, applyHorizontal: Boolean) {
        applyWindowInsetsAsPadding(v, applyVertical, applyVertical, applyHorizontal, applyHorizontal)
    }

    @JvmStatic
    @Suppress("DEPRECATION")
    fun applyWindowInsetsAsPadding(v: View, applyTop: Boolean, applyBottom: Boolean, applyStart: Boolean, applyEnd: Boolean) {
        doOnApplyWindowInsets(v) { view, insets, initialPadding, _ ->
            if (!ViewCompat.getFitsSystemWindows(view)) {
                // Do not add padding if fitsSystemWindows is false
                return@doOnApplyWindowInsets insets
            }
            val systemWindowInsetTop = insets.systemWindowInsetTop
            val systemWindowInsetBottom = insets.systemWindowInsetBottom
            val top = initialPadding.top + if (applyTop) systemWindowInsetTop else 0
            val bottom = initialPadding.bottom + if (applyBottom) systemWindowInsetBottom else 0
            val isRtl = ViewCompat.getLayoutDirection(view) == ViewCompat.LAYOUT_DIRECTION_RTL
            val systemWindowInsetLeft = insets.systemWindowInsetLeft
            val systemWindowInsetRight = insets.systemWindowInsetRight
            val start: Int
            val end: Int
            if (isRtl) {
                start = initialPadding.right + if (applyStart) systemWindowInsetRight else 0
                end = initialPadding.left + if (applyEnd) systemWindowInsetLeft else 0
            } else {
                start = initialPadding.left + if (applyStart) systemWindowInsetLeft else 0
                end = initialPadding.right + if (applyEnd) systemWindowInsetRight else 0
            }
            ViewCompat.setPaddingRelative(view, start, top, end, bottom)
            insets
        }
    }

    @JvmStatic
    fun applyWindowInsetsAsMargin(v: View) {
        applyWindowInsetsAsMargin(v, true, true)
    }

    @JvmStatic
    @Suppress("DEPRECATION")
    fun applyWindowInsetsAsMargin(v: View, bottomMargin: Boolean, topMargin: Boolean) {
        doOnApplyWindowInsets(v) { view, insets, _, initialMargin ->
            if (initialMargin == null || !ViewCompat.getFitsSystemWindows(view)) {
                // Do not add padding if fitsSystemWindows is false
                return@doOnApplyWindowInsets insets
            }
            val layoutParams = view.layoutParams
            if (layoutParams !is ViewGroup.MarginLayoutParams) {
                return@doOnApplyWindowInsets insets
            }

            if (topMargin) {
                layoutParams.topMargin = initialMargin.top + insets.systemWindowInsetTop
            }
            if (bottomMargin) {
                layoutParams.bottomMargin = initialMargin.bottom + insets.systemWindowInsetBottom
            }
            layoutParams.leftMargin = initialMargin.left + insets.systemWindowInsetLeft
            layoutParams.rightMargin = initialMargin.right + insets.systemWindowInsetRight

            view.layoutParams = layoutParams
            insets
        }
    }

    /**
     * Wrapper around [androidx.core.view.OnApplyWindowInsetsListener] that records the initial
     * margin of the view and requests that insets are applied when attached.
     */
    @JvmStatic
    @SuppressLint("RestrictedApi")
    fun doOnApplyWindowInsets(view: View, listener: OnApplyWindowInsetsListener) {
        val layoutParams = view.layoutParams
        val initialMargins: Rect? = if (layoutParams is ViewGroup.MarginLayoutParams) {
            // Create a snapshot of the view's margin state.
            Rect(
                layoutParams.leftMargin, layoutParams.topMargin,
                layoutParams.rightMargin, layoutParams.bottomMargin
            )
        } else null
        val initialPadding = Rect(
            view.paddingLeft, view.paddingTop, view.paddingRight,
            view.paddingBottom
        )
        // Set an actual OnApplyWindowInsetsListener which proxies to the given callback, also passing
        // in the original margin state.
        ViewCompat.setOnApplyWindowInsetsListener(view) { view1, insets ->
            listener.onApplyWindowInsets(view1, insets, initialPadding, initialMargins)
        }
        // Request some insets
        ViewUtils.requestApplyInsetsWhenAttached(view)
    }

    @JvmStatic
    fun isDarkMode(context: Context): Boolean {
        val conf = context.resources.configuration
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            conf.isNightModeActive
        } else (conf.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    @JvmStatic
    @Suppress("DEPRECATION")
    fun isDarkMode(): Boolean {
        return when (AppCompatDelegate.getDefaultNightMode()) {
            AppCompatDelegate.MODE_NIGHT_YES -> true
            AppCompatDelegate.MODE_NIGHT_NO -> false
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY, AppCompatDelegate.MODE_NIGHT_UNSPECIFIED, AppCompatDelegate.MODE_NIGHT_AUTO_TIME -> isDarkModeOnSystem
            else -> false
        }
    }

    @get:JvmStatic
    val isDarkModeOnSystem: Boolean
        get() {
            val conf = Resources.getSystem().configuration
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                conf.isNightModeActive
            } else (conf.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }

    /**
     * Fixes focus by forcing Android to focus on the current view and reset
     */
    @JvmStatic
    fun fixFocus(view: View) {
        if (!view.hasFocus()) {
            val focusable = view.isFocusable
            val focusableInTouch = view.isFocusableInTouchMode
            if (!focusable) {
                view.isFocusable = true
            }
            if (!focusableInTouch) {
                view.isFocusableInTouchMode = true
            }
            view.requestFocus()
            view.post { view.clearFocus() }
            if (!focusable) {
                view.isFocusable = false
            }
            if (!focusableInTouch) {
                view.isFocusableInTouchMode = false
            }
        }
    }

    @JvmStatic
    @Suppress("DEPRECATION")
    fun setSystemBarStyle(window: Window, needLightStatusBar: Boolean) {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)

        if (!isDarkMode()) {
            window.decorView.systemUiVisibility = (window.decorView.systemUiVisibility
                    or WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && needLightStatusBar) {
                window.decorView.systemUiVisibility = (window.decorView.systemUiVisibility
                        or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val windowInsetBottom = window.decorView.rootWindowInsets.systemWindowInsetBottom
                if (windowInsetBottom >= Resources.getSystem().displayMetrics.density * 40) {
                    window.decorView.systemUiVisibility = (window.decorView.systemUiVisibility
                            or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR)
                }
            }
        }
        setSystemBarTransparent(window)
    }

    @JvmStatic
    @Suppress("DEPRECATION")
    private fun setSystemBarTransparent(window: Window) {
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
    }
}
