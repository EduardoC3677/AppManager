// SPDX-License-Identifier: GPL-3.0-or-later OR Apache-2.0

package io.github.muntashirakon.AppManager.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.os.Build
import androidx.core.os.ConfigurationCompat
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.settings.Prefs
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.util.IllformedLocaleException
import java.util.Locale

object LangUtils {
    const val LANG_AUTO = "auto"

    private var sLocaleMap: MutableMap<String, Locale?>? = null

    @SuppressLint("AppBundleLocaleChanges") // We don't use Play Store
    private fun loadAppLanguages(context: Context) {
        if (sLocaleMap == null) sLocaleMap = LinkedHashMap()
        val res = context.resources
        val conf = res.configuration

        sLocaleMap!![LANG_AUTO] = null
        for (locale in parseLocalesConfig(context)) {
            conf.setLocale(Locale.forLanguageTag(locale))
            sLocaleMap!![locale] = ConfigurationCompat.getLocales(conf)[0]
        }
    }

    @JvmStatic
    fun getAppLanguages(context: Context): Map<String, Locale?> {
        if (sLocaleMap == null) loadAppLanguages(context)
        return sLocaleMap!!
    }

    @JvmStatic
    fun getFromPreference(context: Context): Locale {
        val language = Prefs.Appearance.getLanguage(context)
        val locale = getAppLanguages(context)[language]
        if (locale != null) {
            return locale
        }
        // Load from system configuration
        val conf = Resources.getSystem().configuration
        @Suppress("DEPRECATION")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            conf.locales[0]
        } else {
            conf.locale
        }
    }

    @JvmStatic
    fun isValidLocale(languageTag: String): Boolean {
        try {
            val locale = Locale.forLanguageTag(languageTag)
            for (validLocale in Locale.getAvailableLocales()) {
                if (validLocale == locale) {
                    return true
                }
            }
        } catch (ignore: IllformedLocaleException) {
        }
        return false
    }

    @JvmStatic
    fun getSeparatorString(): String {
        return if (Locale.getDefault().language == Locale("fr").language) {
            " : "
        } else {
            ": "
        }
    }

    @JvmStatic
    fun parseLocalesConfig(context: Context): List<String> {
        val localeNames = ArrayList<String>()

        context.resources.getXml(R.xml.locales_config).use { parser ->
            try {
                var eventType = parser.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG && "locale" == parser.name) {
                        val localeName = parser.getAttributeValue("http://schemas.android.com/apk/res/android", "name")
                        if (localeName != null) {
                            localeNames.add(localeName)
                        }
                    }
                    eventType = parser.next()
                }
            } catch (ignore: XmlPullParserException) {
            } catch (ignore: IOException) {
            }
        }
        return localeNames
    }
}
