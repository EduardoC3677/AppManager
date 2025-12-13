// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils

import android.app.Activity
import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
import android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ConfigurationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.UserHandleHidden
import android.text.GetChars
import android.text.TextUtils
import android.util.Pair
import android.view.View
import android.view.WindowManager
import androidx.annotation.CheckResult
import androidx.annotation.StringRes
import androidx.core.content.pm.PermissionInfoCompat
import androidx.fragment.app.FragmentActivity
import aosp.libcore.util.EmptyArray
import io.github.muntashirakon.AppManager.BuildConfig
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.apk.signing.SignerInfo
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat
import io.github.muntashirakon.AppManager.misc.OsEnvironment
import org.jetbrains.annotations.Contract
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.security.cert.X509Certificate
import java.util.Arrays
import java.util.Locale

object Utils {
    @JvmField
    val TERMUX_LOGIN_PATH: String = OsEnvironment.getDataDirectoryRaw() + "/data/com.termux/files/usr/bin/login"

    @JvmStatic
    fun camelCaseToSpaceSeparatedString(str: String): String {
        return TextUtils.join(" ", splitByCharacterType(str, true)).replace(" _", "")
    }

    @JvmStatic
    fun containsOrHasInitials(query: String, str: String): Boolean {
        val lowerQuery = query.lowercase(Locale.ROOT)
        if (str.lowercase(Locale.ROOT).contains(lowerQuery)) return true
        return getFirstLettersInLowerCase(camelCaseToSpaceSeparatedString(str)).contains(lowerQuery)
    }

    @JvmStatic
    fun getFirstLettersInLowerCase(str: String): String {
        val strings = str.split("\\s".toRegex()).toTypedArray()
        val builder = StringBuilder()
        for (s in strings) {
            if (s.isNotEmpty()) builder.append(s[0])
        }
        return builder.toString().lowercase(Locale.ROOT)
    }

    // https://github.com/apache/commons-lang/blob/master/src/main/java/org/apache/commons/lang3/StringUtils.java#L7514
    @JvmStatic
    fun splitByCharacterType(str: String, camelCase: Boolean): Array<String> {
        if (str.isEmpty()) return EmptyArray.STRING
        val c = str.toCharArray()
        val list = ArrayList<String>()
        var tokenStart = 0
        var currentType = Character.getType(c[tokenStart])
        for (pos in tokenStart + 1 until c.size) {
            val type = Character.getType(c[pos])
            if (type == currentType) {
                continue
            }
            if (camelCase && type == Character.LOWERCASE_LETTER.toInt() && currentType == Character.UPPERCASE_LETTER.toInt()) {
                val newTokenStart = pos - 1
                if (newTokenStart != tokenStart) {
                    list.add(String(c, tokenStart, newTokenStart - tokenStart))
                    tokenStart = newTokenStart
                }
            } else {
                list.add(String(c, tokenStart, pos - tokenStart))
                tokenStart = pos
            }
            currentType = type
        }
        list.add(String(c, tokenStart, c.size - tokenStart))
        return list.toTypedArray()
    }

    @JvmStatic
    fun getLastComponent(str: String): String {
        return try {
            str.substring(str.lastIndexOf('.') + 1)
        } catch (e: Exception) {
            str
        }
    }

    @JvmStatic
    @StringRes
    fun getProcessStateName(shortName: String): Int {
        return when (shortName) {
            "R" -> R.string.running
            "S" -> R.string.state_sleeping
            "D" -> R.string.state_device_io
            "T" -> R.string.stopped
            "t" -> R.string.state_trace_stop
            "x", "X" -> R.string.state_dead
            "Z" -> R.string.state_zombie
            "P" -> R.string.state_parked
            "I" -> R.string.state_idle
            "K" -> R.string.state_wake_kill
            "W" -> R.string.state_waking
            else -> R.string.state_unknown
        }
    }

