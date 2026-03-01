// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.algo

class AhoCorasick(patterns: Array<String>) : AutoCloseable {
    private var nativeInstanceId: Long = 0

    init {
        nativeInstanceId = createNative(patterns)
        if (nativeInstanceId == 0L) {
            throw RuntimeException("Failed to create native AhoCorasick instance")
        }
    }

    private external fun createNative(patterns: Array<String>): Long

    private external fun searchNative(instanceId: Long, text: String): IntArray

    private external fun destroyNative(instanceId: Long)

    /**
     * Search the text for matching patterns.
     */
    fun search(text: String): IntArray {
        if (nativeInstanceId == 0L) throw IllegalStateException("Instance already closed")
        return searchNative(nativeInstanceId, text)
    }

    /**
     * Releases native resources automatically when try-with-resources ends.
     */
    override fun close() {
        if (nativeInstanceId != 0L) {
            destroyNative(nativeInstanceId)
            nativeInstanceId = 0L
        }
    }

    @Throws(Throwable::class)
    protected fun finalize() {
        close() // Backup safety to release native resources
    }

    companion object {
        init {
            System.loadLibrary("am")
        }
    }
}
