// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils.appearance

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.view.ContextThemeWrapper
import io.github.muntashirakon.AppManager.logs.Log

object TypefaceUtil {
    private const val TAG = "TypefaceUtil"\nprivate val sOverriddenFonts = HashMap<String, Typeface?>()

    @JvmStatic
    fun replaceFontsWithSystem(context: Context) {
        val normalFont = getSystemFontFamily(context)
        if (normalFont == null) {
            Log.i(TAG, "No system font exists. Skip applying font overrides.")
            return
        }

        try {
            overrideFonts(normalFont)
        } catch (e: Exception) {
            Log.w(TAG, e)
        }
    }

    @SuppressLint("DiscouragedPrivateApi")
    @Suppress("UNCHECKED_CAST")
    @JvmStatic
    fun restoreFonts() {
        if (sOverriddenFonts.isEmpty()) {
            return
        }
        try {
            val field = Typeface::class.java.getDeclaredField("sSystemFontMap")
            field.isAccessible = true
            val allFontsForThisApp = field.get(null) as? Map<String, Typeface>
            if (allFontsForThisApp == null) {
                Log.i(TAG, "No fonts are set for this app. Weird!")
                return
            }
            val mutableFonts = allFontsForThisApp.toMutableMap()
            for ((key, value) in sOverriddenFonts) {
                if (value == null) {
                    // Delete this entry
                    mutableFonts.remove(key)
                } else {
                    // Replace
                    mutableFonts[key] = value
                }
            }
            field.set(null, mutableFonts)
            field.isAccessible = false
        } catch (e: Exception) {
            Log.w(TAG, e)
        }
    }

    private fun getSystemFontFamily(context: Context): String? {
        val themedContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextThemeWrapper(context, android.R.style.Theme_DeviceDefault_DayNight)
        } else {
            ContextThemeWrapper(context, android.R.style.Theme_DeviceDefault)
        }
        val ta = themedContext.obtainStyledAttributes(
            android.R.style.TextAppearance_DeviceDefault,
            intArrayOf(android.R.attr.fontFamily)
        )
        val value = ta.getString(0)
        ta.recycle()
        return value
    }

    @SuppressLint("DiscouragedPrivateApi")
    @Suppress("UNCHECKED_CAST")
    private fun overrideFonts(normalFont: String) {
        val field = Typeface::class.java.getDeclaredField("sSystemFontMap")
        field.isAccessible = true
        val allFontsForThisApp = field.get(null) as? Map<String, Typeface>
        if (allFontsForThisApp == null) {
            Log.i(TAG, "No fonts are set for this app. Weird!")
            return
        }
        val fontsMap = buildMap<String, Typeface> {
            // Fortunately for us, normalFont is always the basic font.
            // We can find other fonts by checking whether they starts with normalFont- and
            // append the substring to sans-serif
            val fontFamilies = allFontsForThisApp.keys.toList()
            for (fontFamily in fontFamilies) {
                if (fontFamily.startsWith(normalFont)) {
                    var typeface = allFontsForThisApp[fontFamily]
                    if (fontFamily == normalFont) {
                        typeface?.let { put("sans-serif", it) }
                    } else {
                        val suffix = fontFamily.substring(normalFont.length)
                        if (suffix.contains("-medium")) {
                            // For some reason, material themes use medium instead of bold for bold
                            // fonts. We need to check if a bold font exist for this font. If there
                            // is one, we'll use that font instead of the medium font.
                            val s = normalFont + suffix.replace("-medium", "-bold")
                            if (fontFamilies.contains(s)) {
                                typeface = allFontsForThisApp[s]
                            }
                        }
                        typeface?.let { put("sans-serif$suffix", it) }
                    }
                }
            }
        }
        // Store overridden fonts
        if (sOverriddenFonts.isEmpty()) {
            for (fontFamily in fontsMap.keys) {
                sOverriddenFonts[fontFamily] = allFontsForThisApp[fontFamily]
            }
        }
        val mutableFonts = allFontsForThisApp.toMutableMap()
        mutableFonts.putAll(fontsMap)
        field.set(null, mutableFonts)
        field.isAccessible = false
    }
}
