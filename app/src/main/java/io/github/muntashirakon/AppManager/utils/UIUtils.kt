// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils

import android.annotation.TargetApi
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.TextUtils
import android.text.style.AbsoluteSizeSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.ImageSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.AnyThread
import androidx.annotation.ColorInt
import androidx.annotation.FloatRange
import androidx.annotation.PluralsRes
import androidx.annotation.Px
import androidx.annotation.StringRes
import androidx.annotation.UiThread
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.FragmentActivity
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.misc.AdvancedSearchView
import io.github.muntashirakon.AppManager.misc.AdvancedSearchView
import io.github.muntashirakon.dialog.DialogTitleBuilder
import io.github.muntashirakon.util.UiUtils
import io.github.muntashirakon.view.AutoFitGridLayoutManager
import io.github.muntashirakon.widget.SearchView
import java.util.Locale

object UIUtils {
    @JvmField
    val sSpannableFactory: Spannable.Factory = Spannable.Factory.getInstance()

    @JvmStatic
    fun getHighlightedText(
        text: String,
        constraint: String?,
        @ColorInt color: Int
    ): Spannable {
        val spannable = sSpannableFactory.newSpannable(text)
        if (TextUtils.isEmpty(constraint)) {
            return spannable
        }
        val start = text.lowercase(Locale.ROOT).indexOf(constraint!!)
        if (start == -1) return spannable
        val end = start + constraint.length
        if (end > text.length) return spannable
        spannable.setSpan(BackgroundColorSpan(color), start, end, Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
        return spannable
    }

    @JvmStatic
    fun getColoredText(text: CharSequence, color: Int): Spannable {
        val spannable = charSequenceToSpannable(text)
        spannable.setSpan(
            ForegroundColorSpan(color), 0, spannable.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return spannable
    }

    @JvmStatic
    fun setTypefaceSpan(text: CharSequence, family: String): Spannable {
        val spannable = charSequenceToSpannable(text)
        spannable.setSpan(
            TypefaceSpan(family), 0, spannable.length,
            Spannable.SPAN_INCLUSIVE_EXCLUSIVE
        )
        return spannable
    }

    @JvmStatic
    fun getMonospacedText(text: CharSequence): Spannable {
        return setTypefaceSpan(text, "monospace")
    }

    @JvmStatic
    fun getPrimaryText(context: Context, text: CharSequence): Spannable {
        return getColoredText(setTypefaceSpan(text, "sans-serif-medium"), getTextColorPrimary(context))
    }

    @JvmStatic
    fun getPrimaryText(context: Context, @StringRes strRes: Int): Spannable {
        return getPrimaryText(context, context.getText(strRes))
    }

    @JvmStatic
    fun getStyledKeyValue(
        context: Context,
        @StringRes keyRes: Int,
        value: CharSequence?,
        separator: CharSequence?
    ): Spannable {
        return getStyledKeyValue(context, context.getText(keyRes), value, separator)
    }

    @JvmStatic
    fun getStyledKeyValue(
        context: Context,
        @StringRes keyRes: Int,
        value: CharSequence?
    ): Spannable {
        return getStyledKeyValue(context, context.getText(keyRes), value)
    }

    @JvmStatic
    fun getStyledKeyValue(
        context: Context,
        key: CharSequence?,
        value: CharSequence?
    ): Spannable {
        return getStyledKeyValue(context, key, value, LangUtils.getSeparatorString())
    }

    @JvmStatic
    fun getStyledKeyValue(
        context: Context,
        key: CharSequence?,
        value: CharSequence?,
        separator: CharSequence?
    ): Spannable {
        return SpannableStringBuilder(getPrimaryText(context, SpannableStringBuilder(key).append(separator)))
            .append(value)
    }

    @JvmStatic
    fun getSecondaryText(context: Context, text: CharSequence): Spannable {
        return getColoredText(text, getTextColorSecondary(context))
    }

    @JvmStatic
    fun getTitleText(context: Context, text: CharSequence): Spannable {
        val spannable = charSequenceToSpannable(text)
        spannable.setSpan(
            AbsoluteSizeSpan(getTitleSize(context)), 0, spannable.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return getPrimaryText(context, spannable)
    }

    @JvmStatic
    fun getTitleText(context: Context, @StringRes strRes: Int): Spannable {
        return getTitleText(context, context.getText(strRes))
    }

    @JvmStatic
    fun getSmallerText(text: CharSequence): Spannable {
        val spannable = charSequenceToSpannable(text)
        spannable.setSpan(
            RelativeSizeSpan(.8f), 0, spannable.length,
            Spanned.SPAN_INCLUSIVE_EXCLUSIVE
        )
        return spannable
    }

    @JvmStatic
    fun getUnderlinedString(text: CharSequence): Spannable {
        val spannable = charSequenceToSpannable(text)
        spannable.setSpan(UnderlineSpan(), 0, spannable.length, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
        return spannable
    }

    @JvmStatic
    fun getBoldString(text: CharSequence): Spannable {
        val spannable = charSequenceToSpannable(text)
        spannable.setSpan(StyleSpan(Typeface.BOLD), 0, spannable.length, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
        return spannable
    }

    @JvmStatic
    fun getItalicString(text: CharSequence): Spannable {
        val ss = charSequenceToSpannable(text)
        ss.setSpan(StyleSpan(Typeface.ITALIC), 0, ss.length, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
        return ss
    }

    @JvmStatic
    fun setImageSpan(
        text: CharSequence,
        image: Drawable?,
        tv: TextView
    ): Spannable {
        return setImageSpan(text, image, tv, 0, 1)
    }

    @JvmStatic
    fun setImageSpan(
        text: CharSequence,
        image: Drawable?,
        tv: TextView,
        start: Int
    ): Spannable {
        return setImageSpan(text, image, tv, start, start + 1)
    }

    @JvmStatic
    fun setImageSpan(
        text: CharSequence,
        image: Drawable?,
        tv: TextView,
        start: Int,
        end: Int
    ): Spannable {
        val spannable = charSequenceToSpannable(text)
        if (image == null) {
            return spannable
        }
        val textPaint = tv.paint
        val fontMetrics = textPaint.fontMetricsInt
        image.setBounds(0, fontMetrics.ascent, fontMetrics.bottom - fontMetrics.ascent, fontMetrics.bottom)
        spannable.setSpan(ImageSpan(image), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return spannable
    }

    @JvmStatic
    @TargetApi(Build.VERSION_CODES.Q)
    fun getSystemColor(context: Context, resAttrColor: Int): Int { // Ex. android.R.attr.colorPrimary
        val typedValue = TypedValue()
        val contextThemeWrapper = ContextThemeWrapper(
            context,
            android.R.style.Theme_DeviceDefault_DayNight
        )
        contextThemeWrapper.theme.resolveAttribute(resAttrColor, typedValue, true)
        return typedValue.data
    }

    @JvmStatic
    fun getTextColorPrimary(context: Context): Int {
        return MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface, -1)
    }

    @JvmStatic
    fun getTextColorSecondary(context: Context): Int {
        return MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, -1)
    }

    @JvmStatic
    fun getTitleSize(context: Context): Int {
        return context.resources.getDimensionPixelSize(R.dimen.title_font)
    }

    @JvmStatic
    fun getSubtitleSize(context: Context): Int {
        return context.resources.getDimensionPixelSize(R.dimen.subtitle_font)
    }

    @JvmStatic
    @UiThread
    fun getDialogTitle(
        activity: FragmentActivity,
        title: CharSequence,
        drawable: Drawable?,
        subtitle: CharSequence?
    ): View {
        return DialogTitleBuilder(activity).setTitle(title).setSubtitle(subtitle).setStartIcon(drawable).build()
    }

    @JvmStatic
    fun getProgressDialog(
        activity: FragmentActivity,
        text: CharSequence?,
        circular: Boolean
    ): AlertDialog {
        val layout = if (circular) R.layout.dialog_progress_circular else R.layout.dialog_progress2
        val view = activity.layoutInflater.inflate(layout, null)
        if (text != null) {
            val tv = view.findViewById<TextView>(android.R.id.text1)
            tv.text = text
        }
        return MaterialAlertDialogBuilder(activity)
            .setCancelable(false)
            .setView(view)
            .create()
    }

    @JvmStatic
    fun getProgressDialog(
        activity: FragmentActivity,
        @StringRes text: Int
    ): AlertDialog {
        val view = activity.layoutInflater.inflate(R.layout.dialog_progress2, null)
        val tv = view.findViewById<TextView>(android.R.id.text1)
        tv.setText(text)
        return MaterialAlertDialogBuilder(activity)
            .setCancelable(false)
            .setView(view)
            .create()
    }

    @JvmStatic
    fun setupSearchView(
        actionBar: ActionBar,
        queryTextListener: SearchView.OnQueryTextListener?
    ): SearchView {
        val searchView = SearchView(actionBar.themedContext)
        searchView.id = R.id.action_search
        searchView.setOnQueryTextListener(queryTextListener)
        // Set layout params
        val layoutParams = ActionBar.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        layoutParams.gravity = Gravity.END
        actionBar.setCustomView(searchView, layoutParams)
        return searchView
    }

    @JvmStatic
    fun setupAdvancedSearchView(
        actionBar: ActionBar,
        queryTextListener: AdvancedSearchView.OnQueryTextListener?
    ): AdvancedSearchView {
        val searchView = AdvancedSearchView(actionBar.themedContext)
        searchView.id = R.id.action_search
        searchView.setOnQueryTextListener(queryTextListener)
        // Set layout params
        val layoutParams = ActionBar.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        layoutParams.gravity = Gravity.END
        actionBar.setCustomView(searchView, layoutParams)
        return searchView
    }

    @JvmStatic
    @UiThread
    fun displayShortToast(message: CharSequence?) {
        Toast.makeText(ContextUtils.getContext(), message, Toast.LENGTH_SHORT).show()
    }

    @JvmStatic
    @UiThread
    fun displayShortToast(format: String, vararg args: Any?) {
        Toast.makeText(ContextUtils.getContext(), String.format(Locale.getDefault(), format, *args), Toast.LENGTH_SHORT).show()
    }

    @JvmStatic
    @UiThread
    fun displayShortToast(@StringRes res: Int) {
        Toast.makeText(ContextUtils.getContext(), res, Toast.LENGTH_SHORT).show()
    }

    @JvmStatic
    @UiThread
    fun displayShortToast(@StringRes res: Int, vararg args: Any?) {
        val appContext = ContextUtils.getContext()
        Toast.makeText(appContext, appContext.getString(res, *args), Toast.LENGTH_SHORT).show()
    }

    @JvmStatic
    @UiThread
    fun displayLongToast(message: CharSequence?) {
        Toast.makeText(ContextUtils.getContext(), message, Toast.LENGTH_LONG).show()
    }

    @JvmStatic
    @UiThread
    fun displayLongToast(format: String, vararg args: Any?) {
        Toast.makeText(
            ContextUtils.getContext(), String.format(Locale.getDefault(), format, *args),
            Toast.LENGTH_LONG
        ).show()
    }

    @JvmStatic
    @UiThread
    fun displayLongToast(@StringRes res: Int) {
        Toast.makeText(ContextUtils.getContext(), res, Toast.LENGTH_LONG).show()
    }

    @JvmStatic
    @UiThread
    fun displayLongToast(@StringRes res: Int, vararg args: Any?) {
        val appContext = ContextUtils.getContext()
        Toast.makeText(appContext, appContext.getString(res, *args), Toast.LENGTH_LONG).show()
    }

    @JvmStatic
    @UiThread
    fun displayLongToastPl(@PluralsRes res: Int, count: Int, vararg args: Any?) {
        val appContext = ContextUtils.getContext()
        Toast.makeText(appContext, appContext.resources.getQuantityString(res, count, *args), Toast.LENGTH_LONG).show()
    }

    @JvmStatic
    fun getGridLayoutAt450Dp(context: Context): AutoFitGridLayoutManager {
        return AutoFitGridLayoutManager(context, UiUtils.dpToPx(context, 450))
    }

    @JvmStatic
    fun charSequenceToSpannable(text: CharSequence): Spannable {
        return if (text is Spannable) {
            text
        } else {
            sSpannableFactory.newSpannable(text)
        }
    }

    @JvmStatic
    @AnyThread
    fun getMutableBitmapFromDrawable(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) {
            val bitmap = drawable.bitmap
            return bitmap.copy(Bitmap.Config.ARGB_8888, true)
        }
        val bmp = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bmp
    }

    @JvmStatic
    @AnyThread
    fun getBitmapFromDrawable(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) {
            return drawable.bitmap
        }
        val bmp = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bmp
    }

    @JvmStatic
    @AnyThread
    fun getBitmapFromDrawable(drawable: Drawable, @Px padding: Int): Bitmap {
        if (padding == 0) {
            return getBitmapFromDrawable(drawable)
        }
        val width = drawable.intrinsicWidth + 2 * padding
        val height = drawable.intrinsicHeight + 2 * padding
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(padding, padding, canvas.width - padding, canvas.height - padding)
        drawable.draw(canvas)
        return bmp
    }

    @JvmStatic
    fun generateBitmapFromText(text: String, typeface: Typeface?): Bitmap {
        val fontSize = 100
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        textPaint.textSize = fontSize.toFloat()
        textPaint.color = Color.WHITE
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = typeface ?: Typeface.SANS_SERIF

        val textRect = Rect()
        textPaint.getTextBounds(text, 0, text.length, textRect)
        val length = Math.max(textRect.width(), textRect.height()) + 80
        val bitmap = Bitmap.createBitmap(length, length, Bitmap.Config.ARGB_8888)

        val canvas = Canvas(bitmap)
        val x = canvas.width / 2f - textRect.width() / 2f - textRect.left
        val y = canvas.height / 2f + textRect.height() / 2f - textRect.bottom
        canvas.drawText(text, x, y, textPaint)
        return bitmap
    }

    @JvmStatic
    fun getDimmedBitmap(bitmap: Bitmap): Bitmap {
        val newBmp = if (bitmap.isMutable) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, true)
        setBrightness(newBmp, -120f)
        return newBmp
    }

    @JvmStatic
    fun setBrightness(bmp: Bitmap, @FloatRange(from = -255.0, to = 255.0) brightness: Float) {
        assert(bmp.isMutable)
        val cm = ColorMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, brightness,
                0f, 1f, 0f, 0f, brightness,
                0f, 0f, 1f, 0f, brightness,
                0f, 0f, 0f, 1f, 0f
            )
        )
        val canvas = Canvas(bmp)
        val paint = Paint()
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(bmp, 0f, 0f, paint)
    }
}
