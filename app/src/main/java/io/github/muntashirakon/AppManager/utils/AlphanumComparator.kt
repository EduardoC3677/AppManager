// SPDX-License-Identifier: MIT AND GPL-3.0

package io.github.muntashirakon.AppManager.utils

// Copyright 2007-2017 David Koelle
// Copyright 2025 Muntashir Al-Islam
class AlphanumComparator : Comparator<String> {
    override fun compare(s1: String?, s2: String?): Int {
        return compareString(s1, s2)
    }

    companion object {
        private fun isDigit(ch: Char): Boolean {
            return ch in '0'..'9'
        }

        /**
         * Length of string is passed in for improved efficiency (only need to calculate it once)
         */
        private fun getChunk(s: String, sLength: Int, marker: Int): String {
            val chunk = StringBuilder()
            var currentMarker = marker
            var c = s[currentMarker]
            chunk.append(c)
            currentMarker++
            if (isDigit(c)) {
                while (currentMarker < sLength) {
                    c = s[currentMarker]
                    if (!isDigit(c)) break
                    chunk.append(c)
                    currentMarker++
                }
            } else {
                while (currentMarker < sLength) {
                    c = s[currentMarker]
                    if (isDigit(c)) break
                    chunk.append(c)
                    currentMarker++
                }
            }
            return chunk.toString()
        }

        private fun compareString(s1: String?, s2: String?, ignoreCase: Boolean): Int {
            if (s1 == null || s2 == null) {
                return 0
            }

            var thisMarker = 0
            var thatMarker = 0
            val s1Length = s1.length
            val s2Length = s2.length

            while (thisMarker < s1Length && thatMarker < s2Length) {
                val thisChunk = getChunk(s1, s1Length, thisMarker)
                thisMarker += thisChunk.length

                val thatChunk = getChunk(s2, s2Length, thatMarker)
                thatMarker += thatChunk.length

                // If both chunks contain numeric characters, sort them numerically
                val result: Int
                if (isDigit(thisChunk[0]) && isDigit(thatChunk[0])) {
                    // Simple chunk comparison by length.
                    val thisChunkLength = thisChunk.length
                    result = thisChunkLength - thatChunk.length
                    // If equal, the first different number counts
                    if (result == 0) {
                        for (i in 0 until thisChunkLength) {
                            val diff = thisChunk[i] - thatChunk[i]
                            if (diff != 0) {
                                return diff
                            }
                        }
                    }
                } else {
                    result = if (ignoreCase) {
                        thisChunk.compareTo(thatChunk, ignoreCase = true)
                    } else {
                        thisChunk.compareTo(thatChunk)
                    }
                }

                if (result != 0) return result
            }

            return s1Length - s2Length
        }

        @JvmStatic
        fun compareString(s1: String?, s2: String?): Int {
            return compareString(s1, s2, false)
        }

        @JvmStatic
        fun compareStringIgnoreCase(s1: String?, s2: String?): Int {
            return compareString(s1, s2, true)
        }
    }
}
