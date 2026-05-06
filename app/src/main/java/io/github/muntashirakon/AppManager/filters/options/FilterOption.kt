// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options

import android.content.pm.ComponentInfo
import android.os.Parcel
import android.os.Parcelable
import androidx.annotation.CallSuper
import androidx.annotation.IntDef
import io.github.muntashirakon.AppManager.db.entity.Backup
import io.github.muntashirakon.AppManager.filters.IFilterableAppInfo
import io.github.muntashirakon.AppManager.utils.JSONUtils
import io.github.muntashirakon.util.LocalizedString
import org.json.JSONException
import org.json.JSONObject
import java.util.regex.Pattern

abstract class FilterOption(val type: String) : LocalizedString, Parcelable {
    companion object {
        const val TYPE_NONE = 0
        const val TYPE_STR_SINGLE = 1
        const val TYPE_STR_MULTIPLE = 2
        const val TYPE_INT = 3
        const val TYPE_LONG = 4
        const val TYPE_REGEX = 5
        const val TYPE_INT_FLAGS = 6
        const val TYPE_TIME_MILLIS = 7
        const val TYPE_DURATION_MILLIS = 8
        const val TYPE_SIZE_BYTES = 9

        const val KEY_ALL = "all"\n@JvmField
        val CREATOR: Parcelable.Creator<FilterOption> = object : Parcelable.Creator<FilterOption> {
            override fun createFromParcel(`in`: Parcel): FilterOption {
                val type = `in`.readString()!!
                val filterOption = FilterOptions.create(type)
                filterOption.id = `in`.readInt()
                val key = `in`.readString()!!
                val value = `in`.readString()
                filterOption.setKeyValue(key, value)
                return filterOption
            }

            override fun newArray(size: Int): Array<FilterOption?> {
                return arrayOfNulls(size)
            }
        }

        @JvmStatic
        @Throws(JSONException::class)
        fun fromJson(`object`: JSONObject): FilterOption {
            val option = FilterOptions.create(`object`.getString("type"))
            option.id = `object`.getInt("id")
            // int keyType = object.getInt("key_type");
            val key = `object`.getString("key")
            val value = JSONUtils.optString(`object`, "value")
            option.setKeyValue(key, value)
            return option
        }
    }

    @IntDef(
        TYPE_NONE,
        TYPE_STR_SINGLE,
        TYPE_STR_MULTIPLE,
        TYPE_INT,
        TYPE_LONG,
        TYPE_REGEX,
        TYPE_INT_FLAGS,
        TYPE_TIME_MILLIS,
        TYPE_DURATION_MILLIS,
        TYPE_SIZE_BYTES
    )
    @Retention(AnnotationRetention.SOURCE)
    annotation class KeyType

    var id: Int = 0

    /**
     * A key under the option (e.g., for target_sdk: eq, le, ge, all; for last_update: before, after, all)
     */
    @get:NonNull
    protected var key: String = KEY_ALL

    /**
     * Type of the key (e.g., for target_sdk: eq, le, ge => int, all => none, for last_update: before, after => date, all => none)
     */
    @get:KeyType
    protected var keyType: Int = TYPE_NONE

    /**
     * Value for the key if keyType is anything but TYPE_NONE
     */
    protected var value: String? = null

    protected var intValue: Int = 0
    protected var longValue: Long = 0
    protected var regexValue: Pattern? = null
    protected var stringValues: Array<String>? = null

    fun getFullId(): String {
        return type + "_" + id
    }

    fun getKey(): String {
        return key
    }

    @KeyType
    fun getKeyType(): Int {
        return keyType
    }

    fun getValue(): String? {
        return value
    }

    @CallSuper
    open fun setKeyValue(key: String, value: String?) {
        val keyType = getKeysWithType()[key]
            ?: throw IllegalArgumentException("Invalid key: $key for type: $type")
        this.key = key
        this.keyType = keyType
        if (keyType != TYPE_NONE) {
            this.value = value!!
            when (keyType) {
                TYPE_INT, TYPE_INT_FLAGS -> this.intValue = value.toInt()
                TYPE_LONG, TYPE_TIME_MILLIS, TYPE_DURATION_MILLIS, TYPE_SIZE_BYTES -> this.longValue = value.toLong()
                TYPE_REGEX -> this.regexValue = Pattern.compile(Pattern.quote(value))
                TYPE_STR_MULTIPLE -> this.stringValues = value.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            }
        }
    }

    abstract fun getKeysWithType(): Map<String, Int>

    open fun getFlags(key: String): Map<Int, CharSequence> {
        throw UnsupportedOperationException("Flags must be returned by the corresponding subclasses. key: $key")
    }

    abstract fun test(info: IFilterableAppInfo, result: TestResult): TestResult

    override fun toString(): String {
        return "FilterOption{" +
                "type='" + type + '\'' +
                ", id=" + id +
                ", key='" + key + '\'' +
                ", keyType=" + keyType +
                ", value='" + value + '\'' +
                '}'
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(type)
        dest.writeInt(id)
        dest.writeString(key)
        dest.writeString(value)
    }

    override fun describeContents(): Int {
        return 0
    }

    @Throws(JSONException::class)
    open fun toJson(): JSONObject? {
        val `object` = JSONObject()
        `object`.put("type", type)
        `object`.put("id", id)
        `object`.put("key", key)
        `object`.put("key_type", keyType)
        if (value != null) {
            `object`.put("value", value)
        }
        return `object`
    }

    protected fun flagsToString(key: String, flags: Int): String {
        val result = mutableListOf<CharSequence>()
        for ((flag, value) in getFlags(key)) {
            if (flags and flag != 0) {
                result.add(value)
            }
        }
        return result.joinToString(", ")
    }

    class TestResult {
        private var mMatched = true
        private var mMatchedBackups: List<Backup>? = null
        private var mMatchedComponents: Map<ComponentInfo, Int>? = null
        private var mMatchedTrackers: Map<ComponentInfo, Int>? = null
        private var mMatchedPermissions: List<String>? = null
        private var mMatchedSubjectLines: List<String>? = null

        fun setMatched(matched: Boolean): TestResult {
            mMatched = matched
            return this
        }

        fun isMatched(): Boolean {
            return mMatched
        }

        fun setMatchedBackups(matchedBackups: List<Backup>?): TestResult {
            mMatchedBackups = matchedBackups
            return this
        }

        fun getMatchedBackups(): List<Backup>? {
            return mMatchedBackups
        }

        fun setMatchedComponents(matchedComponents: Map<ComponentInfo, Int>?): TestResult {
            mMatchedComponents = matchedComponents
            return this
        }

        fun getMatchedComponents(): Map<ComponentInfo, Int>? {
            return mMatchedComponents
        }

        fun setMatchedTrackers(matchedTrackers: Map<ComponentInfo, Int>?): TestResult {
            mMatchedTrackers = matchedTrackers
            return this
        }

        fun getMatchedTrackers(): Map<ComponentInfo, Int>? {
            return mMatchedTrackers
        }

        fun setMatchedPermissions(matchedPermissions: List<String>?): TestResult {
            mMatchedPermissions = matchedPermissions
            return this
        }

        fun getMatchedPermissions(): List<String>? {
            return mMatchedPermissions
        }

        fun setMatchedSubjectLines(matchedSubjectLines: List<String>?): TestResult {
            mMatchedSubjectLines = matchedSubjectLines
            return this
        }

        fun getMatchedSubjectLines(): List<String>? {
            return mMatchedSubjectLines
        }
    }
}
