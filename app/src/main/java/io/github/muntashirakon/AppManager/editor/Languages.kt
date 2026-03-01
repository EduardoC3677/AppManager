// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.editor

import android.content.Context
import io.github.muntashirakon.AppManager.logs.Log
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import org.eclipse.tm4e.core.registry.IGrammarSource
import org.eclipse.tm4e.core.registry.IThemeSource
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

object Languages {
    @JvmStatic
    fun getLanguage(context: Context, language: String, themeSource: IThemeSource?): Language {
        return try {
            val grammarSource = IGrammarSource.fromInputStream(context.assets.open("languages/$language/tmLanguage.json"), "tmLanguage.json", StandardCharsets.UTF_8)
            val languageConfiguration = InputStreamReader(context.assets.open("languages/$language/language-configuration.json"))
            if (themeSource == null) {
                throw FileNotFoundException("Invalid theme source")
            }
            TextMateLanguage.create(grammarSource, languageConfiguration, themeSource)
        } catch (e: IOException) {
            Log.w("CodeEditor", "Could not load resources for language $language", e)
            EmptyLanguage()
        }
    }
}
