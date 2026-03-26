// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.util

import android.animation.TimeInterpolator
import android.annotation.SuppressLint
import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.core.graphics.PathParser
import androidx.core.view.animation.PathInterpolatorCompat
import com.google.android.material.resources.MaterialAttributes

/**
 * A utility class for motion system functions.
 */
// Copyright 2020 The Android Open Source Project
object MotionUtils {
    // Constants corresponding to motionEasing* theme attr values.
    private const val EASING_TYPE_CUBIC_BEZIER = "cubic-bezier"
    private const val EASING_TYPE_PATH = "path"
    private const val EASING_TYPE_FORMAT_START = "("
    private const val EASING_TYPE_FORMAT_END = ")"

    @JvmStatic
    @SuppressLint("RestrictedApi")
    fun resolveThemeDuration(
        context: Context, @AttrRes attrResId: Int, defaultDuration: Int
    ): Int {
        return MaterialAttributes.resolveInteger(context, attrResId, defaultDuration)
    }

    @JvmStatic
    fun resolveThemeInterpolator(
        context: Context,
        @AttrRes attrResId: Int,
        defaultInterpolator: TimeInterpolator
    ): TimeInterpolator {
        val easingValue = TypedValue()
        if (context.theme.resolveAttribute(attrResId, easingValue, true)) {
            if (easingValue.type != TypedValue.TYPE_STRING) {
                throw IllegalArgumentException("Motion easing theme attribute must be a string")
            }

            val easingString = easingValue.string.toString()

            if (isEasingType(easingString, EASING_TYPE_CUBIC_BEZIER)) {
                val controlPointsString = getEasingContent(easingString, EASING_TYPE_CUBIC_BEZIER)
                val controlPoints = controlPointsString.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                if (controlPoints.size != 4) {
                    throw IllegalArgumentException(
                        "Motion easing theme attribute must have 4 control points if using bezier curve"
                                + " format; instead got: "
                                + controlPoints.size
                    )
                }

                val controlX1 = getControlPoint(controlPoints, 0)
                val controlY1 = getControlPoint(controlPoints, 1)
                val controlX2 = getControlPoint(controlPoints, 2)
                val controlY2 = getControlPoint(controlPoints, 3)
                return PathInterpolatorCompat.create(controlX1, controlY1, controlX2, controlY2)
            } else if (isEasingType(easingString, EASING_TYPE_PATH)) {
                val path = getEasingContent(easingString, EASING_TYPE_PATH)
                return PathInterpolatorCompat.create(PathParser.createPathFromPathData(path))
            } else {
                throw IllegalArgumentException("Invalid motion easing type: $easingString")
            }
        }
        return defaultInterpolator
    }

    private fun isEasingType(easingString: String, easingType: String): Boolean {
        return easingString.startsWith(easingType + EASING_TYPE_FORMAT_START) &&
                easingString.endsWith(EASING_TYPE_FORMAT_END)
    }

    private fun getEasingContent(easingString: String, easingType: String): String {
        return easingString.substring(
            easingType.length + EASING_TYPE_FORMAT_START.length,
            easingString.length - EASING_TYPE_FORMAT_END.length
        )
    }

    private fun getControlPoint(controlPoints: Array<String>, index: Int): Float {
        val controlPoint = controlPoints[index].toFloat()
        if (controlPoint < 0 || controlPoint > 1) {
            throw IllegalArgumentException(
                "Motion easing control point value must be between 0 and 1; instead got: $controlPoint"
            )
        }
        return controlPoint
    }
}
