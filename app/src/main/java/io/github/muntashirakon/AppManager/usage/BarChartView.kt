// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.usage

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.text.TextUtils
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.ColorInt
import androidx.appcompat.widget.TintTypedArray
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper
import com.google.android.material.internal.ThemeEnforcement
import com.google.android.material.theme.overlay.MaterialThemeOverlay
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.util.UiUtils
import java.text.DecimalFormat
import java.util.*

class BarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.barChartViewStyle
) : View(MaterialThemeOverlay.wrap(context, attrs, defStyleAttr, DEF_STYLE_RES), attrs, defStyleAttr) {

    private val mBarDataList = mutableListOf<BarData>()
    private val mXAxisLabels = mutableListOf<String>()

    private lateinit var mBarPaint: Paint
    private lateinit var mSelectedBarPaint: Paint
    private lateinit var mGridPaint: Paint
    private lateinit var mTextPaint: Paint
    private lateinit var mTouchLinePaint: Paint
    private lateinit var mTooltipTextPaint: Paint
    private lateinit var mTooltipBgPaint: Paint

    private var mChartWidth: Float = 0f
    private var mChartHeight: Float = 0f
    private var mChartLeft: Float = 0f
    private var mChartTop: Float = 0f
    private var mChartRight: Float = 0f
    private var mChartBottom: Float = 0f

    private var mShowTouchLine: Boolean = false
    private var mTouchedBarIndex: Int = -1

    private var mGridLineCount: Int = 0
    private var mShowGridLabelsOnLeft: Boolean = true
    private var mMaxValue: Float = 0f
    private var mManualMinValue: Float? = null
    private var mManualMaxValue: Float? = null
    private var mYAxisFormat: String? = null
    private val mValueFormatter = DecimalFormat("#.#")

    @ColorInt private var mBarColor: Int = 0
    @ColorInt private var mSelectedBarColor: Int = 0
    @ColorInt private var mGridColor: Int = 0
    private var mGridStrokeWidth: Float = 0f
    @ColorInt private var mTextColor: Int = 0
    private var mTextSizeSp: Float = 0f
    @ColorInt private var mTouchLineColor: Int = 0
    private var mTouchLineWidth: Float = 0f
    private var mMinBarWidthDp: Float = 0f
    private var mMaxBarWidthDp: Float = 0f
    @ColorInt private var mTooltipBgColor: Int = 0
    @ColorInt private var mTooltipTextColor: Int = 0
    private var mTooltipCornerRadius: Float = 0f
    private var mUseCustomLabelSpacing: Boolean = false
    private var mCustomSkipEvery: Int = 1
    private var mCustomStartFrom: Int = 0
    private var mEmptyText: String? = null
    private var mValueOnTopOfBar: Boolean = false

    private var mAccessibilityHelper: BarChartAccessibilityHelper? = null
    private var mFocusedBarIndex: Int = -1

    private var mTooltipListener: TooltipListener? = null

    interface TooltipListener {
        fun getTooltipText(context: Context, barIndex: Int, value: Float, label: String): String
        fun getAccessibilityText(context: Context, barIndex: Int, barCount: Int, value: Float, label: String): String
    }

    init {
        val a = ThemeEnforcement.obtainTintedStyledAttributes(
            getContext(), attrs, R.styleable.BarChartView, defStyleAttr, DEF_STYLE_RES
        )
        try {
            mBarColor = a.getColor(R.styleable.BarChartView_barColor, 0)
            mSelectedBarColor = a.getColor(R.styleable.BarChartView_selectedBarColor, 0)
            mGridColor = a.getColor(R.styleable.BarChartView_gridColor, 0)
            mGridStrokeWidth = a.getDimension(R.styleable.BarChartView_gridStrokeWidth, 0f)
            mTextColor = a.getColor(R.styleable.BarChartView_textColor, 0)
            mTextSizeSp = a.getDimension(R.styleable.BarChartView_textSize, 0f)
            mTouchLineColor = a.getColor(R.styleable.BarChartView_touchLineColor, 0)
            mTouchLineWidth = a.getDimension(R.styleable.BarChartView_touchLineWidth, 0f)
            mMinBarWidthDp = a.getDimension(R.styleable.BarChartView_minBarWidth, 0f)
            mMaxBarWidthDp = a.getDimension(R.styleable.BarChartView_maxBarWidth, 0f)
            mTooltipBgColor = a.getColor(R.styleable.BarChartView_tooltipBackgroundColor, 0)
            mTooltipTextColor = a.getColor(R.styleable.BarChartView_tooltipTextColor, 0)
            mTooltipCornerRadius = a.getDimension(R.styleable.BarChartView_tooltipCornerRadius, 0f)
            mValueOnTopOfBar = a.getBoolean(R.styleable.BarChartView_valueOnTopOfBar, false)
            mGridLineCount = a.getInt(R.styleable.BarChartView_gridLineCount, 0)
            mShowGridLabelsOnLeft = a.getBoolean(R.styleable.BarChartView_gridLabelsOnLeft, true)
            mYAxisFormat = a.getString(R.styleable.BarChartView_yAxisFormat)
            mEmptyText = a.getString(R.styleable.BarChartView_emptyText)
        } finally {
            a.recycle()
        }
        initializePaints()
        initializeAccessibility()
    }

    private fun initializePaints() {
        mBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = mBarColor
            style = Paint.Style.FILL
        }
        mSelectedBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = mSelectedBarColor
            style = Paint.Style.FILL
        }
        mGridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = mGridColor
            strokeWidth = mGridStrokeWidth
        }
        mTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = mTextColor
            textSize = mTextSizeSp
        }
        mTouchLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = mTouchLineColor
            strokeWidth = mTouchLineWidth
            style = Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(12f, 6f), 0f)
        }
        mTooltipTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = mTooltipTextColor
            textSize = spToPx(13f)
        }
        mTooltipBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = mTooltipBgColor
            style = Paint.Style.FILL
        }
    }

    private fun initializeAccessibility() {
        mAccessibilityHelper = BarChartAccessibilityHelper(this)
        ViewCompat.setAccessibilityDelegate(this, mAccessibilityHelper)
        isFocusable = true
        isFocusableInTouchMode = true
        updateOverallContentDescription()
    }

    private fun dpToPx(dp: Float): Float = UiUtils.dpToPx(context, dp)
    private fun spToPx(sp: Float): Float = UiUtils.spToPx(context, sp)

    fun setTooltipListener(listener: TooltipListener?) {
        mTooltipListener = listener
    }

    fun setManualYAxisRange(minValue: Float?, maxValue: Float?) {
        mManualMinValue = minValue
        mManualMaxValue = maxValue
        calculateValueRange()
        invalidate()
    }

    fun setYAxisFormat(format: String?) {
        mYAxisFormat = format
        invalidate()
    }

    fun setBarColor(@ColorInt color: Int) {
        mBarColor = color
        mBarPaint.color = color
        invalidate()
    }

    fun setSelectedBarColor(@ColorInt color: Int) {
        mSelectedBarColor = color
        mSelectedBarPaint.color = color
        invalidate()
    }

    fun setData(values: List<Float>?, labels: List<String>?) {
        mBarDataList.clear()
        mXAxisLabels.clear()
        if (values != null && labels != null && values.size == labels.size) {
            for (i in values.indices) {
                mBarDataList.add(BarData(values[i], labels[i]))
                mXAxisLabels.add(labels[i])
            }
            calculateValueRange()
        }
        invalidate()
        updateOverallContentDescription()
        mAccessibilityHelper?.invalidateRoot()
        mFocusedBarIndex = -1
        mTouchedBarIndex = -1
        mShowTouchLine = false
    }

    private fun calculateValueRange() {
        if (mBarDataList.isEmpty()) {
            mMaxValue = 1f
            return
        }
        val values = mBarDataList.map { it.value }
        mMaxValue = mManualMaxValue ?: Collections.max(values)
    }

    fun setGridLabelsOnLeft(onLeft: Boolean) {
        mShowGridLabelsOnLeft = onLeft
        invalidate()
    }

    fun setGridLineCount(count: Int) {
        mGridLineCount = Math.max(2, count)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateDynamicMargins(w, h)
    }

    private fun calculateDynamicMargins(width: Int, height: Int) {
        var leftMargin: Float
        var rightMargin: Float
        var topMargin: Float
        var bottomMargin: Float
        if (mBarDataList.isEmpty()) {
            leftMargin = dpToPx(40f)
            rightMargin = dpToPx(20f)
            topMargin = dpToPx(20f)
            bottomMargin = dpToPx(40f)
        } else {
            var maxYLabelWidth = 0f
            for (i in 0 until mGridLineCount) {
                val value = (mMaxValue * i) / (mGridLineCount - 1)
                val label = formatYAxisValue(value)
                val textBounds = Rect()
                mTextPaint.getTextBounds(label, 0, label.length, textBounds)
                maxYLabelWidth = Math.max(maxYLabelWidth, textBounds.width().toFloat())
            }
            var maxXLabelWidth = 0f
            var maxXLabelHeight = 0f
            for (label in mXAxisLabels) {
                val textBounds = Rect()
                mTextPaint.getTextBounds(label, 0, label.length, textBounds)
                maxXLabelWidth = Math.max(maxXLabelWidth, textBounds.width().toFloat())
                maxXLabelHeight = Math.max(maxXLabelHeight, textBounds.height().toFloat())
            }
            leftMargin = maxYLabelWidth + dpToPx(12f)
            rightMargin = Math.max(dpToPx(16f), maxXLabelWidth * 0.5f)
            topMargin = dpToPx(30f)
            bottomMargin = maxXLabelHeight + dpToPx(16f)
        }
        mChartLeft = leftMargin
        mChartTop = topMargin
        mChartRight = width - rightMargin
        mChartBottom = height - bottomMargin
        mChartWidth = mChartRight - mChartLeft
        mChartHeight = mChartBottom - mChartTop
    }

    private fun formatYAxisValue(value: Float): String {
        return mYAxisFormat?.let { String.format(it, value) } ?: mValueFormatter.format(value.toDouble())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (mBarDataList.isEmpty()) {
            drawEmptyState(canvas)
            return
        }
        calculateDynamicMargins(width, height)
        drawGridLines(canvas)
        drawBarsWithSpacing(canvas)
        drawXAxisLabelsWithTickMarks(canvas)
        if (mShowTouchLine && mTouchedBarIndex >= 0) {
            drawTouchLineAndTooltip(canvas)
        }
    }

    private fun drawEmptyState(canvas: Canvas) {
        if (TextUtils.isEmpty(mEmptyText)) return
        val emptyTextPaint = Paint(mTextPaint).apply {
            textSize = spToPx(16f)
            color = mTextColor
        }
        val textBounds = Rect()
        emptyTextPaint.getTextBounds(mEmptyText!!, 0, mEmptyText!!.length, textBounds)
        val x = (width - textBounds.width()) / 2f
        val y = (height + textBounds.height()) / 2f
        canvas.drawText(mEmptyText!!, x, y, emptyTextPaint)
    }

    private fun drawGridLines(canvas: Canvas) {
        for (i in 0 until mGridLineCount) {
            val y = mChartBottom - (i * mChartHeight / (mGridLineCount - 1))
            canvas.drawLine(mChartLeft, y, mChartRight, y, mGridPaint)
            val value = (mMaxValue * i) / (mGridLineCount - 1)
            val label = formatYAxisValue(value)
            val textBounds = Rect()
            mTextPaint.getTextBounds(label, 0, label.length, textBounds)
            if (mShowGridLabelsOnLeft) {
                canvas.drawText(label, mChartLeft - textBounds.width() - dpToPx(8f), y + textBounds.height() / 2f, mTextPaint)
            } else {
                canvas.drawText(label, mChartRight + dpToPx(8f), y + textBounds.height() / 2f, mTextPaint)
            }
        }
    }

    private fun drawBarsWithSpacing(canvas: Canvas) {
        val barCount = mBarDataList.size
        if (barCount == 0) return
        val dims = calculateBarDimensions()
        for (i in 0 until barCount) {
            val bar = mBarDataList[i]
            val barLeft = mChartLeft + dims.gapWidth + (i * (dims.barWidth + dims.gapWidth))
            val barRight = barLeft + dims.barWidth
            val barTop = mChartBottom - (bar.value / mMaxValue) * mChartHeight
            val barBottom = mChartBottom
            val currentBarPaint = if (i == mTouchedBarIndex) mSelectedBarPaint else mBarPaint
            canvas.drawRect(barLeft, barTop, barRight, barBottom, currentBarPaint)
            if (mValueOnTopOfBar && bar.value > 0 && barTop > mChartTop + dpToPx(20f)) {
                val valueText = formatYAxisValue(bar.value)
                val textBounds = Rect()
                mTextPaint.getTextBounds(valueText, 0, valueText.length, textBounds)
                val textX = barLeft + (dims.barWidth - textBounds.width()) / 2f
                val textY = barTop - dpToPx(5f)
                if (textBounds.width() <= dims.barWidth) {
                    canvas.drawText(valueText, textX, textY, mTextPaint)
                }
            }
        }
    }

    private fun calculateBarDimensions(): BarDimensions {
        val barCount = mBarDataList.size
        val minGap = dpToPx(4f)
        val totalGapWidth = minGap * (barCount + 1)
        val availableWidthForBars = mChartWidth - totalGapWidth
        val calculatedBarWidth = availableWidthForBars / barCount
        val barWidth = Math.max(mMinBarWidthDp, Math.min(calculatedBarWidth, mMaxBarWidthDp))
        val actualTotalBarWidth = barWidth * barCount
        val remainingWidth = mChartWidth - actualTotalBarWidth
        val gapWidth = remainingWidth / (barCount + 1)
        return BarDimensions(barWidth, gapWidth)
    }

    private data class BarDimensions(val barWidth: Float, val gapWidth: Float)

    private fun drawTouchLineAndTooltip(canvas: Canvas) {
        if (mTouchedBarIndex < 0 || mTouchedBarIndex >= mBarDataList.size) return
        val dims = calculateBarDimensions()
        val barCenter = mChartLeft + dims.gapWidth + (mTouchedBarIndex * (dims.barWidth + dims.gapWidth)) + dims.barWidth / 2f
        val bar = mBarDataList[mTouchedBarIndex]
        val barTop = mChartBottom - (bar.value / mMaxValue) * mChartHeight
        canvas.drawLine(barCenter, mChartTop, barCenter, barTop, mTouchLinePaint)
        drawTooltip(canvas, barCenter)
    }

    private fun drawTooltip(canvas: Canvas, lineX: Float) {
        if (mTouchedBarIndex < 0 || mTouchedBarIndex >= mBarDataList.size) return
        val bar = mBarDataList[mTouchedBarIndex]
        val tooltipText = mTooltipListener?.getTooltipText(context, mTouchedBarIndex, bar.value, bar.label)
            ?: "(${bar.label}, ${formatYAxisValue(bar.value)})"\nval textBounds = Rect()
        mTooltipTextPaint.getTextBounds(tooltipText, 0, tooltipText.length, textBounds)
        val tooltipWidth = textBounds.width() + dpToPx(16f)
        val tooltipHeight = textBounds.height() + dpToPx(12f)
        var tooltipX = lineX - tooltipWidth / 2f
        var tooltipY = mChartTop - tooltipHeight - dpToPx(8f)
        tooltipX = Math.max(dpToPx(8f), Math.min(tooltipX, width - tooltipWidth - dpToPx(8f)))
        tooltipY = Math.max(dpToPx(8f), tooltipY)
        canvas.drawRoundRect(tooltipX, tooltipY, tooltipX + tooltipWidth, tooltipY + tooltipHeight, mTooltipCornerRadius, mTooltipCornerRadius, mTooltipBgPaint)
        canvas.drawText(tooltipText, tooltipX + dpToPx(8f), tooltipY + tooltipHeight - dpToPx(6f), mTooltipTextPaint)
    }

    override fun dispatchHoverEvent(event: MotionEvent): Boolean {
        return (mAccessibilityHelper?.dispatchHoverEvent(event) ?: false) || super.dispatchHoverEvent(event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        return (mAccessibilityHelper?.dispatchKeyEvent(event) ?: false) || super.dispatchKeyEvent(event)
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        mAccessibilityHelper?.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> handleDirectionalNavigation(keyCode)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> handleBarSelection()
            KeyEvent.KEYCODE_ESCAPE -> handleClearSelection()
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun handleDirectionalNavigation(keyCode: Int): Boolean {
        if (mBarDataList.isEmpty()) return false
        var newFocusedIndex = mFocusedBarIndex
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            newFocusedIndex = Math.max(0, mFocusedBarIndex - 1)
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            newFocusedIndex = Math.min(mBarDataList.size - 1, mFocusedBarIndex + 1)
        }
        if (newFocusedIndex != mFocusedBarIndex) {
            mFocusedBarIndex = newFocusedIndex
            val bar = mBarDataList[mFocusedBarIndex]
            announceForAccessibility(getAccessibleBarDescription(mFocusedBarIndex, bar))
            mAccessibilityHelper?.invalidateVirtualView(mFocusedBarIndex)
            invalidate()
            return true
        }
        return false
    }

    private fun handleBarSelection(): Boolean {
        if (mFocusedBarIndex in mBarDataList.indices) {
            selectBarForAccessibility(mFocusedBarIndex)
            return true
        }
        return false
    }

    private fun handleClearSelection(): Boolean {
        if (mTouchedBarIndex >= 0) {
            mTouchedBarIndex = -1
            mShowTouchLine = false
            invalidate()
            announceForAccessibility("Selection cleared")
            return true
        }
        return false
    }

    override fun performClick(): Boolean {
        super.performClick()
        if (mFocusedBarIndex in mBarDataList.indices) {
            selectBarForAccessibility(mFocusedBarIndex)
            return true
        }
        return true
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                handleTouch(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                mShowTouchLine = false
                mTouchedBarIndex = -1
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleTouch(x: Float, y: Float) {
        if (mBarDataList.isEmpty()) return
        if (x < mChartLeft || x > mChartRight || y < mChartTop || y > mChartBottom) {
            mShowTouchLine = false
            mTouchedBarIndex = -1
            invalidate()
            return
        }
        val dims = calculateBarDimensions()
        for (i in mBarDataList.indices) {
            val barLeft = mChartLeft + dims.gapWidth + (i * (dims.barWidth + dims.gapWidth))
            val barRight = barLeft + dims.barWidth
            if (x in barLeft..barRight) {
                mTouchedBarIndex = i
                mShowTouchLine = true
                invalidate()
                break
            }
        }
    }

    private fun drawXAxisLabelsWithTickMarks(canvas: Canvas) {
        val barCount = mBarDataList.size
        if (barCount == 0) return
        val dims = calculateBarDimensions()
        val spacing = if (mUseCustomLabelSpacing) LabelSpacing(mCustomSkipEvery, mCustomStartFrom) else calculateOptimalLabelSpacing(dims)
        for (i in mXAxisLabels.indices) {
            if (!spacing.shouldShowLabel(i)) continue
            val barCenter = mChartLeft + dims.gapWidth + (i * (dims.barWidth + dims.gapWidth)) + dims.barWidth / 2f
            canvas.drawLine(barCenter, mChartBottom, barCenter, mChartBottom + dpToPx(4f), mGridPaint)
            val label = mXAxisLabels[i]
            val textBounds = Rect()
            mTextPaint.getTextBounds(label, 0, label.length, textBounds)
            val textX = barCenter - textBounds.width() / 2f
            val textY = mChartBottom + textBounds.height() + dpToPx(8f)
            canvas.drawText(label, textX, textY, mTextPaint)
        }
    }

    private fun calculateOptimalLabelSpacing(dims: BarDimensions): LabelSpacing {
        if (mXAxisLabels.isEmpty()) return LabelSpacing(1, 0)
        var maxLabelWidth = 0f
        for (label in mXAxisLabels) {
            val textBounds = Rect()
            mTextPaint.getTextBounds(label, 0, label.length, textBounds)
            maxLabelWidth = Math.max(maxLabelWidth, textBounds.width().toFloat())
        }
        val minSpacingNeeded = maxLabelWidth + dpToPx(8f)
        val availableSpacePerLabel = dims.barWidth + dims.gapWidth
        var skipCount = 1
        if (availableSpacePerLabel < minSpacingNeeded) {
            skipCount = Math.ceil((minSpacingNeeded / availableSpacePerLabel).toDouble()).toInt()
        }
        return LabelSpacing(skipCount, 0)
    }

    private data class LabelSpacing(val skipCount: Int, val startOffset: Int) {
        fun shouldShowLabel(index: Int): Boolean = (index - startOffset) % skipCount == 0 && index >= startOffset
    }

    fun setLabelSkipPattern(skipEvery: Int, startFrom: Int) {
        mCustomSkipEvery = Math.max(1, skipEvery)
        mCustomStartFrom = Math.max(0, startFrom)
        mUseCustomLabelSpacing = true
        invalidate()
    }

    fun setAutoLabelSpacing(auto: Boolean) {
        mUseCustomLabelSpacing = !auto
        invalidate()
    }

    fun getCurrentLabelSpacing(): IntArray {
        if (mUseCustomLabelSpacing) return intArrayOf(mCustomSkipEvery, mCustomStartFrom)
        if (mBarDataList.isEmpty()) return intArrayOf(1, 0)
        val spacing = calculateOptimalLabelSpacing(calculateBarDimensions())
        return intArrayOf(spacing.skipCount, spacing.startOffset)
    }

    private data class BarData(val value: Float, val label: String)

    private fun getAccessibleBarDescription(barIndex: Int, bar: BarData): String {
        return mTooltipListener?.getAccessibilityText(context, barIndex, mBarDataList.size, bar.value, bar.label)
            ?: String.format(Locale.getDefault(), "%s: %s. Bar %d of %d.", bar.label, formatYAxisValue(bar.value), barIndex + 1, mBarDataList.size)
    }

    private fun getBarBounds(barIndex: Int): Rect {
        if (barIndex !in mBarDataList.indices) return Rect()
        val dims = calculateBarDimensions()
        val barLeft = mChartLeft + dims.gapWidth + (barIndex * (dims.barWidth + dims.gapWidth))
        val barRight = barLeft + dims.barWidth
        val bar = mBarDataList[barIndex]
        val barTop = mChartBottom - (bar.value / mMaxValue) * mChartHeight
        val barBottom = mChartBottom
        return Rect(barLeft.toInt(), barTop.toInt(), barRight.toInt(), barBottom.toInt())
    }

    private fun getBarIndexAtPosition(x: Float, y: Float): Int {
        if (mBarDataList.isEmpty() || x < mChartLeft || x > mChartRight || y < mChartTop || y > mChartBottom) return -1
        val dims = calculateBarDimensions()
        for (i in mBarDataList.indices) {
            val barLeft = mChartLeft + dims.gapWidth + (i * (dims.barWidth + dims.gapWidth))
            val barRight = barLeft + dims.barWidth
            if (x in barLeft..barRight) return i
        }
        return -1
    }

    private fun selectBarForAccessibility(barIndex: Int) {
        if (barIndex in mBarDataList.indices) {
            mTouchedBarIndex = barIndex
            mShowTouchLine = true
            invalidate()
            val bar = mBarDataList[barIndex]
            announceForAccessibility("Selected. ${getAccessibleBarDescription(barIndex, bar)}")
            mAccessibilityHelper?.let {
                it.invalidateVirtualView(barIndex)
                it.sendEventForVirtualView(barIndex, AccessibilityEvent.TYPE_VIEW_SELECTED)
            }
        }
    }

    private fun updateOverallContentDescription() {
        contentDescription = if (mBarDataList.isEmpty()) mEmptyText ?: "Empty bar chart"\nelse context.getString(R.string.bar_chart_content_description, mBarDataList.size)
    }

    private inner class BarChartAccessibilityHelper(forView: View) : ExploreByTouchHelper(forView) {
        override fun getVirtualViewAt(x: Float, y: Float): Int {
            val barIndex = getBarIndexAtPosition(x, y)
            return if (barIndex >= 0) barIndex else INVALID_ID
        }

        override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>) {
            for (i in mBarDataList.indices) virtualViewIds.add(i)
        }

        override fun onPopulateNodeForVirtualView(virtualViewId: Int, node: AccessibilityNodeInfoCompat) {
            if (virtualViewId !in mBarDataList.indices) return
            val bar = mBarDataList[virtualViewId]
            node.contentDescription = getAccessibleBarDescription(virtualViewId, bar)
            node.className = "android.widget.Button"\nnode.isClickable = true
            node.isFocusable = true
            node.isVisibleToUser = true
            node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
            node.setBoundsInParent(getBarBounds(virtualViewId))
            if (virtualViewId == mTouchedBarIndex) {
                node.isSelected = true
                node.addAction(AccessibilityNodeInfoCompat.ACTION_CLEAR_SELECTION)
            }
        }

        override fun onPerformActionForVirtualView(virtualViewId: Int, action: Int, arguments: Bundle?): Boolean {
            if (virtualViewId !in mBarDataList.indices) return false
            return when (action) {
                AccessibilityNodeInfoCompat.ACTION_CLICK -> {
                    selectBarForAccessibility(virtualViewId)
                    true
                }
                AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS -> {
                    mFocusedBarIndex = virtualViewId
                    invalidateVirtualView(virtualViewId)
                    true
                }
                AccessibilityNodeInfoCompat.ACTION_CLEAR_ACCESSIBILITY_FOCUS -> {
                    if (mFocusedBarIndex == virtualViewId) {
                        mFocusedBarIndex = -1
                        invalidateVirtualView(virtualViewId)
                    }
                    true
                }
                AccessibilityNodeInfoCompat.ACTION_CLEAR_SELECTION -> {
                    if (mTouchedBarIndex == virtualViewId) {
                        mTouchedBarIndex = -1
                        mShowTouchLine = false
                        invalidate()
                        announceForAccessibility("Bar deselected")
                    }
                    true
                }
                else -> false
            }
        }

        override fun onPopulateEventForVirtualView(virtualViewId: Int, event: AccessibilityEvent) {
            if (virtualViewId !in mBarDataList.indices) return
            val bar = mBarDataList[virtualViewId]
            event.contentDescription = getAccessibleBarDescription(virtualViewId, bar)
            event.className = "android.widget.Button"
        }
    }

    companion object {
        private val DEF_STYLE_RES = R.style.Widget_AppTheme_BarChartView
    }
}
