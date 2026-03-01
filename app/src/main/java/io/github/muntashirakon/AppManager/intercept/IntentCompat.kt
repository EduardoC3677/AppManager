// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.intercept

import android.content.ComponentName
import android.content.Intent
import android.content.IntentHidden
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.text.TextUtils
import androidx.core.os.BundleCompat
import androidx.core.util.Pair
import dev.rikka.tools.refine.Refine
import io.github.muntashirakon.AppManager.compat.IntegerCompat
import io.github.muntashirakon.AppManager.compat.UriCompat
import io.github.muntashirakon.AppManager.fm.FmUtils
import io.github.muntashirakon.AppManager.utils.ExUtils
import java.util.*

object IntentCompat {
    @JvmStatic
    fun putWrappedParcelableExtra(intent: Intent, name: String?, parcelable: Parcelable?) {
        val bundle = Bundle()
        bundle.putParcelable(name, parcelable)
        intent.putExtra(name, bundle)
    }

    @JvmStatic
    fun <T : Parcelable> getUnwrappedParcelableExtra(intent: Intent, name: String?, clazz: Class<T>): T? {
        val bundle = intent.getBundleExtra(name) ?: return null
        return BundleCompat.getParcelable(bundle, name, clazz)
    }

    @JvmStatic
    fun <T : Parcelable> getParcelableExtra(intent: Intent, name: String?, clazz: Class<T>): T? {
        return androidx.core.content.IntentCompat.getParcelableExtra(intent, name, clazz)
    }

    @JvmStatic
    fun <T : Parcelable> getParcelableArrayListExtra(intent: Intent, name: String?, clazz: Class<out T>): ArrayList<T>? {
        return androidx.core.content.IntentCompat.getParcelableArrayListExtra(intent, name, clazz)
    }

    @JvmStatic
    fun getDataUri(intent: Intent?): Uri? {
        if (intent == null) return null
        return if (Intent.ACTION_SEND == intent.action) {
            FmUtils.sanitizeContentInput(getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
        } else {
            FmUtils.sanitizeContentInput(intent.data)
        }
    }

    @JvmStatic
    fun getDataUris(intent: Intent): List<Uri>? {
        if (Intent.ACTION_SEND_MULTIPLE == intent.action) {
            val inputUris = getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) ?: return null
            val filteredUris = inputUris.mapNotNull { FmUtils.sanitizeContentInput(it) }
            return filteredUris.ifEmpty { null }
        }
        return getDataUri(intent)?.let { listOf(it) }
    }

