// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.editor

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.inputmethod.BaseInputConnection
import android.widget.Toast
import io.github.muntashirakon.AppManager.utils.ClipboardUtils
import io.github.muntashirakon.AppManager.utils.Utils
import io.github.rosemoe.sora.text.TextRange
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.DirectAccessProps
import java.lang.reflect.Field

class CodeEditorWidget @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : CodeEditor(context, attrs, defStyleAttr, defStyleRes) {

    fun pasteText() {
        try {
            val data = ClipboardUtils.readClipboard(context)
            val inputConnection = getInputConnection()
            val lastInsertion = getLastInsertion()
            if (data != null && inputConnection != null) {
                val text = data.toString()
                inputConnection.commitText(text, 1)
                if (getProps().formatPastedText) {
                    formatCodeAsync(lastInsertion!!.start, lastInsertion.end)
                }
                notifyIMEExternalCursorChange()
            }
        } catch (e: Exception) {
            Log.w(TAG, e)
            Toast.makeText(context, e.toString(), Toast.LENGTH_SHORT).show()
        }
    }

    fun copyText(shouldCopyLine: Boolean = true) {
        val cursor = cursor
        if (cursor.isSelected) {
            val clip = text.substring(cursor.left, cursor.right)
            Utils.copyToClipboard(context, "text", clip)
        } else if (shouldCopyLine) {
            copyLine()
        }
    }

    private fun copyLine() {
        val cursor = cursor
        if (cursor.isSelected) {
            copyText()
            return
        }
        val line = cursor.left().line
        setSelectionRegion(line, 0, line, text.getColumnCount(line))
        copyText(false)
    }

    private fun getInputConnection(): BaseInputConnection? {
        return try {
            val field = CodeEditor::class.java.getDeclaredField("inputConnection")
            field.isAccessible = true
            field.get(this) as BaseInputConnection
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getLastInsertion(): TextRange? {
        return try {
            val field = CodeEditor::class.java.getDeclaredField("lastInsertion")
            field.isAccessible = true
            field.get(this) as TextRange
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getProps(): DirectAccessProps {
        return try {
            val field = CodeEditor::class.java.getDeclaredField("props")
            field.isAccessible = true
            field.get(this) as DirectAccessProps
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    companion object {
        val TAG: String = CodeEditorWidget::class.java.simpleName
    }
}
