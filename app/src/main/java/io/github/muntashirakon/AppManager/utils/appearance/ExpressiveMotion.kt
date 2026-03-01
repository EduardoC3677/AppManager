// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils.appearance

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.os.Build
import android.view.View
import android.view.animation.PathInterpolator
import androidx.annotation.RequiresApi

/**
 * Material You 2026 Expressive Motion System
 *
 * Implements spring-based animations with expressive easing curves
 * following Material Design 3 Expressive guidelines for Android 16+
 *
 * Key features:
 * - Spring physics-based animations
 * - Expressive easing curves
 * - Purposeful motion with meaning
 * - Connected spatial transitions
 */
object ExpressiveMotion {
    
    /**
     * Expressive Easing Curves (Material 3 2026)
     */
    object Easing {
        /**
         * Standard easing - for most animations
         * cubic-bezier(0.2, 0.0, 0, 1.0)
         */
        val STANDARD = PathInterpolator(0.2f, 0.0f, 0f, 1.0f)
        
        /**
         * Emphasized easing - for important transitions
         * cubic-bezier(0.05, 0.7, 0.1, 1.0)
         */
        val EMPHASIZED = PathInterpolator(0.05f, 0.7f, 0.1f, 1.0f)
        
        /**
         * Emphasized Decelerate - for entering animations
         * cubic-bezier(0.05, 0.7, 0.1, 1.0)
         */
        val EMPHASIZED_DECELERERATE = PathInterpolator(0.05f, 0.7f, 0.1f, 1.0f)
        
        /**
         * Emphasized Accelerate - for exiting animations
         * cubic-bezier(0.3, 0.0, 0.8, 0.15)
         */
        val EMPHASIZED_ACCELERATE = PathInterpolator(0.3f, 0.0f, 0.8f, 0.15f)
        
        /**
         * Linear easing - for progress indicators
         */
        val LINEAR = PathInterpolator(0.0f, 0.0f, 1.0f, 1.0f)
        
        /**
         * Spring-like easing - for bouncy, playful animations
         * Custom curve that mimics spring physics
         */
        val SPRING = PathInterpolator(0.08f, 0.82f, 0.17f, 1.05f)
        
        /**
         * Overshoot easing - for playful overshoot effect
         */
        val OVERSHOOT = PathInterpolator(0.0f, 0.0f, 0.2f, 1.2f)
    }
    
    /**
     * Animation Durations (Material 3 2026 Guidelines)
     */
    object Duration {
        const val INSTANT = 50L      // Immediate feedback
        const val QUICK = 100L       // Small state changes
        const val STANDARD = 200L    // Most animations
        const val MODERATE = 300L    // Larger transitions
        const val SLOW = 400L        // Complex animations
        const val VERY_SLOW = 500L   // Full screen transitions
    }
    
    /**
     * Spring Physics Constants
     */
    object SpringPhysics {
        const val STIFFNESS_LOW = 0.2f
        const val STIFFNESS_MEDIUM = 0.5f
        const val STIFFNESS_HIGH = 0.8f
        
        const val DAMPING_LOW = 0.4f
        const val DAMPING_MEDIUM = 0.7f
        const val DAMPING_HIGH = 0.9f
    }
    
    /**
     * Animate view with spring physics
     * @param view The view to animate
     * @param scaleX Target scale X
     * @param scaleY Target scale Y
     * @param translationX Target translation X
     * @param translationY Target translation Y
     * @param duration Animation duration
     * @param springStiffness Spring stiffness (0.0-1.0)
     * @param springDamping Spring damping (0.0-1.0)
     */
    fun animateSpring(
        view: View,
        scaleX: Float = 1f,
        scaleY: Float = 1f,
        translationX: Float = 0f,
        translationY: Float = 0f,
        duration: Long = Duration.MODERATE,
        springStiffness: Float = SpringPhysics.STIFFNESS_MEDIUM,
        springDamping: Float = SpringPhysics.DAMPING_MEDIUM
    ) {
        val interpolator = createSpringInterpolator(springStiffness, springDamping)
        
        view.animate()
            .scaleX(scaleX)
            .scaleY(scaleY)
            .translationX(translationX)
            .translationY(translationY)
            .setDuration(duration)
            .setInterpolator(interpolator)
            .start()
    }
    
