// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.editor

import android.content.Context
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import com.google.android.material.color.MaterialColors
import com.google.android.material.elevation.SurfaceColors
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.util.UiUtils
import io.github.rosemoe.sora.lang.styling.color.EditorColor
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import org.eclipse.tm4e.core.registry.IThemeSource

object EditorThemes {
    val TAG: String = EditorThemes::class.java.simpleName

    @JvmStatic
    fun getColorScheme(context: Context): EditorColorScheme {
        return if (UiUtils.isDarkMode(context)) getDarkScheme(context) else getLightScheme(context)
    }

    private fun getLightScheme(context: Context): EditorColorScheme {
        return try {
            TextMateColorSchemeFixed(IThemeSource.fromInputStream(context.assets.open("editor_themes/light.tmTheme"), "light.tmTheme", null))
        } catch (e: Exception) {
            Log.e(TAG, "Could not create light scheme for TM language", e)
            LightScheme().apply { fixColor(context, this) }
        }
    }

    private fun getDarkScheme(context: Context): EditorColorScheme {
        return try {
            TextMateColorSchemeFixed(IThemeSource.fromInputStream(context.assets.open("editor_themes/dark.tmTheme.json"), "dark.tmTheme.json", null))
        } catch (e: Exception) {
            Log.e(TAG, "Could not create dark scheme for TM language", e)
            DarkScheme().apply { fixColor(context, this) }
        }
    }

    private fun fixColor(context: Context, scheme: EditorColorScheme) {
        scheme.setColor(EditorColorScheme.WHOLE_BACKGROUND, SurfaceColors.SURFACE_0.getColor(context))
        scheme.setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, SurfaceColors.SURFACE_2.getColor(context))
        scheme.setColor(EditorColorScheme.COMPLETION_WND_BACKGROUND, SurfaceColors.SURFACE_1.getColor(context))
        scheme.setColor(EditorColorScheme.HIGHLIGHTED_DELIMITERS_FOREGROUND, Color.RED)
        val thumbColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorControlActivated, EditorColor::class.java.simpleName)
        scheme.setColor(EditorColorScheme.SCROLL_BAR_THUMB, thumbColor)
        scheme.setColor(EditorColorScheme.SCROLL_BAR_THUMB_PRESSED, thumbColor)
        val trackColor = ColorUtils.setAlphaComponent(MaterialColors.getColor(context, com.google.android.material.R.attr.colorControlNormal, EditorColor::class.java.simpleName), 0x39)
        scheme.setColor(EditorColorScheme.SCROLL_BAR_TRACK, trackColor)
    }

    private class TextMateColorSchemeFixed(themeSource: IThemeSource) : TextMateColorScheme(ThemeRegistry.getInstance(), ThemeModel(themeSource)) {
        override fun attachEditor(editor: CodeEditor) {
            super.attachEditor(editor)
            fixColor(editor.context, this)
        }
    }

    private class LightScheme : EditorColorScheme() {
        override fun applyDefault() {
            super.applyDefault()
            setColor(ANNOTATION, -0x9b9b9c)
            setColor(FUNCTION_NAME, -0x1000000)
            setColor(IDENTIFIER_NAME, -0x1000000)
            setColor(IDENTIFIER_VAR, -0x479cc2)
            setColor(LITERAL, -0xd5ff01)
            setColor(OPERATOR, -0xc60000)
            setColor(COMMENT, -0xc080a1)
            setColor(KEYWORD, -0x80ff8c)
            setColor(WHOLE_BACKGROUND, -0x1)
            setColor(TEXT_NORMAL, -0x1000000)
            setColor(LINE_NUMBER_BACKGROUND, -0x1)
            setColor(LINE_NUMBER, -0x878788)
            setColor(SELECTED_TEXT_BACKGROUND, -0xcc6601)
            setColor(MATCHED_TEXT_BACKGROUND, -0x2b2b2c)
            setColor(CURRENT_LINE, -0x170d02)
            setColor(SELECTION_INSERT, -0xfc1415)
            setColor(SELECTION_HANDLE, -0xfc1415)
            setColor(BLOCK_LINE, -0x272728)
            setColor(BLOCK_LINE_CURRENT, 0)
            setColor(TEXT_SELECTED, -0x1)
        }
    }

    private class DarkScheme : EditorColorScheme() {
        override fun applyDefault() {
            super.applyDefault()
            setColor(ANNOTATION, -0x444ad7)
            setColor(FUNCTION_NAME, -0x332f27)
            setColor(IDENTIFIER_NAME, -0x332f27)
            setColor(IDENTIFIER_VAR, -0x678956)
            setColor(LITERAL, -0x9578a7)
            setColor(OPERATOR, -0x332f27)
            setColor(COMMENT, -0x7f7f80)
            setColor(KEYWORD, -0x3387ce)
            setColor(WHOLE_BACKGROUND, -0xd4d4d5)
            setColor(TEXT_NORMAL, -0x332f27)
            setColor(LINE_NUMBER_BACKGROUND, -0xcecccb)
            setColor(LINE_NUMBER, -0x9f9c9a)
            setColor(LINE_DIVIDER, -0x9f9c9a)
            setColor(SCROLL_BAR_THUMB, -0x59595a)
            setColor(SCROLL_BAR_THUMB_PRESSED, -0xa9a9aa)
            setColor(SELECTED_TEXT_BACKGROUND, -0xc98948)
            setColor(MATCHED_TEXT_BACKGROUND, -0xcda6c3)
            setColor(CURRENT_LINE, -0xcdcdce)
            setColor(SELECTION_INSERT, -0x332f27)
            setColor(SELECTION_HANDLE, -0x332f27)
            setColor(BLOCK_LINE, -0xa8a8a9)
            setColor(BLOCK_LINE_CURRENT, -0x22a8a8a9)
            setColor(NON_PRINTABLE_CHAR, -0x222223)
            setColor(TEXT_SELECTED, -0x332f27)
        }
    }
}
