// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.runner

import android.os.Build
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.misc.NoOps
import java.io.File
import java.io.IOException
import java.io.StringWriter
import java.io.Writer
import java.security.InvalidParameterException
import java.util.*

object RunnerUtils {
    @JvmField
    val TAG: String = RunnerUtils::class.java.simpleName

    @JvmField
    val CMD_AM = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) "cmd activity" else "am"\n@JvmField
    val CMD_PM = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) "cmd package" else "pm"\nprivate const val EMPTY = ""\n/**
     * Translator object for escaping Shell command language.
     *
     * @see [Shell Command Language](http://pubs.opengroup.org/onlinepubs/7908799/xcu/chap2.html)
     */
    @JvmField
    val ESCAPE_XSI: LookupTranslator

    init {
        val escapeXsiMap = HashMap<CharSequence, CharSequence>()
        escapeXsiMap["|"] = "\|"\nescapeXsiMap["&"] = "\&"\nescapeXsiMap[";"] = "\;"\nescapeXsiMap["<"] = "\<"\nescapeXsiMap[">"] = "\>"\nescapeXsiMap["("] = "\("\nescapeXsiMap[")"] = "\)"\nescapeXsiMap["$"] = "\$"\nescapeXsiMap["`"] = "`"\nescapeXsiMap[""] = ""\nescapeXsiMap["""] = """\nescapeXsiMap["'"] = "'"\nescapeXsiMap[" "] = "\ "\nescapeXsiMap["	"] = "	"\nescapeXsiMap["\n"] = EMPTY
        escapeXsiMap["\n"] = EMPTY
        escapeXsiMap["*"] = "\*"\nescapeXsiMap["?"] = "\?"\nescapeXsiMap["["] = "\["\nescapeXsiMap["#"] = "\#"\nescapeXsiMap["~"] = "\~"\nescapeXsiMap["="] = "\="\nescapeXsiMap["%"] = "\%"\nESCAPE_XSI = LookupTranslator(Collections.unmodifiableMap(escapeXsiMap))
    }

    /**
     * Escapes the characters in a [String] using XSI rules.
     *
     * **Beware!** In most cases you don't want to escape shell commands but use multi-argument
     * methods provided by [java.lang.ProcessBuilder] or [java.lang.Runtime.exec]
     * instead.
     *
     * Example:
     * ```
     * input string: He didn't say, "Stop!"\n* output string: He\ didn't\ say,\ "Stop!"\n* ```
     *
     * @param input String to escape values in, may be null
     * @return String with escaped values, `null` if null string input
     * @see [Shell Command Language](http://pubs.opengroup.org/onlinepubs/7908799/xcu/chap2.html)
     */
    @JvmStatic
    fun escape(input: String): String? {
        return ESCAPE_XSI.translate(input)
    }

    @JvmStatic
    @NoOps
    fun isRootGiven(): Boolean {
        return isAppGrantedRoot() == true
    }

    @JvmStatic
    @NoOps
    fun isRootAvailable(): Boolean {
        return isAppGrantedRoot() != false
    }

    /**
     * @see com.topjohnwu.superuser.Shell.isAppGrantedRoot
     */
    @JvmStatic
    @NoOps
    fun isAppGrantedRoot(): Boolean? {
        if (Runner.getRootInstance().isRoot()) {
            // Root granted
            return true
        }
        // Check if root is available
        val pathEnv = System.getenv("PATH")
        Log.d(TAG, "PATH=%s", pathEnv)
        if (pathEnv == null) return false
        for (pathDir in pathEnv.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
            val suFile = File(pathDir, "su")
            Log.d(TAG, "SU(file=%s, exists=%s, executable=%s)", suFile, suFile.exists(), suFile.canExecute())
            if (File(pathDir, "su").canExecute()) {
                // Root available but App Manager is not granted root
                return null
            }
        }
        return false
    }

    /**
     * An API for translating text.
     * Its core use is to escape and unescape text. Because escaping and unescaping
     * is completely contextual, the API does not present two separate signatures.
     *
     * @since 1.0
     */
    class LookupTranslator(lookupMap: Map<CharSequence, CharSequence>?) {
        private val mLookupMap = HashMap<String, String>()
        private val mPrefixSet = BitSet()
        private val mShortest: Int
        private val mLongest: Int

        init {
            if (lookupMap == null) {
                throw InvalidParameterException("lookupMap cannot be null")
            }
            var currentShortest = Int.MAX_VALUE
            var currentLongest = 0

            for ((key, value) in lookupMap) {
                mLookupMap[key.toString()] = value.toString()
                mPrefixSet.set(key[0].toInt())
                val sz = key.length
                if (sz < currentShortest) {
                    currentShortest = sz
                }
                if (sz > currentLongest) {
                    currentLongest = sz
                }
            }
            mShortest = currentShortest
            mLongest = currentLongest
        }

        /**
         * Translate a set of codepoints, represented by an int index into a CharSequence,
         * into another set of codepoints. The number of codepoints consumed must be returned,
         * and the only IOExceptions thrown must be from interacting with the Writer so that
         * the top level API may reliably ignore StringWriter IOExceptions.
         *
         * @param input CharSequence that is being translated
         * @param index int representing the current point of translation
         * @param out   Writer to translate the text to
         * @return int count of codepoints consumed
         * @throws IOException if and only if the Writer produces an IOException
         */
        @Throws(IOException::class)
        fun translate(input: CharSequence, index: Int, out: Writer): Int {
            // check if translation exists for the input at position index
            if (mPrefixSet[input[index].toInt()]) {
                var max = mLongest
                if (index + mLongest > input.length) {
                    max = input.length - index
                }
                // implement greedy algorithm by trying maximum match first
                for (i in max downTo mShortest) {
                    val subSeq = input.subSequence(index, index + i)
                    val result = mLookupMap[subSeq.toString()]

                    if (result != null) {
                        out.write(result)
                        return i
                    }
                }
            }
            return 0
        }

        /**
         * Helper for non-Writer usage.
         *
         * @param input CharSequence to be translated
         * @return String output of translation
         */
        fun translate(input: CharSequence?): String? {
            if (input == null) {
                return null
            }
            return try {
                val writer = StringWriter(input.length * 2)
                translate(input, writer)
                writer.toString()
            } catch (ioe: IOException) {
                // this should never ever happen while writing to a StringWriter
                throw RuntimeException(ioe)
            }
        }

        /**
         * Translate an input onto a Writer. This is intentionally final as its algorithm is
         * tightly coupled with the abstract method of this class.
         *
         * @param input CharSequence that is being translated
         * @param out   Writer to translate the text to
         * @throws IOException if and only if the Writer produces an IOException
         */
        @Throws(IOException::class)
        fun translate(input: CharSequence?, out: Writer) {
            if (input == null) {
                return
            }
            var pos = 0
            val len = input.length
            while (pos < len) {
                val consumed = translate(input, pos, out)
                if (consumed == 0) {
                    // inlined implementation of Character.toChars(Character.codePointAt(input, pos))
                    // avoids allocating temp char arrays and duplicate checks
                    val c1 = input[pos]
                    out.write(c1.toInt())
                    pos++
                    if (Character.isHighSurrogate(c1) && pos < len) {
                        val c2 = input[pos]
                        if (Character.isLowSurrogate(c2)) {
                            out.write(c2.toInt())
                            pos++
                        }
                    }
                    continue
                }
                // contract with translators is that they have to understand codepoints
                // and they just took care of a surrogate pair
                for (pt in 0 until consumed) {
                    pos += Character.charCount(Character.codePointAt(input, pos))
                }
            }
        }
    }
}