    /**
     * Create spring-like interpolator
     */
    fun createSpringInterpolator(
        stiffness: Float = SpringPhysics.STIFFNESS_MEDIUM,
        damping: Float = SpringPhysics.DAMPING_MEDIUM
    ): PathInterpolator {
        // Adjust control points based on spring physics
        val controlX1 = 0.05f + (1f - stiffness) * 0.1f
        val controlY1 = 0.7f + damping * 0.2f
        val controlX2 = 0.1f + (1f - stiffness) * 0.2f
        val controlY2 = 1.0f + (1f - damping) * 0.1f
        
        return PathInterpolator(controlX1, controlY1, controlX2, controlY2)
    }
    
    /**
     * Card press animation (Material 3 Expressive)
     * Scale down slightly on press, spring back on release
     */
    fun animateCardPress(view: View, isPressed: Boolean) {
        val targetScale = if (isPressed) 0.96f else 1f
        val duration = if (isPressed) Duration.QUICK else Duration.MODERATE
        
        view.animate()
            .scaleX(targetScale)
            .scaleY(targetScale)
            .setDuration(duration)
            .setInterpolator(if (isPressed) Easing.EMPHASIZED_ACCELERATE else Easing.EMPHASIZED_DECELERERATE)
            .start()
    }
    
    /**
     * Fade in animation with spring
     */
    fun animateFadeIn(
        view: View,
        duration: Long = Duration.MODERATE,
        delay: Long = 0
    ) {
        view.alpha = 0f
        view.animate()
            .alpha(1f)
            .setDuration(duration)
            .setStartDelay(delay)
            .setInterpolator(Easing.EMPHASIZED_DECELERERATE)
            .start()
    }
    
    /**
     * Fade out animation
     */
    fun animateFadeOut(
        view: View,
        duration: Long = Duration.MODERATE
    ) {
        view.animate()
            .alpha(0f)
            .setDuration(duration)
            .setInterpolator(Easing.EMPHASIZED_ACCELERATE)
            .start()
    }
    
    /**
     * Slide in from bottom with spring
     */
    fun animateSlideInBottom(
        view: View,
        duration: Long = Duration.MODERATE,
        delay: Long = 0
    ) {
        view.translationY = view.height.toFloat()
        view.alpha = 0f
        
        view.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(duration)
            .setStartDelay(delay)
            .setInterpolator(Easing.EMPHASIZED_DECELERERATE)
            .start()
    }
    
    /**
     * Slide out to bottom
     */
    fun animateSlideOutBottom(
        view: View,
        duration: Long = Duration.MODERATE
    ) {
        view.animate()
            .translationY(view.height.toFloat())
            .alpha(0f)
            .setDuration(duration)
            .setInterpolator(Easing.EMPHASIZED_ACCELERATE)
            .start()
    }
    
    /**
     * Scale enter animation (playful)
     */
    fun animateScaleEnter(
        view: View,
        duration: Long = Duration.MODERATE,
        delay: Long = 0
    ) {
        view.scaleX = 0.8f
        view.scaleY = 0.8f
        view.alpha = 0f
        
        view.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(duration)
            .setStartDelay(delay)
            .setInterpolator(Easing.SPRING)
            .start()
    }
    
    /**
     * Shared axis transition for related content
     */
    fun animateSharedAxisX(
        view: View,
        enter: Boolean,
        duration: Long = Duration.MODERATE
    ) {
        val startX = if (enter) -view.width.toFloat() * 0.2f else 0f
        val endX = if (enter) 0f else view.width.toFloat() * 0.2f
        
        view.translationX = startX
        view.alpha = if (enter) 0f else 1f
        
        view.animate()
            .translationX(endX)
            .alpha(if (enter) 1f else 0f)
            .setDuration(duration)
            .setInterpolator(if (enter) Easing.EMPHASIZED_DECELERERATE else Easing.EMPHASIZED_ACCELERATE)
            .start()
    }
    