    @JvmStatic
    @StringRes
    fun getProcessStateExtraName(shortName: String?): Int {
        if (shortName == null) return R.string.empty
        return when (shortName) {
            "<" -> R.string.state_high_priority
            "N" -> R.string.state_low_priority
            "L" -> R.string.state_locked_memory
            "s" -> R.string.state_session_leader
            "+" -> R.string.state_foreground
            "l" -> R.string.state_multithreaded
            else -> R.string.state_unknown
        }
    }

    @JvmStatic
    @StringRes
    fun getLaunchMode(mode: Int): Int {
        return when (mode) {
            ActivityInfo.LAUNCH_MULTIPLE -> R.string.launch_mode_multiple
            ActivityInfo.LAUNCH_SINGLE_INSTANCE -> R.string.launch_mode_single_instance
            ActivityInfo.LAUNCH_SINGLE_TASK -> R.string.launch_mode_single_task
            ActivityInfo.LAUNCH_SINGLE_TOP -> R.string.launch_mode_single_top
            else -> R.string._null
        }
    }

    @JvmStatic
    @StringRes
    fun getOrientationString(orientation: Int): Int {
        return when (orientation) {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED -> R.string.orientation_unspecified
            ActivityInfo.SCREEN_ORIENTATION_BEHIND -> R.string.orientation_behind
            ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR -> R.string.orientation_full_sensor
            ActivityInfo.SCREEN_ORIENTATION_FULL_USER -> R.string.orientation_full_user
            ActivityInfo.SCREEN_ORIENTATION_LOCKED -> R.string.orientation_locked
            ActivityInfo.SCREEN_ORIENTATION_NOSENSOR -> R.string.orientation_no_sensor
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE -> R.string.orientation_landscape
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT -> R.string.orientation_portrait
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT -> R.string.orientation_reverse_portrait
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE -> R.string.orientation_reverse_landscape
            ActivityInfo.SCREEN_ORIENTATION_USER -> R.string.orientation_user
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE -> R.string.orientation_sensor_landscape
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT -> R.string.orientation_sensor_portrait
            ActivityInfo.SCREEN_ORIENTATION_SENSOR -> R.string.orientation_sensor
            ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE -> R.string.orientation_user_landscape
            ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT -> R.string.orientation_user_portrait
            else -> R.string._null
        }
    }

    // FIXME: Translation support
    @JvmStatic
    fun getSoftInputString(flag: Int): String {
        val builder = StringBuilder()
        if ((flag and WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING) != 0)
            builder.append("Adjust nothing, ")
        if ((flag and WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN) != 0)
            builder.append("Adjust pan, ")
        if ((flag and WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE) != 0)
            builder.append("Adjust resize, ")
        if ((flag and WindowManager.LayoutParams.SOFT_INPUT_ADJUST_UNSPECIFIED) != 0)
            builder.append("Adjust unspecified, ")
        if ((flag and WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN) != 0)
            builder.append("Always hidden, ")
        if ((flag and WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE) != 0)
            builder.append("Always visible, ")
        if ((flag and WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN) != 0)
            builder.append("Hidden, ")
        if ((flag and WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE) != 0)
            builder.append("Visible, ")
        if ((flag and WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED) != 0)
            builder.append("Unchanged, ")
        if ((flag and WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED) != 0)
            builder.append("Unspecified, ")
        if ((flag and WindowManager.LayoutParams.SOFT_INPUT_IS_FORWARD_NAVIGATION) != 0)
            builder.append("ForwardNav, ")
        if ((flag and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST) != 0)
            builder.append("Mask adjust, ")
        if ((flag and WindowManager.LayoutParams.SOFT_INPUT_MASK_STATE) != 0)
            builder.append("Mask state, ")
        if ((flag and WindowManager.LayoutParams.SOFT_INPUT_MODE_CHANGED) != 0)
            builder.append("Mode changed, ")
        checkStringBuilderEnd(builder)
        val result = builder.toString()
        return if (result.isEmpty()) "null" else result
    }

