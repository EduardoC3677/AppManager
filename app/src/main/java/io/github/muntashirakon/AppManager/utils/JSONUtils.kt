// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.lang.reflect.Array as JavaArray

object JSONUtils {
    @JvmStatic
    @Throws(JSONException::class)
    fun putAll(base: JSONObject, jsonObject: JSONObject?) {
        if (jsonObject == null) return
        val keys = jsonObject.names()
        if (keys != null) {
            for (i in 0 until keys.length()) {
                val key = keys.getString(i)
                base.put(key, jsonObject.get(key))
            }
        }
    }

    @JvmStatic
    fun <T> getJSONArray(typicalArray: Array<T>?): JSONArray? {
        if (typicalArray == null) return null
        val jsonArray = JSONArray()
        for (elem in typicalArray) jsonArray.put(elem as Any)
        return jsonArray
    }

    @JvmStatic
    fun getJSONArray(typicalArray: IntArray?): JSONArray? {
        if (typicalArray == null) return null
        val jsonArray = JSONArray()
        for (elem in typicalArray) jsonArray.put(elem)
        return jsonArray
    }

    @JvmStatic
    fun <T> getJSONArray(collection: Collection<T>?): JSONArray? {
        if (collection == null) return null
        val jsonArray = JSONArray()
        for (elem in collection) jsonArray.put(elem)
        return jsonArray
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun <T> getArray(clazz: Class<T>, jsonArray: JSONArray?): Array<T> {
        if (jsonArray == null) {
            @Suppress("UNCHECKED_CAST")
            return JavaArray.newInstance(clazz, 0) as Array<T>
        }
        @Suppress("UNCHECKED_CAST")
        val typicalArray = JavaArray.newInstance(clazz, jsonArray.length()) as Array<T>
        for (i in 0 until jsonArray.length()) {
            typicalArray[i] = clazz.cast(jsonArray.get(i))
        }
        return typicalArray
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun getIntArray(jsonArray: JSONArray?): IntArray {
        if (jsonArray == null) return IntArray(0)
        val typicalArray = IntArray(jsonArray.length())
        for (i in 0 until jsonArray.length()) {
            typicalArray[i] = jsonArray.getInt(i)
        }
        return typicalArray
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun <T> getArray(jsonArray: JSONArray?): ArrayList<T> {
        if (jsonArray == null) return ArrayList(0)
        val arrayList = ArrayList<T>(jsonArray.length())
        for (i in 0 until jsonArray.length()) {
            @Suppress("UNCHECKED_CAST")
            arrayList.add(jsonArray.get(i) as T)
        }
        return arrayList
    }

    @JvmStatic
    fun getString(jsonObject: JSONObject, key: String, defaultValue: String?): String? {
        return try {
            jsonObject.getString(key)
        } catch (e: JSONException) {
            defaultValue
        }
    }

    @JvmStatic
    fun getIntOrNull(jsonObject: JSONObject, key: String): Int? {
        return try {
            jsonObject.getInt(key)
        } catch (ignore: JSONException) {
            null
        }
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun getString(jsonObject: JSONObject, key: String): String? {
        val obj = jsonObject.get(key)
        if (obj == JSONObject.NULL) {
            return null
        }
        return jsonObject.getString(key)
    }

    @JvmStatic
    fun optString(jsonObject: JSONObject, key: String): String? {
        val obj = jsonObject.opt(key)
        if (obj == null || obj == JSONObject.NULL) {
            return null
        }
        return jsonObject.optString(key)
    }

    @JvmStatic
    fun optString(jsonObject: JSONObject, key: String, fallback: String?): String? {
        val obj = jsonObject.opt(key)
        if (obj == null || obj == JSONObject.NULL) {
            return null
        }
        return jsonObject.optString(key, fallback)
    }
}
