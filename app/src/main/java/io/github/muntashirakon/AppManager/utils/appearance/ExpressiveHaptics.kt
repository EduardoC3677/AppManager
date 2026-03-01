// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils.appearance

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.PathInterpolator
import androidx.annotation.IntDef
import io.github.muntashirakon.AppManager.utils.ContextUtils

/**
 * Material You 2026 Expressive Haptic Feedback System
 *
 * Implements the latest Material Design 3 expressive haptic guidelines:
 * - Spring-based haptic timing
 * - Context-aware haptic strength
 * - Sentiment-based feedback patterns
 *
 * Based on Android 16 Material 3 Expressive design principles
 */
object ExpressiveHaptics {
    
    @IntDef(
        HAPTIC_LIGHT,
        HAPTIC_MEDIUM,
        HAPTIC_HEAVY,
        HAPTIC_TEXTURE,
        HAPTIC_SUCCESS,
        HAPTIC_ERROR,
        HAPTIC_WARNING
    )
    @Retention(AnnotationRetention.SOURCE)
    annotation class HapticType
    
    const val HAPTIC_LIGHT = 0      // Light touch interactions
    const val HAPTIC_MEDIUM = 1     // Standard interactions
    const val HAPTIC_HEAVY = 2      // Important/destructive actions
    const val HAPTIC_TEXTURE = 3    // Scrolling/texture feedback
    const val HAPTIC_SUCCESS = 4    // Positive confirmation
    const val HAPTIC_ERROR = 5      // Negative/error feedback
    const val HAPTIC_WARNING = 6    // Warning/caution feedback
    
    private var vibrator: Vibrator? = null
    
    /**
     * Initialize haptic system
     */
    fun initialize(context: Context = ContextUtils.getContext()) {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }
    
    /**
     * Perform haptic feedback based on type
     */
    fun performHapticFeedback(@HapticType type: Int) {
        when (type) {
            HAPTIC_LIGHT -> performLightHaptic()
            HAPTIC_MEDIUM -> performMediumHaptic()
            HAPTIC_HEAVY -> performHeavyHaptic()
            HAPTIC_TEXTURE -> performTextureHaptic()
            HAPTIC_SUCCESS -> performSuccessHaptic()
            HAPTIC_ERROR -> performErrorHaptic()
            HAPTIC_WARNING -> performWarningHaptic()
        }
    }
    
    /**
     * Light haptic - for subtle interactions (toggles, light buttons)
     * Uses EFFECT_TICK for minimal feedback
     */
    private fun performLightHaptic() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } else {
            vibrator?.vibrate(10)
        }
    }
    
    /**
     * Medium haptic - standard interactions (buttons, cards)
     * Uses EFFECT_CLICK as baseline
     */
    private fun performMediumHaptic() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        } else {
            vibrator?.vibrate(20)
        }
    }
    
    /**
     * Heavy haptic - important actions (destructive, confirmations)
     * Uses EFFECT_HEAVY_CLICK for maximum feedback
     */
    private fun performHeavyHaptic() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
        } else {
            vibrator?.vibrate(40)
        }
    }
    
    /**
     * Texture haptic - for scrolling through content
     * Creates subtle repeated feedback
     */
    private fun performTextureHaptic() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TEXT_HANDLE_MOVE))
        } else {
            vibrator?.vibrate(5)
        }
    }
    
    /**
     * Success haptic - positive confirmation
     * Pattern: Light double-click with ascending strength
     */
    private fun performSuccessHaptic() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Ascending pattern for positive feedback
            val pattern = longArrayOf(0, 10, 20, 15)
            val amplitudes = intArrayOf(0, 80, 120, 100)
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, amplitudes, -1))
        } else {
            vibrator?.vibrate(longArrayOf(0, 10, 20, 15), intArrayOf(0, 1, 2, 1), -1)
        }
    }
    
    /**
     * Error haptic - negative feedback
     * Pattern: Heavy double-click with descending strength
     */
    private fun performErrorHaptic() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Descending pattern for negative feedback
            val pattern = longArrayOf(0, 30, 40, 50, 30)
            val amplitudes = intArrayOf(0, 200, 150, 200, 100)
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, amplitudes, -1))
        } else {
            vibrator?.vibrate(longArrayOf(0, 30, 40, 50, 30), intArrayOf(0, 2, 1, 2, 1), -1)
        }
    }
    
    /**
     * Warning haptic - caution feedback
     * Pattern: Triple click with medium strength
     */
    private fun performWarningHaptic() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val pattern = longArrayOf(0, 20, 30, 20, 30, 20)
            val amplitudes = intArrayOf(0, 150, 100, 150, 100, 150)
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, amplitudes, -1))
        } else {
            vibrator?.vibrate(longArrayOf(0, 20, 30, 20, 30, 20), intArrayOf(0, 1, 1, 1, 1, 1), -1)
        }
    }
    
    /**
     * Spring-based haptic timing
     * Calculates haptic duration based on spring animation physics
     */
    fun calculateSpringHapticDuration(
        stiffness: Float = 0.5f,
        damping: Float = 0.7f
    ): Long {
        // Based on spring physics: higher stiffness = shorter, sharper haptic
        // Higher damping = smoother, longer haptic
        val baseDuration = 100L
        val stiffnessFactor = (1.0 - stiffness) * 50
        val dampingFactor = damping * 30
        return baseDuration + stiffnessFactor.toLong() + dampingFactor.toLong()
    }
    
    /**
     * Perform haptic with spring timing
     */
    fun performSpringHaptic(
        @HapticType type: Int,
        stiffness: Float = 0.5f,
        damping: Float = 0.7f
    ) {
        performHapticFeedback(type)
        // Additional subtle feedback based on spring physics
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val duration = calculateSpringHapticDuration(stiffness, damping)
            vibrator?.vibrate(VibrationEffect.createOneShot(duration, 100))
        }
    }
    
    /**
     * Button press haptic pattern (pairwise interaction)
     * Press: Stronger feedback, Release: Lighter feedback
     */
    fun performButtonPressHaptic(isPress: Boolean) {
        if (isPress) {
            performMediumHaptic()
        } else {
            performLightHaptic()
        }
    }
    
    /**
     * Card interaction haptic
     * For card selection, expansion, etc.
     */
    fun performCardHaptic() {
        performMediumHaptic()
    }
    
    /**
     * List item selection haptic
     * Light feedback for list interactions
     */
    fun performListItemHaptic() {
        performLightHaptic()
    }
    
    /**
     * Scrolling haptic with texture
     * For scrolling through lists with haptic feedback
     */
    fun performScrollHaptic(scrollDelta: Float) {
        if (Math.abs(scrollDelta) > 50) { // Threshold for haptic feedback
            performTextureHaptic()
        }
    }
    
    /**
     * Confirmation dialog haptic
     * Medium-heavy for important confirmations
     */
    fun performConfirmationHaptic() {
        performHapticFeedback(HAPTIC_MEDIUM)
    }
    
    /**
     * Destructive action haptic
     * Heavy feedback for delete, remove, etc.
     */
    fun performDestructiveHaptic() {
        performHapticFeedback(HAPTIC_HEAVY)
    }
    
    /**
     * Clean up resources
     */
    fun cancel() {
        vibrator?.cancel()
    }
}
