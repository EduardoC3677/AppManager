// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters

import android.os.Parcel
import android.os.Parcelable
import android.text.TextUtils
import androidx.collection.ArrayMap
import androidx.core.os.ParcelCompat
import io.github.muntashirakon.AppManager.filters.options.*
import io.github.muntashirakon.AppManager.history.IJsonSerializer
import io.github.muntashirakon.AppManager.history.JsonDeserializer
import io.github.muntashirakon.util.ParcelUtils
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class FilterItem : IJsonSerializer, Parcelable {
    private class ExprEvaluator(private val mFilterOptions: ArrayMap<Int, FilterOption>) : AbsExpressionEvaluator() {
        private var mInfo: IFilterableAppInfo? = null
        var result: FilterOption.TestResult? = null
            private set

        fun setInfo(info: IFilterableAppInfo?) {
            mInfo = info
            result = FilterOption.TestResult()
        }

        override fun evalId(id: String): Boolean {
            if (result == null) {
                result = FilterOption.TestResult()
            }
            val idx = id.lastIndexOf('_')
            val intId = if (idx >= 0 && id.length > idx + 1) {
                id.substring(idx + 1).toInt()
            } else 0
            val option = mFilterOptions[intId]
            if (option == null || mInfo == null) {
                return false
            }
            return option.test(mInfo!!, result!!).isMatched
        }
    }

    private var mName: String
    private val mFilterOptions: ArrayMap<Int, FilterOption>
    private var mExpr = ""
    private var mCustomExpr = false
    private var mNextId = 1
    var timesUsageInfoUsed = 0
        private set
    var timesRunningOptionUsed = 0
        private set

    constructor() : this("Untitled")

    private constructor(name: String) {
        mName = name
        mFilterOptions = ArrayMap()
    }

    var name: String
        get() = mName
        set(value) { mName = value }

    var expr: String
        get() = mExpr
        set(value) {
            mExpr = value
            mCustomExpr = true
        }

    fun addFilterOption(filterOption: FilterOption): Int {
        filterOption.id = getNextId()
        if (!mCustomExpr) {
            val id = filterOption.getFullId()
            if (TextUtils.isEmpty(mExpr)) {
                mExpr = id
            } else mExpr += " & $id"
        }
        incrementUsage(filterOption, true)
        if (mFilterOptions.put(filterOption.id, filterOption) == null) {
            return mFilterOptions.indexOfKey(filterOption.id)
        }
        return -1
    }

    fun updateFilterOptionAt(i: Int, filterOption: FilterOption) {
        val oldFilterOption = mFilterOptions.valueAt(i) ?: throw IllegalArgumentException("Invalid index $i")
        filterOption.id = oldFilterOption.id
        mFilterOptions.setValueAt(i, filterOption)
        if (!mCustomExpr) {
            val idStr = oldFilterOption.getFullId()
            val ops = mExpr.split(" & ").toTypedArray()
            val sb = StringBuilder()
            for (op in ops) {
                if (sb.isNotEmpty()) {
                    sb.append(" & ")
                }
                if (idStr == op) {
                    sb.append(filterOption.getFullId())
                } else sb.append(op)
            }
            mExpr = sb.toString()
        }
        incrementUsage(oldFilterOption, false)
        incrementUsage(filterOption, true)
    }

    fun removeFilterOptionAt(i: Int): Boolean {
        val filterOption = mFilterOptions.removeAt(i) ?: return false
        mNextId = filterOption.id
        if (!mCustomExpr) {
            val idStr = filterOption.getFullId()
            val ops = mExpr.split(" & ").toTypedArray()
            val sb = StringBuilder()
            for (op in ops) {
                if (idStr != op) {
                    if (sb.isNotEmpty()) {
                        sb.append(" & ")
                    }
                    sb.append(op)
                }
            }
            mExpr = sb.toString()
        }
        incrementUsage(filterOption, false)
        return true
    }

    val size: Int
        get() = mFilterOptions.size

    fun getFilterOptionAt(i: Int): FilterOption = mFilterOptions.valueAt(i)

    fun getFilterOptionForId(id: Int): FilterOption? = mFilterOptions[id]

    fun <T : IFilterableAppInfo> getFilteredList(allFilterableAppInfo: List<T>): List<FilteredItemInfo<T>> {
        val filteredFilterableAppInfo = ArrayList<FilteredItemInfo<T>>()
        val evaluator = ExprEvaluator(mFilterOptions)
        val currentExpr = if (TextUtils.isEmpty(mExpr)) "true" else mExpr
        for (info in allFilterableAppInfo) {
            evaluator.setInfo(info)
            val eval = evaluator.evaluate(currentExpr)
            val result = evaluator.result!!
            if (eval) {
                filteredFilterableAppInfo.add(FilteredItemInfo(info, result))
            }
        }
        return filteredFilterableAppInfo
    }

    private fun incrementUsage(filterOption: FilterOption, increment: Boolean) {
        val requireCountUpdate = filterOption is DataUsageOption || filterOption is TimesOpenedOption || filterOption is ScreenTimeOption
        if (requireCountUpdate) {
            if (increment) ++timesUsageInfoUsed else --timesUsageInfoUsed
            return
        }
        if (filterOption is RunningAppsOption) {
            if (increment) ++timesRunningOptionUsed else --timesRunningOptionUsed
        }
    }

    constructor(`in`: Parcel) {
        mName = `in`.readString()!!
        mExpr = `in`.readString()!!
        mCustomExpr = ParcelCompat.readBoolean(`in`)
        mFilterOptions = ParcelUtils.readArrayMap(`in`, Int::class.java.classLoader, FilterOption::class.java.classLoader)
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(mName)
        dest.writeString(mExpr)
        ParcelCompat.writeBoolean(dest, mCustomExpr)
        ParcelUtils.writeMap(mFilterOptions, dest)
    }

    override fun describeContents(): Int = 0

    @Throws(JSONException::class)
    override fun serializeToJson(): JSONObject {
        val jsonObject = JSONObject()
        val array = JSONArray()
        for (filterOption in mFilterOptions.values) {
            array.put(filterOption.toJson())
        }
        jsonObject.put("name", mName)
        jsonObject.put("expr", mExpr)
        jsonObject.put("custom_expr", mCustomExpr)
        jsonObject.put("options", array)
        return jsonObject
    }

    @Throws(JSONException::class)
    constructor(jsonObject: JSONObject) {
        mName = jsonObject.getString("name")
        mExpr = jsonObject.getString("expr")
        mCustomExpr = jsonObject.getBoolean("custom_expr")
        mFilterOptions = ArrayMap()
        val array = jsonObject.getJSONArray("options")
        for (i in 0 until array.length()) {
            val option = FilterOption.fromJson(array.getJSONObject(i))
            mFilterOptions.put(option.id, option)
        }
    }

    private fun getNextId(): Int {
        while (mFilterOptions.containsKey(mNextId)) {
            mNextId++
        }
        return mNextId
    }

    class FilteredItemInfo<T : IFilterableAppInfo>(val info: T, val result: FilterOption.TestResult)

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<FilterItem> = object : Parcelable.Creator<FilterItem> {
            override fun createFromParcel(`in`: Parcel): FilterItem = FilterItem(`in`)
            override fun newArray(size: Int): Array<FilterItem?> = arrayOfNulls(size)
        }

        @JvmField
        val DESERIALIZER = JsonDeserializer.Creator { FilterItem(it) }
    }
}
