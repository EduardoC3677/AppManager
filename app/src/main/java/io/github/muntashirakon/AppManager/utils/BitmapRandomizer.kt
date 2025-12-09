// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils

import android.graphics.Bitmap
import android.graphics.Color
import io.github.muntashirakon.AppManager.crypto.ks.CompatUtil
import kotlin.math.max
import kotlin.math.min

object BitmapRandomizer {
    @JvmStatic
    fun randomizePixel(bitmap: Bitmap) {
        if (!bitmap.isMutable) {
            throw IllegalArgumentException("Bitmap must be mutable")
        }

        val width = bitmap.width
        val height = bitmap.height

        // Randomly select a pixel location
        val x = CompatUtil.getPrng().nextInt(width)
        val y = CompatUtil.getPrng().nextInt(height)

        // Get the original pixel color
        val originalColor = bitmap.getPixel(x, y)

        // Extract ARGB components
        val alpha = Color.alpha(originalColor)
        val red = Color.red(originalColor)
        val green = Color.green(originalColor)
        val blue = Color.blue(originalColor)

        // Get neighboring pixels for blending
        val neighborColors = getNeighborColors(bitmap, x, y)

        // Calculate average of neighbors for blending
        var avgRed = 0
        var avgGreen = 0
        var avgBlue = 0
        for (neighborColor in neighborColors) {
            avgRed += Color.red(neighborColor)
            avgGreen += Color.green(neighborColor)
            avgBlue += Color.blue(neighborColor)
        }
        avgRed /= neighborColors.size
        avgGreen /= neighborColors.size
        avgBlue /= neighborColors.size

        // Modify at least one MSB (Most Significant Bit) while blending
        var newRed = modifyMsbWithBlending(red, avgRed)
        val newGreen = modifyMsbWithBlending(green, avgGreen)
        val newBlue = modifyMsbWithBlending(blue, avgBlue)

        // Ensure the new color is different from original
        var newColor = Color.argb(alpha, newRed, newGreen, newBlue)
        if (newColor == originalColor) {
            // Force a change by flipping the MSB of red channel
            newRed = red xor 0x80 // Flip bit 7 (MSB)
            newColor = Color.argb(alpha, newRed, newGreen, newBlue)
        }

        // Set the modified pixel
        bitmap.setPixel(x, y, newColor)
    }

    private fun getNeighborColors(bitmap: Bitmap, x: Int, y: Int): IntArray {
        val width = bitmap.width
        val height = bitmap.height

        // Get up to 8 neighboring pixels
        val neighbors = IntArray(8)
        var count = 0

        for (dx in -1..1) {
            for (dy in -1..1) {
                if (dx == 0 && dy == 0) continue // Skip center pixel

                val nx = x + dx
                val ny = y + dy

                if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                    neighbors[count++] = bitmap.getPixel(nx, ny)
                }
            }
        }

        // Return only valid neighbors
        val result = IntArray(count)
        System.arraycopy(neighbors, 0, result, 0, count)
        return result
    }

    private fun modifyMsbWithBlending(originalValue: Int, avgNeighborValue: Int): Int {
        // Blend original with neighbor average (50% blend)
        val blended = (originalValue + avgNeighborValue) / 2

        // Ensure MSB modification by flipping a random bit in upper half (bits 4-7)
        val bitToFlip = 4 + CompatUtil.getPrng().nextInt(4) // Random bit from 4 to 7
        val modified = blended xor (1 shl bitToFlip)

        // Clamp to valid range [0, 255]
        return max(0, min(255, modified))
    }
}