    @JvmStatic
    fun removeFlags(intent: Intent, flags: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.removeFlags(flags)
        } else {
            intent.flags = intent.flags and flags.inv()
        }
    }

    @JvmStatic
    fun parseExtraValue(@AddIntentExtraFragment.Type type: Int, rawValue: String): Any? {
        return when (type) {
            AddIntentExtraFragment.TYPE_STRING -> rawValue
            AddIntentExtraFragment.TYPE_NULL -> null
            AddIntentExtraFragment.TYPE_INTEGER -> IntegerCompat.decode(rawValue)
            AddIntentExtraFragment.TYPE_URI -> Uri.parse(rawValue)
            AddIntentExtraFragment.TYPE_URI_ARR -> {
                val strings = rawValue.split("(?<!\),".toRegex()).toTypedArray()
                Array(strings.size) { i -> Uri.parse(strings[i]) }
            }
            AddIntentExtraFragment.TYPE_URI_AL -> {
                val strings = rawValue.split("(?<!\),".toRegex()).toTypedArray()
                strings.map { Uri.parse(it) }.toMutableList()
            }
            AddIntentExtraFragment.TYPE_COMPONENT_NAME -> ComponentName.unflattenFromString(rawValue) ?: throw IllegalArgumentException("Bad component name: $rawValue")
            AddIntentExtraFragment.TYPE_INT_ARR -> {
                val strings = rawValue.split(",").toTypedArray()
                IntArray(strings.size) { i -> IntegerCompat.decode(strings[i].trim()) }
            }
            AddIntentExtraFragment.TYPE_INT_AL -> {
                val strings = rawValue.split(",").toTypedArray()
                strings.map { IntegerCompat.decode(it.trim()) }.toMutableList()
            }
            AddIntentExtraFragment.TYPE_LONG -> rawValue.toLong()
            AddIntentExtraFragment.TYPE_LONG_ARR -> {
                val strings = rawValue.split(",").toTypedArray()
                LongArray(strings.size) { i -> java.lang.Long.decode(strings[i].trim()) }
            }
            AddIntentExtraFragment.TYPE_LONG_AL -> {
                val strings = rawValue.split(",").toTypedArray()
                strings.map { java.lang.Long.decode(it.trim()) }.toMutableList()
            }
            AddIntentExtraFragment.TYPE_FLOAT -> rawValue.toFloat()
            AddIntentExtraFragment.TYPE_FLOAT_ARR -> {
                val strings = rawValue.split(",").toTypedArray()
                FloatArray(strings.size) { i -> strings[i].toFloat() }
            }
            AddIntentExtraFragment.TYPE_FLOAT_AL -> {
                val strings = rawValue.split(",").toTypedArray()
                strings.map { it.toFloat() }.toMutableList()
            }
            AddIntentExtraFragment.TYPE_STRING_ARR -> rawValue.split("(?<!\),".toRegex()).toTypedArray()
            AddIntentExtraFragment.TYPE_STRING_AL -> {
                val strings = rawValue.split("(?<!\),".toRegex()).toTypedArray()
                mutableListOf(*strings)
            }
            AddIntentExtraFragment.TYPE_BOOLEAN -> {
                when (rawValue) {
                    "true", "t" -> true
                    "false", "f" -> false
                    else -> try { IntegerCompat.decode(rawValue) != 0 } catch (e: NumberFormatException) { throw IllegalArgumentException("Invalid boolean value: $rawValue") }
                }
            }
            else -> throw IllegalArgumentException("Unknown type: $type")
        }
    }

    private fun valueToParsableStringAndType(obj: Any?): Pair<Int, String?>? {
        return when (obj) {
            null -> Pair(AddIntentExtraFragment.TYPE_NULL, null)
            is String -> Pair(AddIntentExtraFragment.TYPE_STRING, obj)
            is Int -> Pair(AddIntentExtraFragment.TYPE_INTEGER, obj.toString())
            is Long -> Pair(AddIntentExtraFragment.TYPE_LONG, obj.toString())
            is Float -> Pair(AddIntentExtraFragment.TYPE_FLOAT, obj.toString())
            is Boolean -> Pair(AddIntentExtraFragment.TYPE_BOOLEAN, obj.toString())
            is Uri -> Pair(AddIntentExtraFragment.TYPE_URI, obj.toString())
            is ComponentName -> Pair(AddIntentExtraFragment.TYPE_COMPONENT_NAME, obj.flattenToString())
            is IntArray -> Pair(AddIntentExtraFragment.TYPE_INT_ARR, obj.joinToString(","))
            is LongArray -> Pair(AddIntentExtraFragment.TYPE_LONG_ARR, obj.joinToString(","))
            is FloatArray -> Pair(AddIntentExtraFragment.TYPE_FLOAT_ARR, obj.joinToString(","))
            is Array<*> -> {
                if (obj.isEmpty()) return Pair(AddIntentExtraFragment.TYPE_NULL, null)
                val first = obj[0]
                when (first) {
                    is String -> Pair(AddIntentExtraFragment.TYPE_STRING_ARR, obj.joinToString(",") { it.toString().replace(",", "\,") })
                    is Uri -> Pair(AddIntentExtraFragment.TYPE_URI_ARR, obj.joinToString(",") { it.toString().replace(",", "\,") })
                    else -> null
                }
            }
            is List<*> -> {
                if (obj.isEmpty()) return Pair(AddIntentExtraFragment.TYPE_NULL, null)
                val first = obj[0]
                when (first) {
                    is Int -> Pair(AddIntentExtraFragment.TYPE_INT_AL, obj.joinToString(","))
                    is Long -> Pair(AddIntentExtraFragment.TYPE_LONG_AL, obj.joinToString(","))
                    is Float -> Pair(AddIntentExtraFragment.TYPE_FLOAT_AL, obj.joinToString(","))
                    is String -> Pair(AddIntentExtraFragment.TYPE_STRING_AL, obj.joinToString(",") { it.toString().replace(",", "\,") })
                    is Uri -> Pair(AddIntentExtraFragment.TYPE_URI_AL, obj.joinToString(",") { it.toString().replace(",", "\,") })
                    else -> null
                }
            }
            else -> null
        }
    }

    @JvmStatic
    fun addToIntent(intent: Intent, extraItem: AddIntentExtraFragment.ExtraItem) {
        if (extraItem.keyValue == null && extraItem.type != AddIntentExtraFragment.TYPE_NULL) return
        when (extraItem.type) {
            AddIntentExtraFragment.TYPE_BOOLEAN -> intent.putExtra(extraItem.keyName, extraItem.keyValue as Boolean)
            AddIntentExtraFragment.TYPE_FLOAT -> intent.putExtra(extraItem.keyName, extraItem.keyValue as Float)
            AddIntentExtraFragment.TYPE_FLOAT_AL, AddIntentExtraFragment.TYPE_STRING_AL, AddIntentExtraFragment.TYPE_LONG_AL, AddIntentExtraFragment.TYPE_INT_AL, AddIntentExtraFragment.TYPE_URI_AL -> intent.putExtra(extraItem.keyName, extraItem.keyValue as ArrayList<*>)
            AddIntentExtraFragment.TYPE_FLOAT_ARR -> intent.putExtra(extraItem.keyName, extraItem.keyValue as FloatArray)
            AddIntentExtraFragment.TYPE_INTEGER -> intent.putExtra(extraItem.keyName, extraItem.keyValue as Int)
            AddIntentExtraFragment.TYPE_INT_ARR -> intent.putExtra(extraItem.keyName, extraItem.keyValue as IntArray)
            AddIntentExtraFragment.TYPE_LONG -> intent.putExtra(extraItem.keyName, extraItem.keyValue as Long)
            AddIntentExtraFragment.TYPE_LONG_ARR -> intent.putExtra(extraItem.keyName, extraItem.keyValue as LongArray)
            AddIntentExtraFragment.TYPE_NULL -> intent.putExtra(extraItem.keyName, null as String?)
            AddIntentExtraFragment.TYPE_STRING -> intent.putExtra(extraItem.keyName, extraItem.keyValue as String)
            AddIntentExtraFragment.TYPE_STRING_ARR -> intent.putExtra(extraItem.keyName, extraItem.keyValue as Array<String>)
            AddIntentExtraFragment.TYPE_COMPONENT_NAME, AddIntentExtraFragment.TYPE_URI -> intent.putExtra(extraItem.keyName, extraItem.keyValue as Parcelable)
            AddIntentExtraFragment.TYPE_URI_ARR -> intent.putExtra(extraItem.keyName, extraItem.keyValue as Array<Parcelable>)
        }
    }

    @JvmStatic
    fun flattenToCommand(intent: Intent): List<String> {
        val args = mutableListOf<String>()
        intent.action?.let { args.add("-a"); args.add(it) }
        intent.dataString?.let { args.add("-d"); args.add(it) }
        intent.type?.let { args.add("-t"); args.add(it) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            intent.identifier?.let { args.add("-i"); args.add(it) }
        }
        intent.categories?.forEach { args.add("-c"); args.add(it) }
        intent.component?.let { args.add("-n"); args.add(it.flattenToString()) }
        intent.extras?.let { extras ->
            for (key in extras.keySet()) {
                val pair = valueToParsableStringAndType(extras.get(key)) ?: continue
                val prefix = when (pair.first) {
                    AddIntentExtraFragment.TYPE_STRING -> "--es"
                    AddIntentExtraFragment.TYPE_NULL -> "--esn"
                    AddIntentExtraFragment.TYPE_BOOLEAN -> "--ez"
                    AddIntentExtraFragment.TYPE_INTEGER -> "--ei"
                    AddIntentExtraFragment.TYPE_LONG -> "--el"
                    AddIntentExtraFragment.TYPE_FLOAT -> "--ef"
                    AddIntentExtraFragment.TYPE_URI -> "--eu"
                    AddIntentExtraFragment.TYPE_COMPONENT_NAME -> "--ecn"
                    AddIntentExtraFragment.TYPE_INT_ARR -> "--eia"
                    AddIntentExtraFragment.TYPE_INT_AL -> "--eial"
                    AddIntentExtraFragment.TYPE_LONG_ARR -> "--ela"
                    AddIntentExtraFragment.TYPE_LONG_AL -> "--elal"
                    AddIntentExtraFragment.TYPE_FLOAT_ARR -> "--efa"
                    AddIntentExtraFragment.TYPE_FLOAT_AL -> "--efal"
                    AddIntentExtraFragment.TYPE_STRING_ARR -> "--esa"
                    AddIntentExtraFragment.TYPE_STRING_AL -> "--esal"
                    else -> continue
                }
                args.add(prefix)
                args.add(key)
                if (pair.first != AddIntentExtraFragment.TYPE_NULL) args.add(pair.second!!)
            }
        }
        args.add("-f")
        args.add(intent.flags.toString())
        intent.`package`?.let { args.add(it) }
        return args
    }

    @JvmStatic
    fun flattenToString(intent: Intent): String {
        val sb = StringBuilder("VERSION	1
")
        intent.action?.let { sb.append("ACTION	$it
") }
        intent.dataString?.let { sb.append("DATA	$it
") }
        intent.type?.let { sb.append("TYPE	$it
") }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            intent.identifier?.let { sb.append("IDENTIFIER	$it
") }
        }
        intent.categories?.forEach { sb.append("CATEGORY	$it
") }
        intent.component?.let { sb.append("COMPONENT	${it.flattenToString()}
") }
        intent.`package`?.let { sb.append("PACKAGE	$it
") }
        if (intent.flags != 0) sb.append("FLAGS	0x${Integer.toHexString(intent.flags)}
")
        intent.extras?.let { extras ->
            for (key in extras.keySet()) {
                val pair = valueToParsableStringAndType(extras.get(key)) ?: continue
                sb.append("EXTRA	$key	${pair.first}")
                if (pair.first != AddIntentExtraFragment.TYPE_NULL) sb.append("	${pair.second}")
                sb.append("
")
            }
        }
        return sb.toString()
    }

    @JvmStatic
    fun describeIntent(intent: Intent, prefix: String): String {
        val sb = StringBuilder()
        intent.action?.let { sb.append("$prefix ACTION	$it
") }
        intent.dataString?.let { sb.append("$prefix DATA	$it
") }
        intent.type?.let { sb.append("$prefix TYPE	$it
") }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            intent.identifier?.let { sb.append("$prefix IDENTIFIER	$it
") }
        }
        intent.categories?.forEach { sb.append("$prefix CATEGORY	$it
") }
        intent.component?.let { sb.append("$prefix COMPONENT	${it.flattenToString()}
") }
        intent.`package`?.let { sb.append("$prefix PACKAGE	$it
") }
        if (intent.flags != 0) sb.append("$prefix FLAGS	0x${Integer.toHexString(intent.flags)}
")
        intent.extras?.let { extras ->
            for (key in extras.keySet()) {
                val pair = valueToParsableStringAndType(extras.get(key)) ?: continue
                sb.append("$prefix EXTRA	$key	${pair.first}")
                if (pair.first != AddIntentExtraFragment.TYPE_NULL) sb.append("	${pair.second}")
                sb.append("
")
            }
        }
        return sb.toString()
    }

    @JvmStatic
    fun unflattenFromString(intentString: String): Intent? {
        val intent = Intent()
        val lines = intentString.split("
")
        var data: Uri? = null
        var type: String? = null
        for (line in lines) {
            if (line.isEmpty()) continue
            val tokenizer = StringTokenizer(line, "	")
            if (tokenizer.countTokens() < 2) return null
            when (tokenizer.nextToken()) {
                "VERSION" -> if (IntegerCompat.decode(tokenizer.nextToken()) != 1) return null
                "ACTION" -> intent.action = tokenizer.nextToken()
                "DATA" -> data = Uri.parse(tokenizer.nextToken())
                "TYPE" -> type = tokenizer.nextToken()
                "IDENTIFIER" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) intent.identifier = tokenizer.nextToken()
                "CATEGORY" -> intent.addCategory(tokenizer.nextToken())
                "COMPONENT" -> intent.component = ComponentName.unflattenFromString(tokenizer.nextToken())
                "PACKAGE" -> intent.`package` = tokenizer.nextToken()
                "FLAGS" -> intent.flags = IntegerCompat.decode(tokenizer.nextToken())
                "EXTRA" -> {
                    val item = AddIntentExtraFragment.ExtraItem().apply {
                        keyName = tokenizer.nextToken()
                        this.type = IntegerCompat.decode(tokenizer.nextToken())
                        keyValue = parseExtraValue(this.type, tokenizer.nextToken())
                    }
                    addToIntent(intent, item)
                }
            }
        }
        if (data != null) intent.setDataAndType(data, type) else if (type != null) intent.type = type
        return intent
    }

    @JvmStatic
    fun getExtendedFlags(intent: Intent): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ExUtils.requireNonNullElse({ Refine.unsafeCast<IntentHidden>(intent).extendedFlags }, 0)
        } else 0
    }

    @JvmStatic
    fun toUri(intent: Intent, flags: Int): String {
        val flagsLong = intent.flags.toLong() and 0xFFFFFFFFL
        if (flagsLong < 0x80000000L || Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            return intent.toUri(flags)
        }
        val uri = StringBuilder(128)
        if (flags and Intent.URI_ANDROID_APP_SCHEME != 0) {
            if (intent.`package` == null) throw IllegalArgumentException("Intent must include an explicit package name to build an android-app: $intent")
            uri.append("android-app://").append(Uri.encode(intent.`package`))
            var scheme: String? = null
            intent.data?.let { data ->
                scheme = UriCompat.encodeIfNotEncoded(data.scheme, null)
                if (scheme != null) {
                    uri.append('/').append(scheme)
                    UriCompat.encodeIfNotEncoded(data.encodedAuthority, null)?.let { auth ->
                        uri.append('/').append(auth)
                        UriCompat.encodeIfNotEncoded(data.encodedPath, "/")?.let { uri.append(it) }
                        UriCompat.encodeIfNotEncoded(data.encodedQuery, null)?.let { uri.append('?').append(it) }
                        UriCompat.encodeIfNotEncoded(data.encodedFragment, null)?.let { uri.append('#').append(it) }
                    }
                }
            }
            toUriFragment(intent, uri, null, if (scheme == null) Intent.ACTION_MAIN else Intent.ACTION_VIEW, intent.`package`, flags)
            return uri.toString()
        }
        var scheme: String? = null
        intent.data?.let { dataUri ->
            var data = dataUri.toString()
            if (flags and Intent.URI_INTENT_SCHEME != 0) {
                for (i in data.indices) {
                    val c = data[i]
                    if (c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '.' || c == '-' || c == '+') continue
                    if (c == ':' && i > 0) {
                        scheme = data.substring(0, i)
                        uri.append("intent:")
                        data = data.substring(i + 1)
                        break
                    }
                    break
                }
            }
            uri.append(data)
        } ?: if (flags and Intent.URI_INTENT_SCHEME != 0) uri.append("intent:")
        toUriFragment(intent, uri, scheme, Intent.ACTION_VIEW, null, flags)
        return uri.toString()
    }

    private fun toUriFragment(intent: Intent, uri: StringBuilder, scheme: String?, defAction: String, defPackage: String?, flags: Int) {
        val frag = StringBuilder(128)
        toUriInner(intent, frag, scheme, defAction, defPackage, flags)
        intent.selector?.let { sel ->
            frag.append("SEL;")
            toUriInner(sel, frag, sel.data?.scheme, null, null, flags)
        }
        if (frag.isNotEmpty()) {
            uri.append("#Intent;").append(frag).append("end")
        }
    }

    private fun toUriInner(intent: Intent, uri: StringBuilder, scheme: String?, defAction: String?, defPackage: String?, flags: Int) {
        scheme?.let { uri.append("scheme=").append(Uri.encode(it)).append(';') }
        val action = intent.action
        if (action != null && action != defAction) uri.append("action=").append(Uri.encode(action)).append(';')
        intent.categories?.forEach { uri.append("category=").append(Uri.encode(it)).append(';') }
        intent.type?.let { uri.append("type=").append(Uri.encode(it, "/")).append(';') }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            intent.identifier?.let { uri.append("identifier=").append(Uri.encode(it, "/")).append(';') }
        }
        if (intent.flags != 0) uri.append("launchFlags=").append(IntegerCompat.toSignedHex(intent.flags)).append(';')
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val ef = getExtendedFlags(intent)
            if (ef != 0) uri.append("extendedLaunchFlags=0x").append(Integer.toHexString(ef)).append(';')
        }
        val pkg = intent.`package`
        if (pkg != null && pkg != defPackage) uri.append("package=").append(Uri.encode(pkg)).append(';')
        intent.component?.let { uri.append("component=").append(Uri.encode(it.flattenToShortString(), "/")).append(';') }
        intent.sourceBounds?.let { uri.append("sourceBounds=").append(Uri.encode(it.flattenToString())).append(';') }
        intent.extras?.let { extras ->
            for (key in extras.keySet()) {
                val value = extras.get(key)
                val type = when (value) {
                    is String -> 'S'
                    is Boolean -> 'B'
                    is Byte -> 'b'
                    is Char -> 'c'
                    is Double -> 'd'
                    is Float -> 'f'
                    is Int -> 'i'
                    is Long -> 'l'
                    is Short -> 's'
                    else -> '\u0000'
                }
                if (type != '\u0000') {
                    uri.append(type).append('.').append(Uri.encode(key)).append('=').append(Uri.encode(value.toString())).append(';')
                }
            }
        }
    }
}