    // FIXME Add translation support
    @JvmStatic
    fun getServiceFlagsString(flag: Int): CharSequence {
        val builder = StringBuilder()
        if ((flag and ServiceInfo.FLAG_STOP_WITH_TASK) != 0)
            builder.append("Stop with task, ")
        if ((flag and ServiceInfo.FLAG_ISOLATED_PROCESS) != 0)
            builder.append("Isolated process, ")

        if ((flag and ServiceInfo.FLAG_SINGLE_USER) != 0)
            builder.append("Single user, ")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if ((flag and ServiceInfo.FLAG_EXTERNAL_SERVICE) != 0)
                builder.append("External service, ")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if ((flag and ServiceInfo.FLAG_USE_APP_ZYGOTE) != 0)
                    builder.append("Use app zygote, ")
            }
        }
        checkStringBuilderEnd(builder)
        val result = builder.toString()
        return if (TextUtils.isEmpty(result)) "" else ("⚑ $result")
    }

    // FIXME Add translation support
    @JvmStatic
    fun getActivitiesFlagsString(flag: Int): String {
        val builder = StringBuilder()
        if ((flag and ActivityInfo.FLAG_ALLOW_TASK_REPARENTING) != 0)
            builder.append("AllowReparenting, ")
        if ((flag and ActivityInfo.FLAG_ALWAYS_RETAIN_TASK_STATE) != 0)
            builder.append("AlwaysRetain, ")
        if ((flag and ActivityInfo.FLAG_AUTO_REMOVE_FROM_RECENTS) != 0)
            builder.append("AutoRemove, ")
        if ((flag and ActivityInfo.FLAG_CLEAR_TASK_ON_LAUNCH) != 0)
            builder.append("ClearOnLaunch, ")
        if ((flag and ActivityInfo.FLAG_ENABLE_VR_MODE) != 0)
            builder.append("EnableVR, ")
        if ((flag and ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS) != 0)
            builder.append("ExcludeRecent, ")
        if ((flag and ActivityInfo.FLAG_FINISH_ON_CLOSE_SYSTEM_DIALOGS) != 0)
            builder.append("FinishCloseDialogs, ")
        if ((flag and ActivityInfo.FLAG_FINISH_ON_TASK_LAUNCH) != 0)
            builder.append("FinishLaunch, ")
        if ((flag and ActivityInfo.FLAG_HARDWARE_ACCELERATED) != 0)
            builder.append("HardwareAccel, ")
        if ((flag and ActivityInfo.FLAG_IMMERSIVE) != 0)
            builder.append("Immersive, ")
        if ((flag and ActivityInfo.FLAG_MULTIPROCESS) != 0)
            builder.append("Multiprocess, ")
        if ((flag and ActivityInfo.FLAG_NO_HISTORY) != 0)
            builder.append("NoHistory, ")
        if ((flag and ActivityInfo.FLAG_RELINQUISH_TASK_IDENTITY) != 0)
            builder.append("RelinquishIdentity, ")
        if ((flag and ActivityInfo.FLAG_RESUME_WHILE_PAUSING) != 0)
            builder.append("Resume, ")
        if ((flag and ActivityInfo.FLAG_SINGLE_USER) != 0)
            builder.append("Single, ")
        if ((flag and ActivityInfo.FLAG_STATE_NOT_NEEDED) != 0)
            builder.append("NotNeeded, ")
        checkStringBuilderEnd(builder)
        val result = builder.toString()
        return if (result.isEmpty()) "⚐" else "⚑ $result"
    }

    // FIXME Add translation support
    @JvmStatic
    fun getProtectionLevelString(permissionInfo: PermissionInfo): String {
        val basePermissionType = PermissionInfoCompat.getProtection(permissionInfo)
        val permissionFlags = PermissionInfoCompat.getProtectionFlags(permissionInfo)
        var protectionLevel = when (basePermissionType) {
            PermissionInfo.PROTECTION_DANGEROUS -> "dangerous"
            PermissionInfo.PROTECTION_NORMAL -> "normal"
            PermissionInfo.PROTECTION_SIGNATURE -> "signature"
            PermissionInfo.PROTECTION_SIGNATURE_OR_SYSTEM,
            PermissionInfo.PROTECTION_SIGNATURE or PermissionInfo.PROTECTION_FLAG_PRIVILEGED -> "signatureOrPrivileged"
            else -> "????"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if ((permissionFlags and PermissionInfo.PROTECTION_FLAG_PRIVILEGED) != 0)
                protectionLevel += "|privileged"
            if ((permissionFlags and PermissionInfo.PROTECTION_FLAG_PRE23) != 0)
                protectionLevel += "|pre23"  // pre marshmallow
            if ((permissionFlags and PermissionInfo.PROTECTION_FLAG_INSTALLER) != 0)
                protectionLevel += "|installer"
            if ((permissionFlags and PermissionInfo.PROTECTION_FLAG_VERIFIER) != 0)
                protectionLevel += "|verifier"
            if ((permissionFlags and PermissionInfo.PROTECTION_FLAG_PREINSTALLED) != 0)
                protectionLevel += "|preinstalled"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                if ((permissionFlags and PermissionInfo.PROTECTION_FLAG_SETUP) != 0)
                    protectionLevel += "|setup"
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if ((permissionFlags and PermissionInfo.PROTECTION_FLAG_RUNTIME_ONLY) != 0)
                    protectionLevel += "|runtime"
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                if ((permissionFlags and PermissionInfo.PROTECTION_FLAG_INSTANT) != 0)
                    protectionLevel += "|instant"
            }
        } else {
            if ((permissionFlags and PermissionInfo.PROTECTION_FLAG_SYSTEM) != 0) {
                protectionLevel += "|system"
            }
        }

        if ((permissionFlags and PermissionInfo.PROTECTION_FLAG_DEVELOPMENT) != 0) {
            protectionLevel += "|development"
        }
        if ((permissionFlags and PermissionInfo.PROTECTION_FLAG_APPOP) != 0) {
            protectionLevel += "|appop"
        }
        return protectionLevel
    }

    // FIXME Add translation support
    @JvmStatic
    fun getInputFeaturesString(flag: Int): String {
        var string = ""
        if ((flag and ConfigurationInfo.INPUT_FEATURE_FIVE_WAY_NAV) != 0)
            string += "Five way nav"
        if ((flag and ConfigurationInfo.INPUT_FEATURE_HARD_KEYBOARD) != 0)
            string += (if (string.isEmpty()) "" else "|") + "Hard keyboard"
        return if (string.isEmpty()) "null" else string
    }

    @JvmStatic
    @StringRes
    fun getKeyboardType(KbType: Int): Int {
        return when (KbType) {
            Configuration.KEYBOARD_NOKEYS -> R.string.keyboard_no_keys
            Configuration.KEYBOARD_QWERTY -> R.string.keyboard_qwerty
            Configuration.KEYBOARD_12KEY -> R.string.keyboard_12_keys
            Configuration.KEYBOARD_UNDEFINED -> R.string._undefined
            else -> R.string._undefined
        }
    }

    @JvmStatic
    @StringRes
    fun getNavigation(navId: Int): Int {
        return when (navId) {
            Configuration.NAVIGATION_NONAV -> R.string.navigation_no_nav
            Configuration.NAVIGATION_DPAD -> R.string.navigation_dial_pad
            Configuration.NAVIGATION_TRACKBALL -> R.string.navigation_trackball
            Configuration.NAVIGATION_WHEEL -> R.string.navigation_wheel
            Configuration.NAVIGATION_UNDEFINED -> R.string._undefined
            else -> R.string._undefined
        }
    }

    @JvmStatic
    @StringRes
    fun getTouchScreen(touchId: Int): Int {
        return when (touchId) {
            Configuration.TOUCHSCREEN_NOTOUCH -> R.string.touchscreen_no_touch
            2 -> R.string.touchscreen_stylus  // Configuration.TOUCHSCREEN_STYLUS
            Configuration.TOUCHSCREEN_FINGER -> R.string.touchscreen_finger
            Configuration.TOUCHSCREEN_UNDEFINED -> R.string._undefined
            else -> R.string._undefined
        }
    }

    @JvmStatic
    fun checkStringBuilderEnd(builder: StringBuilder) {
        val length = builder.length
        if (length > 2) builder.delete(length - 2, length)
    }

    @JvmStatic
    fun getGlEsVersion(reqGlEsVersion: Int): String {
        val major = ((reqGlEsVersion and 0xffff0000.toInt()) shr 16)
        val minor = reqGlEsVersion and 0x0000ffff
        return "$major.$minor"
    }

    @JvmStatic
    fun getVulkanVersion(pm: PackageManager): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return null
        }
        // https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/core/java/android/os/GraphicsEnvironment.java;l=193;drc=f80e786d308318894be30d54b93f38034496fc66
        if (pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION, 0x00403000)) {
            return "1.3"
        }
        if (pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION, 0x00402000)) {
            return "1.2"
        }
        if (pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION, 0x00401000)) {
            return "1.1"
        }
        if (pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION, 0x00400000)) {
            return "1.0"
        }
        return null
    }

    @JvmStatic
    @CheckResult
    fun charsToBytes(chars: CharArray): ByteArray {
        val byteBuffer = StandardCharsets.UTF_8.encode(CharBuffer.wrap(chars))
        val bytes = Arrays.copyOf(byteBuffer.array(), byteBuffer.limit())
        clearBytes(byteBuffer.array())
        return bytes
    }

    @JvmStatic
    fun getChars(getChars: GetChars?): CharArray? {
        if (TextUtils.isEmpty(getChars)) return null
        @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
        val chars = CharArray(getChars!!.length)
        getChars.getChars(0, chars.size, chars, 0)
        return chars
    }

    @JvmStatic
    @CheckResult
    fun bytesToChars(bytes: ByteArray): CharArray {
        val charBuffer = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(bytes))
        val chars = Arrays.copyOf(charBuffer.array(), charBuffer.limit())
        clearChars(charBuffer.array())
        return chars
    }

    @JvmStatic
    fun clearBytes(bytes: ByteArray) {
        Arrays.fill(bytes, 0.toByte())
    }

    @JvmStatic
    fun clearChars(chars: CharArray) {
        Arrays.fill(chars, '\u0000')
    }

    @JvmStatic
    fun getIssuerAndAlg(p: PackageInfo): Pair<String, String> {
        val signerInfo = PackageUtils.getSignerInfo(p, false)
        if (signerInfo != null) {
            val certs = signerInfo.currentSignerCerts
            if (certs != null && certs.isNotEmpty()) {
                val c = certs[0]
                return Pair(c.issuerX500Principal.name, c.sigAlgName)
            }
        }
        return Pair("", "")
    }

    /**
     * Replace the first occurrence of matched string
     *
     * @param text         The text where the operation will be carried out
     * @param searchString The string to replace
     * @param replacement  The string that takes in place of the search string
     * @return The modified string
     */
    // Similar impl. of https://commons.apache.org/proper/commons-lang/apidocs/src-html/org/apache/commons/lang3/StringUtils.html#line.6418
    @JvmStatic
    fun replaceOnce(text: String, searchString: CharSequence, replacement: CharSequence): String {
        if (TextUtils.isEmpty(text) || TextUtils.isEmpty(searchString)) {
            return text
        }
        var start = 0
        val end = TextUtils.indexOf(text, searchString, start)
        if (end == -1) {
            return text
        }
        val replLength = searchString.length
        val increase = maxOf(replacement.length - replLength, 0)
        val buf = StringBuilder(text.length + increase)
        buf.append(text, start, end).append(replacement)
        start = end + replLength
        buf.append(text, start, text.length)
        return buf.toString()
    }

    @JvmStatic
    @Contract("null,_,_ -> fail")
    fun getIntegerFromString(
        needle: CharSequence?,
        stringsToMatch: List<CharSequence>,
        associatedIntegers: List<Int>
    ): Int {
        if (needle == null) throw IllegalArgumentException("Needle cannot be null")
        if (stringsToMatch.size != associatedIntegers.size) {
            throw IllegalArgumentException("String and integer arrays have different sizes")
        }
        val trimmedNeedle = needle.toString().trim()
        val index = stringsToMatch.indexOf(trimmedNeedle)
        return if (index == -1) {
            // Might be a numeric value
            Integer.parseInt(trimmedNeedle)
        } else {
            associatedIntegers[index]
        }
    }

    @JvmStatic
    fun isTv(context: Context): Boolean {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }

    @JvmStatic
    fun canDisplayNotification(context: Context): Boolean {
        // Notifications can be displayed in all supported devices except Android TV (O+)
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !isTv(context)
    }

    @JvmStatic
    fun isAppInForeground(): Boolean {
        val appProcessInfo = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(appProcessInfo)
        return (appProcessInfo.importance == IMPORTANCE_FOREGROUND || appProcessInfo.importance == IMPORTANCE_VISIBLE)
    }

    @JvmStatic
    fun getTotalCores(): Int {
        return Runtime.getRuntime().availableProcessors()
    }

    @JvmStatic
    fun copyToClipboard(context: Context, label: CharSequence?, text: CharSequence) {
        ClipboardUtils.copyToClipboard(context, label, text.toString())
        UIUtils.displayShortToast(R.string.copied_to_clipboard)
    }

    @JvmStatic
    fun openAsFolderInFM(context: Context, dir: String?): View.OnClickListener? {
        if (dir == null) return null
        return View.OnClickListener {
            val openFile = Intent(Intent.ACTION_VIEW)
            openFile.setDataAndType(android.net.Uri.parse(dir), "resource/folder")
            openFile.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            if (openFile.resolveActivityInfo(context.getPackageManager(), 0) != null)
                context.startActivity(openFile)
        }
    }

    @JvmStatic
    fun relaunchApp(activity: FragmentActivity) {
        val intent = PackageManagerCompat.getLaunchIntentForPackage(activity.packageName, UserHandleHidden.myUserId())
        if (intent == null) {
            // No launch intent
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        activity.startActivity(intent)
        activity.finish()
    }

    @JvmStatic
    fun getRealReferrer(activity: Activity): String? {
        val callingPackage = activity.callingPackage
        if (callingPackage != null && !BuildConfig.APPLICATION_ID.equals(callingPackage)) {
            return callingPackage
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            val intent = activity.intent
            intent.removeExtra(Intent.EXTRA_REFERRER_NAME)
            intent.removeExtra(Intent.EXTRA_REFERRER)
            // Now that the custom referrers are removed, it should return the real referrer.
            // android-app:authority
            val referrer = activity.referrer
            return referrer?.authority
        }
        return null
    }

    @JvmStatic
    fun isWifiActive(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val capabilities = cm.getNetworkCapabilities(cm.activeNetwork)
            return capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        }
        val info = cm.activeNetworkInfo
        return info != null && info.type == ConnectivityManager.TYPE_WIFI
    }

    @JvmStatic
    fun <T> prettyPrintObject(obj: T?): String {
        if (obj == null) {
            return "null"
        }
        val sb = StringBuilder()
        val clazz: Class<*> = obj.javaClass
        sb.append(clazz.simpleName).append("{")
        val fields = clazz.declaredFields
        for (i in fields.indices) {
            val field = fields[i]
            field.isAccessible = true
            sb.append(field.name).append("=")
            try {
                val value = field.get(obj)
                sb.append(value)
            } catch (e: IllegalAccessException) {
                sb.append("N/A")
            }
            if (i < fields.size - 1) {
                sb.append(", ")
            }
        }
        sb.append("}")
        return sb.toString()
    }

    @JvmStatic
    fun isRoboUnitTest(): Boolean {
        return "robolectric" == Build.FINGERPRINT
    }
}
