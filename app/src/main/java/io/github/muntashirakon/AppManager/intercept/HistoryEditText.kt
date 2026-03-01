// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.intercept

import android.app.Activity
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import io.github.muntashirakon.adapters.NoFilterArrayAdapter
import io.github.muntashirakon.widget.MaterialAutoCompleteTextView
import java.util.*

open class HistoryEditText(private val mContext: Activity, vararg editors: MaterialAutoCompleteTextView) {
    private val mEditorHandlers: Array<EditorHandler> = Array(editors.size) { i ->
        createHandler("$HISTORY_PREFIX$i", editors[i])
    }

    protected open inner class EditorHandler(private val mId: String, private val mEditor: MaterialAutoCompleteTextView) {
        init {
            showHistory()
        }

        fun toString(pref: SharedPreferences): String = "$mId : '${getHistory(pref)}'"

        protected fun showHistory() {
            val items = getHistoryItems()
            val adapter = NoFilterArrayAdapter(mContext, io.github.muntashirakon.ui.R.layout.auto_complete_dropdown_item, items)
            mEditor.setAdapter(adapter)
        }

        private fun getHistoryItems(): List<String> {
            val sharedPref = PreferenceManager.getDefaultSharedPreferences(mContext)
            return getHistory(sharedPref)
        }

        private fun getHistory(sharedPref: SharedPreferences): List<String> {
            val history = sharedPref.getString(mId, "")
            return asList(history ?: "")
        }

        fun saveHistory(sharedPref: SharedPreferences, edit: SharedPreferences.Editor) {
            var history = getHistory(sharedPref)
            history = include(history, mEditor.text.toString().trim())
            edit.putString(mId, toString(history))
        }

        private fun asList(serialistedListElements: String): List<String> {
            return serialistedListElements.split(DELIMITER).toList()
        }

        private fun toString(list: List<String>?): String {
            val result = StringBuilder()
            list?.let {
                var nextDelim = ""
                for (instance in it) {
                    val instanceString = instance.trim()
                    if (instanceString.isNotEmpty()) {
                        result.append(nextDelim).append(instanceString)
                        nextDelim = DELIMITER
                    }
                }
            }
            return result.toString()
        }

        private fun include(history_: List<String>, newValue: String?): List<String> {
            val history = ArrayList(history_)
            if (!newValue.isNullOrEmpty()) {
                history.remove(newValue)
                history.add(0, newValue)
            }
            while (history.size > MAX_HISTORY_SIZE) {
                history.removeAt(history.size - 1)
            }
            return history
        }
    }

    protected open fun createHandler(id: String, editor: MaterialAutoCompleteTextView): EditorHandler = EditorHandler(id, editor)

    fun saveHistory() {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(mContext)
        val edit = sharedPref.edit()
        for (handler in mEditorHandlers) handler.saveHistory(sharedPref, edit)
        edit.apply()
    }

    override fun toString(): String {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(mContext)
        val result = StringBuilder()
        for (handler in mEditorHandlers) result.append(handler.toString(sharedPref)).append("
")
        return result.toString()
    }

    companion object {
        private const val DELIMITER = "';'"
        private const val MAX_HISTORY_SIZE = 8
        private const val HISTORY_PREFIX = "ActivityInterceptor_history_"
    }
}