    /**
     * Shared axis transition Y
     */
    fun animateSharedAxisY(
        view: View,
        enter: Boolean,
        duration: Long = Duration.MODERATE
    ) {
        val startY = if (enter) -view.height.toFloat() * 0.2f else 0f
        val endY = if (enter) 0f else view.height.toFloat() * 0.2f
        
        view.translationY = startY
        view.alpha = if (enter) 0f else 1f
        
        view.animate()
            .translationY(endY)
            .alpha(if (enter) 1f else 0f)
            .setDuration(duration)
            .setInterpolator(if (enter) Easing.EMPHASIZED_DECELERERATE else Easing.EMPHASIZED_ACCELERATE)
            .start()
    }
    
    /**
     * Staggered animation for list items
     */
    fun animateStaggeredList(
        views: List<View>,
        duration: Long = Duration.MODERATE,
        staggerDelay: Long = 30
    ) {
        views.forEachIndexed { index, view ->
            animateFadeIn(view, duration, staggerDelay * index)
        }
    }
    
    /**
     * Ripple scale animation for buttons
     */
    fun animateButtonRipple(view: View, isPressed: Boolean) {
        val targetScale = if (isPressed) 1.05f else 1f
        view.animate()
            .scaleX(targetScale)
            .scaleY(targetScale)
            .setDuration(if (isPressed) Duration.QUICK else Duration.STANDARD)
            .setInterpolator(if (isPressed) Easing.EMPHASIZED_ACCELERATE else Easing.EMPHASIZED_DECELERERATE)
            .start()
    }
    
    /**
     * FAB (Floating Action Button) spring animation
     */
    fun animateFABSpring(view: View, show: Boolean) {
        if (show) {
            view.scaleX = 0f
            view.scaleY = 0f
            view.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(Duration.MODERATE)
                .setInterpolator(Easing.SPRING)
                .start()
        } else {
            view.animate()
                .scaleX(0f)
                .scaleY(0f)
                .setDuration(Duration.STANDARD)
                .setInterpolator(Easing.EMPHASIZED_ACCELERATE)
                .start()
        }
    }
    
    /**
     * Dialog show animation with spring
     */
    fun animateDialogShow(view: View) {
        view.scaleX = 0.9f
        view.scaleY = 0.9f
        view.alpha = 0f
        
        view.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(Duration.MODERATE)
            .setInterpolator(Easing.EMPHASIZED_DECELERERATE)
            .start()
    }
    
    /**
     * Progress indicator spring animation
     */
    fun animateProgressSpring(progressBar: View, progress: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val animator = ValueAnimator.ofFloat(progressBar.progress / 100f, progress)
            animator.duration = Duration.MODERATE
            animator.interpolator = Easing.EMPHASIZED_DECELERERATE
            animator.addUpdateListener { animation ->
                progressBar.progress = (animation.animatedValue as Float * 100).toInt()
            }
            animator.start()
        }
    }
    
    /**
     * Container transform animation
     */
    fun animateContainerTransform(
        view: View,
        expanded: Boolean,
        duration: Long = Duration.SLOW
    ) {
        if (expanded) {
            animateSpring(
                view,
                scaleX = 1f,
                scaleY = 1f,
                duration = duration,
                springStiffness = SpringPhysics.STIFFNESS_LOW,
                springDamping = SpringPhysics.DAMPING_HIGH
            )
        } else {
            animateSpring(
                view,
                scaleX = 0.95f,
                scaleY = 0.95f,
                duration = duration,
                springStiffness = SpringPhysics.STIFFNESS_HIGH,
                springDamping = SpringPhysics.DAMPING_LOW
            )
        }
    }
    
    /**
     * Create property animator for complex animations
     */
    fun createPropertyAnimator(
        view: View,
        vararg properties: PropertyValuesHolder,
        duration: Long = Duration.MODERATE,
        interpolator: android.animation.TimeInterpolator = Easing.EMPHASIZED
    ): ObjectAnimator {
        return ObjectAnimator.ofPropertyValuesHolder(view, *properties).apply {
            this.duration = duration
            this.interpolator = interpolator
        }
    }
    
    /**
     * Add spring bounce effect to view
     */
    fun addSpringBounce(view: View) {
        view.animate()
            .scaleX(1.05f)
            .scaleY(1.05f)
            .setDuration(100)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(150)
                    .setInterpolator(Easing.SPRING)
                    .start()
            }
            .start()
    }
}
