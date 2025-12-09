// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import io.github.muntashirakon.AppManager.fm.FmProvider
import io.github.muntashirakon.AppManager.self.filecache.FileCache
import io.github.muntashirakon.io.Paths
import java.io.IOException
import java.util.Locale

object ClipboardUtils {
    private const val MAX_CLIPBOARD_SIZE_BYTES = 1024 * 1024

    /**
     * Copies text to clipboard, using URI fallback if text is larger.
     */
    @JvmStatic
    fun copyToClipboard(context: Context, label: CharSequence?, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val textBytes = text.toByteArray()
        val clip = if (textBytes.size < MAX_CLIPBOARD_SIZE_BYTES) {
            // Small text: copy directly
            ClipData.newPlainText(label, text)
        } else {
            // Large text: save to file and copy Uri reference
            try {
                val cacheFile = FileCache.getGlobalFileCache().getCachedFile(textBytes, "txt")
                // Use FileProvider to get content Uri for the file
                val contentUri = FmProvider.getContentUri(Paths.get(cacheFile))
                // Grant temporary read permission
                ClipData.newUri(context.contentResolver, label, contentUri)
            } catch (e: IOException) {
                e.printStackTrace()
                // Fallback: copy truncated text if writing file fails
                ClipData.newPlainText("text", text.substring(0, MAX_CLIPBOARD_SIZE_BYTES - 1))
            }
        }
        clipboard.setPrimaryClip(clip)
    }

    @JvmStatic
    fun readClipboard(context: Context): CharSequence? {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = clipboard.primaryClip
        if (clipData != null && clipData.itemCount > 0) {
            return clipData.getItemAt(0).coerceToText(context)
        }
        return null
    }

    @JvmStatic
    fun readHashValueFromClipboard(context: Context): String? {
        val clipData = readClipboard(context) ?: return null
        val data = clipData.toString().trim().lowercase(Locale.ROOT)
        if (data.matches(Regex("[0-9a-f: \n]+"))) {
            return data.replace(Regex("[: \n]+"), "")
        }
        return null
    }
}
