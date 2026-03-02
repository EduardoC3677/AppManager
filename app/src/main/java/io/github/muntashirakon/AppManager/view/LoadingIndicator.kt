// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.view

import android.content.Context
import android.util.AttributeSet
import android.view.animation.LinearInterpolator
import androidx.core.view.isVisible
import com.google.android.material.progressindicator.LinearProgressIndicator
import io.github.muntashirakon.AppManager.utils.appearance.ExpressiveMotion

/**
 * Material 3 Expressive Loading Indicator
 * 
 * Official implementation following Google's M3 Expressive guidelines:
 * - Shows progress for loads under 5 seconds
 * - Replaces indeterminate circular progress indicators
 * - Used in pull-to-refresh operations
 * - Smooth, fluid animation with emphasized easing
 * 
 * Based on: https://developer.android.com/design/ui/mobile/guides/components/loading-indicator
 */
class LoadingIndicator @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearProgressIndicator(context, attrs, defStyleAttr) {

    init {
        setupLoadingIndicator()
    }

    private fun setupLoadingIndicator() {
        // M3 Expressive styling
        setIndicatorColor(context.getColor(android.R.color.system_accent1_500))
        setBackgroundColor(context.getColor(android.R.color.system_neutral1_100))
        
        // Track thickness
        trackThickness = 4.dpToPx()
        
        // Animation settings
        indicatorDirectionLinear = IndicatorDirection.LTR
        setAnimationMode(ANIMATION_MODE_CONTINUOUS)
        
        // Interpolator - use emphasized decelerate for smooth start
        interpolator = ExpressiveMotion.Easing.EMPHASIZED_DECELERERATE
        
        // Hide initially
        isVisible = false
    }

    /**
     * Start loading animation
     * For indeterminate loading (duration unknown)
     */
    fun startLoading() {
        isVisible = true
        isIndeterminate = true
        
        // M3 Expressive: Smooth fade in
        alpha = 0f
        animate()
            .alpha(1f)
            .setDuration(200)
            .setInterpolator(ExpressiveMotion.Easing.EMPHASIZED_DECELERERATE)
            .start()
    }

    /**
     * Start loading with known progress
     * @param maxProgress Maximum progress value
     */
    fun startLoading(maxProgress: Int) {
        isVisible = true
        isIndeterminate = false
        max = maxProgress
        progress = 0
    }

    /**
     * Update progress
     * @param currentProgress Current progress value
     */
    fun updateProgress(currentProgress: Int) {
        if (isIndeterminate) return
        
        // Animate progress change with spring physics
        val progressAnimator = androidx.core.animation.doAnimateProperty(this, "progress") {
            setDuration(300)
            interpolator = ExpressiveMotion.Easing.EMPHASIZED
        }
        progressAnimator.start()
    }

    /**
     * Complete loading with success
     * Smooth fade out animation
     */
    fun completeLoading() {
        // If indeterminate, just fade out
        if (isIndeterminate) {
            fadeOut()
            return
        }

        // Animate to 100% then fade out
        progress = max
        postDelayed({
            fadeOut()
        }, 300)
    }

    /**
     * Complete loading with error
     * Shows error state then fades out
     */
    fun completeLoadingWithError() {
        // Change color to error
        setIndicatorColor(context.getColor(android.R.color.system_error_500))
        
        // Brief pause to show error state
        postDelayed({
            fadeOut()
            // Reset color
            postDelayed({
                setIndicatorColor(context.getColor(android.R.color.system_accent1_500))
            }, 200)
        }, 500)
    }

    /**
     * Hide loading indicator
     */
    fun hideLoading() {
        fadeOut()
    }

    private fun fadeOut() {
        animate()
            .alpha(0f)
            .setDuration(200)
            .setInterpolator(ExpressiveMotion.Easing.EMPHASIZED_ACCELERATE)
            .withEndAction {
                isVisible = false
                alpha = 1f
                progress = 0
            }
            .start()
    }

    /**
     * Set loading for pull-to-refresh
     * Specifically tuned for swipe refresh operations
     */
    fun startPullToRefresh() {
        isIndeterminate = true
        startLoading()
    }

    /**
     * Complete pull-to-refresh
     */
    fun completePullToRefresh() {
        completeLoading()
    }

    private fun Int.dpToPx(): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }

    companion object {
        private const val ANIMATION_MODE_CONTINUOUS = 0
    }
}
