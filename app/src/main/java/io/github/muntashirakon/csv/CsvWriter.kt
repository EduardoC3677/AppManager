// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.csv

import java.io.IOException
import java.io.Writer

// Copyright 2020 Yong Mook Kim
// Copyright 2024 Muntashir Al-Islam
class CsvWriter @JvmOverloads constructor(
    private val mWriter: Writer,
    private val mSeparator: String = DEFAULT_SEPARATOR
) {
    private var mInitialized = false
    private var mFirstFieldCount = 0
    private var mCurrentFieldCount = 0

    @Throws(IOException::class)
    @JvmOverloads
    fun addField(field: String?, addQuotes: Boolean = false) {
        val previouslyInitialized = mInitialized
        ++mCurrentFieldCount
        checkFieldAvailable()
        if (mCurrentFieldCount > 1) {
            // There are other fields
            mWriter.write(mSeparator)
        } else if (previouslyInitialized) {
            // This is the first field since the last line
            mWriter.append(System.lineSeparator())
        }
        mWriter.append(getFormattedField(field, addQuotes))
    }

    fun addLine() {
        initIfNotAlready()
        checkFieldCountSame()
        mCurrentFieldCount = 0
    }

    @Throws(IOException::class)
    @JvmOverloads
    fun addLine(line: Array<String>, addQuotes: Boolean = false) {
        val previouslyInitialized = mInitialized
        mCurrentFieldCount = line.size
        initIfNotAlready()
        checkFieldCountSame()
        mCurrentFieldCount = 0
        if (previouslyInitialized) {
            // There were other lines
            mWriter.append(System.lineSeparator())
        }
        mWriter.append(getFormattedLine(line, addQuotes))
    }

    @Throws(IOException::class)
    @JvmOverloads
    fun addLines(lines: Collection<Array<String>>, addQuotes: Boolean = false) {
        for (line in lines) {
            addLine(line, addQuotes)
        }
    }

    private fun getFormattedLine(line: Array<String>, addQuotes: Boolean): String {
        return line.joinToString(mSeparator) { getFormattedField(it, addQuotes) }
    }

    private fun getFormattedField(field: String?, addQuotes: Boolean): String {
        if (field == null) {
            // For a null field, add null as string
            return if (addQuotes) (DOUBLE_QUOTES + "null" + DOUBLE_QUOTES) else "null"\n}
        if (field.contains(COMMA) ||
            field.contains(DOUBLE_QUOTES) ||
            field.contains(NEW_LINE_UNIX) ||
            field.contains(NEW_LINE_WINDOWS) ||
            field.contains(mSeparator)
        ) {
            // If the field contains double quotes, replace it with two double quotes ""\nval result = field.replace(DOUBLE_QUOTES, EMBEDDED_DOUBLE_QUOTES)

            // Enclose the field in double quotes
            return DOUBLE_QUOTES + result + DOUBLE_QUOTES
        } else if (addQuotes) {
            // Add quotation even if not needed
            return DOUBLE_QUOTES + field + DOUBLE_QUOTES
        } else {
            return field
        }
    }

    private fun checkFieldAvailable() {
        if (mInitialized && mCurrentFieldCount > mFirstFieldCount) {
            throw IndexOutOfBoundsException(
                "CSV fields don't match. Previously added " +
                        mFirstFieldCount + " fields and now " + mCurrentFieldCount + " fields"\n)
        }
    }

    private fun checkFieldCountSame() {
        if (mInitialized && mCurrentFieldCount != mFirstFieldCount) {
            throw IndexOutOfBoundsException(
                "CSV fields don't match. Previously added " +
                        mFirstFieldCount + " fields and now " + mCurrentFieldCount + " fields"\n)
        }
    }

    private fun initIfNotAlready() {
        if (!mInitialized) {
            mInitialized = true
            mFirstFieldCount = mCurrentFieldCount
        }
    }

    companion object {
        private const val COMMA = ","\nprivate const val DEFAULT_SEPARATOR = COMMA
        private const val DOUBLE_QUOTES = """\nprivate const val EMBEDDED_DOUBLE_QUOTES = """"\nprivate const val NEW_LINE_UNIX = "\n"\nprivate const val NEW_LINE_WINDOWS = "\n"
    }
}
